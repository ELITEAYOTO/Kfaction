package me.krunsh.kfaction.policy;

/**
 * Identite fonctionnelle d'un article vendu pour les quetes ITEM_SELL.
 *
 * Une cible historique sans CIT continue de correspondre au materiau. Quand
 * sparrowmc-item est configure, le materiau et la valeur CIT exacte doivent
 * correspondre. Le tag level ne fait volontairement pas partie du modele.
 */
public final class QuestSaleIdentity {
    private QuestSaleIdentity() {}

    public static boolean matches(String configuredMaterial, String configuredCit,
                                  String soldMaterial, String soldCit) {
        if (configuredMaterial == null || soldMaterial == null) return false;
        if (!configuredMaterial.trim().equalsIgnoreCase(soldMaterial.trim())) return false;

        String expectedCit = normalizeCit(configuredCit);
        if (expectedCit == null) {
            return true;
        }
        return expectedCit.equals(normalizeCit(soldCit));
    }

    public static String normalizeCit(String cit) {
        if (cit == null) return null;
        String trimmed = cit.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
