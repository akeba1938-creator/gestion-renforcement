package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.InstallmentStatus;
import com.ecole.saas.finance.domain.PaymentSchedule;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentScheduleResponse(
        UUID id,
        UUID invoiceId,
        int installmentNo,
        String label,
        LocalDate dueDate,
        long amountXof,
        long paidAmountXof,
        long remainingAmountXof,
        InstallmentStatus status) {

    public static PaymentScheduleResponse from(PaymentSchedule schedule) {
        return new PaymentScheduleResponse(
                schedule.getId(),
                schedule.getInvoiceId(),
                schedule.getInstallmentNo(),
                schedule.getLabel(),
                schedule.getDueDate(),
                schedule.getAmountXof(),
                schedule.getPaidAmountXof(),
                schedule.remainingAmountXof(),
                schedule.getStatus());
    }
}
