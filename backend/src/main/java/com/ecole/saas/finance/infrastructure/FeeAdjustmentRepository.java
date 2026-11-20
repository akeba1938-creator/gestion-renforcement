package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeeAdjustmentRepository
        extends JpaRepository<FeeAdjustment, UUID>, JpaSpecificationExecutor<FeeAdjustment> {

    List<FeeAdjustment> findByInvoiceIdOrderByRequestedAtDesc(UUID invoiceId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, ApprovalStatus status);
}
