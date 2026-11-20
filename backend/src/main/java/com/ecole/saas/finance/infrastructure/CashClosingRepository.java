package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.CashClosing;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CashClosingRepository
        extends JpaRepository<CashClosing, UUID>, JpaSpecificationExecutor<CashClosing> {

    boolean existsByClosingDate(LocalDate closingDate);

    Optional<CashClosing> findByClosingDate(LocalDate closingDate);
}
