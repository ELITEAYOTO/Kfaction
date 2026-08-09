package me.krunsh.kfaction.data;

/**
 * Types de quêtes faction
 */
public enum QuestType {
    
    BLOCK_BREAK("block_break", "Miner"),
    ENTITY_KILL("entity_kill", "Tuer"),
    ITEM_SMELT("item_smelt", "Fondre"),
    ITEM_SELL("item_sell", "Vendre");
    
    private final String configKey;
    private final String verb;
    
    QuestType(String configKey, String verb) {
        this.configKey = configKey;
        this.verb = verb;
    }
    
    public String getConfigKey() { return configKey; }
    public String getVerb() { return verb; }
    
    public static QuestType fromConfigKey(String key) {
        if (key == null) return null;
        for (QuestType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) return type;
        }
        // Fallback: essayer le nom enum directement
        try { return valueOf(key.toUpperCase()); } catch (Exception e) { return null; }
    }
}
