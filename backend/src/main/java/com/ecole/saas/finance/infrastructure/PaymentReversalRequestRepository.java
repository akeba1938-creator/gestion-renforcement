package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.PaymentReversalRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentReversalRequestRepository
        extends JpaRepository<PaymentReversalRequest, UUID>, JpaSpecificationExecutor<PaymentReversalRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentReversalRequest r WHERE r.id = :id")
    Optional<PaymentReversalRequest> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPaymentIdAndStatus(UUID paymentId, ApprovalStatus status);

    List<PaymentReversalRequest> findByPaymentIdOrderByRequestedAtDesc(UUID paymentId);
}
