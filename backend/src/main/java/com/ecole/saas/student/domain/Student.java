package com.ecole.saas.student.domain;

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
@Table(name = "students")
public class Student extends TenantScopedEntity {

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "matricule", nullable = false, updatable = false, length = 64)
    private String matricule;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private StudentGender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 120)
    private String birthPlace;

    @Column(name = "nationality", length = 80)
    private String nationality;

    @Column(name = "address")
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    @Column(name = "medical_notes")
    private String medicalNotes;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "user_id")
    private UUID userId;

    protected Student() {
    }

    public Student(
            String matricule,
            String firstName,
            String lastName,
            StudentGender gender,
            LocalDate birthDate,
            String birthPlace,
            String nationality,
            String address,
            String phone,
            String email,
            String medicalNotes,
            LocalDate admissionDate,
            LocalDate referenceToday) {
        this.matricule = MatriculeFormatter.normalize(matricule);
        this.firstName = requireNonBlank(firstName, 100, "Le prénom de l'élève est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom de l'élève est obligatoire.");
        this.gender = Objects.requireNonNull(gender, "Le genre de l'élève est obligatoire.");
        this.birthDate = validateBirthDate(birthDate, referenceToday);
        this.birthPlace = normalizeOptional(birthPlace, 120, "Le lieu de naissance");
        this.nationality = normalizeOptional(nationality, 80, "La nationalité");
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.medicalNotes = normalizeOptional(medicalNotes, 1000, "Les notes médicales");
        this.admissionDate = validateAdmissionDate(admissionDate, this.birthDate);
        this.status = StudentStatus.ACTIVE;
    }

    public void updateProfile(
            String firstName,
            String lastName,
            StudentGender gender,
            LocalDate birthDate,
            String birthPlace,
            String nationality,
            String address,
            String phone,
            String email,
            String medicalNotes,
            LocalDate admissionDate,
            LocalDate referenceToday) {
        assertMutable();
        this.firstName = requireNonBlank(firstName, 100, "Le prénom de l'élève est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom de l'élève est obligatoire.");
        this.gender = Objects.requireNonNull(gender, "Le genre de l'élève est obligatoire.");
        this.birthDate = validateBirthDate(birthDate, referenceToday);
        this.birthPlace = normalizeOptional(birthPlace, 120, "Le lieu de naissance");
        this.nationality = normalizeOptional(nationality, 80, "La nationalité");
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.medicalNotes = normalizeOptional(medicalNotes, 1000, "Les notes médicales");
        if (admissionDate != null) {
            this.admissionDate = validateAdmissionDate(admissionDate, this.birthDate);
        } else if (this.admissionDate.isBefore(this.birthDate)) {
            throw new IllegalArgumentException(
                    "La date de naissance ne peut pas être postérieure à la date d'admission.");
        }
    }

    public void markTransferred(String reason) {
        if (this.status != StudentStatus.ACTIVE) {
            throw new InvalidStudentTransitionException(
                    "Seul un élève actif peut être marqué comme transféré (statut actuel : " + this.status + ").");
        }
        this.statusReason = requireNonBlank(reason, 255, "Le motif de transfert est obligatoire.");
        this.status = StudentStatus.TRANSFERRED;
    }

    public void markGraduated(String reason) {
        if (this.status != StudentStatus.ACTIVE) {
            throw new InvalidStudentTransitionException(
                    "Seul un élève actif peut être marqué comme diplômé (statut actuel : " + this.status + ").");
        }
        this.statusReason = normalizeOptional(reason, 255, "Le motif");
        this.status = StudentStatus.GRADUATED;
    }

    public void markWithdrawn(String reason) {
        if (this.status != StudentStatus.ACTIVE) {
            throw new InvalidStudentTransitionException(
                    "Seul un élève actif peut être marqué comme retiré (statut actuel : " + this.status + ").");
        }
        this.statusReason = requireNonBlank(reason, 255, "Le motif de retrait est obligatoire.");
        this.status = StudentStatus.WITHDRAWN;
    }

    public void reactivate() {
        if (this.status != StudentStatus.TRANSFERRED
                && this.status != StudentStatus.GRADUATED
                && this.status != StudentStatus.WITHDRAWN) {
            throw new InvalidStudentTransitionException(
                    "Impossible de réactiver un élève depuis le statut " + this.status + ".");
        }
        this.status = StudentStatus.ACTIVE;
        this.statusReason = null;
    }

    public void archive(String reason, Instant at) {
        if (this.status == StudentStatus.ARCHIVED) {
            throw new InvalidStudentTransitionException("Ce dossier élève est déjà archivé.");
        }
        this.statusReason = requireNonBlank(reason, 255, "Le motif d'archivage est obligatoire.");
        this.archivedAt = Objects.requireNonNull(at, "La date d'archivage est obligatoire.");
        this.status = StudentStatus.ARCHIVED;
    }

    public boolean isActive() {
        return this.status == StudentStatus.ACTIVE;
    }

    public boolean isArchived() {
        return this.status == StudentStatus.ARCHIVED;
    }

    public void assertMutable() {
        if (this.status == StudentStatus.ARCHIVED) {
            throw new InvalidStudentTransitionException("Un dossier élève archivé ne peut plus être modifié.");
        }
    }

    public void assertActive() {
        if (this.status != StudentStatus.ACTIVE) {
            throw new StudentNotActiveException(
                    "L'opération exige un dossier élève actif (statut actuel : " + this.status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getMatricule() {
        return matricule;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public StudentGender getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public String getNationality() {
        return nationality;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public String getMedicalNotes() {
        return medicalNotes;
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    private static LocalDate validateBirthDate(LocalDate date, LocalDate referenceToday) {
        LocalDate validated = Objects.requireNonNull(date, "La date de naissance de l'élève est obligatoire.");
        LocalDate today = Objects.requireNonNull(referenceToday, "La date de référence est obligatoire.");
        if (validated.isBefore(MIN_BIRTH_DATE)) {
            throw new IllegalArgumentException("La date de naissance est antérieure au 1er janvier 1900.");
        }
        if (validated.isAfter(today)) {
            throw new IllegalArgumentException("La date de naissance ne peut pas être dans le futur.");
        }
        return validated;
    }

    private static LocalDate validateAdmissionDate(LocalDate admissionDate, LocalDate birthDate) {
        LocalDate validated = Objects.requireNonNull(admissionDate, "La date d'admission est obligatoire.");
        if (validated.isBefore(birthDate)) {
            throw new IllegalArgumentException(
                    "La date d'admission ne peut pas être antérieure à la date de naissance.");
        }
        int year = validated.getYear();
        if (year < MatriculeFormatter.MIN_YEAR || year > MatriculeFormatter.MAX_YEAR) {
            throw new IllegalArgumentException(
                    "L'année d'admission doit être comprise entre "
                            + MatriculeFormatter.MIN_YEAR + " et " + MatriculeFormatter.MAX_YEAR + ".");
        }
        return validated;
    }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = rawPhone.trim();
        if (normalized.length() > 20 || !E164_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le numéro de téléphone doit respecter le format international E.164 (ex. +221770000001).");
        }
        return normalized;
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("L'adresse e-mail de l'élève est invalide.");
        }
        return normalized;
    }

    private static String requireNonBlank(String raw, int maxLength, String blankMessage) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "La valeur dépasse la longueur maximale autorisée de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldLabel + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
