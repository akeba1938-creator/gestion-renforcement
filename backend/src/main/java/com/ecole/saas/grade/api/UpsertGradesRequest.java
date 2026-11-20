package com.ecole.saas.grade.api;

import com.ecole.saas.grade.domain.GradeAttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpsertGradesRequest(
        @NotEmpty(message = "La liste des notes ne peut pas être vide.")
        List<@Valid GradeEntryItem> entries) {

    public record GradeEntryItem(
            @NotNull(message = "L'élève est obligatoire.")
            UUID studentId,
            @NotNull(message = "Le statut de présence à l'évaluation est obligatoire.")
            GradeAttendanceStatus attendanceStatus,
            BigDecimal score,
            @Size(max = 500)
            String comment) {
    }
}
