package com.ecole.saas.communication.api;

import com.ecole.saas.communication.application.ContactPreferenceService;
import com.ecole.saas.communication.domain.RecipientType;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communication/preferences")
public class ContactPreferenceController {

    private final ContactPreferenceService preferenceService;

    public ContactPreferenceController(ContactPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<ContactPreferenceResponse> getPreference(
            @RequestParam RecipientType contactType,
            @RequestParam UUID contactId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ContactPreferenceResponse.from(preferenceService.getPreference(contactType, contactId)));
    }

    @PutMapping
    public ResponseEntity<ContactPreferenceResponse> upsertPreference(
            @Valid @RequestBody UpsertContactPreferenceRequest request,
            JwtAuthenticationToken authentication) {
        ContactPreferenceService.ContactPreferenceView view = preferenceService.upsertPreference(
                request.contactType(),
                request.contactId(),
                !Boolean.FALSE.equals(request.inAppEnabled()),
                !Boolean.FALSE.equals(request.smsEnabled()),
                !Boolean.FALSE.equals(request.whatsappEnabled()),
                !Boolean.FALSE.equals(request.emailEnabled()),
                !Boolean.FALSE.equals(request.marketingOptIn()),
                request.preferredChannel(),
                request.preferredLanguage(),
                request.quietHoursStart(),
                request.quietHoursEnd(),
                request.consentSource(),
                CommunicationApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ContactPreferenceResponse.from(view));
    }

    @GetMapping("/me")
    public ResponseEntity<ContactPreferenceResponse> getMyPreference(JwtAuthenticationToken authentication) {
        UUID actorId = CommunicationApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ContactPreferenceResponse.from(preferenceService.getMyPreference(actorId)));
    }

    @PutMapping("/me")
    public ResponseEntity<ContactPreferenceResponse> updateMyPreference(
            @Valid @RequestBody UpdateMyPreferenceRequest request,
            JwtAuthenticationToken authentication) {
        UUID actorId = CommunicationApiSupport.extractActorId(authentication);
        ContactPreferenceService.ContactPreferenceView view = preferenceService.updateMyPreference(
                actorId,
                !Boolean.FALSE.equals(request.inAppEnabled()),
                !Boolean.FALSE.equals(request.smsEnabled()),
                !Boolean.FALSE.equals(request.whatsappEnabled()),
                !Boolean.FALSE.equals(request.emailEnabled()),
                !Boolean.FALSE.equals(request.marketingOptIn()),
                request.preferredChannel(),
                request.preferredLanguage(),
                request.quietHoursStart(),
                request.quietHoursEnd());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ContactPreferenceResponse.from(view));
    }
}
