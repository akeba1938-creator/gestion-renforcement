package com.ecole.saas.grade.api;

import com.ecole.saas.grade.domain.EvaluationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEvaluationRequest(
        @NotNull(message = "La période d'évaluation est obligatoire.")
        UUID evaluationPeriodId,
        @NotNull(message = "La classe est obligatoire.")
        UUID classRoomId,
        @NotNull(message = "La matière est obligatoire.")
        UUID subjectId,
        UUID teacherId,
        @NotBlank(message = "L'intitulé de l'évaluation est obligatoire.")
        @Size(max = 150)
        String title,
        @NotNull(message = "Le type d'évaluation est obligatoire.")
        EvaluationType evaluationType,
        @NotNull(message = "La date de l'évaluation est obligatoire.")
        LocalDate evaluationDate,
        BigDecimal maxScore,
        @Min(value = 1, message = "Le poids doit être compris entre 1 et 20.")
        @Max(value = 20, message = "Le poids doit être compris entre 1 et 20.")
        Integer weight,
        @Size(max = 500)
        String notes) {
}
