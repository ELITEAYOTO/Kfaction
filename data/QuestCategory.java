package me.krunsh.kfaction.data;

/**
 * Catégories de quêtes faction
 */
public enum QuestCategory {
    
    MINEUR("mineur", "Mineur", "&7⛏"),
    FARMER("farmer", "Farmer", "&a\u2618"),
    CHASSEUR("chasseur", "Chasseur", "&c\u2694");
    
    private final String configKey;
    private final String displayName;
    private final String icon;
    
    QuestCategory(String configKey, String displayName, String icon) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
    }
    
    public String getConfigKey() { return configKey; }
    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    
    public static QuestCategory fromConfigKey(String key) {
        if (key == null) return null;
        for (QuestCategory cat : values()) {
            if (cat.configKey.equalsIgnoreCase(key)) return cat;
        }
        try { return valueOf(key.toUpperCase()); } catch (Exception e) { return null; }
    }
}
