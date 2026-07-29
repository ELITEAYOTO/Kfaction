package me.krunsh.kfaction.data;

/**
 * Types de récompenses de niveau faction
 */
public enum RewardType {
    
    FACTION_CHEST_UNLOCK("faction_chest_unlock", "Coffre Faction"),
    FACTION_CHEST_RESIZE("faction_chest_resize", "Agrandir Coffre"),
    ANTI_SETHOME("anti_sethome", "Anti-Sethome"),
    WARPS_INCREASE("warps_increase", "+Warps"),
    FACTION_FLY("faction_fly", "Vol Faction"),
    CLAIM_DAMAGE_REDUCTION("claim_damage_reduction", "Réduction Dégâts"),
    MEMBERS_LIMIT_INCREASE("members_limit_increase", "+Membres"),
    FACTION_POWER_INCREASE("faction_power_increase", "+Power"),
    PLAYERPOINTS_LEADER("playerpoints_leader", "Points Leader");
    
    private final String configKey;
    private final String displayName;
    
    RewardType(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }
    
    public String getConfigKey() { return configKey; }
    public String getDisplayName() { return displayName; }
    
    public static RewardType fromConfigKey(String key) {
        if (key == null) return null;
        for (RewardType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) return type;
        }
        return null;
    }
}
