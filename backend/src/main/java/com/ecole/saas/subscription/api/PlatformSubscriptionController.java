package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.application.PlatformSubscriptionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration plateforme des abonnements d'un établissement ({@code ADMIN_SAAS}).
 */
@RestController
@RequestMapping("/api/v1/platform/schools/{schoolId}/subscriptions")
public class PlatformSubscriptionController {

    private final PlatformSubscriptionService platformSubscriptionService;

    public PlatformSubscriptionController(PlatformSubscriptionService platformSubscriptionService) {
        this.platformSubscriptionService = platformSubscriptionService;
    }

    @PostMapping("/trial")
    public ResponseEntity<SubscriptionResponse> startTrial(
            @PathVariable UUID schoolId,
            @Valid @RequestBody StartTrialSubscriptionRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot created = platformSubscriptionService.startTrial(
                schoolId,
                request.planId(),
                request.targetBillingCycle(),
                request.trialDays(),
                extractActorId(authentication));
        return ResponseEntity
                .created(URI.create("/api/v1/platform/schools/" + schoolId + "/subscriptions/" + created.subscriptionId()))
                .body(SubscriptionResponse.from(created));
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createActive(
            @PathVariable UUID schoolId,
            @Valid @RequestBody CreateActiveSubscriptionRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot created = platformSubscriptionService.createActiveSubscription(
                schoolId,
                request.planId(),
                request.billingCycle(),
                request.startsAt(),
                request.endsAt(),
                extractActorId(authentication));
        return ResponseEntity
                .created(URI.create("/api/v1/platform/schools/" + schoolId + "/subscriptions/" + created.subscriptionId()))
                .body(SubscriptionResponse.from(created));
    }

    @GetMapping("/current")
    public ResponseEntity<SubscriptionResponse> getCurrent(@PathVariable UUID schoolId) {
        return ResponseEntity.ok(
                SubscriptionResponse.from(platformSubscriptionService.getCurrentSubscription(schoolId)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionResponse>> listSubscriptions(
            @PathVariable UUID schoolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = SubscriptionPagination.of(
                page, size, sortBy, direction, SubscriptionPagination.SUBSCRIPTION_SORT_FIELDS, "createdAt");
        Page<SubscriptionResponse> subscriptions = platformSubscriptionService
                .listSubscriptions(schoolId, pageable)
                .map(SubscriptionResponse::from);
        return ResponseEntity.ok(PageResponse.from(subscriptions));
    }

    @GetMapping("/{subscriptionId}/events")
    public ResponseEntity<PageResponse<SubscriptionEventResponse>> listEvents(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = SubscriptionPagination.ofFixedOrder(page, size);
        Page<SubscriptionEventResponse> events = platformSubscriptionService
                .listEvents(schoolId, subscriptionId, pageable)
                .map(SubscriptionEventResponse::from);
        return ResponseEntity.ok(PageResponse.from(events));
    }

    @PostMapping("/{subscriptionId}/activate")
    public ResponseEntity<SubscriptionResponse> activate(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody(required = false) ActivateSubscriptionRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.activateSubscription(
                schoolId,
                subscriptionId,
                request != null ? request.billingCycle() : null,
                request != null ? request.startsAt() : null,
                request != null ? request.endsAt() : null,
                request != null ? request.reason() : null,
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PostMapping("/{subscriptionId}/renew")
    public ResponseEntity<SubscriptionResponse> renew(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody(required = false) RenewSubscriptionRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.renewSubscription(
                schoolId,
                subscriptionId,
                request != null ? request.billingCycle() : null,
                request != null ? request.endsAt() : null,
                request != null ? request.reason() : null,
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PutMapping("/{subscriptionId}/plan")
    public ResponseEntity<SubscriptionResponse> changePlan(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody ChangeSubscriptionPlanRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.changePlan(
                schoolId,
                subscriptionId,
                request.planId(),
                request.billingCycle(),
                Boolean.TRUE.equals(request.resetExtraQuotas()),
                request.reason(),
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PutMapping("/{subscriptionId}/quotas")
    public ResponseEntity<SubscriptionResponse> adjustQuotas(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody AdjustQuotasRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.adjustExtraQuotas(
                schoolId,
                subscriptionId,
                request.extraStudents(),
                request.extraUsers(),
                request.extraStorageMb(),
                request.reason(),
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PostMapping("/{subscriptionId}/past-due")
    public ResponseEntity<SubscriptionResponse> markPastDue(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody MarkPastDueRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.markPastDue(
                schoolId,
                subscriptionId,
                request.gracePeriodEndsAt(),
                request.reason(),
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PostMapping("/{subscriptionId}/suspend")
    public ResponseEntity<SubscriptionResponse> suspend(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody SubscriptionStatusReasonRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.suspendSubscription(
                schoolId,
                subscriptionId,
                request.reason(),
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody SubscriptionStatusReasonRequest request,
            JwtAuthenticationToken authentication) {
        SubscriptionSnapshot updated = platformSubscriptionService.cancelSubscription(
                schoolId,
                subscriptionId,
                request.reason(),
                extractActorId(authentication));
        return ResponseEntity.ok(SubscriptionResponse.from(updated));
    }

    private static UUID extractActorId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getToken().getSubject());
    }
}
