package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dépôt en ajout seul sur {@code payments} : aucune méthode de suppression n'est exposée.
 */
public interface PaymentRepository extends Repository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByPaymentMethodAndExternalReference(PaymentMethod paymentMethod, String externalReference);

    List<Payment> findByInvoiceIdOrderByPaidAtDesc(UUID invoiceId);

    List<Payment> findByPaymentMethodAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            PaymentMethod paymentMethod, Instant startInclusive, Instant endExclusive);
}
