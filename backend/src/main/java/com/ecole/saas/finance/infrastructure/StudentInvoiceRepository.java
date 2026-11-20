package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.StudentInvoice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StudentInvoiceRepository
        extends JpaRepository<StudentInvoice, UUID>, JpaSpecificationExecutor<StudentInvoice> {

    Optional<StudentInvoice> findByInvoiceNumber(String invoiceNumber);

    List<StudentInvoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<StudentInvoice> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(UUID studentId, UUID academicYearId);

    List<StudentInvoice> findByStudentIdIn(Collection<UUID> studentIds);

    List<StudentInvoice> findByStudentIdInAndAcademicYearId(Collection<UUID> studentIds, UUID academicYearId);
}
