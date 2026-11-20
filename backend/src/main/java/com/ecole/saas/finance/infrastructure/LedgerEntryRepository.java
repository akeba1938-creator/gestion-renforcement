package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Dépôt en ajout seul sur {@code ledger_entries} (ADR 0003) : aucune méthode de suppression n'est exposée.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findById(UUID id);

    List<LedgerEntry> findByStudentIdOrderBySequenceNoAsc(UUID studentId);

    List<LedgerEntry> findByStudentIdIn(Collection<UUID> studentIds);

    List<LedgerEntry> findByInvoiceIdOrderBySequenceNoAsc(UUID invoiceId);

    Optional<LedgerEntry> findFirstByReferenceTypeAndReferenceIdAndEntryType(
            LedgerReferenceType referenceType, UUID referenceId, LedgerEntryType entryType);
}
