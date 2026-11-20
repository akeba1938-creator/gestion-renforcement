package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.PaymentAllocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Dépôt en ajout seul sur {@code payment_allocations} : aucune méthode de suppression n'est exposée.
 */
public interface PaymentAllocationRepository extends Repository<PaymentAllocation, UUID> {

    PaymentAllocation save(PaymentAllocation allocation);

    List<PaymentAllocation> saveAll(Iterable<PaymentAllocation> allocations);

    List<PaymentAllocation> findByPaymentId(UUID paymentId);
}
