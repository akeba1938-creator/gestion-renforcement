package com.ecole.saas.finance.application;

import com.ecole.saas.finance.domain.FinanceSequenceType;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import com.ecole.saas.finance.infrastructure.FinanceSequenceRepository;
import com.ecole.saas.finance.infrastructure.LedgerEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assistant centralisé d'écriture en ajout seul dans le registre comptable immuable {@code ledger_entries}.
 */
@Component
class LedgerPostingHelper {

    private final FinanceSequenceRepository sequenceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    LedgerPostingHelper(
            FinanceSequenceRepository sequenceRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.sequenceRepository = sequenceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    LedgerEntry appendEntry(
            UUID studentId,
            UUID invoiceId,
            LedgerEntryType entryType,
            long amountXof,
            LedgerReferenceType referenceType,
            UUID referenceId,
            UUID reversalOfEntryId,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        long sequenceNo = sequenceRepository.nextSequenceValue(FinanceSequenceType.LEDGER, 0);
        LedgerEntry entry = new LedgerEntry(
                sequenceNo,
                studentId,
                invoiceId,
                entryType,
                amountXof,
                referenceType,
                referenceId,
                reversalOfEntryId,
                reason,
                actorId,
                occurredAt);
        return ledgerEntryRepository.save(entry);
    }
}
