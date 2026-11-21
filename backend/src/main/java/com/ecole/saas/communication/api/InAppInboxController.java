package com.ecole.saas.communication.api;

import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.application.InAppInboxService;
import com.ecole.saas.communication.domain.MessageDelivery;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communication/inbox")
public class InAppInboxController {

    private final InAppInboxService inboxService;

    public InAppInboxController(InAppInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InboxMessageResponse>> listInbox(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
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
                CommunicationPagination.INBOX_SORT_FIELDS,
                "createdAt",
                Sort.Direction.DESC);
        UUID currentUserId = CommunicationApiSupport.extractActorId(authentication);
        Page<InboxMessageResponse> messages = inboxService
                .listMyInbox(currentUserId, unreadOnly, category, pageable)
                .map(InboxMessageResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(messages));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(JwtAuthenticationToken authentication) {
        UUID currentUserId = CommunicationApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new UnreadCountResponse(inboxService.countMyUnread(currentUserId)));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<InboxMessageResponse> getInboxMessage(
            @PathVariable UUID deliveryId,
            JwtAuthenticationToken authentication) {
        UUID currentUserId = CommunicationApiSupport.extractActorId(authentication);
        MessageDelivery message = inboxService.getMyInboxMessage(currentUserId, deliveryId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InboxMessageResponse.from(message));
    }

    @PostMapping("/{deliveryId}/read")
    public ResponseEntity<InboxMessageResponse> markAsRead(
            @PathVariable UUID deliveryId,
            JwtAuthenticationToken authentication) {
        UUID currentUserId = CommunicationApiSupport.extractActorId(authentication);
        MessageDelivery updated = inboxService.markAsRead(currentUserId, deliveryId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InboxMessageResponse.from(updated));
    }

    @PostMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead(JwtAuthenticationToken authentication) {
        UUID currentUserId = CommunicationApiSupport.extractActorId(authentication);
        int marked = inboxService.markAllAsRead(currentUserId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MarkAllReadResponse(marked));
    }
}
