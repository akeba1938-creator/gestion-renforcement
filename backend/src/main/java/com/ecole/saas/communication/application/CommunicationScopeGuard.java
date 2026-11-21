package com.ecole.saas.communication.application;

import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.center.CenterGroupEnrollmentSummary;
import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import com.ecole.saas.communication.domain.CommunicationForbiddenException;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Garde de périmètre fonctionnel pour le module {@code communication}.
 * Applique côté serveur les restrictions fines par rôle ({@code CASHIER}, {@code CENSOR}, {@code TEACHER}/{@code TUTOR}).
 */
@Component
class CommunicationScopeGuard {

    private static final Set<String> FULL_ACCESS_ROLES =
            Set.of("ROLE_PROMOTER", "ROLE_DIRECTOR", "ROLE_CENTER_MANAGER", "ROLE_SECRETARY");

    private static final Set<MessageCategory> TEACHER_ALLOWED_CATEGORIES = Set.of(
            MessageCategory.GENERAL,
            MessageCategory.ABSENCE_ALERT,
            MessageCategory.CENTER_SESSION);

    private static final Set<AudienceType> CASHIER_ALLOWED_AUDIENCES = Set.of(
            AudienceType.GUARDIAN,
            AudienceType.FAMILY,
            AudienceType.STUDENT,
            AudienceType.STUDENT_GUARDIANS,
            AudienceType.CLASS_ROOM_GUARDIANS,
            AudienceType.CENTER_GROUP_GUARDIANS,
            AudienceType.ALL_GUARDIANS);

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CenterDirectoryPort centerDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;

    CommunicationScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            CenterDirectoryPort centerDirectoryPort,
            StudentDirectoryPort studentDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.centerDirectoryPort = centerDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
    }

    boolean hasFullAdminScope() {
        return hasFullAdminScope(currentAuthorities());
    }

    private static boolean hasFullAdminScope(Set<String> roles) {
        return roles.stream().anyMatch(FULL_ACCESS_ROLES::contains);
    }

    boolean canAccessFinancialCampaigns() {
        Set<String> roles = currentAuthorities();
        return hasFullAdminScope(roles) || roles.contains("ROLE_CASHIER");
    }

    boolean canAccessNonFinancialCampaigns() {
        Set<String> roles = currentAuthorities();
        return hasFullAdminScope(roles) || roles.contains("ROLE_CENSOR");
    }

    boolean hasTeacherOrTutorScope() {
        Set<String> roles = currentAuthorities();
        return roles.contains("ROLE_TEACHER") || roles.contains("ROLE_TUTOR");
    }

    void assertCanSendCampaign(
            UUID actorUserId,
            MessageCategory category,
            AudienceType audienceType,
            UUID audienceTargetId) {
        Set<String> roles = currentAuthorities();
        if (hasFullAdminScope(roles)) {
            return;
        }

        boolean hasCashier = roles.contains("ROLE_CASHIER");
        boolean hasCensor = roles.contains("ROLE_CENSOR");
        boolean hasTeacher = roles.contains("ROLE_TEACHER") || roles.contains("ROLE_TUTOR");

        if (category.isFinancial()) {
            if (hasCashier) {
                if (!CASHIER_ALLOWED_AUDIENCES.contains(audienceType)) {
                    throw new CommunicationForbiddenException(
                            "Le rôle Caissier ne peut envoyer de relances qu'aux familles, tuteurs ou élèves.");
                }
                return;
            }
            if (hasCensor) {
                throw new CommunicationForbiddenException(
                        "Le rôle Censeur ne peut pas envoyer de communications financières.");
            }
            if (hasTeacher) {
                throw new CommunicationForbiddenException(
                        "Les enseignants et intervenants ne peuvent envoyer que des messages pédagogiques, alertes d'absence ou notifications de séance.");
            }
            throw new CommunicationForbiddenException(
                    "Vous ne disposez pas des autorisations nécessaires pour envoyer cette communication.");
        }

        // Catégories non financières : union des capacités CENSOR puis TEACHER/TUTOR
        if (hasCensor) {
            return;
        }
        if (hasTeacher) {
            if (!TEACHER_ALLOWED_CATEGORIES.contains(category)) {
                throw new CommunicationForbiddenException(
                        "Les enseignants et intervenants ne peuvent envoyer que des messages pédagogiques, alertes d'absence ou notifications de séance.");
            }
            assertTeacherOrTutorAudienceAllowed(actorUserId, audienceType, audienceTargetId);
            return;
        }
        if (hasCashier) {
            throw new CommunicationForbiddenException(
                    "Le rôle Caissier est limité aux relances et reçus financiers (PAYMENT_RECEIPT, PAYMENT_DUE_REMINDER).");
        }
        throw new CommunicationForbiddenException(
                "Vous ne disposez pas des autorisations nécessaires pour envoyer cette communication.");
    }

    void assertCanReadCampaign(UUID actorUserId, CommunicationCampaign campaign) {
        Set<String> roles = currentAuthorities();
        if (hasFullAdminScope(roles)) {
            return;
        }

        boolean hasCashier = roles.contains("ROLE_CASHIER");
        boolean hasCensor = roles.contains("ROLE_CENSOR");
        boolean hasTeacher = roles.contains("ROLE_TEACHER") || roles.contains("ROLE_TUTOR");

        if (campaign.getCategory().isFinancial()) {
            if (hasCashier) {
                return;
            }
            if (hasCensor) {
                throw new CommunicationForbiddenException(
                        "Le rôle Censeur ne peut pas consulter les communications financières.");
            }
            if (hasTeacher) {
                throw new CommunicationForbiddenException(
                        "Un enseignant ou intervenant ne peut consulter que ses propres envois.");
            }
            throw new CommunicationForbiddenException("Accès refusé à cette campagne de communication.");
        }

        if (hasCensor) {
            return;
        }
        if (hasTeacher) {
            if (actorUserId != null && actorUserId.equals(campaign.getCreatedBy())) {
                return;
            }
            throw new CommunicationForbiddenException(
                    "Un enseignant ou intervenant ne peut consulter que ses propres envois.");
        }
        if (hasCashier) {
            throw new CommunicationForbiddenException(
                    "Le rôle Caissier ne peut consulter que les communications financières.");
        }
        throw new CommunicationForbiddenException("Accès refusé à cette campagne de communication.");
    }

    void assertCanMutateCampaign(UUID actorUserId, CommunicationCampaign campaign) {
        if (hasFullAdminScope()) {
            return;
        }
        assertCanReadCampaign(actorUserId, campaign);
        if (actorUserId == null || !actorUserId.equals(campaign.getCreatedBy())) {
            throw new CommunicationForbiddenException(
                    "Vous ne pouvez modifier ou annuler que les campagnes que vous avez créées.");
        }
    }

    private void assertTeacherOrTutorAudienceAllowed(
            UUID actorUserId,
            AudienceType audienceType,
            UUID audienceTargetId) {
        if (actorUserId == null) {
            throw new CommunicationForbiddenException("Identifiant utilisateur introuvable.");
        }
        switch (audienceType) {
            case CLASS_ROOM_GUARDIANS, CLASS_ROOM_STUDENTS -> {
                if (audienceTargetId == null
                        || !teacherDirectoryPort.isUserAssignedToClass(actorUserId, audienceTargetId)) {
                    throw new CommunicationForbiddenException(
                            "Vous ne pouvez envoyer un message qu'aux classes auxquelles vous êtes activement affecté.");
                }
            }
            case CENTER_GROUP_GUARDIANS, CENTER_GROUP_STUDENTS -> {
                TeacherSummary teacher = requireActiveTeacherForUser(actorUserId);
                if (audienceTargetId == null
                        || !centerDirectoryPort.isTeacherAssignedToGroup(teacher.id(), audienceTargetId)) {
                    throw new CommunicationForbiddenException(
                            "Vous ne pouvez envoyer un message qu'aux cohortes centre auxquelles vous êtes activement affecté.");
                }
            }
            case STUDENT, STUDENT_GUARDIANS -> {
                if (audienceTargetId == null || !isStudentInTeacherOrTutorScope(actorUserId, audienceTargetId)) {
                    throw new CommunicationForbiddenException(
                            "Vous ne pouvez contacter que les élèves inscrits dans vos classes ou cohortes affectées.");
                }
            }
            default -> throw new CommunicationForbiddenException(
                    "L'audience " + audienceType + " n'est pas autorisée pour un enseignant ou intervenant.");
        }
    }

    private boolean isStudentInTeacherOrTutorScope(UUID actorUserId, UUID studentId) {
        Set<UUID> assignedClasses = teacherDirectoryPort.findAssignedClassRoomIdsByUserId(actorUserId);
        if (!assignedClasses.isEmpty()) {
            Optional<StudentEnrollmentSummary> enrollment = studentDirectoryPort.findCurrentEnrollmentForStudent(studentId);
            if (enrollment.isPresent()
                    && "ACTIVE".equals(enrollment.get().status())
                    && assignedClasses.contains(enrollment.get().classRoomId())) {
                return true;
            }
        }
        Optional<TeacherSummary> teacher = teacherDirectoryPort.findTeacherByUserId(actorUserId);
        if (teacher.isPresent() && teacher.get().active()) {
            Set<UUID> assignedGroups = centerDirectoryPort.findAssignedGroupIdsByTeacherId(teacher.get().id());
            if (!assignedGroups.isEmpty()) {
                for (CenterGroupEnrollmentSummary groupEnrollment :
                        centerDirectoryPort.findActiveEnrollmentsByStudent(studentId)) {
                    if (assignedGroups.contains(groupEnrollment.groupId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private TeacherSummary requireActiveTeacherForUser(UUID actorUserId) {
        return teacherDirectoryPort.findTeacherByUserId(actorUserId)
                .filter(TeacherSummary::active)
                .orElseThrow(() -> new CommunicationForbiddenException(
                        "Aucun dossier enseignant/intervenant actif n'est lié à votre compte."));
    }

    private static Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
