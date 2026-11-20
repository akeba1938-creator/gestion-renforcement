package com.ecole.saas.grade.api;

import jakarta.validation.constraints.Size;

public record UpdateSubjectAppreciationRequest(
        @Size(max = 500)
        String teacherAppreciation) {
}
