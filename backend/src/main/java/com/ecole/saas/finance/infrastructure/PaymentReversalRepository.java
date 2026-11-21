package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.PaymentReversal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt en ajout seul sur {@code payment_reversals} : aucune méthode de suppression n'est exposée.
 */
public interface PaymentReversalRepository extends Repository<PaymentReversal, UUID> {

    interface CashReversalAggregate {
        long getTotalAmountXof();

        long getOperationCount();
    }

    PaymentReversal save(PaymentReversal reversal);

    Optional<PaymentReversal> findById(UUID id);

    Optional<PaymentReversal> findByOriginalPaymentId(UUID originalPaymentId);

    boolean existsByOriginalPaymentId(UUID originalPaymentId);

    List<PaymentReversal> findByOriginalPaymentIdIn(Collection<UUID> originalPaymentIds);

    List<PaymentReversal> findByPaymentMethodAndReversedAtGreaterThanEqualAndReversedAtLessThan(
            PaymentMethod paymentMethod, Instant startInclusive, Instant endExclusive);

    @Query("""
            SELECT COALESCE(SUM(r.amountXof), 0L) AS totalAmountXof,
                   COUNT(r.id) AS operationCount
            FROM PaymentReversal r
            WHERE r.paymentMethod = :method
              AND r.reversedAt >= :startInclusive
              AND r.reversedAt < :endExclusive
            """)
    CashReversalAggregate aggregateByPaymentMethodAndReversedAtRange(
            @Param("method") PaymentMethod method,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
