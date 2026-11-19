package com.ecole.saas.school.application;

import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolStatus;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.school.infrastructure.SchoolRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService implements SchoolDirectory {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Transactional
    public School createSchool(String code, String name, SchoolType type, String phone, String email, String address) {
        School school = new School(code, name, type, phone, email, address);
        return schoolRepository.save(school);
    }

    @Transactional(readOnly = true)
    public Optional<School> findById(UUID id) {
        return schoolRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<School> findByCode(String code) {
        return schoolRepository.findByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolSummary> findSummaryByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return schoolRepository.findByCode(normalized).map(SchoolService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SchoolSummary> findSummaryById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return schoolRepository.findById(id).map(SchoolService::toSummary);
    }

    private static SchoolSummary toSummary(School school) {
        return new SchoolSummary(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getStatus() == SchoolStatus.ACTIVE);
    }
}
