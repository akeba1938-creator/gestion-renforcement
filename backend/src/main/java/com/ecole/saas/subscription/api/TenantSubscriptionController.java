package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.application.TenantSubscriptionService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation de l'abonnement courant, des quotas effectifs et de l'historique par les
 * responsables d'établissement ({@code PROMOTER}, {@code DIRECTOR}, {@code CENTER_MANAGER}).
 * Le tenant est exclusivement issu du JWT côté serveur.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class TenantSubscriptionController {

    private final TenantSubscriptionService tenantSubscriptionService;

    public TenantSubscriptionController(TenantSubscriptionService tenantSubscriptionService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    @GetMapping("/current")
    public ResponseEntity<SubscriptionResponse> getCurrent() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SubscriptionResponse.from(tenantSubscriptionService.getCurrentSubscription()));
    }

    @GetMapping("/current/quotas")
    public ResponseEntity<TenantQuotasOverviewResponse> getCurrentQuotas() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TenantQuotasOverviewResponse.from(tenantSubscriptionService.getCurrentQuotasOverview()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionResponse>> listSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = SubscriptionPagination.of(
                page, size, sortBy, direction, SubscriptionPagination.SUBSCRIPTION_SORT_FIELDS, "createdAt");
        Page<SubscriptionResponse> subscriptions = tenantSubscriptionService.listSubscriptions(pageable)
                .map(SubscriptionResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(subscriptions));
    }

    @GetMapping("/{subscriptionId}/events")
    public ResponseEntity<PageResponse<SubscriptionEventResponse>> listEvents(
            @PathVariable UUID subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = SubscriptionPagination.ofFixedOrder(page, size);
        Page<SubscriptionEventResponse> events = tenantSubscriptionService.listEvents(subscriptionId, pageable)
                .map(SubscriptionEventResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(events));
    }
}
