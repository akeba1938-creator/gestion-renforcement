package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.StudentInvoiceService;
import java.util.List;

public record InvoiceDetailResponse(
        InvoiceResponse invoice,
        List<InvoiceLineResponse> lines,
        List<PaymentScheduleResponse> schedules) {

    public static InvoiceDetailResponse from(StudentInvoiceService.InvoiceDetailView view) {
        return new InvoiceDetailResponse(
                InvoiceResponse.from(view.invoice()),
                view.lines().stream().map(InvoiceLineResponse::from).toList(),
                view.schedules().stream().map(PaymentScheduleResponse::from).toList());
    }
}
