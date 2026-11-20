package com.ecole.saas.academic;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearSummary(
        UUID id,
        String code,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        String periodSystem,
        String status,
        boolean current) {
}
