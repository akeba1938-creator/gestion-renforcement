package com.ecole.saas.family.domain;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "guardians")
public class Guardian extends TenantScopedEntity {

    public static final String DEFAULT_PREFERRED_LANGUAGE = "fr";

    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private GuardianGender gender;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "secondary_phone", length = 20)
    private String secondaryPhone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "profession", length = 120)
    private String profession;

    @Column(name = "address")
    private String address;

    @Column(name = "national_id_number", length = 60)
    private String nationalIdNumber;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GuardianStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Guardian() {
    }

    public Guardian(
            UUID familyId,
            String firstName,
            String lastName,
            GuardianGender gender,
            String phone,
            String secondaryPhone,
            String email,
            String profession,
            String address,
            String nationalIdNumber,
            String preferredLanguage) {
        this.familyId = familyId;
        this.firstName = requireNonBlank(firstName, 100, "Le prénom du parent ou tuteur est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom du parent ou tuteur est obligatoire.");
        this.gender = gender;
        this.phone = requirePhone(phone);
        this.secondaryPhone = normalizeOptionalPhone(secondaryPhone);
        this.email = normalizeEmail(email);
        this.profession = normalizeOptional(profession, 120, "La profession");
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.nationalIdNumber = normalizeOptional(nationalIdNumber, 60, "Le numéro de pièce d'identité");
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
        this.status = GuardianStatus.ACTIVE;
    }

    public void update(
            UUID familyId,
            String firstName,
            String lastName,
            GuardianGender gender,
            String phone,
            String secondaryPhone,
            String email,
            String profession,
            String address,
            String nationalIdNumber,
            String preferredLanguage) {
        assertActive();
        this.familyId = familyId;
        this.firstName = requireNonBlank(firstName, 100, "Le prénom du parent ou tuteur est obligatoire.");
        this.lastName = requireNonBlank(lastName, 100, "Le nom du parent ou tuteur est obligatoire.");
        this.gender = gender;
        this.phone = requirePhone(phone);
        this.secondaryPhone = normalizeOptionalPhone(secondaryPhone);
        this.email = normalizeEmail(email);
        this.profession = normalizeOptional(profession, 120, "La profession");
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.nationalIdNumber = normalizeOptional(nationalIdNumber, 60, "Le numéro de pièce d'identité");
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
    }

    public void archive(Instant at) {
        this.archivedAt = Objects.requireNonNull(at, "La date d'archivage est obligatoire.");
        this.status = GuardianStatus.ARCHIVED;
    }

    public void reactivate() {
        this.status = GuardianStatus.ACTIVE;
        this.archivedAt = null;
    }

    public boolean isActive() {
        return this.status == GuardianStatus.ACTIVE;
    }

    public void assertActive() {
        if (this.status != GuardianStatus.ACTIVE) {
            throw new GuardianArchivedException("Un parent ou tuteur archivé ne peut plus être modifié.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public GuardianGender getGender() {
        return gender;
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

    public String getProfession() {
        return profession;
    }

    public String getAddress() {
        return address;
    }

    public String getNationalIdNumber() {
        return nationalIdNumber;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public UUID getUserId() {
        return userId;
    }

    public GuardianStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    private static String requirePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("Le numéro de téléphone principal est obligatoire.");
        }
        String normalized = rawPhone.trim();
        if (normalized.length() > 20 || !E164_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le numéro de téléphone doit respecter le format international E.164 (ex. +221770000001).");
        }
        return normalized;
    }

    private static String normalizeOptionalPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = rawPhone.trim();
        if (normalized.length() > 20 || !E164_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le numéro de téléphone secondaire doit respecter le format international E.164 (ex. +221770000002).");
        }
        return normalized;
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("L'adresse e-mail du parent ou tuteur est invalide.");
        }
        return normalized;
    }

    private static String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return DEFAULT_PREFERRED_LANGUAGE;
        }
        String trimmed = rawLanguage.trim();
        if (trimmed.length() > 10 || !LANGUAGE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("La langue préférée doit être un code ISO valide (ex. fr, en).");
        }
        return trimmed;
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
