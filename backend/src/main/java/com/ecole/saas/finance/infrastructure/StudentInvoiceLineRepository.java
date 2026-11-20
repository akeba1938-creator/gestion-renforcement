package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.StudentInvoiceLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentInvoiceLineRepository extends JpaRepository<StudentInvoiceLine, UUID> {

    List<StudentInvoiceLine> findByInvoiceIdOrderByLineOrderAsc(UUID invoiceId);

    void deleteByInvoiceId(UUID invoiceId);
}
