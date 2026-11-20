package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.PaymentReversalRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentReversalRequestRepository
        extends JpaRepository<PaymentReversalRequest, UUID>, JpaSpecificationExecutor<PaymentReversalRequest> {

    boolean existsByPaymentIdAndStatus(UUID paymentId, ApprovalStatus status);

    List<PaymentReversalRequest> findByPaymentIdOrderByRequestedAtDesc(UUID paymentId);
}
