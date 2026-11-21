package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.MessageTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MessageTemplateRepository
        extends JpaRepository<MessageTemplate, UUID>, JpaSpecificationExecutor<MessageTemplate> {

    boolean existsByCode(String code);

    Optional<MessageTemplate> findByCode(String code);
}
