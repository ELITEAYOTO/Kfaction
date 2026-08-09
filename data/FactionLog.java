package me.krunsh.kfaction.data;

import java.util.UUID;

/**
 * Log faction historique conservé pour /f logs + Kgui.
 *
 * L'audit durable V2 vit dans audit.db. LogManager dual-write
 * automatiquement ces entrées vers AuditService.
 */
public class FactionLog {

    public enum LogType {

        // Membres
        MEMBER_JOIN("Rejoindre", "&#00AA00"),
        MEMBER_LEAVE("Quitter", "&#AA0000"),
        MEMBER_KICK("Expulsion", "&#AA0000"),
        MEMBER_PROMOTE("Promotion", "&#FFAA00"),
        MEMBER_DEMOTE("Rétrogradation", "&#FFAA00"),

        // Territoire
        TERRITORY_CLAIM("Claim", "&#00AAAA"),
        TERRITORY_UNCLAIM("Unclaim", "&#AA5500"),
        TERRITORY_SETHOME("Définir home", "&#55FFFF"),
        TERRITORY_SETWARP("Définir warp", "&#55FFFF"),
        TERRITORY_DELWARP("Supprimer warp", "&#FF5555"),

        // Économie
        ECONOMY_DEPOSIT("Dépôt", "&#55FF55"),
        ECONOMY_WITHDRAW("Retrait", "&#FF5555"),

        // Téléportations
        TP_HOME("TP Home", "&#AAAAFF"),
        TP_WARP("TP Warp", "&#AAAAFF"),
        TP_INVITE("TP Invite", "&#AAFFAA"),

        // Coffre
        CHEST_DEPOSIT("Dépôt coffre", "&#55FF55"),
        CHEST_WITHDRAW("Retrait coffre", "&#FF5555"),

        // Types V2 supplémentaires.
        RELATION_CHANGE("Relation", "&#FFAA00"),
        CLAIM_GROUP_CHANGE("Claim Group", "&#00AAAA"),
        PERMISSION_CHANGE("Permission", "&#AA55FF"),
        FACTION_DISBAND("Dissolution", "&#AA0000");

        private final String displayName;
        private final String color;

        LogType(
                String displayName,
                String color
        ) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }
    }

    private final String id;
    private final String factionId;
    private final LogType type;

    private final UUID playerUuid;
    private final String playerName;

    private final UUID targetUuid;
    private final String targetName;

    private final String details;
    private final long timestamp;

    public FactionLog(
            String id,
            String factionId,
            LogType type,
            UUID playerUuid,
            String playerName,
            UUID targetUuid,
            String targetName,
            String details,
            long timestamp
    ) {
        this.id =
                id != null
                        && !id.trim().isEmpty()
                        ? id
                        : UUID.randomUUID()
                                .toString();

        this.factionId = factionId;
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.details = details;
        this.timestamp =
                timestamp > 0L
                        ? timestamp
                        : System.currentTimeMillis();
    }

    public FactionLog(
            String factionId,
            LogType type,
            UUID playerUuid,
            String playerName,
            String details
    ) {
        this(
                UUID.randomUUID().toString(),
                factionId,
                type,
                playerUuid,
                playerName,
                null,
                null,
                details,
                System.currentTimeMillis()
        );
    }

    public FactionLog(
            String factionId,
            LogType type,
            UUID playerUuid,
            String playerName,
            UUID targetUuid,
            String targetName,
            String details
    ) {
        this(
                UUID.randomUUID().toString(),
                factionId,
                type,
                playerUuid,
                playerName,
                targetUuid,
                targetName,
                details,
                System.currentTimeMillis()
        );
    }

    public String getId() {
        return id;
    }

    public String getFactionId() {
        return factionId;
    }

    public LogType getType() {
        return type;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean hasTarget() {
        return targetUuid != null;
    }

    public long getAgeMillis() {
        return Math.max(
                0L,
                System.currentTimeMillis()
                        - timestamp
        );
    }

    public double getAgeHours() {
        return getAgeMillis()
                / 3600000.0D;
    }

    public boolean isExpired(
            long retentionHours
    ) {
        return getAgeHours()
                > retentionHours;
    }

    public String format() {
        StringBuilder builder =
                new StringBuilder();

        builder.append(
                type != null
                        ? type.getColor()
                        : "§7"
        );

        builder.append(
                type != null
                        ? type.getDisplayName()
                        : "Log"
        );

        builder.append("§7: §e");

        builder.append(
                playerName != null
                        ? playerName
                        : "SYSTEM"
        );

        if (hasTarget()) {
            builder.append(" §7→ §e")
                    .append(
                            targetName != null
                                    ? targetName
                                    : targetUuid
                    );
        }

        if (details != null
                && !details.isEmpty()) {
            builder.append(" §8(")
                    .append(details)
                    .append(')');
        }

        return builder.toString();
    }

    public String formatTime() {
        long ageMinutes =
                getAgeMillis()
                        / 60000L;

        if (ageMinutes < 1L) {
            return "À l'instant";
        }

        if (ageMinutes < 60L) {
            return "Il y a "
                    + ageMinutes
                    + "m";
        }

        if (ageMinutes < 1440L) {
            return "Il y a "
                    + (ageMinutes / 60L)
                    + "h";
        }

        return "Il y a "
                + (ageMinutes / 1440L)
                + "j";
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
