package com.ecole.saas.student.application;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.domain.StudentStatus;
import com.ecole.saas.student.infrastructure.StudentRepository;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remonte l'usage courant des élèves actifs au module {@code subscription} via le SPI {@link QuotaUsageProvider}.
 */
@Component
public class StudentQuotaUsageProvider implements QuotaUsageProvider {

    private final StudentRepository studentRepository;

    public StudentQuotaUsageProvider(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public QuotaResource resource() {
        return QuotaResource.STUDENTS;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentUsageForCurrentTenant() {
        TenantContext.requireCurrent();
        return studentRepository.countByStatus(StudentStatus.ACTIVE);
    }
}
