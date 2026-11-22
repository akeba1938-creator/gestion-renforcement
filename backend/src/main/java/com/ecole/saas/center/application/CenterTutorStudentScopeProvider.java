package com.ecole.saas.center.application;

import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.TutorCohortStudentScopeProvider;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du SPI {@link TutorCohortStudentScopeProvider} pour le mode centre :
 * résout en lot les élèves activement inscrits dans les cohortes affectées à un intervenant.
 */
@Component
class CenterTutorStudentScopeProvider implements TutorCohortStudentScopeProvider {

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CenterDirectoryPort centerDirectoryPort;

    CenterTutorStudentScopeProvider(
            TeacherDirectoryPort teacherDirectoryPort,
            CenterDirectoryPort centerDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.centerDirectoryPort = centerDirectoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findActiveStudentIdsForTutorUser(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Set.of();
        }
        Optional<TeacherSummary> teacherOpt = teacherDirectoryPort.findTeacherByUserId(userId);
        if (teacherOpt.isEmpty()) {
            return Set.of();
        }
        Set<UUID> groupIds = centerDirectoryPort.findAssignedGroupIdsByTeacherId(teacherOpt.get().id());
        if (groupIds.isEmpty()) {
            return Set.of();
        }
        return centerDirectoryPort.findActiveStudentIdsByGroupIds(groupIds);
    }
}
