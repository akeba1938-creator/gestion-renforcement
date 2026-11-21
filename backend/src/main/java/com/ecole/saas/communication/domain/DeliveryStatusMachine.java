package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.DeliveryStatus;
import java.util.Objects;

/**
 * Machine à états stricte des transitions de {@link DeliveryStatus}.
 */
public final class DeliveryStatusMachine {

    private DeliveryStatusMachine() {
    }

    public static void assertTransitionAllowed(DeliveryStatus current, DeliveryStatus target) {
        Objects.requireNonNull(current, "Le statut actuel est obligatoire.");
        Objects.requireNonNull(target, "Le statut cible est obligatoire.");

        if (current == target) {
            if (current == DeliveryStatus.READ) {
                return;
            }
            throw new CommunicationConflictException(
                    "invalid-delivery-transition",
                    "La remise est déjà dans le statut " + current + ".");
        }

        boolean allowed = switch (current) {
            case QUEUED -> target == DeliveryStatus.SENT
                    || target == DeliveryStatus.DELIVERED
                    || target == DeliveryStatus.FAILED;
            case SENT -> target == DeliveryStatus.DELIVERED
                    || target == DeliveryStatus.FAILED
                    || target == DeliveryStatus.READ;
            case DELIVERED -> target == DeliveryStatus.READ;
            case FAILED -> target == DeliveryStatus.QUEUED;
            case READ -> false;
        };

        if (!allowed) {
            throw new CommunicationConflictException(
                    "invalid-delivery-transition",
                    "Transition de statut de remise interdite : " + current + " -> " + target + ".");
        }
    }
}
