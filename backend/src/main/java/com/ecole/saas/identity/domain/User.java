package com.ecole.saas.identity.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Embedded
    private LoginSecurity security = new LoginSecurity();

    protected User() {
    }

    public User(String email, String phone, String firstName, String lastName, String passwordHash) {
        this.email = normalizeEmail(email);
        this.phone = phone;
        this.firstName = requireNonBlank(firstName, "Le prénom est obligatoire.").trim();
        this.lastName = requireNonBlank(lastName, "Le nom est obligatoire.").trim();
        this.passwordHash = requireNonBlank(passwordHash, "Le hachage du mot de passe est obligatoire.");
        this.status = UserStatus.ACTIVE;
    }

    public static String normalizeEmail(String email) {
        return requireNonBlank(email, "L'adresse e-mail est obligatoire.").trim().toLowerCase(Locale.ROOT);
    }

    public boolean canAuthenticate(Instant now) {
        return status == UserStatus.ACTIVE && !security.isLocked(now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void updateName(String firstName, String lastName) {
        this.firstName = requireNonBlank(firstName, "Le prénom est obligatoire.").trim();
        this.lastName = requireNonBlank(lastName, "Le nom est obligatoire.").trim();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LoginSecurity getSecurity() {
        return security;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
