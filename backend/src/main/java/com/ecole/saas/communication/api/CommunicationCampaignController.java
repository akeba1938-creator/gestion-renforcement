package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CampaignStatus;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.application.CommunicationCampaignService;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import com.ecole.saas.communication.domain.MessageDelivery;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communication")
public class CommunicationCampaignController {

    private final CommunicationCampaignService campaignService;

    public CommunicationCampaignController(CommunicationCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping("/campaigns")
    public ResponseEntity<CommunicationCampaignResponse> createCampaign(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateCampaignRequest request,
            JwtAuthenticationToken authentication) {
        String idempotencyKey = CommunicationApiSupport.resolveIdempotencyKey(
                idempotencyKeyHeader, request.idempotencyKey());
        boolean dispatchNow = request.dispatchImmediately() != null
                ? request.dispatchImmediately()
                : request.scheduledAt() == null;
        CommunicationCampaignService.CampaignDispatchView view = campaignService.createCampaign(
                idempotencyKey,
                request.title(),
                request.category(),
                request.channel(),
                request.templateId(),
                request.audienceType(),
                request.audienceTargetId(),
                request.guardianTargetingMode(),
                request.subject(),
                request.body(),
                request.variables(),
                Boolean.TRUE.equals(request.respectMarketingOptOut()),
                request.scheduledAt(),
                dispatchNow,
                CommunicationApiSupport.extractActorId(authentication));
        CommunicationCampaignResponse body = CommunicationCampaignResponse.from(view.campaign());
        if (view.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/communication/campaigns/" + view.campaign().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/campaigns")
    public ResponseEntity<PageResponse<CommunicationCampaignResponse>> listCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) CommunicationChannel channel,
            @RequestParam(required = false) MessageCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = CommunicationPagination.of(
                page,
                size,
                sortBy,
                direction,
                CommunicationPagination.CAMPAIGN_SORT_FIELDS,
                "createdAt",
                Sort.Direction.DESC);
        Page<CommunicationCampaignResponse> campaigns = campaignService
                .listCampaigns(
                        status,
                        channel,
                        category,
                        CommunicationApiSupport.extractActorId(authentication),
                        pageable)
                .map(CommunicationCampaignResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(campaigns));
    }

    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<CommunicationCampaignResponse> getCampaign(
            @PathVariable UUID campaignId,
            JwtAuthenticationToken authentication) {
        CommunicationCampaign campaign = campaignService.getCampaign(
                campaignId, CommunicationApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommunicationCampaignResponse.from(campaign));
    }

    @PostMapping("/campaigns/{campaignId}/dispatch")
    public ResponseEntity<CommunicationCampaignResponse> dispatchCampaign(
            @PathVariable UUID campaignId,
            JwtAuthenticationToken authentication) {
        CommunicationCampaign dispatched = campaignService.dispatchCampaign(
                campaignId, CommunicationApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommunicationCampaignResponse.from(dispatched));
    }

    @PostMapping("/campaigns/{campaignId}/cancel")
    public ResponseEntity<CommunicationCampaignResponse> cancelCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CancelCampaignRequest request,
            JwtAuthenticationToken authentication) {
        CommunicationCampaign cancelled = campaignService.cancelCampaign(
                campaignId, request.reason(), CommunicationApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommunicationCampaignResponse.from(cancelled));
    }

    @PostMapping("/campaigns/{campaignId}/retry-failed")
    public ResponseEntity<RetryCampaignResponse> retryFailed(@PathVariable UUID campaignId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(RetryCampaignResponse.from(campaignService.retryFailedDeliveries(campaignId)));
    }

    @GetMapping("/campaigns/{campaignId}/deliveries")
    public ResponseEntity<PageResponse<MessageDeliveryResponse>> listCampaignDeliveries(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = CommunicationPagination.of(
                page,
                size,
                sortBy,
                direction,
                CommunicationPagination.DELIVERY_SORT_FIELDS,
                "createdAt",
                Sort.Direction.ASC);
        Page<MessageDeliveryResponse> deliveries = campaignService
                .listCampaignDeliveries(
                        campaignId,
                        status,
                        CommunicationApiSupport.extractActorId(authentication),
                        pageable)
                .map(MessageDeliveryResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(deliveries));
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<MessageDeliveryResponse> getDelivery(
            @PathVariable UUID deliveryId,
            JwtAuthenticationToken authentication) {
        MessageDelivery delivery = campaignService.getDelivery(
                deliveryId, CommunicationApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageDeliveryResponse.from(delivery));
    }

    @GetMapping("/deliveries/{deliveryId}/events")
    public ResponseEntity<List<DeliveryEventResponse>> getDeliveryEvents(
            @PathVariable UUID deliveryId,
            JwtAuthenticationToken authentication) {
        List<DeliveryEventResponse> events = campaignService
                .getDeliveryEvents(deliveryId, CommunicationApiSupport.extractActorId(authentication))
                .stream()
                .map(DeliveryEventResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    @PostMapping("/deliveries/{deliveryId}/status")
    public ResponseEntity<MessageDeliveryResponse> updateDeliveryStatus(
            @PathVariable UUID deliveryId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        MessageDelivery updated = campaignService.recordDeliveryStatus(
                deliveryId,
                request.status(),
                request.providerStatus(),
                request.failureCode(),
                request.failureReason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageDeliveryResponse.from(updated));
    }

    @GetMapping("/summary")
    public ResponseEntity<CommunicationSummaryResponse> getSummary() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommunicationSummaryResponse.from(campaignService.getSummary()));
    }
}
