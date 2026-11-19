package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.application.SubscriptionPlanService;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Administration plateforme du catalogue global des forfaits SaaS ({@code ADMIN_SAAS}).
 */
@RestController
@RequestMapping("/api/v1/platform/subscription-plans")
public class PlatformSubscriptionPlanController {

    private final SubscriptionPlanService planService;

    public PlatformSubscriptionPlanController(SubscriptionPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionPlanResponse>> listAllPlans(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = SubscriptionPagination.of(
                page, size, sortBy, direction, SubscriptionPagination.PLAN_SORT_FIELDS, "createdAt");
        Page<SubscriptionPlanResponse> plans = planService.findAllPlans(active, pageable)
                .map(SubscriptionPlanResponse::from);
        return ResponseEntity.ok(PageResponse.from(plans));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<SubscriptionPlanResponse> getPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(SubscriptionPlanResponse.from(planService.getPlan(planId)));
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanResponse> createPlan(
            @Valid @RequestBody CreateSubscriptionPlanRequest request) {
        int trialDays = request.trialDays() != null ? request.trialDays() : SubscriptionPlan.DEFAULT_TRIAL_DAYS;
        SubscriptionPlan created = planService.createPlan(
                request.code(),
                request.name(),
                request.description(),
                request.monthlyPriceXof(),
                request.yearlyPriceXof(),
                request.maxStudents(),
                request.maxUsers(),
                request.maxStorageMb(),
                trialDays);
        return ResponseEntity
                .created(URI.create("/api/v1/platform/subscription-plans/" + created.getId()))
                .body(SubscriptionPlanResponse.from(created));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<SubscriptionPlanResponse> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request) {
        int trialDays = request.trialDays() != null ? request.trialDays() : SubscriptionPlan.DEFAULT_TRIAL_DAYS;
        SubscriptionPlan updated = planService.updatePlan(
                planId,
                request.name(),
                request.description(),
                request.monthlyPriceXof(),
                request.yearlyPriceXof(),
                request.maxStudents(),
                request.maxUsers(),
                request.maxStorageMb(),
                trialDays);
        return ResponseEntity.ok(SubscriptionPlanResponse.from(updated));
    }

    @PostMapping("/{planId}/activate")
    public ResponseEntity<SubscriptionPlanResponse> activatePlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(SubscriptionPlanResponse.from(planService.activatePlan(planId)));
    }

    @PostMapping("/{planId}/deactivate")
    public ResponseEntity<SubscriptionPlanResponse> deactivatePlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(SubscriptionPlanResponse.from(planService.deactivatePlan(planId)));
    }
}
