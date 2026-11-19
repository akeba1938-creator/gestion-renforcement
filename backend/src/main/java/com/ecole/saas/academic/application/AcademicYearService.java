package com.ecole.saas.academic.application;

import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearStatus;
import com.ecole.saas.academic.infrastructure.AcademicYearRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;

    public AcademicYearService(AcademicYearRepository academicYearRepository) {
        this.academicYearRepository = academicYearRepository;
    }

    @Transactional
    public AcademicYear createAcademicYear(
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            AcademicYearStatus status,
            boolean current) {
        return academicYearRepository.save(new AcademicYear(code, label, startDate, endDate, status, current));
    }

    @Transactional(readOnly = true)
    public List<AcademicYear> findAllForCurrentTenant() {
        return academicYearRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<AcademicYear> findById(UUID id) {
        return academicYearRepository.findById(id);
    }

    @Transactional
    public Optional<AcademicYear> renameAcademicYear(UUID id, String newLabel) {
        Optional<AcademicYear> found = academicYearRepository.findById(id);
        found.ifPresent(year -> year.updateLabel(newLabel));
        return found;
    }
}
