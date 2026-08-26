package me.krunsh.kfaction.utils;

import me.krunsh.kfaction.data.FactionRole;

/** Couleurs de rôle partagées par le chat et PlaceholderAPI. */
public final class FactionRolePresentation {

    private FactionRolePresentation() {
    }

    public static String color(FactionRole role) {
        if (role == null) {
            return "&8";
        }

        switch (role) {
            case LEADER:
                return "&c";
            case COLEADER:
                return "&6";
            case MODERATOR:
                return "&e";
            case OFFICER:
                return "&a";
            case MEMBER:
                return "&7";
            case RECRUIT:
            default:
                return "&8";
        }
    }
}
