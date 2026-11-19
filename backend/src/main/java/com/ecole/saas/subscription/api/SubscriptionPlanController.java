package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.application.SubscriptionPlanService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation du catalogue des forfaits actifs pour les utilisateurs authentifiés.
 */
@RestController
@RequestMapping("/api/v1/subscription-plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    public SubscriptionPlanController(SubscriptionPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionPlanResponse>> listActivePlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "monthlyPriceXof") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = SubscriptionPagination.of(
                page, size, sortBy, direction, SubscriptionPagination.PLAN_SORT_FIELDS, "monthlyPriceXof");
        Page<SubscriptionPlanResponse> plans = planService.findActivePlans(pageable)
                .map(SubscriptionPlanResponse::from);
        return ResponseEntity.ok(PageResponse.from(plans));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<SubscriptionPlanResponse> getActivePlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(SubscriptionPlanResponse.from(planService.getActivePlan(planId)));
    }
}
