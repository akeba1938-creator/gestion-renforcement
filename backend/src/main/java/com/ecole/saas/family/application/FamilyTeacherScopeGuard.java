package com.ecole.saas.family.application;

import com.ecole.saas.family.domain.FamilyAccessForbiddenException;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.family.infrastructure.GuardianRepository;
import com.ecole.saas.family.infrastructure.StudentGuardianLinkRepository;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Applique la restriction fine des rôles {@code TEACHER} et {@code TUTOR} dans le module {@code family}
 * (accès borné aux familles et tuteurs des élèves inscrits dans les classes affectées) ainsi que
 * le masquage de {@code nationalIdNumber} hors direction / secrétariat.
 */
@Component
class FamilyTeacherScopeGuard {

    private static final Set<String> UNRESTRICTED_READ_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY",
            "ROLE_CASHIER",
            "ROLE_CENSOR");

    private static final Set<String> TEACHER_AUTHORITIES = Set.of("ROLE_TEACHER", "ROLE_TUTOR");

    private static final Set<String> NATIONAL_ID_VIEW_AUTHORITIES = Set.of(
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY");

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final StudentGuardianLinkRepository linkRepository;
    private final GuardianRepository guardianRepository;

    FamilyTeacherScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            StudentDirectoryPort studentDirectoryPort,
            StudentGuardianLinkRepository linkRepository,
            GuardianRepository guardianRepository) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.linkRepository = linkRepository;
        this.guardianRepository = guardianRepository;
    }

    boolean isRestrictedTeacher() {
        Set<String> authorities = currentAuthorities();
        if (authorities.isEmpty()) {
            return false;
        }
        boolean hasUnrestricted = authorities.stream().anyMatch(UNRESTRICTED_READ_AUTHORITIES::contains);
        boolean hasTeacher = authorities.stream().anyMatch(TEACHER_AUTHORITIES::contains);
        return hasTeacher && !hasUnrestricted;
    }

    boolean canViewNationalIdNumber() {
        Set<String> authorities = currentAuthorities();
        return authorities.stream().anyMatch(NATIONAL_ID_VIEW_AUTHORITIES::contains);
    }

    Set<UUID> resolveAssignedClassRoomIds() {
        UUID userId = extractCurrentUserId();
        if (userId == null) {
            return Set.of();
        }
        return teacherDirectoryPort.findAssignedClassRoomIdsByUserId(userId);
    }

    Set<UUID> resolveVisibleStudentIds() {
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (classRoomIds.isEmpty()) {
            return Set.of();
        }
        return studentDirectoryPort.findActiveStudentIdsByClassRooms(classRoomIds);
    }

    Set<UUID> resolveVisibleGuardianIds() {
        Set<UUID> studentIds = resolveVisibleStudentIds();
        if (studentIds.isEmpty()) {
            return Set.of();
        }
        return linkRepository.findByStudentIdInAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(studentIds).stream()
                .map(StudentGuardianLink::getGuardianId)
                .collect(Collectors.toUnmodifiableSet());
    }

    Set<UUID> resolveVisibleFamilyIds() {
        Set<UUID> guardianIds = resolveVisibleGuardianIds();
        if (guardianIds.isEmpty()) {
            return Set.of();
        }
        return guardianRepository.findAllById(guardianIds).stream()
                .map(Guardian::getFamilyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    void assertCanAccessStudent(UUID studentId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (classRoomIds.isEmpty()
                || !studentDirectoryPort.isStudentActivelyEnrolledInAnyClass(studentId, classRoomIds)) {
            throw new FamilyAccessForbiddenException(
                    "Vous ne pouvez consulter que les tuteurs des élèves inscrits dans vos classes affectées.");
        }
    }

    void assertCanAccessGuardian(UUID guardianId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> visibleStudentIds = resolveVisibleStudentIds();
        if (visibleStudentIds.isEmpty()
                || !linkRepository.existsByGuardianIdAndStudentIdInAndActiveTrue(guardianId, visibleStudentIds)) {
            throw new FamilyAccessForbiddenException(
                    "Vous ne pouvez consulter que les parents et tuteurs des élèves inscrits dans vos classes affectées.");
        }
    }

    void assertCanAccessFamily(UUID familyId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> visibleGuardianIds = resolveVisibleGuardianIds();
        if (visibleGuardianIds.isEmpty()
                || !guardianRepository.existsByFamilyIdAndIdIn(familyId, visibleGuardianIds)) {
            throw new FamilyAccessForbiddenException(
                    "Vous ne pouvez consulter que les familles des élèves inscrits dans vos classes affectées.");
        }
    }

    private static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static UUID extractCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
