package me.krunsh.kfaction.data;

/**
 * Représente une quête active d'une faction
 * Chaque faction peut avoir 3 quêtes actives simultanément
 */
public class FactionQuest {
    
    private final String id;           // ID unique (ex: "mine_diamond_ore")
    private final QuestType type;      // BLOCK_BREAK, ENTITY_KILL, etc.
    private final QuestCategory category; // MINEUR, FARMER, CHASSEUR
    private final String target;       // Material ou EntityType en string
    private final String sparrowItemId; // CIT exact optionnel pour ITEM_SELL
    private final String displayName;  // Nom affiché (ex: "Miner 500 Diamond Ore")
    private int progress;              // Progression actuelle
    private final int required;        // Objectif à atteindre
    private final int xpReward;        // XP gagnée à la complétion
    private boolean completed;         // Quête terminée
    
    public FactionQuest(String id, QuestType type, QuestCategory category,
                        String target, String displayName, int required, int xpReward) {
        this(id, type, category, target, null, displayName, required, xpReward);
    }

    public FactionQuest(String id, QuestType type, QuestCategory category,
                        String target, String sparrowItemId, String displayName,
                        int required, int xpReward) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.target = target;
        this.sparrowItemId = sparrowItemId;
        this.displayName = displayName;
        this.progress = 0;
        this.required = required;
        this.xpReward = xpReward;
        this.completed = false;
    }
    
    /**
     * Incrémente la progression
     * @param amount Montant à ajouter
     * @return true si la quête vient d'être complétée
     */
    public boolean addProgress(int amount) {
        if (completed) return false;
        this.progress = Math.min(progress + amount, required);
        if (progress >= required) {
            completed = true;
            return true;
        }
        return false;
    }
    
    /**
     * @return Pourcentage de progression (0-100)
     */
    public int getProgressPercent() {
        if (required <= 0) return 100;
        return Math.min(100, (int) ((progress * 100.0) / required));
    }
    
    /**
     * Génère une barre de progression colorée
     * @param length Nombre total de caractères
     * @return Barre formatée avec couleurs
     */
    public String getProgressBar(int length) {
        int filled = (int) ((progress * (double) length) / required);
        filled = Math.min(filled, length);
        
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < length; i++) {
            if (i == filled) bar.append("§7");
            bar.append("▌");
        }
        return bar.toString();
    }
    
    // === Getters ===
    
    public String getId() { return id; }
    public QuestType getType() { return type; }
    public QuestCategory getCategory() { return category; }
    public String getTarget() { return target; }
    public String getSparrowItemId() { return sparrowItemId; }
    public String getDisplayName() { return displayName; }
    public int getProgress() { return progress; }
    public int getRequired() { return required; }
    public int getXpReward() { return xpReward; }
    public boolean isCompleted() { return completed; }
    
    // === Setters (pour la désérialisation) ===
    
    public void setProgress(int progress) { 
        this.progress = progress; 
        if (this.progress >= required) {
            this.completed = true;
        }
    }
    
    public void setCompleted(boolean completed) { 
        this.completed = completed; 
    }
    
    @Override
    public String toString() {
        return "FactionQuest{" + id + ", " + progress + "/" + required + 
               (completed ? " ✓" : "") + "}";
    }
}
