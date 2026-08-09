package me.krunsh.kfaction.api.v2;

import java.util.UUID;

/**
 * Snapshot immutable d'un membre.
 */
public final class MemberView {

    private final UUID uuid;
    private final String name;
    private final String role;
    private final boolean online;

    public MemberView(
            UUID uuid,
            String name,
            String role,
            boolean online
    ) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.online = online;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isOnline() {
        return online;
    }
}
