package com.ecole.saas.grade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradingCalculatorAndRankingDomainTest {

    private final GradingPolicy policy = GradingPolicy.defaultSenegalPolicy();

    @Test
    void shouldNormalizeScoresAndComputeWeightedSubjectAverageWithAbsences() {
        // Interrogation sur 10 (poids 1) : 7.5/10 -> 15/20
        // Composition sur 20 (poids 2) : 14/20 -> 14/20
        // Devoir absent justifié (neutre)
        // Devoir dispensé (neutre)
        // Moyenne attendue = (15*1 + 14*2) / 3 = 43 / 3 = 14.33
        List<GradingCalculator.EvaluationScoreInput> entries = List.of(
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.PRESENT, new BigDecimal("7.50"), new BigDecimal("10.00"), 1),
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.PRESENT, new BigDecimal("14.00"), new BigDecimal("20.00"), 2),
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.ABSENT_JUSTIFIED, null, new BigDecimal("20.00"), 1),
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.EXEMPTED, null, new BigDecimal("20.00"), 1));

        BigDecimal avg = GradingCalculator.computeSubjectAverage(entries, 20, policy);
        assertThat(avg).isEqualByComparingTo("14.33");
    }

    @Test
    void shouldCountUnjustifiedAbsenceAsZeroByDefaultAndExcludeWhenConfigured() {
        // Devoir 1 (poids 1) : 15/20
        // Devoir 2 (poids 1) : ABSENT_UNJUSTIFIED -> 0/20
        // Moyenne = 7.50
        List<GradingCalculator.EvaluationScoreInput> entries = List.of(
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.PRESENT, new BigDecimal("15.00"), new BigDecimal("20.00"), 1),
                new GradingCalculator.EvaluationScoreInput(
                        GradeAttendanceStatus.ABSENT_UNJUSTIFIED, null, new BigDecimal("20.00"), 1));

        assertThat(GradingCalculator.computeSubjectAverage(entries, 20, policy))
                .isEqualByComparingTo("7.50");

        GradingPolicy lenient = new GradingPolicy(false, false, 2, java.math.RoundingMode.HALF_UP);
        assertThat(GradingCalculator.computeSubjectAverage(entries, 20, lenient))
                .isEqualByComparingTo("15.00");
    }

    @Test
    void shouldComputeWeightedGeneralAverageOnlyOnGradedSubjects() {
        // Math coef 4 : 15.00 -> 60.00
        // Français coef 3 : 12.50 -> 37.50
        // EPS coef 2 : null (non noté / dispensé) -> exclu
        // Total pondéré = 97.50, total coef = 7, moyenne = 97.50 / 7 = 13.92857 -> 13.93 (HALF_UP)
        GradingCalculator.GeneralAverageResult result = GradingCalculator.computeGeneralAverage(
                List.of(
                        new GradingCalculator.SubjectAverageInput(4, new BigDecimal("15.00")),
                        new GradingCalculator.SubjectAverageInput(3, new BigDecimal("12.50")),
                        new GradingCalculator.SubjectAverageInput(2, null)),
                policy);

        assertThat(result.totalCoefficients()).isEqualTo(7);
        assertThat(result.totalWeightedScore()).isEqualByComparingTo("97.50");
        assertThat(result.generalAverage()).isEqualByComparingTo("13.93");
    }

    @Test
    void shouldComputeCompetitionRankingWithTiesAndNullAverages() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        UUID s4 = UUID.randomUUID();
        UUID s5 = UUID.randomUUID();

        Map<UUID, BigDecimal> averages = new HashMap<>();
        averages.put(s1, new BigDecimal("16.50"));
        averages.put(s2, new BigDecimal("14.25"));
        averages.put(s3, new BigDecimal("14.25"));
        averages.put(s4, new BigDecimal("11.00"));
        averages.put(s5, null);

        Map<UUID, RankingCalculator.RankEntry> ranks = RankingCalculator.rankDescending(averages);

        assertThat(ranks.get(s1)).isEqualTo(new RankingCalculator.RankEntry(1, false));
        assertThat(ranks.get(s2)).isEqualTo(new RankingCalculator.RankEntry(2, true));
        assertThat(ranks.get(s3)).isEqualTo(new RankingCalculator.RankEntry(2, true));
        assertThat(ranks.get(s4)).isEqualTo(new RankingCalculator.RankEntry(4, false));
        assertThat(ranks.get(s5)).isEqualTo(new RankingCalculator.RankEntry(null, false));
    }

    @Test
    void shouldResolveMentionsProportionallyAcrossGradingScales10_20_100() {
        assertThat(MentionPolicy.resolve(new BigDecimal("18.00"), 20)).isEqualTo(Mention.EXCELLENT);
        assertThat(MentionPolicy.resolve(new BigDecimal("16.00"), 20)).isEqualTo(Mention.TRES_BIEN);
        assertThat(MentionPolicy.resolve(new BigDecimal("14.00"), 20)).isEqualTo(Mention.BIEN);
        assertThat(MentionPolicy.resolve(new BigDecimal("12.00"), 20)).isEqualTo(Mention.ASSEZ_BIEN);
        assertThat(MentionPolicy.resolve(new BigDecimal("10.00"), 20)).isEqualTo(Mention.PASSABLE);
        assertThat(MentionPolicy.resolve(new BigDecimal("8.00"), 20)).isEqualTo(Mention.INSUFFISANT);
        assertThat(MentionPolicy.resolve(new BigDecimal("7.99"), 20)).isEqualTo(Mention.MEDIOCRE);
        assertThat(MentionPolicy.resolve(null, 20)).isEqualTo(Mention.NONE);

        // Barème sur 10 et sur 100
        assertThat(MentionPolicy.resolve(new BigDecimal("8.00"), 10)).isEqualTo(Mention.TRES_BIEN);
        assertThat(MentionPolicy.resolve(new BigDecimal("70.00"), 100)).isEqualTo(Mention.BIEN);
    }

    @Test
    void shouldEnforceEvaluationAndStudentGradeInvariantsAndReportCardLifecycle() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> new Evaluation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        "Devoir 1",
                        EvaluationType.HOMEWORK,
                        LocalDate.of(2026, 11, 10),
                        new BigDecimal("25.00"),
                        20,
                        1,
                        actor,
                        null))
                .isInstanceOf(GradeValidationException.class);

        assertThatThrownBy(() -> new StudentGrade(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        GradeAttendanceStatus.PRESENT,
                        new BigDecimal("20.50"),
                        new BigDecimal("20.00"),
                        null,
                        actor,
                        Instant.now()))
                .isInstanceOf(GradeValidationException.class);

        assertThatThrownBy(() -> new StudentGrade(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        GradeAttendanceStatus.ABSENT_JUSTIFIED,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"),
                        null,
                        actor,
                        Instant.now()))
                .isInstanceOf(GradeValidationException.class);

        PeriodReportCard card = new PeriodReportCard(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 20, Instant.now());
        card.validate(actor, Instant.now());
        assertThat(card.getStatus()).isEqualTo(ReportCardStatus.VALIDATED);
        card.lock(actor, Instant.now());
        assertThat(card.getStatus()).isEqualTo(ReportCardStatus.LOCKED);
        assertThatThrownBy(() -> card.updateDirectorAppreciationAndDecision("Test", CouncilDecision.FELICITATIONS))
                .isInstanceOf(GradeConflictException.class);
    }
}
