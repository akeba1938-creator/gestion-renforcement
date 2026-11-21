package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import org.springframework.stereotype.Component;

/**
 * Adaptateur interne pour les notifications {@code IN_APP} (remise immédiate en boîte de réception, 0 FCFA).
 */
@Component
public class InAppDeliveryGateway implements ChannelDeliveryGateway {

    private static final String PROVIDER = "IN_APP_INTERNAL";

    @Override
    public CommunicationChannel supportedChannel() {
        return CommunicationChannel.IN_APP;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public long defaultUnitCostXof() {
        return 0L;
    }

    @Override
    public GatewayDispatchResult dispatch(GatewayDispatchRequest request) {
        return GatewayDispatchResult.delivered(PROVIDER, "inapp-" + request.deliveryId(), 0L);
    }
}
