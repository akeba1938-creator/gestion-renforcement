package com.ecole.saas.identity.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.PlatformAuditRecordCommand;
import com.ecole.saas.identity.domain.AccountLockedException;
import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.AuthenticationFailedException;
import com.ecole.saas.identity.domain.Base32;
import com.ecole.saas.identity.domain.LoginSecurity;
import com.ecole.saas.identity.domain.MfaPolicy;
import com.ecole.saas.identity.domain.PasswordHasher;
import com.ecole.saas.identity.domain.PlatformAdmin;
import com.ecole.saas.identity.domain.PlatformRefreshToken;
import com.ecole.saas.identity.domain.RefreshCookieCodec;
import com.ecole.saas.identity.domain.RefreshCookieCodec.EncodedRefreshCookie;
import com.ecole.saas.identity.domain.RefreshCookieCodec.ParsedRefreshCookie;
import com.ecole.saas.identity.domain.RefreshToken;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.SecretCipher;
import com.ecole.saas.identity.domain.SubjectType;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.TokenIssuer.IssuedToken;
import com.ecole.saas.identity.domain.TokenLifetimes;
import com.ecole.saas.identity.domain.TotpGenerator;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.domain.UserRole;
import com.ecole.saas.identity.infrastructure.PlatformAdminRepository;
import com.ecole.saas.identity.infrastructure.PlatformRefreshTokenRepository;
import com.ecole.saas.identity.infrastructure.RefreshTokenRepository;
import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.identity.infrastructure.UserRoleRepository;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolDirectory.SchoolSummary;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service applicatif centralisant l'authentification, le MFA TOTP, la rotation des jetons
 * de renouvellement avec détection de réutilisation, et la résolution du profil courant.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOTP_ISSUER = "EcoleSaaS";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Identifiants invalides.";
    private static final String ACCOUNT_LOCKED_MESSAGE =
            "Compte temporairement verrouillé suite à plusieurs tentatives échouées. Réessayez plus tard.";

    private final SchoolDirectory schoolDirectory;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlatformRefreshTokenRepository platformRefreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final SecretCipher secretCipher;
    private final TokenIssuer tokenIssuer;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final String dummyPasswordHash;

    public AuthService(
            SchoolDirectory schoolDirectory,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PlatformAdminRepository platformAdminRepository,
            RefreshTokenRepository refreshTokenRepository,
            PlatformRefreshTokenRepository platformRefreshTokenRepository,
            PasswordHasher passwordHasher,
            SecretCipher secretCipher,
            TokenIssuer tokenIssuer,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.schoolDirectory = schoolDirectory;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.platformRefreshTokenRepository = platformRefreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.secretCipher = secretCipher;
        this.tokenIssuer = tokenIssuer;
        this.auditLogPort = auditLogPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.clock = clock;
        this.secureRandom = new SecureRandom();
        this.dummyPasswordHash = passwordHasher.hash("bouclier-temporel-non-secret-2026");
    }

    public AuthOutcome loginTenantUser(String schoolCode, String email, String rawPassword) {
        Optional<SchoolSummary> schoolOpt = schoolDirectory.findSummaryByCode(schoolCode);
        if (schoolOpt.isEmpty() || !schoolOpt.get().active()) {
            passwordHasher.matches(rawPassword, dummyPasswordHash);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }

        SchoolSummary school = schoolOpt.get();
        String normalizedEmail = User.normalizeEmail(email);
        Instant now = clock.instant();

        LoginTxOutcome result = TenantContext.executeWithTenant(
                TenantId.of(school.id()),
                () -> transactionTemplate.execute(status -> {
                    Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
                    if (userOpt.isEmpty()) {
                        return LoginTxOutcome.needsDummyCheck();
                    }
                    User user = userOpt.get();
                    if (user.getSecurity().isLocked(now)) {
                        auditTenantAuth(
                                AuditAction.AUTH_ACCOUNT_LOCKED,
                                AuditOutcome.DENIED,
                                AuditSeverity.WARNING,
                                user.getId(),
                                "Tentative de connexion sur un compte utilisateur verrouillé.",
                                Map.of("reason", "ACCOUNT_LOCKED"));
                        return LoginTxOutcome.locked();
                    }
                    if (!user.isActive()) {
                        return LoginTxOutcome.needsDummyCheck();
                    }
                    if (!passwordHasher.matches(rawPassword, user.getPasswordHash())) {
                        boolean justLocked = user.getSecurity().registerFailure(now);
                        userRepository.save(user);
                        if (justLocked) {
                            log.warn("Compte utilisateur verrouillé après échecs répétés (accountId={})", user.getId());
                            auditTenantAuth(
                                    AuditAction.AUTH_ACCOUNT_LOCKED,
                                    AuditOutcome.DENIED,
                                    AuditSeverity.CRITICAL,
                                    user.getId(),
                                    "Compte utilisateur verrouillé après échecs d'authentification répétés.",
                                    Map.of("failedAttempts", user.getSecurity().getFailedLoginAttempts()));
                            return LoginTxOutcome.locked();
                        }
                        log.info("Échec d'authentification utilisateur (accountId={})", user.getId());
                        auditTenantAuth(
                                AuditAction.AUTH_LOGIN_FAILED,
                                AuditOutcome.FAILURE,
                                AuditSeverity.WARNING,
                                user.getId(),
                                "Échec d'authentification d'un utilisateur d'établissement.",
                                Map.of("failedAttempts", user.getSecurity().getFailedLoginAttempts()));
                        return LoginTxOutcome.invalidCredentials();
                    }

                    List<RoleCode> roleCodes = loadUserRoleCodes(user.getId());
                    if (roleCodes.isEmpty()) {
                        return LoginTxOutcome.invalidCredentials();
                    }

                    user.getSecurity().registerSuccess();
                    userRepository.save(user);

                    AccountRef account = AccountRef.user(user.getId(), school.id());
                    if (MfaPolicy.isRequiredForTenantUser(roleCodes, user.getSecurity().isMfaEnabled())) {
                        IssuedToken mfaToken = tokenIssuer.issueMfaPendingToken(account);
                        auditTenantAuth(
                                AuditAction.AUTH_MFA_CHALLENGE_ISSUED,
                                AuditOutcome.SUCCESS,
                                AuditSeverity.INFO,
                                user.getId(),
                                "Défi MFA émis pour un utilisateur d'établissement.",
                                Map.of("enrollmentRequired", !user.getSecurity().isMfaEnabled()));
                        return LoginTxOutcome.authenticated(AuthOutcome.mfaChallenge(
                                !user.getSecurity().isMfaEnabled(),
                                mfaToken.value(),
                                mfaToken.expiresInSeconds()));
                    }

                    AuthOutcome authOutcome = issueTenantSession(user, school, roleCodes, UUID.randomUUID(), now);
                    auditTenantAuth(
                            AuditAction.AUTH_LOGIN_SUCCESS,
                            AuditOutcome.SUCCESS,
                            AuditSeverity.INFO,
                            user.getId(),
                            "Connexion réussie d'un utilisateur d'établissement.",
                            Map.of("mfaRequired", false));
                    return LoginTxOutcome.authenticated(authOutcome);
                }));

        return unwrapLoginOutcome(result, rawPassword);
    }

    public AuthOutcome loginPlatformAdmin(String email, String rawPassword) {
        String normalizedEmail = User.normalizeEmail(email);
        Instant now = clock.instant();

        LoginTxOutcome result = transactionTemplate.execute(status -> {
            Optional<PlatformAdmin> adminOpt = platformAdminRepository.findByEmail(normalizedEmail);
            if (adminOpt.isEmpty()) {
                return LoginTxOutcome.needsDummyCheck();
            }
            PlatformAdmin admin = adminOpt.get();
            if (admin.getSecurity().isLocked(now)) {
                auditPlatformAuth(
                        AuditAction.AUTH_ACCOUNT_LOCKED,
                        AuditOutcome.DENIED,
                        AuditSeverity.WARNING,
                        admin.getId(),
                        "Tentative de connexion sur un compte administrateur SaaS verrouillé.",
                        Map.of("reason", "ACCOUNT_LOCKED"));
                return LoginTxOutcome.locked();
            }
            if (!admin.isActive()) {
                return LoginTxOutcome.needsDummyCheck();
            }
            if (!passwordHasher.matches(rawPassword, admin.getPasswordHash())) {
                boolean justLocked = admin.getSecurity().registerFailure(now);
                platformAdminRepository.save(admin);
                if (justLocked) {
                    log.warn("Compte administrateur SaaS verrouillé après échecs répétés (accountId={})", admin.getId());
                    auditPlatformAuth(
                            AuditAction.AUTH_ACCOUNT_LOCKED,
                            AuditOutcome.DENIED,
                            AuditSeverity.CRITICAL,
                            admin.getId(),
                            "Compte administrateur SaaS verrouillé après échecs répétés.",
                            Map.of("failedAttempts", admin.getSecurity().getFailedLoginAttempts()));
                    return LoginTxOutcome.locked();
                }
                log.info("Échec d'authentification administrateur SaaS (accountId={})", admin.getId());
                auditPlatformAuth(
                        AuditAction.AUTH_LOGIN_FAILED,
                        AuditOutcome.FAILURE,
                        AuditSeverity.WARNING,
                        admin.getId(),
                        "Échec d'authentification d'un administrateur SaaS.",
                        Map.of("failedAttempts", admin.getSecurity().getFailedLoginAttempts()));
                return LoginTxOutcome.invalidCredentials();
            }

            admin.getSecurity().registerSuccess();
            platformAdminRepository.save(admin);

            AccountRef account = AccountRef.platformAdmin(admin.getId());
            if (MfaPolicy.isRequiredForPlatformAdmin()) {
                IssuedToken mfaToken = tokenIssuer.issueMfaPendingToken(account);
                auditPlatformAuth(
                        AuditAction.AUTH_MFA_CHALLENGE_ISSUED,
                        AuditOutcome.SUCCESS,
                        AuditSeverity.INFO,
                        admin.getId(),
                        "Défi MFA émis pour un administrateur SaaS.",
                        Map.of("enrollmentRequired", !admin.getSecurity().isMfaEnabled()));
                return LoginTxOutcome.authenticated(AuthOutcome.mfaChallenge(
                        !admin.getSecurity().isMfaEnabled(),
                        mfaToken.value(),
                        mfaToken.expiresInSeconds()));
            }

            AuthOutcome authOutcome = issuePlatformSession(admin, UUID.randomUUID(), now);
            auditPlatformAuth(
                    AuditAction.AUTH_LOGIN_SUCCESS,
                    AuditOutcome.SUCCESS,
                    AuditSeverity.INFO,
                    admin.getId(),
                    "Connexion réussie d'un administrateur SaaS.",
                    Map.of("mfaRequired", false));
            return LoginTxOutcome.authenticated(authOutcome);
        });

        return unwrapLoginOutcome(result, rawPassword);
    }

    public MfaEnrollmentResult enrollMfa(AccountRef account) {
        assertTenantContextMatches(account);
        Instant now = clock.instant();

        return transactionTemplate.execute(status -> {
            byte[] rawSecret = TotpGenerator.newSecret(secureRandom);
            String base32Secret = Base32.encode(rawSecret);
            String encryptedSecret = secretCipher.encrypt(base32Secret);

            String accountEmail;
            if (account.type() == SubjectType.USER) {
                User user = userRepository.findById(account.accountId())
                        .orElseThrow(() -> new AuthenticationFailedException("Compte introuvable."));
                ensureCanAuthenticateForMfa(user.isActive(), user.getSecurity(), now);
                user.getSecurity().startMfaEnrollment(encryptedSecret);
                userRepository.save(user);
                accountEmail = user.getEmail();
                auditTenantAuth(
                        AuditAction.AUTH_MFA_ENROLLED,
                        AuditOutcome.SUCCESS,
                        AuditSeverity.INFO,
                        user.getId(),
                        "Enrôlement MFA TOTP initié pour un utilisateur d'établissement.",
                        Map.of("algorithm", "TOTP_SHA1"));
            } else {
                PlatformAdmin admin = platformAdminRepository.findById(account.accountId())
                        .orElseThrow(() -> new AuthenticationFailedException("Compte introuvable."));
                ensureCanAuthenticateForMfa(admin.isActive(), admin.getSecurity(), now);
                admin.getSecurity().startMfaEnrollment(encryptedSecret);
                platformAdminRepository.save(admin);
                accountEmail = admin.getEmail();
                auditPlatformAuth(
                        AuditAction.AUTH_MFA_ENROLLED,
                        AuditOutcome.SUCCESS,
                        AuditSeverity.INFO,
                        admin.getId(),
                        "Enrôlement MFA TOTP initié pour un administrateur SaaS.",
                        Map.of("algorithm", "TOTP_SHA1"));
            }

            String otpauthUri = buildOtpauthUri(accountEmail, base32Secret);
            return new MfaEnrollmentResult(
                    base32Secret,
                    otpauthUri,
                    TotpGenerator.DIGITS,
                    TotpGenerator.PERIOD_SECONDS);
        });
    }

    public AuthOutcome verifyMfa(AccountRef account, String code) {
        assertTenantContextMatches(account);
        Instant now = clock.instant();

        MfaTxOutcome result = transactionTemplate.execute(status -> {
            if (account.type() == SubjectType.USER) {
                Optional<SchoolSummary> schoolOpt = schoolDirectory.findSummaryById(account.tenantId());
                if (schoolOpt.isEmpty() || !schoolOpt.get().active()) {
                    return MfaTxOutcome.invalidAccount();
                }
                Optional<User> userOpt = userRepository.findById(account.accountId());
                if (userOpt.isEmpty()) {
                    return MfaTxOutcome.invalidAccount();
                }
                User user = userOpt.get();
                LoginSecurity security = user.getSecurity();
                if (security.isLocked(now)) {
                    return MfaTxOutcome.locked();
                }
                if (!user.isActive()) {
                    return MfaTxOutcome.invalidAccount();
                }
                if (security.getMfaSecretEncrypted() == null) {
                    return MfaTxOutcome.notEnrolled();
                }

                OptionalLong verifiedStep = verifyTotpCode(security, code, now);
                if (verifiedStep.isEmpty()) {
                    boolean justLocked = security.registerFailure(now);
                    userRepository.save(user);
                    if (justLocked) {
                        log.warn("Compte utilisateur verrouillé après échecs MFA (accountId={})", user.getId());
                        auditTenantAuth(
                                AuditAction.AUTH_ACCOUNT_LOCKED,
                                AuditOutcome.DENIED,
                                AuditSeverity.CRITICAL,
                                user.getId(),
                                "Compte utilisateur verrouillé après échecs MFA répétés.",
                                Map.of("failedAttempts", security.getFailedLoginAttempts()));
                        return MfaTxOutcome.locked();
                    }
                    log.info("Code MFA invalide pour utilisateur (accountId={})", user.getId());
                    auditTenantAuth(
                            AuditAction.AUTH_MFA_FAILED,
                            AuditOutcome.FAILURE,
                            AuditSeverity.WARNING,
                            user.getId(),
                            "Échec de vérification du code MFA utilisateur.",
                            Map.of("failedAttempts", security.getFailedLoginAttempts()));
                    return MfaTxOutcome.invalidCode();
                }

                List<RoleCode> roleCodes = loadUserRoleCodes(user.getId());
                if (roleCodes.isEmpty()) {
                    return MfaTxOutcome.invalidAccount();
                }

                security.acceptMfaCode(verifiedStep.getAsLong());
                security.registerSuccess();
                userRepository.save(user);

                AuthOutcome authOutcome =
                        issueTenantSession(user, schoolOpt.get(), roleCodes, UUID.randomUUID(), now);
                auditTenantAuth(
                        AuditAction.AUTH_MFA_VERIFIED,
                        AuditOutcome.SUCCESS,
                        AuditSeverity.INFO,
                        user.getId(),
                        "Vérification MFA réussie et session utilisateur ouverte.",
                        Map.of("mfaEnabled", true));
                return MfaTxOutcome.success(authOutcome);
            } else {
                Optional<PlatformAdmin> adminOpt = platformAdminRepository.findById(account.accountId());
                if (adminOpt.isEmpty()) {
                    return MfaTxOutcome.invalidAccount();
                }
                PlatformAdmin admin = adminOpt.get();
                LoginSecurity security = admin.getSecurity();
                if (security.isLocked(now)) {
                    return MfaTxOutcome.locked();
                }
                if (!admin.isActive()) {
                    return MfaTxOutcome.invalidAccount();
                }
                if (security.getMfaSecretEncrypted() == null) {
                    return MfaTxOutcome.notEnrolled();
                }

                OptionalLong verifiedStep = verifyTotpCode(security, code, now);
                if (verifiedStep.isEmpty()) {
                    boolean justLocked = security.registerFailure(now);
                    platformAdminRepository.save(admin);
                    if (justLocked) {
                        log.warn("Compte administrateur SaaS verrouillé après échecs MFA (accountId={})", admin.getId());
                        auditPlatformAuth(
                                AuditAction.AUTH_ACCOUNT_LOCKED,
                                AuditOutcome.DENIED,
                                AuditSeverity.CRITICAL,
                                admin.getId(),
                                "Compte administrateur SaaS verrouillé après échecs MFA répétés.",
                                Map.of("failedAttempts", security.getFailedLoginAttempts()));
                        return MfaTxOutcome.locked();
                    }
                    log.info("Code MFA invalide pour administrateur SaaS (accountId={})", admin.getId());
                    auditPlatformAuth(
                            AuditAction.AUTH_MFA_FAILED,
                            AuditOutcome.FAILURE,
                            AuditSeverity.WARNING,
                            admin.getId(),
                            "Échec de vérification du code MFA administrateur SaaS.",
                            Map.of("failedAttempts", security.getFailedLoginAttempts()));
                    return MfaTxOutcome.invalidCode();
                }

                security.acceptMfaCode(verifiedStep.getAsLong());
                security.registerSuccess();
                platformAdminRepository.save(admin);

                AuthOutcome authOutcome = issuePlatformSession(admin, UUID.randomUUID(), now);
                auditPlatformAuth(
                        AuditAction.AUTH_MFA_VERIFIED,
                        AuditOutcome.SUCCESS,
                        AuditSeverity.INFO,
                        admin.getId(),
                        "Vérification MFA réussie et session administrateur SaaS ouverte.",
                        Map.of("mfaEnabled", true));
                return MfaTxOutcome.success(authOutcome);
            }
        });

        if (result == null) {
            throw new AuthenticationFailedException("Vérification MFA impossible.");
        }
        return switch (result.state()) {
            case SUCCESS -> result.outcome();
            case LOCKED -> throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
            case NOT_ENROLLED -> throw new IllegalStateException("Aucun enrôlement MFA n'est en cours pour ce compte.");
            case INVALID_ACCOUNT -> throw new AuthenticationFailedException("Compte inactif ou introuvable.");
            case INVALID_CODE -> throw new AuthenticationFailedException(
                    "Code de vérification MFA invalide ou déjà utilisé.");
        };
    }

    public AuthOutcome refresh(String rawRefreshCookie) {
        ParsedRefreshCookie parsed = RefreshCookieCodec.decode(rawRefreshCookie)
                .orElseThrow(() -> new AuthenticationFailedException("Jeton de renouvellement manquant ou invalide."));
        Instant now = clock.instant();

        RefreshTxOutcome result;
        if (parsed.subjectType() == SubjectType.USER) {
            Optional<SchoolSummary> schoolOpt = schoolDirectory.findSummaryById(parsed.tenantId());
            result = TenantContext.executeWithTenant(
                    TenantId.of(parsed.tenantId()),
                    () -> transactionTemplate.execute(status -> refreshTenantToken(parsed, schoolOpt, now)));
        } else {
            result = transactionTemplate.execute(status -> refreshPlatformToken(parsed, now));
        }

        if (result == null) {
            throw new AuthenticationFailedException("Session invalide ou expirée.");
        }
        return switch (result.state()) {
            case ROTATED -> result.outcome();
            case REUSED -> throw new AuthenticationFailedException(
                    "Session révoquée par mesure de sécurité. Veuillez vous reconnecter.");
            case EXPIRED -> throw new AuthenticationFailedException("Session expirée. Veuillez vous reconnecter.");
            case INVALID -> throw new AuthenticationFailedException("Jeton de renouvellement manquant ou invalide.");
        };
    }

    public void logout(String rawRefreshCookie) {
        Optional<ParsedRefreshCookie> parsedOpt = RefreshCookieCodec.decode(rawRefreshCookie);
        if (parsedOpt.isEmpty()) {
            return;
        }
        ParsedRefreshCookie parsed = parsedOpt.get();
        Instant now = clock.instant();

        if (parsed.subjectType() == SubjectType.USER) {
            TenantContext.runWithTenant(
                    TenantId.of(parsed.tenantId()),
                    () -> transactionTemplate.executeWithoutResult(status -> refreshTokenRepository
                            .findByTokenHashForUpdate(parsed.tokenHash())
                            .ifPresent(token -> {
                                revokeTenantFamily(token.getFamilyId(), now);
                                auditTenantAuth(
                                        AuditAction.AUTH_LOGOUT,
                                        AuditOutcome.SUCCESS,
                                        AuditSeverity.INFO,
                                        token.getUserId(),
                                        "Déconnexion et révocation de la famille de sessions utilisateur.",
                                        Map.of("familyId", token.getFamilyId().toString()));
                            })));
        } else {
            transactionTemplate.executeWithoutResult(status -> platformRefreshTokenRepository
                    .findByTokenHashForUpdate(parsed.tokenHash())
                    .ifPresent(token -> {
                        revokePlatformFamily(token.getFamilyId(), now);
                        auditPlatformAuth(
                                AuditAction.AUTH_LOGOUT,
                                AuditOutcome.SUCCESS,
                                AuditSeverity.INFO,
                                token.getAdminId(),
                                "Déconnexion et révocation de la famille de sessions administrateur SaaS.",
                                Map.of("familyId", token.getFamilyId().toString()));
                    }));
        }
    }

    public UserProfile getCurrentUser(AccountRef account) {
        assertTenantContextMatches(account);

        return readOnlyTransactionTemplate.execute(status -> {
            if (account.type() == SubjectType.USER) {
                SchoolSummary school = schoolDirectory.findSummaryById(account.tenantId())
                        .filter(SchoolSummary::active)
                        .orElseThrow(() -> new AuthenticationFailedException("Établissement inactif ou introuvable."));
                User user = userRepository.findById(account.accountId())
                        .filter(User::isActive)
                        .orElseThrow(() -> new AuthenticationFailedException("Session utilisateur invalide."));
                List<String> roles = loadUserRoleCodes(user.getId()).stream()
                        .map(Enum::name)
                        .sorted()
                        .toList();
                return toTenantProfile(user, school, roles);
            } else {
                PlatformAdmin admin = platformAdminRepository.findById(account.accountId())
                        .filter(PlatformAdmin::isActive)
                        .orElseThrow(() -> new AuthenticationFailedException("Session administrateur invalide."));
                return toPlatformProfile(admin);
            }
        });
    }

    private RefreshTxOutcome refreshTenantToken(
            ParsedRefreshCookie parsed,
            Optional<SchoolSummary> schoolOpt,
            Instant now) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHashForUpdate(parsed.tokenHash());
        if (tokenOpt.isEmpty()) {
            return RefreshTxOutcome.invalid();
        }
        RefreshToken current = tokenOpt.get();
        if (current.isRevoked()) {
            revokeTenantFamily(current.getFamilyId(), now);
            log.warn(
                    "Réutilisation d'un jeton de renouvellement révoqué détectée ; famille révoquée (familyId={})",
                    current.getFamilyId());
            auditTenantAuth(
                    AuditAction.AUTH_REFRESH_REUSE_DETECTED,
                    AuditOutcome.DENIED,
                    AuditSeverity.CRITICAL,
                    current.getUserId(),
                    "Réutilisation d'un jeton de renouvellement révoqué détectée : famille révoquée.",
                    Map.of("familyId", current.getFamilyId().toString()));
            return RefreshTxOutcome.reused();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            refreshTokenRepository.save(current);
            return RefreshTxOutcome.expired();
        }
        if (schoolOpt.isEmpty() || !schoolOpt.get().active()) {
            revokeTenantFamily(current.getFamilyId(), now);
            return RefreshTxOutcome.invalid();
        }
        Optional<User> userOpt = userRepository.findById(current.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().canAuthenticate(now)) {
            revokeTenantFamily(current.getFamilyId(), now);
            return RefreshTxOutcome.invalid();
        }
        User user = userOpt.get();
        List<RoleCode> roleCodes = loadUserRoleCodes(user.getId());
        if (roleCodes.isEmpty()) {
            revokeTenantFamily(current.getFamilyId(), now);
            return RefreshTxOutcome.invalid();
        }

        EncodedRefreshCookie nextCookie = RefreshCookieCodec.createForTenantUser(parsed.tenantId(), secureRandom);
        RefreshToken nextToken = refreshTokenRepository.saveAndFlush(new RefreshToken(
                user.getId(),
                nextCookie.tokenHash(),
                current.getFamilyId(),
                now.plus(TokenLifetimes.REFRESH_TOKEN)));
        current.replaceBy(nextToken.getId(), now);
        refreshTokenRepository.save(current);

        AuthOutcome outcome = buildTenantAuthOutcome(user, schoolOpt.get(), roleCodes, nextCookie.rawValue());
        return RefreshTxOutcome.rotated(outcome);
    }

    private RefreshTxOutcome refreshPlatformToken(ParsedRefreshCookie parsed, Instant now) {
        Optional<PlatformRefreshToken> tokenOpt =
                platformRefreshTokenRepository.findByTokenHashForUpdate(parsed.tokenHash());
        if (tokenOpt.isEmpty()) {
            return RefreshTxOutcome.invalid();
        }
        PlatformRefreshToken current = tokenOpt.get();
        if (current.isRevoked()) {
            revokePlatformFamily(current.getFamilyId(), now);
            log.warn(
                    "Réutilisation d'un jeton de renouvellement plateforme révoqué détectée ; famille révoquée (familyId={})",
                    current.getFamilyId());
            auditPlatformAuth(
                    AuditAction.AUTH_REFRESH_REUSE_DETECTED,
                    AuditOutcome.DENIED,
                    AuditSeverity.CRITICAL,
                    current.getAdminId(),
                    "Réutilisation d'un jeton de renouvellement plateforme révoqué détectée : famille révoquée.",
                    Map.of("familyId", current.getFamilyId().toString()));
            return RefreshTxOutcome.reused();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            platformRefreshTokenRepository.save(current);
            return RefreshTxOutcome.expired();
        }
        Optional<PlatformAdmin> adminOpt = platformAdminRepository.findById(current.getAdminId());
        if (adminOpt.isEmpty() || !adminOpt.get().canAuthenticate(now)) {
            revokePlatformFamily(current.getFamilyId(), now);
            return RefreshTxOutcome.invalid();
        }
        PlatformAdmin admin = adminOpt.get();

        EncodedRefreshCookie nextCookie = RefreshCookieCodec.createForPlatformAdmin(secureRandom);
        PlatformRefreshToken nextToken = platformRefreshTokenRepository.saveAndFlush(new PlatformRefreshToken(
                admin.getId(),
                nextCookie.tokenHash(),
                current.getFamilyId(),
                now.plus(TokenLifetimes.REFRESH_TOKEN)));
        current.replaceBy(nextToken.getId(), now);
        platformRefreshTokenRepository.save(current);

        AuthOutcome outcome = buildPlatformAuthOutcome(admin, nextCookie.rawValue());
        return RefreshTxOutcome.rotated(outcome);
    }

    private AuthOutcome issueTenantSession(
            User user,
            SchoolSummary school,
            List<RoleCode> roleCodes,
            UUID familyId,
            Instant now) {
        EncodedRefreshCookie refreshCookie = RefreshCookieCodec.createForTenantUser(school.id(), secureRandom);
        refreshTokenRepository.save(new RefreshToken(
                user.getId(),
                refreshCookie.tokenHash(),
                familyId,
                now.plus(TokenLifetimes.REFRESH_TOKEN)));
        return buildTenantAuthOutcome(user, school, roleCodes, refreshCookie.rawValue());
    }

    private AuthOutcome buildTenantAuthOutcome(
            User user,
            SchoolSummary school,
            List<RoleCode> roleCodes,
            String refreshCookieValue) {
        Set<String> roleSet = roleCodes.stream().map(Enum::name).collect(Collectors.toSet());
        IssuedToken accessToken = tokenIssuer.issueAccessToken(AccountRef.user(user.getId(), school.id()), roleSet);
        List<String> sortedRoles = roleSet.stream().sorted().toList();
        return AuthOutcome.authenticated(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshCookieValue,
                toTenantProfile(user, school, sortedRoles));
    }

    private AuthOutcome issuePlatformSession(PlatformAdmin admin, UUID familyId, Instant now) {
        EncodedRefreshCookie refreshCookie = RefreshCookieCodec.createForPlatformAdmin(secureRandom);
        platformRefreshTokenRepository.save(new PlatformRefreshToken(
                admin.getId(),
                refreshCookie.tokenHash(),
                familyId,
                now.plus(TokenLifetimes.REFRESH_TOKEN)));
        return buildPlatformAuthOutcome(admin, refreshCookie.rawValue());
    }

    private AuthOutcome buildPlatformAuthOutcome(PlatformAdmin admin, String refreshCookieValue) {
        IssuedToken accessToken = tokenIssuer.issueAccessToken(
                AccountRef.platformAdmin(admin.getId()),
                Set.of(RoleCode.ADMIN_SAAS.name()));
        return AuthOutcome.authenticated(
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshCookieValue,
                toPlatformProfile(admin));
    }

    private void revokeTenantFamily(UUID familyId, Instant now) {
        List<RefreshToken> familyTokens = refreshTokenRepository.findByFamilyId(familyId);
        for (RefreshToken token : familyTokens) {
            token.revoke(now);
        }
        refreshTokenRepository.saveAll(familyTokens);
    }

    private void revokePlatformFamily(UUID familyId, Instant now) {
        List<PlatformRefreshToken> familyTokens = platformRefreshTokenRepository.findByFamilyId(familyId);
        for (PlatformRefreshToken token : familyTokens) {
            token.revoke(now);
        }
        platformRefreshTokenRepository.saveAll(familyTokens);
    }

    private List<RoleCode> loadUserRoleCodes(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleCode)
                .distinct()
                .toList();
    }

    private OptionalLong verifyTotpCode(LoginSecurity security, String code, Instant now) {
        String base32Secret = secretCipher.decrypt(security.getMfaSecretEncrypted());
        byte[] secretBytes = Base32.decode(base32Secret);
        return TotpGenerator.verify(secretBytes, code, now, security.getMfaLastUsedStep());
    }

    private static void ensureCanAuthenticateForMfa(boolean active, LoginSecurity security, Instant now) {
        if (security.isLocked(now)) {
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }
        if (!active) {
            throw new AuthenticationFailedException("Compte inactif.");
        }
    }

    private static void assertTenantContextMatches(AccountRef account) {
        if (account.type() == SubjectType.USER) {
            UUID currentTenant = TenantContext.requireCurrent().value();
            if (!currentTenant.equals(account.tenantId())) {
                throw new AuthenticationFailedException("Contexte d'établissement incohérent.");
            }
        } else if (TenantContext.getCurrent().isPresent()) {
            throw new AuthenticationFailedException("Contexte d'établissement interdit pour un administrateur SaaS.");
        }
    }

    private AuthOutcome unwrapLoginOutcome(LoginTxOutcome result, String rawPassword) {
        if (result == null) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
        }
        return switch (result.state()) {
            case AUTHENTICATED -> result.outcome();
            case NEEDS_DUMMY_CHECK -> {
                passwordHasher.matches(rawPassword, dummyPasswordHash);
                throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
            }
            case INVALID_CREDENTIALS -> throw new AuthenticationFailedException(INVALID_CREDENTIALS_MESSAGE);
            case LOCKED -> throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        };
    }

    private static String buildOtpauthUri(String email, String base32Secret) {
        String encodedIssuer = URLEncoder.encode(TOTP_ISSUER, StandardCharsets.UTF_8);
        String encodedAccount = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return "otpauth://totp/"
                + encodedIssuer
                + ":"
                + encodedAccount
                + "?secret="
                + base32Secret
                + "&issuer="
                + encodedIssuer
                + "&algorithm=SHA1&digits="
                + TotpGenerator.DIGITS
                + "&period="
                + TotpGenerator.PERIOD_SECONDS;
    }

    private static UserProfile toTenantProfile(User user, SchoolSummary school, List<String> roles) {
        return new UserProfile(
                user.getId(),
                SubjectType.USER,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                roles,
                user.getSecurity().isMfaEnabled(),
                school.id(),
                school.code(),
                school.name());
    }

    private static UserProfile toPlatformProfile(PlatformAdmin admin) {
        return new UserProfile(
                admin.getId(),
                SubjectType.PLATFORM_ADMIN,
                admin.getEmail(),
                admin.getFirstName(),
                admin.getLastName(),
                List.of(RoleCode.ADMIN_SAAS.name()),
                admin.getSecurity().isMfaEnabled(),
                null,
                null,
                null);
    }

    private void auditTenantAuth(
            AuditAction action,
            AuditOutcome outcome,
            AuditSeverity severity,
            UUID userId,
            String summary,
            Map<String, Object> metadata) {
        auditLogPort.recordTenantEvent(new AuditRecordCommand(
                AuditCategory.AUTH,
                action,
                outcome,
                severity,
                "USER",
                userId,
                summary,
                userId,
                userId != null ? AuditActorType.USER : AuditActorType.ANONYMOUS,
                null,
                metadata));
    }

    private void auditPlatformAuth(
            AuditAction action,
            AuditOutcome outcome,
            AuditSeverity severity,
            UUID adminId,
            String summary,
            Map<String, Object> metadata) {
        auditLogPort.recordPlatformEvent(new PlatformAuditRecordCommand(
                AuditCategory.AUTH,
                action,
                outcome,
                severity,
                null,
                "PLATFORM_ADMIN",
                adminId,
                summary,
                null,
                adminId,
                adminId != null ? AuditActorType.PLATFORM_ADMIN : AuditActorType.ANONYMOUS,
                RoleCode.ADMIN_SAAS.name(),
                metadata));
    }

    private enum LoginState {
        AUTHENTICATED,
        NEEDS_DUMMY_CHECK,
        INVALID_CREDENTIALS,
        LOCKED
    }

    private record LoginTxOutcome(LoginState state, AuthOutcome outcome) {
        static LoginTxOutcome authenticated(AuthOutcome outcome) {
            return new LoginTxOutcome(LoginState.AUTHENTICATED, outcome);
        }

        static LoginTxOutcome needsDummyCheck() {
            return new LoginTxOutcome(LoginState.NEEDS_DUMMY_CHECK, null);
        }

        static LoginTxOutcome invalidCredentials() {
            return new LoginTxOutcome(LoginState.INVALID_CREDENTIALS, null);
        }

        static LoginTxOutcome locked() {
            return new LoginTxOutcome(LoginState.LOCKED, null);
        }
    }

    private enum MfaState {
        SUCCESS,
        LOCKED,
        NOT_ENROLLED,
        INVALID_ACCOUNT,
        INVALID_CODE
    }

    private record MfaTxOutcome(MfaState state, AuthOutcome outcome) {
        static MfaTxOutcome success(AuthOutcome outcome) {
            return new MfaTxOutcome(MfaState.SUCCESS, outcome);
        }

        static MfaTxOutcome locked() {
            return new MfaTxOutcome(MfaState.LOCKED, null);
        }

        static MfaTxOutcome notEnrolled() {
            return new MfaTxOutcome(MfaState.NOT_ENROLLED, null);
        }

        static MfaTxOutcome invalidAccount() {
            return new MfaTxOutcome(MfaState.INVALID_ACCOUNT, null);
        }

        static MfaTxOutcome invalidCode() {
            return new MfaTxOutcome(MfaState.INVALID_CODE, null);
        }
    }

    private enum RefreshState {
        ROTATED,
        REUSED,
        EXPIRED,
        INVALID
    }

    private record RefreshTxOutcome(RefreshState state, AuthOutcome outcome) {
        static RefreshTxOutcome rotated(AuthOutcome outcome) {
            return new RefreshTxOutcome(RefreshState.ROTATED, outcome);
        }

        static RefreshTxOutcome reused() {
            return new RefreshTxOutcome(RefreshState.REUSED, null);
        }

        static RefreshTxOutcome expired() {
            return new RefreshTxOutcome(RefreshState.EXPIRED, null);
        }

        static RefreshTxOutcome invalid() {
            return new RefreshTxOutcome(RefreshState.INVALID, null);
        }
    }
}
