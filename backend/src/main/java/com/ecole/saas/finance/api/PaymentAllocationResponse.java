package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.PaymentAllocation;
import java.util.UUID;

public record PaymentAllocationResponse(UUID id, UUID scheduleId, long amountXof) {

    public static PaymentAllocationResponse from(PaymentAllocation allocation) {
        return new PaymentAllocationResponse(
                allocation.getId(),
                allocation.getScheduleId(),
                allocation.getAmountXof());
    }
}
