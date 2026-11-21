package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.CommunicationCampaign;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommunicationCampaignRepository
        extends JpaRepository<CommunicationCampaign, UUID>, JpaSpecificationExecutor<CommunicationCampaign> {

    Optional<CommunicationCampaign> findByIdempotencyKey(String idempotencyKey);

    Optional<CommunicationCampaign> findByReference(String reference);
}
