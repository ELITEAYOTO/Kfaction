package me.krunsh.kfaction.api.v2;

import java.util.UUID;

/**
 * Snapshot immutable d'un FPlayer.
 */
public final class PlayerView {

    private final UUID uuid;
    private final String name;
    private final String factionId;

    private final String role;
    private final String roleDisplayName;
    private final String rolePrefix;

    private final String chatMode;

    private final double power;
    private final double maxPower;

    private final boolean online;
    private final boolean bypassing;
    private final boolean mapAutoEnabled;

    private final long firstJoin;
    private final long lastSeen;

    private final int kills;
    private final int deaths;

    /**
     * Constructeur Lot20 conservé.
     */
    public PlayerView(
            UUID uuid,
            String name,
            String factionId,
            String role,
            double power,
            double maxPower,
            boolean online,
            boolean bypassing,
            boolean mapAutoEnabled,
            long firstJoin,
            long lastSeen,
            int kills,
            int deaths
    ) {
        this(
                uuid,
                name,
                factionId,
                role,
                role,
                "",
                null,
                power,
                maxPower,
                online,
                bypassing,
                mapAutoEnabled,
                firstJoin,
                lastSeen,
                kills,
                deaths
        );
    }

    public PlayerView(
            UUID uuid,
            String name,
            String factionId,
            String role,
            String roleDisplayName,
            String rolePrefix,
            String chatMode,
            double power,
            double maxPower,
            boolean online,
            boolean bypassing,
            boolean mapAutoEnabled,
            long firstJoin,
            long lastSeen,
            int kills,
            int deaths
    ) {
        this.uuid = uuid;
        this.name = name;
        this.factionId = factionId;
        this.role = role;
        this.roleDisplayName = roleDisplayName;
        this.rolePrefix = rolePrefix;
        this.chatMode = chatMode;
        this.power = power;
        this.maxPower = maxPower;
        this.online = online;
        this.bypassing = bypassing;
        this.mapAutoEnabled = mapAutoEnabled;
        this.firstJoin = Math.max(0L, firstJoin);
        this.lastSeen = Math.max(0L, lastSeen);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getFactionId() {
        return factionId;
    }

    public String getRole() {
        return role;
    }

    public String getRoleDisplayName() {
        return roleDisplayName;
    }

    public String getRolePrefix() {
        return rolePrefix;
    }

    public String getChatMode() {
        return chatMode;
    }

    public double getPower() {
        return power;
    }

    public double getMaxPower() {
        return maxPower;
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isBypassing() {
        return bypassing;
    }

    public boolean isMapAutoEnabled() {
        return mapAutoEnabled;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public boolean hasFaction() {
        return factionId != null;
    }
}
