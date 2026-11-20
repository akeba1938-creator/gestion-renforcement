package com.ecole.saas.finance.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Politique métier pure d'imputation des paiements et de rééquilibrage des échéanciers
 * après approbation d'une remise ou d'une bourse.
 */
public final class ScheduleAllocator {

    private ScheduleAllocator() {
    }

    public record AllocationPlan(PaymentSchedule schedule, long amountXof) {
    }

    /**
     * Calcule les imputations d'un paiement sur un échéancier :
     * - Si {@code preferredScheduleId} est fourni, impute en priorité sur cette échéance, puis FIFO sur les suivantes.
     * - Sinon, impute en FIFO par ordre croissant de {@code installmentNo}.
     */
    public static List<AllocationPlan> allocatePayment(
            List<PaymentSchedule> schedules,
            long paymentAmountXof,
            UUID preferredScheduleId) {
        MoneyMath.requirePositive(paymentAmountXof, "Le montant du paiement");
        List<PaymentSchedule> ordered = schedules.stream()
                .sorted(Comparator.comparingInt(PaymentSchedule::getInstallmentNo))
                .toList();

        List<PaymentSchedule> candidateOrder = new ArrayList<>(ordered.size());
        if (preferredScheduleId != null) {
            PaymentSchedule preferred = ordered.stream()
                    .filter(s -> Objects.equals(s.getId(), preferredScheduleId))
                    .findFirst()
                    .orElseThrow(() -> new FinanceNotFoundException(
                            "schedule-not-found",
                            "Échéance introuvable",
                            "L'échéance demandée n'appartient pas à cette facture."));
            if (preferred.remainingAmountXof() <= 0L) {
                throw new FinanceConflictException(
                        "schedule-already-paid",
                        "Échéance déjà soldée",
                        "L'échéance ciblée est déjà totalement soldée ou annulée.");
            }
            candidateOrder.add(preferred);
            for (PaymentSchedule schedule : ordered) {
                if (!Objects.equals(schedule.getId(), preferredScheduleId)) {
                    candidateOrder.add(schedule);
                }
            }
        } else {
            candidateOrder.addAll(ordered);
        }

        long remaining = paymentAmountXof;
        List<AllocationPlan> plans = new ArrayList<>();
        for (PaymentSchedule schedule : candidateOrder) {
            if (remaining == 0L) {
                break;
            }
            long open = schedule.remainingAmountXof();
            if (open <= 0L) {
                continue;
            }
            long applied = Math.min(open, remaining);
            schedule.applyPayment(applied);
            plans.add(new AllocationPlan(schedule, applied));
            remaining = MoneyMath.subtractExact(remaining, applied);
        }

        if (remaining != 0L) {
            throw new FinanceConflictException(
                    "schedule-allocation-insufficient",
                    "Imputation impossible",
                    "Les échéances ouvertes ne suffisent pas pour absorber le montant du paiement.");
        }
        return plans;
    }

    /**
     * Réduit les montants non soldés d'un échéancier à partir de la dernière échéance
     * afin que la somme des échéances reste exactement égale au montant net de la facture.
     */
    public static void reduceFromLastInstallment(List<PaymentSchedule> schedules, long adjustmentAmountXof) {
        MoneyMath.requirePositive(adjustmentAmountXof, "Le montant de remise ou bourse");
        List<PaymentSchedule> reversedOrder = schedules.stream()
                .sorted(Comparator.comparingInt(PaymentSchedule::getInstallmentNo).reversed())
                .toList();

        long remaining = adjustmentAmountXof;
        for (PaymentSchedule schedule : reversedOrder) {
            if (remaining == 0L) {
                break;
            }
            long reduced = schedule.reduceByAdjustment(remaining);
            remaining = MoneyMath.subtractExact(remaining, reduced);
        }

        if (remaining != 0L) {
            throw new FinanceConflictException(
                    "adjustment-exceeds-schedule-balance",
                    "Rééquilibrage impossible",
                    "Le solde restant des échéances ne permet pas d'absorber la remise ou bourse.");
        }
    }
}
