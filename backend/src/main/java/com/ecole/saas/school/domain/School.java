package com.ecole.saas.school.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "schools")
public class School {

    public static final String DEFAULT_TIMEZONE = "Africa/Dakar";
    public static final String DEFAULT_CURRENCY = "XOF";
    public static final String DEFAULT_COUNTRY_CODE = "SN";
    public static final int DEFAULT_GRADING_SCALE = 20;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Set<Integer> ALLOWED_GRADING_SCALES = Set.of(10, 20, 100);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_name", length = 60)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "school_type", nullable = false, length = 30)
    private SchoolType schoolType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SchoolStatus status;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "motto", length = 200)
    private String motto;

    @Column(name = "ministry_code", length = 60)
    private String ministryCode;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "logo_object_key", length = 512)
    private String logoObjectKey;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_period_system", nullable = false, length = 20)
    private GradingPeriodSystem gradingPeriodSystem;

    @Column(name = "grading_scale", nullable = false)
    private int gradingScale;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected School() {
    }

    public School(String code, String name, SchoolType schoolType, String phone, String email, String address) {
        this(
                code,
                name,
                null,
                schoolType,
                phone,
                email,
                address,
                null,
                null,
                DEFAULT_COUNTRY_CODE,
                null,
                null,
                null,
                DEFAULT_TIMEZONE,
                DEFAULT_CURRENCY,
                GradingPeriodSystem.TRIMESTER,
                DEFAULT_GRADING_SCALE);
    }

    public School(
            String code,
            String name,
            String shortName,
            SchoolType schoolType,
            String phone,
            String email,
            String address,
            String city,
            String region,
            String countryCode,
            String motto,
            String ministryCode,
            String website,
            String timezone,
            String currency,
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        this.code = normalizeCode(code);
        this.name = requireNonBlankWithMax(name, 150, "Le nom de l'établissement est obligatoire.");
        this.shortName = normalizeOptional(shortName, 60, "Le nom court");
        this.schoolType = Objects.requireNonNull(schoolType, "Le type d'établissement est obligatoire.");
        this.status = SchoolStatus.ACTIVE;
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.city = normalizeOptional(city, 100, "La ville");
        this.region = normalizeOptional(region, 100, "La région");
        this.countryCode = normalizeCountryCode(countryCode);
        this.motto = normalizeOptional(motto, 200, "La devise de l'établissement");
        this.ministryCode = normalizeOptional(ministryCode, 60, "Le numéro d'autorisation");
        this.website = normalizeOptional(website, 255, "Le site web");
        this.timezone = normalizeTimezone(timezone);
        this.currency = normalizeCurrency(currency);
        this.gradingPeriodSystem = gradingPeriodSystem != null ? gradingPeriodSystem : GradingPeriodSystem.TRIMESTER;
        this.gradingScale = validateGradingScale(gradingScale != null ? gradingScale : DEFAULT_GRADING_SCALE);
    }

    public void updatePlatformDetails(
            String name,
            String shortName,
            SchoolType schoolType,
            String phone,
            String email,
            String address,
            String city,
            String region,
            String countryCode,
            String motto,
            String ministryCode,
            String website,
            String timezone,
            String currency,
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        if (this.status == SchoolStatus.ARCHIVED) {
            throw new InvalidSchoolTransitionException("Un établissement archivé ne peut plus être modifié.");
        }
        this.name = requireNonBlankWithMax(name, 150, "Le nom de l'établissement est obligatoire.");
        this.shortName = normalizeOptional(shortName, 60, "Le nom court");
        this.schoolType = Objects.requireNonNull(schoolType, "Le type d'établissement est obligatoire.");
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.city = normalizeOptional(city, 100, "La ville");
        this.region = normalizeOptional(region, 100, "La région");
        this.countryCode = normalizeCountryCode(countryCode);
        this.motto = normalizeOptional(motto, 200, "La devise de l'établissement");
        this.ministryCode = normalizeOptional(ministryCode, 60, "Le numéro d'autorisation");
        this.website = normalizeOptional(website, 255, "Le site web");
        this.timezone = normalizeTimezone(timezone);
        this.currency = normalizeCurrency(currency);
        if (gradingPeriodSystem != null) {
            this.gradingPeriodSystem = gradingPeriodSystem;
        }
        if (gradingScale != null) {
            this.gradingScale = validateGradingScale(gradingScale);
        }
    }

    public void updateTenantProfile(
            String name,
            String shortName,
            String phone,
            String email,
            String address,
            String city,
            String region,
            String countryCode,
            String motto,
            String ministryCode,
            String website,
            String timezone,
            GradingPeriodSystem gradingPeriodSystem,
            Integer gradingScale) {
        if (this.status != SchoolStatus.ACTIVE) {
            throw new SchoolNotActiveException(
                    "Impossible de modifier les paramètres d'un établissement qui n'est pas actif.");
        }
        this.name = requireNonBlankWithMax(name, 150, "Le nom de l'établissement est obligatoire.");
        this.shortName = normalizeOptional(shortName, 60, "Le nom court");
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.city = normalizeOptional(city, 100, "La ville");
        this.region = normalizeOptional(region, 100, "La région");
        this.countryCode = normalizeCountryCode(countryCode);
        this.motto = normalizeOptional(motto, 200, "La devise de l'établissement");
        this.ministryCode = normalizeOptional(ministryCode, 60, "Le numéro d'autorisation");
        this.website = normalizeOptional(website, 255, "Le site web");
        this.timezone = normalizeTimezone(timezone);
        if (gradingPeriodSystem != null) {
            this.gradingPeriodSystem = gradingPeriodSystem;
        }
        if (gradingScale != null) {
            this.gradingScale = validateGradingScale(gradingScale);
        }
    }

    public void suspend(String reason, Instant at) {
        if (this.status != SchoolStatus.ACTIVE) {
            throw new InvalidSchoolTransitionException(
                    "Seul un établissement actif peut être suspendu (statut actuel : " + this.status + ").");
        }
        this.statusReason = requireNonBlankWithMax(reason, 255, "Le motif de suspension est obligatoire.");
        this.suspendedAt = Objects.requireNonNull(at, "La date de suspension est obligatoire.");
        this.status = SchoolStatus.SUSPENDED;
    }

    public void activate() {
        if (this.status != SchoolStatus.SUSPENDED) {
            throw new InvalidSchoolTransitionException(
                    "Seul un établissement suspendu peut être réactivé (statut actuel : " + this.status + ").");
        }
        this.status = SchoolStatus.ACTIVE;
        this.suspendedAt = null;
        this.statusReason = null;
    }

    public void archive(String reason, Instant at) {
        if (this.status == SchoolStatus.ARCHIVED) {
            throw new InvalidSchoolTransitionException("Cet établissement est déjà archivé.");
        }
        this.statusReason = requireNonBlankWithMax(reason, 255, "Le motif d'archivage est obligatoire.");
        this.archivedAt = Objects.requireNonNull(at, "La date d'archivage est obligatoire.");
        this.status = SchoolStatus.ARCHIVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public SchoolType getSchoolType() {
        return schoolType;
    }

    public SchoolStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == SchoolStatus.ACTIVE;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getMotto() {
        return motto;
    }

    public String getMinistryCode() {
        return ministryCode;
    }

    public String getWebsite() {
        return website;
    }

    public String getLogoObjectKey() {
        return logoObjectKey;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public GradingPeriodSystem getGradingPeriodSystem() {
        return gradingPeriodSystem;
    }

    public int getGradingScale() {
        return gradingScale;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de l'établissement est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de l'établissement doit contenir entre 2 et 32 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
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
            throw new IllegalArgumentException("L'adresse e-mail de l'établissement est invalide.");
        }
        return normalized;
    }

    private static String normalizeTimezone(String rawTimezone) {
        if (rawTimezone == null || rawTimezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        String normalized = rawTimezone.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Le fuseau horaire ne peut pas dépasser 50 caractères.");
        }
        try {
            ZoneId.of(normalized);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Le fuseau horaire indiqué est invalide.");
        }
        return normalized;
    }

    private static String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String normalized = rawCurrency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("La devise doit être un code ISO sur 3 lettres (ex. XOF).");
        }
        return normalized;
    }

    private static String normalizeCountryCode(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return DEFAULT_COUNTRY_CODE;
        }
        String normalized = rawCountry.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Le code pays doit contenir exactement 2 lettres majuscules (ex. SN).");
        }
        return normalized;
    }

    private static int validateGradingScale(int scale) {
        if (!ALLOWED_GRADING_SCALES.contains(scale)) {
            throw new IllegalArgumentException("Le barème de notation doit être 10, 20 ou 100.");
        }
        return scale;
    }

    private static String requireNonBlankWithMax(String value, int maxLength, String blankMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "La valeur dépasse la longueur maximale autorisée de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String value, int maxLength, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldLabel + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
