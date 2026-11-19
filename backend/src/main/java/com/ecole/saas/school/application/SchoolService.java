package com.ecole.saas.school.application;

import com.ecole.saas.school.domain.School;
import com.ecole.saas.school.domain.SchoolType;
import com.ecole.saas.school.infrastructure.SchoolRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {

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
}
