package me.krunsh.kfaction.policy;

/**
 * Règle unique utilisée par /f sethome.
 */
public final class HomeClaimPolicy {

    private HomeClaimPolicy() {
    }

    public static boolean canSetHome(String factionId, String territoryOwnerId,
                                     boolean explicitStaffBypass) {
        if (explicitStaffBypass) {
            return true;
        }
        return factionId != null && factionId.equals(territoryOwnerId);
    }
}
