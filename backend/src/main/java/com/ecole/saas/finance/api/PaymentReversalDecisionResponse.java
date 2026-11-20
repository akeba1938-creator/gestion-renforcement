package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.PaymentReversalService;

public record PaymentReversalDecisionResponse(
        PaymentReversalRequestResponse request,
        PaymentReversalResponse reversal) {

    public static PaymentReversalDecisionResponse from(PaymentReversalService.ReversalDecisionView view) {
        return new PaymentReversalDecisionResponse(
                PaymentReversalRequestResponse.from(view.request()),
                PaymentReversalResponse.from(view.reversal()));
    }
}
