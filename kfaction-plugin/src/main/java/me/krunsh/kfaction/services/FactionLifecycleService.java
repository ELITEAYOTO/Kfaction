package me.krunsh.kfaction.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerLeaveFactionEvent;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;

/**
 * Lifecycle V2 d'une faction.
 *
 * La dissolution est traitée comme une seule opération applicative :
 * membres -> claims -> coffre -> relations/requêtes -> index -> stockage.
 *
 * Les logs de faction sont volontairement conservés afin de garder les
 * preuves/audits après dissolution.
 */
public final class FactionLifecycleService {

    private final Kfaction plugin;
    private final MembershipService membershipService;

    public FactionLifecycleService(Kfaction plugin) {
        this.plugin = plugin;
        this.membershipService = new MembershipService(plugin);
    }

    public OperationResult<Integer> disband(
            Faction faction,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(
                    Status.UNAVAILABLE,
                    "disband.main-thread-required"
            );
        }

        if (faction == null) {
            return OperationResult.failure(
                    Status.INVALID_INPUT,
                    "disband.invalid-faction"
            );
        }

        if (faction.isSystemFaction()) {
            return OperationResult.failure(
                    Status.FORBIDDEN,
                    "disband.system-faction"
            );
        }

        final String factionId = faction.getId();
        final String factionName = faction.getName();

        List<UUID> members = new ArrayList<>(faction.getMembers());
        UUID leaderId = faction.getLeader();

        /*
         * Retirer le leader en dernier évite de promouvoir successivement tous
         * les autres membres pendant une dissolution.
         */
        if (leaderId != null) {
            members.remove(leaderId);
        }

        int removedMembers = 0;

        for (UUID memberId : members) {
            fireOnlineDisbandLeaveEvent(memberId, faction);

            OperationResult<Void> result = membershipService.remove(
                    faction,
                    memberId,
                    ChangeReason.DISBAND,
                    context,
                    true
            );

            if (result.isSuccessful()) {
                removedMembers++;
            }
        }

        if (leaderId != null && faction.isMember(leaderId)) {
            fireOnlineDisbandLeaveEvent(leaderId, faction);

            OperationResult<Void> result = membershipService.remove(
                    faction,
                    leaderId,
                    ChangeReason.DISBAND,
                    context,
                    true
            );

            if (result.isSuccessful()) {
                removedMembers++;
            }
        }

        // Défense contre un état ancien incohérent.
        if (faction.getMemberCount() > 0) {
            return OperationResult.failure(
                    Status.CONFLICT,
                    "disband.members-remain",
                    "remaining=" + faction.getMemberCount()
            );
        }

        // Retirer tous les claims de l'index avant de supprimer la faction.
        plugin.getClaimManager().unclaimAll(faction);

        // Fermer et jeter tout coffre encore ouvert pour éviter un inventaire
        // orphelin après suppression.
        if (plugin.getFactionChestManager() != null) {
            plugin.getFactionChestManager()
                    .closeAndDiscardFactionChest(factionId);
        }

        cleanupRelations(factionId);
        cleanupLoadedLegacyInvites(factionId);

        plugin.getFactionManager().unregisterFaction(faction);
        plugin.getStorageManager().deleteFaction(factionId);

        if (plugin.getQuestManager() != null
                && plugin.getQuestManager()
                        .getService() != null) {
            plugin.getQuestManager()
                    .getService()
                    .releaseFactionRuntimeState(
                            factionId
                    );
        }

        if (plugin.getLogManager() != null) {
            plugin.getLogManager()
                    .audit(
                            context != null
                                    ? context
                                    : OperationContext.system(),
                            AuditCategory.LIFECYCLE,
                            "FACTION_DISBAND",
                            AuditOutcome.SUCCESS,
                            factionId,
                            leaderId,
                            leaderId != null
                                    ? leaderId.toString()
                                    : null,
                            "name="
                                    + factionName
                                    + ";membersRemoved="
                                    + removedMembers
                    );
        }

        plugin.getLogger().info(
                "[Lifecycle] Faction " + factionName
                        + " (" + factionId + ") dissoute"
                        + " - membres=" + removedMembers
                        + " - source=" + (context != null
                                ? context.getSource().name()
                                : "UNKNOWN")
                        + " - correlation=" + (context != null
                                ? context.getCorrelationId()
                                : "none")
        );

        return OperationResult.success(removedMembers);
    }

    private void cleanupRelations(String deletedFactionId) {
        Collection<Faction> factions =
                new ArrayList<>(plugin
                        .getFactionManager()
                        .getPlayerFactions());

        for (Faction other : factions) {
            if (other.getId().equals(deletedFactionId)) {
                continue;
            }

            boolean changed = false;

            if (other.getAllRelations().containsKey(deletedFactionId)) {
                other.setRelation(deletedFactionId, null);
                changed = true;
            }

            if (other.removeRelationRequestsForFaction(deletedFactionId)) {
                changed = true;
            }

            if (changed) {
                plugin.getStorageManager().markDirty(other);
            }
        }
    }

    /**
     * FPlayer.pendingInvite est un ancien mécanisme V1.
     * Les nouvelles invitations sont stockées sur Faction, mais on nettoie
     * les profils déjà chargés pour ne conserver aucune référence fantôme.
     */
    private void cleanupLoadedLegacyInvites(String deletedFactionId) {
        for (FPlayer fPlayer
                : plugin.getFPlayerManager().getAllPlayers()) {
            if (deletedFactionId.equals(fPlayer.getPendingInvite())) {
                fPlayer.clearPendingInvite();
                plugin.getStorageManager().markDirty(fPlayer);
            }
        }
    }

    private void fireOnlineDisbandLeaveEvent(
            UUID playerId,
            Faction faction
    ) {
        Player player = Bukkit.getPlayer(playerId);

        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerLeaveFactionEvent event =
                new PlayerLeaveFactionEvent(
                        player,
                        faction,
                        PlayerLeaveFactionEvent.LeaveReason.DISBAND
                );

        Bukkit.getPluginManager().callEvent(event);
        // DISBAND n'est volontairement pas annulable dans l'event V1.
    }
}
