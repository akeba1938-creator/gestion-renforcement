package com.ecole.saas.schoolimport.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.attendance.AttendanceImportPort;
import com.ecole.saas.attendance.AttendanceSessionImportContext;
import com.ecole.saas.attendance.ImportAttendanceEntryCommand;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.family.ImportFamilyCommand;
import com.ecole.saas.family.ImportGuardianCommand;
import com.ecole.saas.family.ImportStudentGuardianLinkCommand;
import com.ecole.saas.grade.EvaluationImportContext;
import com.ecole.saas.grade.GradeImportPort;
import com.ecole.saas.grade.ImportGradeEntryCommand;
import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.domain.ImportFieldParser;
import com.ecole.saas.schoolimport.domain.ImportFieldParser.FieldParseException;
import com.ecole.saas.schoolimport.domain.ImportRowSanitizer;
import com.ecole.saas.schoolimport.domain.ParsedTabularRow;
import com.ecole.saas.schoolimport.domain.SchoolImportConflictException;
import com.ecole.saas.schoolimport.domain.SchoolImportNotFoundException;
import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import com.ecole.saas.student.ImportEnrollmentCommand;
import com.ecole.saas.student.ImportStudentCommand;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.teacher.ImportTeacherCommand;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Moteur d'analyse à sec et de validation stricte ligne par ligne avec préchargement en lot
 * (zéro N+1) de toutes les références inter-modules, détection des doublons intra-fichier et base,
 * projection des capacités de classe et projection du quota d'abonnement {@code STUDENTS}.
 */
@Component
class SchoolImportRowAnalyzer {

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    sealed interface PreparedRowPayload permits
            PreparedStudentRow,
            PreparedFamilyGuardianRow,
            PreparedTeacherRow,
            PreparedEnrollmentRow,
            PreparedGradeRow,
            PreparedAttendanceRow {
    }

    record PreparedStudentRow(
            UUID existingStudentId,
            ImportStudentCommand command,
            UUID academicYearId,
            UUID optionalClassRoomId) implements PreparedRowPayload {
    }

    record PreparedFamilyGuardianRow(
            UUID existingFamilyId,
            ImportFamilyCommand familyCommand,
            UUID existingGuardianId,
            ImportGuardianCommand guardianCommand,
            UUID studentId,
            ImportStudentGuardianLinkCommand linkCommand) implements PreparedRowPayload {
    }

    record PreparedTeacherRow(
            UUID existingTeacherId,
            ImportTeacherCommand command) implements PreparedRowPayload {
    }

    record PreparedEnrollmentRow(
            UUID existingEnrollmentId,
            UUID currentClassRoomId,
            ImportEnrollmentCommand command) implements PreparedRowPayload {
    }

    record PreparedGradeRow(
            UUID evaluationId,
            ImportGradeEntryCommand command) implements PreparedRowPayload {
    }

    record PreparedAttendanceRow(
            UUID attendanceSessionId,
            ImportAttendanceEntryCommand command) implements PreparedRowPayload {
    }

    record AnalyzedRow(
            int rowNumber,
            String naturalKey,
            ImportRowStatus rowStatus,
            ImportRowAction plannedAction,
            String errorCode,
            String errorField,
            String errorMessage,
            String targetEntityType,
            UUID targetEntityId,
            String previewJson,
            PreparedRowPayload payload) {

        static AnalyzedRow error(
                int rowNumber,
                String naturalKey,
                String errorCode,
                String errorField,
                String errorMessage,
                String previewJson) {
            return new AnalyzedRow(
                    rowNumber,
                    naturalKey,
                    ImportRowStatus.ERROR,
                    ImportRowAction.ERROR,
                    errorCode,
                    errorField,
                    errorMessage,
                    null,
                    null,
                    previewJson,
                    null);
        }

        static AnalyzedRow skipDuplicate(
                int rowNumber,
                String naturalKey,
                String targetEntityType,
                UUID targetEntityId,
                String previewJson) {
            return new AnalyzedRow(
                    rowNumber,
                    naturalKey,
                    ImportRowStatus.DUPLICATE,
                    ImportRowAction.SKIP_DUPLICATE,
                    null,
                    null,
                    null,
                    targetEntityType,
                    targetEntityId,
                    previewJson,
                    null);
        }

        static AnalyzedRow valid(
                int rowNumber,
                String naturalKey,
                ImportRowAction action,
                String targetEntityType,
                UUID targetEntityId,
                String previewJson,
                PreparedRowPayload payload) {
            return new AnalyzedRow(
                    rowNumber,
                    naturalKey,
                    ImportRowStatus.VALID,
                    action,
                    null,
                    null,
                    null,
                    targetEntityType,
                    targetEntityId,
                    previewJson,
                    payload);
        }
    }

    record AnalysisResult(
            UUID resolvedAcademicYearId,
            UUID resolvedEvaluationId,
            UUID resolvedAttendanceSessionId,
            List<AnalyzedRow> rows,
            int totalRows,
            int validRows,
            int errorRows,
            int duplicateRows,
            String quotaResource,
            Long quotaLimit,
            Long quotaUsedBefore,
            Long quotaProjectedAfter) {
    }

    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final GradeImportPort gradeImportPort;
    private final AttendanceImportPort attendanceImportPort;
    private final SchoolImportAccessGuard accessGuard;
    private final SchoolImportSubscriptionGuard subscriptionGuard;

    SchoolImportRowAnalyzer(
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            GradeImportPort gradeImportPort,
            AttendanceImportPort attendanceImportPort,
            SchoolImportAccessGuard accessGuard,
            SchoolImportSubscriptionGuard subscriptionGuard) {
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.gradeImportPort = gradeImportPort;
        this.attendanceImportPort = attendanceImportPort;
        this.accessGuard = accessGuard;
        this.subscriptionGuard = subscriptionGuard;
    }

    AnalysisResult analyze(
            ImportType importType,
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows,
            UUID requestedAcademicYearId,
            UUID requestedEvaluationId,
            UUID requestedAttendanceSessionId,
            UUID actorId,
            LocalDate schoolToday) {
        return switch (importType) {
            case STUDENTS -> analyzeStudents(duplicateStrategy, rows, requestedAcademicYearId, schoolToday);
            case FAMILIES_GUARDIANS -> analyzeFamiliesGuardians(duplicateStrategy, rows);
            case TEACHERS -> analyzeTeachers(duplicateStrategy, rows);
            case ENROLLMENTS -> analyzeEnrollments(duplicateStrategy, rows, requestedAcademicYearId, schoolToday);
            case GRADES -> analyzeGrades(duplicateStrategy, rows, requestedEvaluationId, actorId);
            case ATTENDANCE -> analyzeAttendance(duplicateStrategy, rows, requestedAttendanceSessionId, actorId);
        };
    }

    private AnalysisResult analyzeStudents(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows,
            UUID requestedAcademicYearId,
            LocalDate schoolToday) {
        // 1. Préchargement en lot des références académiques (si année fournie ou class_code présent)
        boolean anyClassCode = rows.stream().anyMatch(r -> r.get("class_code") != null);
        AcademicYearSummary year = null;
        Map<String, ClassRoomSummary> classRoomsByCode = Map.of();
        Map<UUID, Long> projectedClassCounts = new HashMap<>();

        if (requestedAcademicYearId != null || anyClassCode) {
            year = resolveMutableAcademicYear(requestedAcademicYearId);
            List<ClassRoomSummary> rooms = academicStructurePort.findClassRoomsByAcademicYear(year.id());
            classRoomsByCode = rooms.stream()
                    .collect(Collectors.toMap(
                            r -> r.code().trim().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
            Set<UUID> roomIds = rooms.stream().map(ClassRoomSummary::id).collect(Collectors.toSet());
            projectedClassCounts.putAll(studentDirectoryPort.countActiveEnrollmentsByClassRooms(roomIds));
        }

        // 2. Préchargement en lot des élèves existants (par matricule et par nom)
        Set<String> matricules = rows.stream()
                .map(r -> r.get("matricule"))
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> lastNames = rows.stream()
                .map(r -> r.get("last_name"))
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        Map<String, StudentSummary> studentsByMatricule = studentDirectoryPort
                .findStudentsByMatricules(matricules).stream()
                .collect(Collectors.toMap(
                        s -> s.matricule().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Map<String, StudentSummary> studentsByIdentityKey = new HashMap<>();
        for (StudentSummary s : studentDirectoryPort.findStudentsByLastNamesIgnoreCase(lastNames)) {
            if (s.birthDate() != null) {
                studentsByIdentityKey.putIfAbsent(
                        studentIdentityKey(s.firstName(), s.lastName(), s.birthDate()), s);
            }
        }

        long activeStudentsBefore = studentDirectoryPort.countActiveStudents();
        QuotaCheckResult baselineQuota = subscriptionGuard.evaluateStudentQuota(activeStudentsBefore, 0L);
        long studentQuotaLimit = baselineQuota.limit();
        long projectedNewStudents = 0L;

        Set<String> seenMatriculesInFile = new HashSet<>();
        Set<String> seenIdentityKeysInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String matricule = ImportFieldParser.normalizeCode(row.get("matricule"), "matricule", false);
                String firstName = ImportFieldParser.requireText(row.get("first_name"), "first_name", 100);
                String lastName = ImportFieldParser.requireText(row.get("last_name"), "last_name", 100);
                String gender = ImportFieldParser.parseGender(row.get("gender"), "gender", true);
                LocalDate birthDate = ImportFieldParser.parseDate(row.get("birth_date"), "birth_date", true);
                if (birthDate.isBefore(MIN_BIRTH_DATE) || birthDate.isAfter(schoolToday)) {
                    throw new FieldParseException(
                            "birth_date",
                            "INVALID_BIRTH_DATE",
                            "La date de naissance doit être comprise entre 1900-01-01 et la date du jour.");
                }
                LocalDate admissionDate = ImportFieldParser.parseDate(row.get("admission_date"), "admission_date", false);
                if (admissionDate != null && birthDate != null && admissionDate.isBefore(birthDate)) {
                    throw new FieldParseException(
                            "admission_date",
                            "INVALID_ADMISSION_DATE",
                            "La date d'admission ne peut pas être antérieure à la date de naissance.");
                }
                String birthPlace = ImportFieldParser.optionalText(row.get("birth_place"), "birth_place", 120);
                String nationality = ImportFieldParser.optionalText(row.get("nationality"), "nationality", 80);
                String address = ImportFieldParser.optionalText(row.get("address"), "address", 255);
                String phone = ImportFieldParser.parsePhoneE164(row.get("phone"), "phone", false);
                String email = ImportFieldParser.parseEmail(row.get("email"), "email");
                String medicalNotes = ImportFieldParser.optionalText(row.get("medical_notes"), "medical_notes", 500);
                String classCode = ImportFieldParser.normalizeCode(row.get("class_code"), "class_code", false);

                String identityKey = birthDate != null ? studentIdentityKey(firstName, lastName, birthDate) : null;
                String naturalKey = matricule != null ? matricule : (identityKey != null ? identityKey : firstName + "|" + lastName);

                // Détection doublon intra-fichier
                if (matricule != null && !seenMatriculesInFile.add(matricule)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), naturalKey, "INTRA_FILE_DUPLICATE", "matricule",
                            "Matricule en doublon dans le fichier importé.", preview));
                    continue;
                }
                if (identityKey != null && !seenIdentityKeysInFile.add(identityKey)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), naturalKey, "INTRA_FILE_DUPLICATE", "first_name",
                            "Doublon d'élève (prénom, nom, date de naissance) détecté dans le fichier.", preview));
                    continue;
                }

                // Résolution classe optionnelle
                ClassRoomSummary targetClass = null;
                if (classCode != null) {
                    targetClass = classRoomsByCode.get(classCode);
                    if (targetClass == null) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "CLASSROOM_NOT_FOUND", "class_code",
                                "Classe '" + classCode + "' introuvable pour l'année scolaire sélectionnée.", preview));
                        continue;
                    }
                    if (!"ACTIVE".equals(targetClass.status())) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "CLASSROOM_ARCHIVED", "class_code",
                                "La classe '" + classCode + "' est archivée.", preview));
                        continue;
                    }
                }

                // Recherche existant en base (par matricule ou identité)
                StudentSummary existing = null;
                if (matricule != null) {
                    existing = studentsByMatricule.get(matricule);
                    if (existing == null) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "STUDENT_NOT_FOUND", "matricule",
                                "Aucun élève existant trouvé pour le matricule '" + matricule
                                        + "' (laissez la colonne vide pour créer un nouvel élève).", preview));
                        continue;
                    }
                } else if (identityKey != null) {
                    existing = studentsByIdentityKey.get(identityKey);
                }

                ImportStudentCommand cmd = new ImportStudentCommand(
                        firstName, lastName, gender, birthDate, birthPlace, nationality,
                        address, phone, email, medicalNotes, admissionDate);

                if (existing != null) {
                    if ("ARCHIVED".equals(existing.status())) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), existing.matricule(), "STUDENT_ARCHIVED", "matricule",
                                "Impossible de modifier un élève archivé (" + existing.matricule() + ").", preview));
                        continue;
                    }
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), existing.matricule(), "DUPLICATE_RECORD", "matricule",
                                "L'élève existe déjà dans l'établissement (" + existing.matricule() + ").", preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), existing.matricule(), "STUDENT", existing.id(), preview));
                    } else {
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(),
                                existing.matricule(),
                                ImportRowAction.UPDATE,
                                "STUDENT",
                                existing.id(),
                                preview,
                                new PreparedStudentRow(existing.id(), cmd, year != null ? year.id() : null,
                                        targetClass != null ? targetClass.id() : null)));
                    }
                } else {
                    // Vérification projection quota STUDENTS
                    if (activeStudentsBefore + projectedNewStudents + 1L > studentQuotaLimit) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(),
                                naturalKey,
                                "SUBSCRIPTION_QUOTA_EXCEEDED",
                                "quota",
                                "Quota d'élèves de l'abonnement dépassé (limite : " + studentQuotaLimit
                                        + ", utilisés : " + (activeStudentsBefore + projectedNewStudents) + ").",
                                preview));
                        continue;
                    }
                    // Vérification capacité classe si inscription demandée
                    if (targetClass != null) {
                        long currentInClass = projectedClassCounts.getOrDefault(targetClass.id(), 0L);
                        if (currentInClass + 1L > targetClass.capacity()) {
                            analyzed.add(AnalyzedRow.error(
                                    row.rowNumber(),
                                    naturalKey,
                                    "CLASSROOM_CAPACITY_EXCEEDED",
                                    "class_code",
                                    "La classe '" + targetClass.code() + "' dépasse sa capacité maximale ("
                                            + targetClass.capacity() + ").",
                                    preview));
                            continue;
                        }
                        projectedClassCounts.put(targetClass.id(), currentInClass + 1L);
                    }
                    projectedNewStudents++;
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(),
                            naturalKey,
                            ImportRowAction.CREATE,
                            "STUDENT",
                            null,
                            preview,
                            new PreparedStudentRow(null, cmd, year != null ? year.id() : null,
                                    targetClass != null ? targetClass.id() : null)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(
                year != null ? year.id() : null,
                null,
                null,
                analyzed,
                QuotaResource.STUDENTS.name(),
                studentQuotaLimit,
                activeStudentsBefore,
                activeStudentsBefore + projectedNewStudents);
    }

    private AnalysisResult analyzeFamiliesGuardians(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows) {
        // Préchargement en lot : familles par code, tuteurs par téléphone, élèves par matricule
        Set<String> familyCodes = rows.stream()
                .map(r -> r.get("family_code"))
                .filter(Objects::nonNull)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> phones = rows.stream()
                .map(r -> r.get("guardian_phone"))
                .filter(Objects::nonNull)
                .map(p -> p.trim().replaceAll("[\\s.-]", ""))
                .collect(Collectors.toSet());
        Set<String> studentMatricules = rows.stream()
                .map(r -> r.get("student_matricule"))
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Map<String, FamilySummary> familiesByCode = familyDirectoryPort.findFamiliesByCodes(familyCodes).stream()
                .filter(f -> f.code() != null)
                .collect(Collectors.toMap(
                        f -> f.code().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Map<String, GuardianContactSummary> guardiansByPhone = familyDirectoryPort.findGuardiansByPhones(phones)
                .stream()
                .collect(Collectors.toMap(GuardianContactSummary::phone, Function.identity(), (a, b) -> a));

        Map<String, StudentSummary> studentsByMatricule = studentDirectoryPort
                .findStudentsByMatricules(studentMatricules).stream()
                .collect(Collectors.toMap(
                        s -> s.matricule().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Set<String> seenKeysInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String familyCode = ImportFieldParser.normalizeCode(row.get("family_code"), "family_code", false);
                String familyName = ImportFieldParser.optionalText(row.get("family_name"), "family_name", 150);
                String familyCity = ImportFieldParser.optionalText(row.get("family_city"), "family_city", 100);
                String firstName = ImportFieldParser.requireText(
                        row.get("guardian_first_name"), "guardian_first_name", 100);
                String lastName = ImportFieldParser.requireText(
                        row.get("guardian_last_name"), "guardian_last_name", 100);
                String gender = ImportFieldParser.parseGender(row.get("guardian_gender"), "guardian_gender", false);
                String phone = ImportFieldParser.parsePhoneE164(row.get("guardian_phone"), "guardian_phone", true);
                String secondaryPhone = ImportFieldParser.parsePhoneE164(
                        row.get("guardian_secondary_phone"), "guardian_secondary_phone", false);
                String email = ImportFieldParser.parseEmail(row.get("guardian_email"), "guardian_email");
                String profession = ImportFieldParser.optionalText(row.get("profession"), "profession", 120);
                String address = ImportFieldParser.optionalText(row.get("address"), "address", 255);
                String nationalId = ImportFieldParser.optionalText(
                        row.get("national_id_number"), "national_id_number", 60);
                String preferredLang = ImportFieldParser.optionalText(
                        row.get("preferred_language"), "preferred_language", 10);
                String studentMatricule = ImportFieldParser.normalizeCode(
                        row.get("student_matricule"), "student_matricule", false);
                String relationship = ImportFieldParser.parseRelationshipType(
                        row.get("relationship_type"), "relationship_type");
                Boolean primaryContact = ImportFieldParser.parseOptionalBoolean(
                        row.get("primary_contact"), "primary_contact");
                Boolean financialResponsible = ImportFieldParser.parseOptionalBoolean(
                        row.get("financial_responsible"), "financial_responsible");

                String naturalKey = studentMatricule != null ? phone + "|" + studentMatricule : phone;
                if (!seenKeysInFile.add(naturalKey)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), naturalKey, "INTRA_FILE_DUPLICATE", "guardian_phone",
                            "Doublon de parent/tuteur (et élève lié) détecté dans le fichier.", preview));
                    continue;
                }

                StudentSummary student = null;
                if (studentMatricule != null) {
                    student = studentsByMatricule.get(studentMatricule);
                    if (student == null) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "STUDENT_NOT_FOUND", "student_matricule",
                                "Élève introuvable pour le matricule '" + studentMatricule + "'.", preview));
                        continue;
                    }
                    if ("ARCHIVED".equals(student.status())) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "STUDENT_ARCHIVED", "student_matricule",
                                "Impossible de rattacher un tuteur à un élève archivé.", preview));
                        continue;
                    }
                }

                FamilySummary existingFamily = familyCode != null ? familiesByCode.get(familyCode) : null;
                if (existingFamily != null && !existingFamily.active()) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), naturalKey, "FAMILY_ARCHIVED", "family_code",
                            "La famille '" + familyCode + "' est archivée.", preview));
                    continue;
                }

                ImportFamilyCommand familyCmd = null;
                if (familyCode != null || familyName != null) {
                    String effectiveFamilyName = familyName != null ? familyName : "Famille " + lastName;
                    familyCmd = new ImportFamilyCommand(
                            familyCode, effectiveFamilyName, phone, email, address, familyCity, null);
                }

                GuardianContactSummary existingGuardian = guardiansByPhone.get(phone);
                ImportGuardianCommand guardianCmd = new ImportGuardianCommand(
                        existingFamily != null ? existingFamily.id() : null,
                        firstName, lastName, gender, phone, secondaryPhone, email,
                        profession, address, nationalId, preferredLang != null ? preferredLang : "fr");

                ImportStudentGuardianLinkCommand linkCmd = student != null
                        ? new ImportStudentGuardianLinkCommand(
                                student.id(),
                                existingGuardian != null ? existingGuardian.id() : null,
                                relationship,
                                primaryContact,
                                financialResponsible,
                                Boolean.TRUE,
                                Boolean.FALSE,
                                Boolean.TRUE,
                                null)
                        : null;

                if (existingGuardian != null) {
                    if (!existingGuardian.active()) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "GUARDIAN_ARCHIVED", "guardian_phone",
                                "Le tuteur portant ce numéro est archivé et ne peut pas être modifié.", preview));
                        continue;
                    }
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), naturalKey, "DUPLICATE_RECORD", "guardian_phone",
                                "Un parent/tuteur avec ce téléphone existe déjà (" + phone + ").", preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), naturalKey, "GUARDIAN", existingGuardian.id(), preview));
                    } else {
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(),
                                naturalKey,
                                ImportRowAction.UPDATE,
                                "GUARDIAN",
                                existingGuardian.id(),
                                preview,
                                new PreparedFamilyGuardianRow(
                                        existingFamily != null ? existingFamily.id() : null,
                                        familyCmd,
                                        existingGuardian.id(),
                                        guardianCmd,
                                        student != null ? student.id() : null,
                                        linkCmd)));
                    }
                } else {
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(),
                            naturalKey,
                            ImportRowAction.CREATE,
                            "GUARDIAN",
                            null,
                            preview,
                            new PreparedFamilyGuardianRow(
                                    existingFamily != null ? existingFamily.id() : null,
                                    familyCmd,
                                    null,
                                    guardianCmd,
                                    student != null ? student.id() : null,
                                    linkCmd)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(null, null, null, analyzed, null, null, null, null);
    }

    private AnalysisResult analyzeTeachers(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows) {
        // Préchargement en lot : enseignants par code employé + matières par code
        Set<String> employeeCodes = rows.stream()
                .map(r -> r.get("employee_code"))
                .filter(Objects::nonNull)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Map<String, TeacherSummary> teachersByCode = teacherDirectoryPort
                .findTeachersByEmployeeCodes(employeeCodes).stream()
                .collect(Collectors.toMap(
                        t -> t.employeeCode().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Map<String, SubjectSummary> subjectsByCode = academicStructurePort.findAllSubjects().stream()
                .collect(Collectors.toMap(
                        s -> s.code().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Set<String> seenCodesInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String employeeCode = ImportFieldParser.normalizeCode(row.get("employee_code"), "employee_code", true);
                String firstName = ImportFieldParser.requireText(row.get("first_name"), "first_name", 100);
                String lastName = ImportFieldParser.requireText(row.get("last_name"), "last_name", 100);
                String gender = ImportFieldParser.parseGender(row.get("gender"), "gender", false);
                String teacherType = ImportFieldParser.parseTeacherType(row.get("teacher_type"), "teacher_type");
                String phone = ImportFieldParser.parsePhoneE164(row.get("phone"), "phone", true);
                String secondaryPhone = ImportFieldParser.parsePhoneE164(
                        row.get("secondary_phone"), "secondary_phone", false);
                String email = ImportFieldParser.parseEmail(row.get("email"), "email");
                String address = ImportFieldParser.optionalText(row.get("address"), "address", 255);
                String nationalId = ImportFieldParser.optionalText(
                        row.get("national_id_number"), "national_id_number", 60);
                LocalDate hireDate = ImportFieldParser.parseDate(row.get("hire_date"), "hire_date", false);
                Long hourlyRateXof = ImportFieldParser.parseOptionalAmountXof(
                        row.get("hourly_rate_xof"), "hourly_rate_xof");
                String subjectCode = ImportFieldParser.normalizeCode(row.get("subject_code"), "subject_code", false);
                String notes = ImportFieldParser.optionalText(row.get("notes"), "notes", 500);

                if (!seenCodesInFile.add(employeeCode)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), employeeCode, "INTRA_FILE_DUPLICATE", "employee_code",
                            "Code employé en doublon dans le fichier importé.", preview));
                    continue;
                }

                UUID primarySubjectId = null;
                if (subjectCode != null) {
                    SubjectSummary subject = subjectsByCode.get(subjectCode);
                    if (subject == null) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), employeeCode, "SUBJECT_NOT_FOUND", "subject_code",
                                "Matière '" + subjectCode + "' introuvable.", preview));
                        continue;
                    }
                    if (!subject.active()) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), employeeCode, "SUBJECT_INACTIVE", "subject_code",
                                "La matière '" + subjectCode + "' est inactive.", preview));
                        continue;
                    }
                    primarySubjectId = subject.id();
                }

                ImportTeacherCommand cmd = new ImportTeacherCommand(
                        employeeCode, firstName, lastName, gender, teacherType, phone, secondaryPhone,
                        email, address, nationalId, hireDate, hourlyRateXof, primarySubjectId, notes);

                TeacherSummary existing = teachersByCode.get(employeeCode);
                if (existing != null) {
                    if ("ARCHIVED".equals(existing.status())) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), employeeCode, "TEACHER_ARCHIVED", "employee_code",
                                "Impossible de modifier un enseignant archivé (" + employeeCode + ").", preview));
                        continue;
                    }
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), employeeCode, "DUPLICATE_RECORD", "employee_code",
                                "Un enseignant avec le code '" + employeeCode + "' existe déjà.", preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), employeeCode, "TEACHER", existing.id(), preview));
                    } else {
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(), employeeCode, ImportRowAction.UPDATE, "TEACHER", existing.id(),
                                preview, new PreparedTeacherRow(existing.id(), cmd)));
                    }
                } else {
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(), employeeCode, ImportRowAction.CREATE, "TEACHER", null,
                            preview, new PreparedTeacherRow(null, cmd)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(null, null, null, analyzed, null, null, null, null);
    }

    private AnalysisResult analyzeEnrollments(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows,
            UUID requestedAcademicYearId,
            LocalDate schoolToday) {
        AcademicYearSummary year = resolveMutableAcademicYear(requestedAcademicYearId);

        List<ClassRoomSummary> rooms = academicStructurePort.findClassRoomsByAcademicYear(year.id());
        Map<String, ClassRoomSummary> roomsByCode = rooms.stream()
                .collect(Collectors.toMap(
                        r -> r.code().trim().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<UUID, Long> projectedClassCounts = new HashMap<>(
                studentDirectoryPort.countActiveEnrollmentsByClassRooms(
                        rooms.stream().map(ClassRoomSummary::id).collect(Collectors.toSet())));

        Set<String> matricules = rows.stream()
                .map(r -> r.get("student_matricule"))
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, StudentSummary> studentsByMatricule = studentDirectoryPort
                .findStudentsByMatricules(matricules).stream()
                .collect(Collectors.toMap(
                        s -> s.matricule().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Set<UUID> studentIds = studentsByMatricule.values().stream()
                .map(StudentSummary::id)
                .collect(Collectors.toSet());
        Map<UUID, StudentEnrollmentSummary> existingEnrollmentByStudent = studentDirectoryPort
                .findActiveEnrollmentsByYearAndStudents(year.id(), studentIds).stream()
                .collect(Collectors.toMap(
                        StudentEnrollmentSummary::studentId, Function.identity(), (a, b) -> a));

        Set<String> seenMatriculesInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String matricule = ImportFieldParser.normalizeCode(
                        row.get("student_matricule"), "student_matricule", true);
                String classCode = ImportFieldParser.normalizeCode(row.get("class_code"), "class_code", true);
                String enrollmentType = ImportFieldParser.parseEnrollmentType(
                        row.get("enrollment_type"), "enrollment_type");
                LocalDate enrollmentDate = ImportFieldParser.parseDate(
                        row.get("enrollment_date"), "enrollment_date", false);
                Boolean repeating = ImportFieldParser.parseOptionalBoolean(row.get("repeating"), "repeating");
                String previousSchool = ImportFieldParser.optionalText(
                        row.get("previous_school"), "previous_school", 150);
                String notes = ImportFieldParser.optionalText(row.get("notes"), "notes", 500);

                if (!seenMatriculesInFile.add(matricule)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "INTRA_FILE_DUPLICATE", "student_matricule",
                            "Élève en doublon dans le fichier d'inscriptions.", preview));
                    continue;
                }

                StudentSummary student = studentsByMatricule.get(matricule);
                if (student == null) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_FOUND", "student_matricule",
                            "Élève introuvable pour le matricule '" + matricule + "'.", preview));
                    continue;
                }
                if (!student.active()) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_ACTIVE", "student_matricule",
                            "L'élève '" + matricule + "' n'est pas au statut ACTIVE.", preview));
                    continue;
                }

                ClassRoomSummary targetClass = roomsByCode.get(classCode);
                if (targetClass == null) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "CLASSROOM_NOT_FOUND", "class_code",
                            "Classe '" + classCode + "' introuvable pour l'année " + year.code() + ".", preview));
                    continue;
                }
                if (!"ACTIVE".equals(targetClass.status())) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "CLASSROOM_ARCHIVED", "class_code",
                            "La classe '" + classCode + "' est archivée.", preview));
                    continue;
                }

                ImportEnrollmentCommand cmd = new ImportEnrollmentCommand(
                        student.id(),
                        year.id(),
                        targetClass.id(),
                        enrollmentType,
                        enrollmentDate != null ? enrollmentDate : schoolToday,
                        previousSchool,
                        repeating != null ? repeating : Boolean.FALSE,
                        notes);

                StudentEnrollmentSummary existing = existingEnrollmentByStudent.get(student.id());
                if (existing != null) {
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), matricule, "DUPLICATE_RECORD", "student_matricule",
                                "L'élève possède déjà une inscription active pour l'année " + year.code() + ".",
                                preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES
                            || targetClass.id().equals(existing.classRoomId())) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), matricule, "STUDENT_ENROLLMENT", existing.id(), preview));
                    } else {
                        long targetCount = projectedClassCounts.getOrDefault(targetClass.id(), 0L);
                        if (targetCount + 1L > targetClass.capacity()) {
                            analyzed.add(AnalyzedRow.error(
                                    row.rowNumber(), matricule, "CLASSROOM_CAPACITY_EXCEEDED", "class_code",
                                    "La classe cible '" + targetClass.code() + "' a atteint sa capacité maximale ("
                                            + targetClass.capacity() + ").",
                                    preview));
                            continue;
                        }
                        projectedClassCounts.put(targetClass.id(), targetCount + 1L);
                        projectedClassCounts.computeIfPresent(
                                existing.classRoomId(), (k, v) -> Math.max(0L, v - 1L));
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(), matricule, ImportRowAction.UPDATE,
                                "STUDENT_ENROLLMENT", existing.id(), preview,
                                new PreparedEnrollmentRow(existing.id(), existing.classRoomId(), cmd)));
                    }
                } else {
                    long targetCount = projectedClassCounts.getOrDefault(targetClass.id(), 0L);
                    if (targetCount + 1L > targetClass.capacity()) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), matricule, "CLASSROOM_CAPACITY_EXCEEDED", "class_code",
                                "La classe '" + targetClass.code() + "' a atteint sa capacité maximale ("
                                        + targetClass.capacity() + ").",
                                preview));
                        continue;
                    }
                    projectedClassCounts.put(targetClass.id(), targetCount + 1L);
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(), matricule, ImportRowAction.CREATE,
                            "STUDENT_ENROLLMENT", null, preview,
                            new PreparedEnrollmentRow(null, null, cmd)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(year.id(), null, null, analyzed, null, null, null, null);
    }

    private AnalysisResult analyzeGrades(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows,
            UUID requestedEvaluationId,
            UUID actorId) {
        if (requestedEvaluationId == null) {
            throw new SchoolImportValidationException(
                    "L'identifiant d'évaluation (evaluationId) est obligatoire pour un import de notes.");
        }
        EvaluationImportContext evalCtx = gradeImportPort.findEvaluationImportContext(requestedEvaluationId)
                .orElseThrow(() -> new SchoolImportNotFoundException(
                        "evaluation-not-found", "Évaluation cible introuvable dans cet établissement."));

        accessGuard.assertTeacherScopeForGrades(actorId, evalCtx.classRoomId(), evalCtx.subjectId());

        if (!"OPEN".equals(evalCtx.periodStatus())) {
            throw new SchoolImportConflictException(
                    "evaluation-period-closed",
                    "Période d'évaluation non ouverte",
                    "Impossible d'importer des notes : la période d'évaluation est au statut "
                            + evalCtx.periodStatus() + " (seul OPEN est autorisé).");
        }
        if ("LOCKED".equals(evalCtx.status()) || "CANCELLED".equals(evalCtx.status())) {
            throw new SchoolImportConflictException(
                    "evaluation-immutable",
                    "Évaluation non modifiable",
                    "L'évaluation cible est au statut " + evalCtx.status() + " et n'accepte plus de notes.");
        }
        if ("VALIDATED".equals(evalCtx.status()) && accessGuard.isRestrictedTeacher()) {
            throw new SchoolImportConflictException(
                    "evaluation-validated",
                    "Évaluation déjà validée",
                    "Une évaluation déjà validée ne peut plus être modifiée par un enseignant.");
        }

        // Préchargement en lot des élèves inscrits dans la classe de l'évaluation
        Set<UUID> enrolledStudentIds = studentDirectoryPort.findActiveEnrollmentsByClassRoom(evalCtx.classRoomId())
                .stream()
                .map(StudentEnrollmentSummary::studentId)
                .collect(Collectors.toSet());
        Set<String> matricules = rows.stream()
                .map(r -> r.get("student_matricule"))
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, StudentSummary> studentsByMatricule = studentDirectoryPort
                .findStudentsByMatricules(matricules).stream()
                .collect(Collectors.toMap(
                        s -> s.matricule().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Set<String> seenMatriculesInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String matricule = ImportFieldParser.normalizeCode(
                        row.get("student_matricule"), "student_matricule", true);
                String attendanceStatus = ImportFieldParser.parseGradeAttendanceStatus(
                        row.get("attendance_status"), "attendance_status");
                BigDecimal score = ImportFieldParser.parseOptionalScore(
                        row.get("score"), "score", evalCtx.maxScore());
                String comment = ImportFieldParser.optionalText(row.get("comment"), "comment", 500);

                if ("PRESENT".equals(attendanceStatus) && score == null) {
                    throw new FieldParseException(
                            "score", "REQUIRED_FIELD_MISSING", "La note est obligatoire lorsque l'élève est PRESENT.");
                }
                if (!"PRESENT".equals(attendanceStatus) && score != null) {
                    throw new FieldParseException(
                            "score", "SCORE_NOT_ALLOWED",
                            "Aucune note ne doit être renseignée lorsque le statut est " + attendanceStatus + ".");
                }

                if (!seenMatriculesInFile.add(matricule)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "INTRA_FILE_DUPLICATE", "student_matricule",
                            "Élève en doublon dans le fichier de notes.", preview));
                    continue;
                }

                StudentSummary student = studentsByMatricule.get(matricule);
                if (student == null) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_FOUND", "student_matricule",
                            "Élève introuvable pour le matricule '" + matricule + "'.", preview));
                    continue;
                }
                if (!enrolledStudentIds.contains(student.id())) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_ENROLLED_IN_CLASS", "student_matricule",
                            "L'élève '" + matricule + "' n'est pas activement inscrit dans la classe de l'évaluation.",
                            preview));
                    continue;
                }

                ImportGradeEntryCommand cmd = new ImportGradeEntryCommand(
                        student.id(), attendanceStatus, score, comment);
                boolean alreadyGraded = evalCtx.existingGradedStudentIds().contains(student.id());
                if (alreadyGraded) {
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), matricule, "DUPLICATE_RECORD", "student_matricule",
                                "Une note existe déjà pour l'élève '" + matricule + "' sur cette évaluation.", preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), matricule, "STUDENT_GRADE", student.id(), preview));
                    } else {
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(), matricule, ImportRowAction.UPDATE, "STUDENT_GRADE", student.id(),
                                preview, new PreparedGradeRow(evalCtx.evaluationId(), cmd)));
                    }
                } else {
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(), matricule, ImportRowAction.CREATE, "STUDENT_GRADE", student.id(),
                            preview, new PreparedGradeRow(evalCtx.evaluationId(), cmd)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(evalCtx.academicYearId(), evalCtx.evaluationId(), null, analyzed, null, null, null, null);
    }

    private AnalysisResult analyzeAttendance(
            DuplicateStrategy duplicateStrategy,
            List<ParsedTabularRow> rows,
            UUID requestedAttendanceSessionId,
            UUID actorId) {
        if (requestedAttendanceSessionId == null) {
            throw new SchoolImportValidationException(
                    "L'identifiant de séance d'appel (attendanceSessionId) est obligatoire pour un import de présences.");
        }
        AttendanceSessionImportContext sessionCtx = attendanceImportPort
                .findSessionImportContext(requestedAttendanceSessionId)
                .orElseThrow(() -> new SchoolImportNotFoundException(
                        "attendance-session-not-found", "Séance d'appel cible introuvable dans cet établissement."));

        accessGuard.assertTeacherScopeForAttendance(actorId, sessionCtx.classRoomId(), sessionCtx.subjectId());

        if ("LOCKED".equals(sessionCtx.status()) || "CANCELLED".equals(sessionCtx.status())) {
            throw new SchoolImportConflictException(
                    "attendance-session-immutable",
                    "Séance d'appel non modifiable",
                    "La séance d'appel cible est au statut " + sessionCtx.status() + " et n'accepte plus de pointages.");
        }
        if ("LOCKED".equals(sessionCtx.periodStatus())) {
            throw new SchoolImportConflictException(
                    "evaluation-period-locked",
                    "Période verrouillée",
                    "Impossible d'importer des présences sur une période d'évaluation verrouillée.");
        }
        if ("SUBMITTED".equals(sessionCtx.status()) && accessGuard.isRestrictedTeacher()) {
            throw new SchoolImportConflictException(
                    "attendance-session-submitted",
                    "Séance déjà soumise",
                    "Une feuille d'appel déjà soumise ne peut plus être modifiée par un enseignant.");
        }

        Set<UUID> enrolledStudentIds = studentDirectoryPort.findActiveEnrollmentsByClassRoom(sessionCtx.classRoomId())
                .stream()
                .map(StudentEnrollmentSummary::studentId)
                .collect(Collectors.toSet());
        Set<String> matricules = rows.stream()
                .map(r -> r.get("student_matricule"))
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, StudentSummary> studentsByMatricule = studentDirectoryPort
                .findStudentsByMatricules(matricules).stream()
                .collect(Collectors.toMap(
                        s -> s.matricule().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        Set<String> seenMatriculesInFile = new HashSet<>();
        List<AnalyzedRow> analyzed = new ArrayList<>(rows.size());

        for (ParsedTabularRow row : rows) {
            String preview = ImportRowSanitizer.toSanitizedPreviewJson(row.values());
            try {
                String matricule = ImportFieldParser.normalizeCode(
                        row.get("student_matricule"), "student_matricule", true);
                String status = ImportFieldParser.parseAttendanceStatus(row.get("status"), "status");
                Integer lateMinutes = ImportFieldParser.parseOptionalLateMinutes(
                        row.get("late_minutes"), "late_minutes");
                String notes = ImportFieldParser.optionalText(row.get("notes"), "notes", 500);

                if ("LATE".equals(status) && lateMinutes == null) {
                    throw new FieldParseException(
                            "late_minutes", "REQUIRED_FIELD_MISSING",
                            "Le nombre de minutes de retard (1..300) est obligatoire lorsque le statut est LATE.");
                }
                if (!"LATE".equals(status) && lateMinutes != null) {
                    throw new FieldParseException(
                            "late_minutes", "LATE_MINUTES_NOT_ALLOWED",
                            "Les minutes de retard ne peuvent être renseignées que pour le statut LATE.");
                }

                if (!seenMatriculesInFile.add(matricule)) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "INTRA_FILE_DUPLICATE", "student_matricule",
                            "Élève en doublon dans le fichier de présences.", preview));
                    continue;
                }

                StudentSummary student = studentsByMatricule.get(matricule);
                if (student == null) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_FOUND", "student_matricule",
                            "Élève introuvable pour le matricule '" + matricule + "'.", preview));
                    continue;
                }
                if (!enrolledStudentIds.contains(student.id())) {
                    analyzed.add(AnalyzedRow.error(
                            row.rowNumber(), matricule, "STUDENT_NOT_ENROLLED_IN_CLASS", "student_matricule",
                            "L'élève '" + matricule + "' n'est pas activement inscrit dans la classe de la séance.",
                            preview));
                    continue;
                }

                ImportAttendanceEntryCommand cmd = new ImportAttendanceEntryCommand(
                        student.id(), status, lateMinutes, notes);
                boolean alreadyRecorded = sessionCtx.existingRecordedStudentIds().contains(student.id());
                if (alreadyRecorded) {
                    if (duplicateStrategy == DuplicateStrategy.FAIL_ON_DUPLICATE) {
                        analyzed.add(AnalyzedRow.error(
                                row.rowNumber(), matricule, "DUPLICATE_RECORD", "student_matricule",
                                "Un pointage existe déjà pour l'élève '" + matricule + "' sur cette séance.", preview));
                    } else if (duplicateStrategy == DuplicateStrategy.SKIP_DUPLICATES) {
                        analyzed.add(AnalyzedRow.skipDuplicate(
                                row.rowNumber(), matricule, "ATTENDANCE_RECORD", student.id(), preview));
                    } else {
                        analyzed.add(AnalyzedRow.valid(
                                row.rowNumber(), matricule, ImportRowAction.UPDATE, "ATTENDANCE_RECORD", student.id(),
                                preview, new PreparedAttendanceRow(sessionCtx.sessionId(), cmd)));
                    }
                } else {
                    analyzed.add(AnalyzedRow.valid(
                            row.rowNumber(), matricule, ImportRowAction.CREATE, "ATTENDANCE_RECORD", student.id(),
                            preview, new PreparedAttendanceRow(sessionCtx.sessionId(), cmd)));
                }
            } catch (FieldParseException ex) {
                analyzed.add(AnalyzedRow.error(
                        row.rowNumber(), null, ex.errorCode(), ex.fieldName(), ex.getMessage(), preview));
            }
        }

        return buildResult(
                sessionCtx.academicYearId(), null, sessionCtx.sessionId(), analyzed, null, null, null, null);
    }

    private AcademicYearSummary resolveMutableAcademicYear(UUID requestedAcademicYearId) {
        AcademicYearSummary year = requestedAcademicYearId != null
                ? academicStructurePort.findAcademicYearById(requestedAcademicYearId)
                        .orElseThrow(() -> new SchoolImportNotFoundException(
                                "academic-year-not-found", "Année scolaire introuvable."))
                : academicStructurePort.findCurrentAcademicYear()
                        .orElseThrow(() -> new SchoolImportValidationException(
                                "Aucune année scolaire courante active n'est définie ; précisez academicYearId."));
        if (!"DRAFT".equals(year.status()) && !"ACTIVE".equals(year.status())) {
            throw new SchoolImportConflictException(
                    "academic-year-immutable",
                    "Année scolaire figée",
                    "Impossible d'importer des inscriptions sur une année scolaire clôturée ou archivée.");
        }
        return year;
    }

    private static String studentIdentityKey(String firstName, String lastName, LocalDate birthDate) {
        return firstName.trim().toLowerCase(Locale.ROOT)
                + "|" + lastName.trim().toLowerCase(Locale.ROOT)
                + "|" + birthDate;
    }

    private static AnalysisResult buildResult(
            UUID academicYearId,
            UUID evaluationId,
            UUID attendanceSessionId,
            List<AnalyzedRow> rows,
            String quotaResource,
            Long quotaLimit,
            Long quotaUsedBefore,
            Long quotaProjectedAfter) {
        int valid = 0;
        int errors = 0;
        int duplicates = 0;
        for (AnalyzedRow r : rows) {
            switch (r.rowStatus()) {
                case VALID -> valid++;
                case DUPLICATE -> duplicates++;
                case ERROR -> errors++;
                default -> {
                }
            }
        }
        return new AnalysisResult(
                academicYearId,
                evaluationId,
                attendanceSessionId,
                List.copyOf(rows),
                rows.size(),
                valid,
                errors,
                duplicates,
                quotaResource,
                quotaLimit,
                quotaUsedBefore,
                quotaProjectedAfter);
    }
}
