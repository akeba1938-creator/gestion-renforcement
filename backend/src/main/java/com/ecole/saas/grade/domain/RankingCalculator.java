package com.ecole.saas.grade.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calcul des rangs avec gestion standard des ex æquo (classement de compétition : 1, 2, 2, 4).
 */
public final class RankingCalculator {

    private RankingCalculator() {
    }

    public record RankEntry(Integer rank, boolean tied) {
    }

    public static Map<UUID, RankEntry> rankDescending(Map<UUID, BigDecimal> averagesByStudent) {
        Map<UUID, RankEntry> results = new HashMap<>();
        if (averagesByStudent == null || averagesByStudent.isEmpty()) {
            return results;
        }

        List<Map.Entry<UUID, BigDecimal>> graded = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : averagesByStudent.entrySet()) {
            if (entry.getValue() == null) {
                results.put(entry.getKey(), new RankEntry(null, false));
            } else {
                graded.add(entry);
            }
        }

        graded.sort(Comparator.<Map.Entry<UUID, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey));

        int index = 0;
        while (index < graded.size()) {
            BigDecimal currentScore = graded.get(index).getValue();
            int groupEnd = index + 1;
            while (groupEnd < graded.size() && graded.get(groupEnd).getValue().compareTo(currentScore) == 0) {
                groupEnd++;
            }
            int rank = index + 1;
            boolean tied = (groupEnd - index) > 1;
            for (int i = index; i < groupEnd; i++) {
                results.put(graded.get(i).getKey(), new RankEntry(rank, tied));
            }
            index = groupEnd;
        }

        return results;
    }
}
