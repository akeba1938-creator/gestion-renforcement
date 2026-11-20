package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "teachers")
public class Teacher extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_code", nullable = false, updatable = false, length = 32)
    private String employeeCode;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private TeacherGender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type", nullable = false, length = 20)
    private TeacherType teacherType;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "national_id_number", length = 60)
    private String nationalIdNumber;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "hourly_rate_xof")
    private Long hourlyRateXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TeacherStatus status;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "notes")
    private String notes;

    protected Teacher() {
    }

    public Teacher(
            String employeeCode,
            UUID userId,
            String firstName,
            String lastName,
            TeacherGender gender,
            TeacherType teacherType,
            String phone,
            String secondaryPhone,
            String email,
            String address,
            String nationalIdNumber,
            LocalDate hireDate,
            Long hourlyRateXof,
            String notes) {
        this.employeeCode = normalizeCode(employeeCode);
        this.userId = userId;
        this.firstName = requireNonBlank(firstName, 100, "Le prénom de l'enseignant est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom de l'enseignant est obligatoire.");
        this.gender = gender;
        this.teacherType = Objects.requireNonNull(teacherType, "Le type d'enseignant est obligatoire.");
        this.phone = requirePhone(phone, "Le numéro de téléphone principal de l'enseignant est obligatoire.");
        this.secondaryPhone = normalizeOptionalPhone(secondaryPhone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.nationalIdNumber = normalizeOptional(nationalIdNumber, 60, "Le numéro de pièce d'identité");
        this.hireDate = hireDate;
        this.hourlyRateXof = validateHourlyRate(hourlyRateXof);
        this.notes = normalizeOptional(notes, 1000, "Les notes");
        this.status = TeacherStatus.ACTIVE;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new TeacherValidationException("Le code enseignant est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new TeacherValidationException(
                    "Le code enseignant doit contenir entre 2 et 32 caractères alphanumériques ('-' et '_' autorisés).");
        }
        return normalized;
    }

    public void updateProfile(
            String firstName,
            String lastName,
            TeacherGender gender,
            TeacherType teacherType,
            String phone,
            String secondaryPhone,
            String email,
            String address,
            String nationalIdNumber,
            LocalDate hireDate,
            Long hourlyRateXof,
            String notes) {
        assertNotArchived();
        this.firstName = requireNonBlank(firstName, 100, "Le prénom de l'enseignant est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom de l'enseignant est obligatoire.");
        this.gender = gender;
        this.teacherType = Objects.requireNonNull(teacherType, "Le type d'enseignant est obligatoire.");
        this.phone = requirePhone(phone, "Le numéro de téléphone principal de l'enseignant est obligatoire.");
        this.secondaryPhone = normalizeOptionalPhone(secondaryPhone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.nationalIdNumber = normalizeOptional(nationalIdNumber, 60, "Le numéro de pièce d'identité");
        this.hireDate = hireDate;
        this.hourlyRateXof = validateHourlyRate(hourlyRateXof);
        this.notes = normalizeOptional(notes, 1000, "Les notes");
    }

    public void linkUser(UUID userId) {
        assertNotArchived();
        this.userId = userId;
    }

    public void suspend(String reason) {
        if (this.status != TeacherStatus.ACTIVE) {
            throw new TeacherConflictException(
                    "invalid-teacher-state",
                    "Transition d'état impossible",
                    "Seul un dossier enseignant actif peut être suspendu.");
        }
        this.status = TeacherStatus.SUSPENDED;
        this.statusReason = requireReason(reason, "Le motif de suspension est obligatoire.");
    }

    public void reactivate() {
        if (this.status != TeacherStatus.SUSPENDED) {
            throw new TeacherConflictException(
                    "invalid-teacher-state",
                    "Transition d'état impossible",
                    "Seul un dossier enseignant suspendu peut être réactivé.");
        }
        this.status = TeacherStatus.ACTIVE;
        this.statusReason = null;
    }

    public void archive(String reason, Instant now) {
        if (this.status == TeacherStatus.ARCHIVED) {
            throw new TeacherConflictException(
                    "invalid-teacher-state",
                    "Dossier déjà archivé",
                    "Ce dossier enseignant est déjà archivé.");
        }
        this.status = TeacherStatus.ARCHIVED;
        this.statusReason = requireReason(reason, "Le motif d'archivage est obligatoire.");
        this.archivedAt = Objects.requireNonNull(now, "L'horodatage d'archivage est obligatoire.");
    }

    public void assertActive() {
        if (this.status != TeacherStatus.ACTIVE) {
            throw new TeacherConflictException(
                    "teacher-not-active",
                    "Enseignant inactif",
                    "Cette opération exige un dossier enseignant au statut ACTIVE.");
        }
    }

    public void assertNotArchived() {
        if (this.status == TeacherStatus.ARCHIVED) {
            throw new TeacherConflictException(
                    "teacher-archived",
                    "Dossier enseignant archivé",
                    "Un dossier enseignant archivé est en lecture seule.");
        }
    }

    public boolean isActive() {
        return this.status == TeacherStatus.ACTIVE;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public TeacherGender getGender() {
        return gender;
    }

    public TeacherType getTeacherType() {
        return teacherType;
    }

    public String getPhone() {
        return phone;
    }

    public String getSecondaryPhone() {
        return secondaryPhone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getNationalIdNumber() {
        return nationalIdNumber;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public Long getHourlyRateXof() {
        return hourlyRateXof;
    }

    public TeacherStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getNotes() {
        return notes;
    }

    private static Long validateHourlyRate(Long hourlyRateXof) {
        if (hourlyRateXof == null) {
            return null;
        }
        if (hourlyRateXof < 0L) {
            throw new TeacherValidationException("Le taux horaire en FCFA doit être supérieur ou égal à 0.");
        }
        return hourlyRateXof;
    }

    private static String requireNonBlank(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new TeacherValidationException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new TeacherValidationException(
                    "La longueur maximale autorisée (" + maxLength + " caractères) est dépassée.");
        }
        return trimmed;
    }

    private static String requireReason(String reason, String message) {
        return requireNonBlank(reason, 255, message);
    }

    private static String normalizeOptional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new TeacherValidationException(
                    label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String requirePhone(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            throw new TeacherValidationException(message);
        }
        String trimmed = phone.trim();
        if (!E164_PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new TeacherValidationException(
                    "Le numéro de téléphone doit respecter le format international E.164 (ex. +221771234567).");
        }
        return trimmed;
    }

    private static String normalizeOptionalPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return requirePhone(phone, "Le numéro de téléphone secondaire est invalide.");
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new TeacherValidationException("L'adresse e-mail de l'enseignant est invalide.");
        }
        return normalized;
    }
}
