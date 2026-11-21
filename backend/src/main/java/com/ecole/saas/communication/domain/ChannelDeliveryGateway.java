package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;
import java.util.UUID;

/**
 * Port sortant abstrait vers une passerelle d'envoi (IN_APP, SMS, WHATSAPP, EMAIL).
 * Permet d'isoler complètement la logique métier de tout appel réseau externe.
 */
public interface ChannelDeliveryGateway {

    CommunicationChannel supportedChannel();

    String providerName();

    long defaultUnitCostXof();

    GatewayDispatchResult dispatch(GatewayDispatchRequest request);

    record GatewayDispatchRequest(
            UUID tenantId,
            UUID campaignId,
            UUID deliveryId,
            CommunicationChannel channel,
            String destinationAddress,
            String subject,
            String body,
            long estimatedCostXof) {
    }
}
