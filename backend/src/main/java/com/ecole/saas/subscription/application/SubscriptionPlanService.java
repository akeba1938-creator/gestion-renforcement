package com.ecole.saas.subscription.application;

import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionPlanAlreadyExistsException;
import com.ecole.saas.subscription.domain.SubscriptionPlanNotFoundException;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionPlanService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanService.class);

    private final SubscriptionPlanRepository planRepository;

    public SubscriptionPlanService(SubscriptionPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> findActivePlans(Pageable pageable) {
        return planRepository.findByActive(true, pageable);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getActivePlan(UUID planId) {
        return planRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException("Forfait actif introuvable."));
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> findAllPlans(Boolean active, Pageable pageable) {
        if (active == null) {
            return planRepository.findAll(pageable);
        }
        return planRepository.findByActive(active, pageable);
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional(readOnly = true)
    public SubscriptionPlan getPlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException("Forfait introuvable."));
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional
    public SubscriptionPlan createPlan(
            String code,
            String name,
            String description,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) {
        String normalizedCode = SubscriptionPlan.normalizeCode(code);
        if (planRepository.existsByCode(normalizedCode)) {
            throw new SubscriptionPlanAlreadyExistsException(
                    "Un forfait portant le code " + normalizedCode + " existe déjà.");
        }
        SubscriptionPlan plan = new SubscriptionPlan(
                normalizedCode,
                name,
                description,
                monthlyPriceXof,
                yearlyPriceXof,
                maxStudents,
                maxUsers,
                maxStorageMb,
                trialDays);
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Forfait créé (planId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional
    public SubscriptionPlan updatePlan(
            UUID planId,
            String name,
            String description,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) {
        SubscriptionPlan plan = getPlan(planId);
        plan.updateDetails(
                name,
                description,
                monthlyPriceXof,
                yearlyPriceXof,
                maxStudents,
                maxUsers,
                maxStorageMb,
                trialDays);
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Forfait mis à jour (planId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional
    public SubscriptionPlan activatePlan(UUID planId) {
        SubscriptionPlan plan = getPlan(planId);
        plan.activate();
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Forfait activé (planId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }

    @PreAuthorize("hasRole('ADMIN_SAAS')")
    @Transactional
    public SubscriptionPlan deactivatePlan(UUID planId) {
        SubscriptionPlan plan = getPlan(planId);
        plan.deactivate();
        SubscriptionPlan saved = planRepository.save(plan);
        log.info("Forfait désactivé (planId={}, code={})", saved.getId(), saved.getCode());
        return saved;
    }
}
