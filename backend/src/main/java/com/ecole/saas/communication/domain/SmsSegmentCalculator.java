package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;

/**
 * Calcul pur du nombre de segments SMS (encodage GSM-7 vs Unicode UCS-2)
 * et des coûts unitaires/totaux strictement en entiers FCFA ({@code long}).
 */
public final class SmsSegmentCalculator {

    private static final String GSM_7_BASIC_CHARS =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM_7_EXTENDED_CHARS = "^{}\\[~]|€";

    private static final int GSM_SINGLE_LIMIT = 160;
    private static final int GSM_CONCAT_LIMIT = 153;
    private static final int UCS2_SINGLE_LIMIT = 70;
    private static final int UCS2_CONCAT_LIMIT = 67;

    private SmsSegmentCalculator() {
    }

    public static int computeSegments(String messageBody) {
        if (messageBody == null || messageBody.isEmpty()) {
            return 0;
        }
        boolean gsm7 = true;
        int septets = 0;
        for (int i = 0; i < messageBody.length(); i++) {
            char c = messageBody.charAt(i);
            if (GSM_7_BASIC_CHARS.indexOf(c) >= 0) {
                septets++;
            } else if (GSM_7_EXTENDED_CHARS.indexOf(c) >= 0) {
                septets += 2;
            } else {
                gsm7 = false;
                break;
            }
        }
        if (gsm7) {
            if (septets <= GSM_SINGLE_LIMIT) {
                return 1;
            }
            return (septets + GSM_CONCAT_LIMIT - 1) / GSM_CONCAT_LIMIT;
        }
        int length = messageBody.length();
        if (length <= UCS2_SINGLE_LIMIT) {
            return 1;
        }
        return (length + UCS2_CONCAT_LIMIT - 1) / UCS2_CONCAT_LIMIT;
    }

    public static long computeDeliveryCostXof(
            CommunicationChannel channel,
            String renderedBody,
            long unitCostXof) {
        if (unitCostXof < 0) {
            throw new CommunicationValidationException("Le coût unitaire en FCFA ne peut pas être négatif.");
        }
        if (unitCostXof == 0 || channel == CommunicationChannel.IN_APP || channel == CommunicationChannel.EMAIL) {
            return unitCostXof;
        }
        if (channel == CommunicationChannel.SMS) {
            int segments = Math.max(1, computeSegments(renderedBody));
            return safeMultiply(unitCostXof, segments);
        }
        return unitCostXof;
    }

    public static long computeTotalCostXof(long unitCostXof, int recipientCount) {
        if (unitCostXof < 0 || recipientCount < 0) {
            throw new CommunicationValidationException("Le coût et le nombre de destinataires doivent être positifs ou nuls.");
        }
        return safeMultiply(unitCostXof, recipientCount);
    }

    public static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            throw new CommunicationValidationException("Débordement lors du calcul des coûts en FCFA.");
        }
    }

    public static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ex) {
            throw new CommunicationValidationException("Débordement lors du calcul des coûts en FCFA.");
        }
    }
}
