package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.StudentInvoice;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentInvoiceRepository
        extends JpaRepository<StudentInvoice, UUID>, JpaSpecificationExecutor<StudentInvoice> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StudentInvoice i WHERE i.id = :id")
    Optional<StudentInvoice> findByIdForUpdate(@Param("id") UUID id);

    Optional<StudentInvoice> findByInvoiceNumber(String invoiceNumber);

    List<StudentInvoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<StudentInvoice> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(UUID studentId, UUID academicYearId);

    List<StudentInvoice> findByStudentIdIn(Collection<UUID> studentIds);

    List<StudentInvoice> findByStudentIdInAndAcademicYearId(Collection<UUID> studentIds, UUID academicYearId);
}
