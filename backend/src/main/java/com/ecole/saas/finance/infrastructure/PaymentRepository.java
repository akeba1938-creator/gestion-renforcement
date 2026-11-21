package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt en ajout seul sur {@code payments} : aucune méthode de suppression n'est exposée.
 */
public interface PaymentRepository extends Repository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    interface CashMethodAggregate {
        long getTotalAmountXof();

        long getOperationCount();
    }

    Payment save(Payment payment);

    Payment saveAndFlush(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByPaymentMethodAndExternalReference(PaymentMethod paymentMethod, String externalReference);

    List<Payment> findByInvoiceIdOrderByPaidAtDesc(UUID invoiceId);

    List<Payment> findByPaymentMethodAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            PaymentMethod paymentMethod, Instant startInclusive, Instant endExclusive);

    @Query("""
            SELECT COALESCE(SUM(p.amountXof), 0L) AS totalAmountXof,
                   COUNT(p.id) AS operationCount
            FROM Payment p
            WHERE p.paymentMethod = :method
              AND p.paidAt >= :startInclusive
              AND p.paidAt < :endExclusive
            """)
    CashMethodAggregate aggregateByPaymentMethodAndPaidAtRange(
            @Param("method") PaymentMethod method,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
