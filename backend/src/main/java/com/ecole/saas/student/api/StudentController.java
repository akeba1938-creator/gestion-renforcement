package com.ecole.saas.student.api;

import com.ecole.saas.student.application.StudentEnrollmentService;
import com.ecole.saas.student.application.StudentService;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentGender;
import com.ecole.saas.student.domain.StudentStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des dossiers élèves d'un établissement (tenant issu exclusivement du JWT côté serveur).
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;
    private final StudentEnrollmentService enrollmentService;

    public StudentController(StudentService studentService, StudentEnrollmentService enrollmentService) {
        this.studentService = studentService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentResponse>> listStudents(
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) StudentGender gender,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = StudentPagination.of(
                page, size, sortBy, direction, StudentPagination.STUDENT_SORT_FIELDS, "lastName", Sort.Direction.ASC);
        Page<StudentResponse> students = studentService
                .listStudents(status, gender, search, pageable)
                .map(StudentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(students));
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<StudentResponse> getStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.getStudent(studentId)));
    }

    @GetMapping("/by-matricule/{matricule}")
    public ResponseEntity<StudentResponse> getStudentByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.getStudentByMatricule(matricule)));
    }

    @PostMapping
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        Student created = studentService.createStudent(
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.birthDate(),
                request.birthPlace(),
                request.nationality(),
                request.address(),
                request.phone(),
                request.email(),
                request.medicalNotes(),
                request.admissionDate());
        return ResponseEntity.created(URI.create("/api/v1/students/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(created));
    }

    @PutMapping("/{studentId}")
    public ResponseEntity<StudentResponse> updateStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody UpdateStudentRequest request) {
        Student updated = studentService.updateStudent(
                studentId,
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.birthDate(),
                request.birthPlace(),
                request.nationality(),
                request.address(),
                request.phone(),
                request.email(),
                request.medicalNotes(),
                request.admissionDate());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(updated));
    }

    @PostMapping("/{studentId}/transfer")
    public ResponseEntity<StudentResponse> transferStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentStatusReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.transferStudent(studentId, request.reason())));
    }

    @PostMapping("/{studentId}/graduate")
    public ResponseEntity<StudentResponse> graduateStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.graduateStudent(studentId, reason)));
    }

    @PostMapping("/{studentId}/withdraw")
    public ResponseEntity<StudentResponse> withdrawStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentStatusReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.withdrawStudent(studentId, request.reason())));
    }

    @PostMapping("/{studentId}/archive")
    public ResponseEntity<StudentResponse> archiveStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentStatusReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.archiveStudent(studentId, request.reason())));
    }

    @PostMapping("/{studentId}/reactivate")
    public ResponseEntity<StudentResponse> reactivateStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentResponse.from(studentService.reactivateStudent(studentId)));
    }

    @GetMapping("/{studentId}/enrollments")
    public ResponseEntity<PageResponse<EnrollmentResponse>> listStudentEnrollments(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "enrollmentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = StudentPagination.of(
                page,
                size,
                sortBy,
                direction,
                StudentPagination.ENROLLMENT_SORT_FIELDS,
                "enrollmentDate",
                Sort.Direction.DESC);
        Page<EnrollmentResponse> enrollments = enrollmentService
                .listStudentEnrollments(studentId, pageable)
                .map(EnrollmentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(enrollments));
    }
}
