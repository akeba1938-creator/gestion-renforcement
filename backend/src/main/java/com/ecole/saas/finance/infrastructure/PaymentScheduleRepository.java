package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.PaymentSchedule;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {

    List<PaymentSchedule> findByInvoiceIdOrderByInstallmentNoAsc(UUID invoiceId);

    List<PaymentSchedule> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    void deleteByInvoiceId(UUID invoiceId);
}
