package com.ecole.saas.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique en lecture seule du module {@code identity} permettant aux autres modules
 * (ex. {@code teacher}, {@code communication}) de valider l'appartenance intra-tenant d'un compte utilisateur.
 */
public interface UserDirectoryPort {

    Optional<UserSummary> findUserById(UUID userId);

    Optional<UserContactSummary> findUserContactById(UUID userId);

    List<UserContactSummary> findAllActiveStaffUsers();
}
