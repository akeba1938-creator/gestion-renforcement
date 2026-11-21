package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.ContactPreference;
import com.ecole.saas.communication.domain.RecipientType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactPreferenceRepository extends JpaRepository<ContactPreference, UUID> {

    Optional<ContactPreference> findByContactTypeAndContactId(RecipientType contactType, UUID contactId);

    List<ContactPreference> findByContactTypeAndContactIdIn(
            RecipientType contactType, Collection<UUID> contactIds);
}
