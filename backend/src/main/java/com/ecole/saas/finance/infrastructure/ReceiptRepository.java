package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.Receipt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dépôt en ajout seul sur {@code receipts} : aucune méthode de suppression n'est exposée.
 */
public interface ReceiptRepository extends Repository<Receipt, UUID>, JpaSpecificationExecutor<Receipt> {

    Receipt save(Receipt receipt);

    Optional<Receipt> findById(UUID id);

    Optional<Receipt> findByPaymentId(UUID paymentId);

    List<Receipt> findByPaymentIdIn(Collection<UUID> paymentIds);

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    Optional<Receipt> findByVerificationToken(String verificationToken);
}
