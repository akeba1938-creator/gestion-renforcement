package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.LedgerDirection;
import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        long sequenceNo,
        UUID studentId,
        UUID invoiceId,
        LedgerEntryType entryType,
        LedgerDirection direction,
        long amountXof,
        String currency,
        LedgerReferenceType referenceType,
        UUID referenceId,
        UUID reversalOfEntryId,
        String reason,
        UUID actorId,
        Instant occurredAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getSequenceNo(),
                entry.getStudentId(),
                entry.getInvoiceId(),
                entry.getEntryType(),
                entry.getDirection(),
                entry.getAmountXof(),
                entry.getCurrency(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getReversalOfEntryId(),
                entry.getReason(),
                entry.getActorId(),
                entry.getOccurredAt());
    }
}
