package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.utils.FactionRolePresentation;

/**
 * Snapshot léger d'un membre pour l'affichage de /f show.
 *
 * La commande construit ces vues une seule fois, puis sépare et trie les
 * membres sans relire les managers dans les boucles de rendu du chat.
 */
final class FactionMemberView {

    private static final Comparator<FactionMemberView> ROLE_THEN_NAME =
            new Comparator<FactionMemberView>() {
                @Override
                public int compare(
                        FactionMemberView left,
                        FactionMemberView right
                ) {
                    int byRole = Integer.compare(
                            priority(right.role),
                            priority(left.role)
                    );

                    if (byRole != 0) {
                        return byRole;
                    }

                    return left.name.compareToIgnoreCase(right.name);
                }
            };

    private final UUID uuid;
    private final String name;
    private final FactionRole role;
    private final boolean online;

    FactionMemberView(
            UUID uuid,
            String name,
            FactionRole role,
            boolean online
    ) {
        this.uuid = uuid;
        this.name = name != null && !name.trim().isEmpty()
                ? name
                : uuid != null
                        ? uuid.toString()
                        : "Inconnu";
        this.role = role != null ? role : FactionRole.RECRUIT;
        this.online = online;
    }

    UUID getUuid() {
        return uuid;
    }

    String getName() {
        return name;
    }

    FactionRole getRole() {
        return role;
    }

    boolean isOnline() {
        return online;
    }

    static List<FactionMemberView> selectAndSort(
            List<FactionMemberView> members,
            boolean online
    ) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        List<FactionMemberView> selected =
                new ArrayList<FactionMemberView>();

        for (FactionMemberView member : members) {
            if (member != null && member.online == online) {
                selected.add(member);
            }
        }

        Collections.sort(selected, ROLE_THEN_NAME);
        return selected;
    }

    static String roleColor(FactionRole role) {
        return FactionRolePresentation.color(role);
    }

    private static int priority(FactionRole role) {
        return role != null ? role.getPriority() : 0;
    }
}
