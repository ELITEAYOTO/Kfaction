package me.krunsh.kfaction.data;

import java.util.UUID;

/**
 * Représente une entrée de log pour une faction
 * Stocke les actions des membres avec timestamp
 */
public class FactionLog {
    
    /**
     * Types d'actions loguées
     */
    public enum LogType {
        // Actions membres
        MEMBER_JOIN("Rejoindre", "&#00AA00"),
        MEMBER_LEAVE("Quitter", "&#AA0000"),
        MEMBER_KICK("Expulsion", "&#AA0000"),
        MEMBER_PROMOTE("Promotion", "&#FFAA00"),
        MEMBER_DEMOTE("Rétrogradation", "&#FFAA00"),
        
        // Actions territoire
        TERRITORY_CLAIM("Claim", "&#00AAAA"),
        TERRITORY_UNCLAIM("Unclaim", "&#AA5500"),
        TERRITORY_SETHOME("Définir home", "&#55FFFF"),
        TERRITORY_SETWARP("Définir warp", "&#55FFFF"),
        TERRITORY_DELWARP("Supprimer warp", "&#FF5555"),
        
        // Actions économie
        ECONOMY_DEPOSIT("Dépôt", "&#55FF55"),
        ECONOMY_WITHDRAW("Retrait", "&#FF5555"),
        
        // Actions TP
        TP_HOME("TP Home", "&#AAAAFF"),
        TP_WARP("TP Warp", "&#AAAAFF"),
        TP_INVITE("TP Invite", "&#AAFFAA"),
        
        // Actions F-Chest (préparé pour le futur)
        CHEST_DEPOSIT("Dépôt coffre", "&#55FF55"),
        CHEST_WITHDRAW("Retrait coffre", "&#FF5555");
        
        private final String displayName;
        private final String color;
        
        LogType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }
    
    // Identifiant unique du log
    private final String id;
    
    // Faction concernée
    private final String factionId;
    
    // Type d'action
    private final LogType type;
    
    // Joueur qui a effectué l'action
    private final UUID playerUuid;
    private final String playerName;
    
    // Joueur cible (si applicable, ex: kick, promote)
    private final UUID targetUuid;
    private final String targetName;
    
    // Détails supplémentaires (ex: montant, coords, item)
    private final String details;
    
    // Timestamp
    private final long timestamp;
    
    /**
     * Constructeur complet
     */
    public FactionLog(String id, String factionId, LogType type, 
                      UUID playerUuid, String playerName,
                      UUID targetUuid, String targetName,
                      String details, long timestamp) {
        this.id = id;
        this.factionId = factionId;
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.details = details;
        this.timestamp = timestamp;
    }
    
    /**
     * Constructeur simplifié (sans cible)
     */
    public FactionLog(String factionId, LogType type, 
                      UUID playerUuid, String playerName,
                      String details) {
        this(UUID.randomUUID().toString(), factionId, type, 
             playerUuid, playerName, null, null, details, 
             System.currentTimeMillis());
    }
    
    /**
     * Constructeur avec cible
     */
    public FactionLog(String factionId, LogType type,
                      UUID playerUuid, String playerName,
                      UUID targetUuid, String targetName,
                      String details) {
        this(UUID.randomUUID().toString(), factionId, type,
             playerUuid, playerName, targetUuid, targetName, details,
             System.currentTimeMillis());
    }
    
    // === Getters ===
    
    public String getId() { return id; }
    public String getFactionId() { return factionId; }
    public LogType getType() { return type; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Vérifie si ce log concerne un joueur cible
     */
    public boolean hasTarget() {
        return targetUuid != null;
    }
    
    /**
     * Retourne l'âge du log en millisecondes
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Retourne l'âge du log en heures
     */
    public double getAgeHours() {
        return getAgeMillis() / 3600000.0;
    }
    
    /**
     * Vérifie si le log a expiré (36h par défaut)
     */
    public boolean isExpired(long retentionHours) {
        return getAgeHours() > retentionHours;
    }
    
    /**
     * Formate le log pour affichage
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getColor()).append(type.getDisplayName()).append("§7: ");
        sb.append("§e").append(playerName);
        
        if (hasTarget()) {
            sb.append(" §7→ §e").append(targetName);
        }
        
        if (details != null && !details.isEmpty()) {
            sb.append(" §8(").append(details).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * Formate le timestamp en texte lisible
     */
    public String formatTime() {
        long ageMinutes = getAgeMillis() / 60000;
        
        if (ageMinutes < 1) {
            return "À l'instant";
        } else if (ageMinutes < 60) {
            return "Il y a " + ageMinutes + "m";
        } else if (ageMinutes < 1440) {
            return "Il y a " + (ageMinutes / 60) + "h";
        } else {
            return "Il y a " + (ageMinutes / 1440) + "j";
        }
    }
    
    @Override
    public String toString() {
        return "FactionLog{" +
                "type=" + type +
                ", player=" + playerName +
                ", target=" + targetName +
                ", details='" + details + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
