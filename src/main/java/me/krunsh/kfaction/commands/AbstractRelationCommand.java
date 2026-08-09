package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionRelationChangeEvent;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.managers.RelationManager.RelationResult;
import me.krunsh.kfaction.permissions.FactionCapability;

/**
 * Pipeline commun des commandes de diplomatie V2.
 *
 * permission -> target -> PRE event -> RelationManager -> message/hook.
 */
public abstract class AbstractRelationCommand extends SubCommand {

    private final String messagePrefix;
    private final Relation desiredRelation;
    private final FactionCapability capability;

    protected AbstractRelationCommand(
            Kfaction plugin,
            String messagePrefix,
            Relation desiredRelation,
            FactionCapability capability
    ) {
        super(plugin);
        this.messagePrefix = messagePrefix;
        this.desiredRelation = desiredRelation;
        this.capability = capability;
    }

    @Override
    public final void execute(
            CommandSender sender,
            String[] args
    ) {
        Player player = getPlayer(sender);

        if (player == null) {
            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()) {
            sendMessage(
                    sender,
                    messagePrefix
                            + ".not-in-faction"
            );
            return;
        }

        if (args.length < 1) {
            sendMessage(
                    sender,
                    messagePrefix
                            + ".usage"
            );
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            sendMessage(
                    sender,
                    "general.error"
            );
            return;
        }

        if (!plugin.getPermissionManager().can(
                player,
                capability
        )) {
            sendMessage(
                    sender,
                    "general.no-permission"
            );
            return;
        }

        Faction target =
                plugin.getFactionManager()
                        .getFactionByName(
                                args[0]
                        );

        if (target == null
                || target.isSystemFaction()) {
            sendMessage(
                    sender,
                    messagePrefix
                            + ".faction-not-found"
            );
            return;
        }

        if (target.getId()
                .equals(
                        faction.getId()
                )) {
            sendMessage(
                    sender,
                    messagePrefix
                            + ".cannot-self"
            );
            return;
        }

        Relation oldRelation =
                faction.getRelationTo(
                        target
                );

        FactionRelationChangeEvent event =
                new FactionRelationChangeEvent(
                        player,
                        faction,
                        target,
                        oldRelation,
                        desiredRelation
                );

        Bukkit.getPluginManager()
                .callEvent(event);

        if (event.isCancelled()) {
            auditRelation(
                    player,
                    faction,
                    target,
                    oldRelation,
                    AuditOutcome.CANCELLED,
                    "event-cancelled"
            );

            sendMessage(
                    sender,
                    messagePrefix
                            + ".cancelled"
            );
            return;
        }

        RelationResult result =
                applyRelation(
                        faction,
                        target
                );

        if (result == null
                || !result.isSuccess()) {
            auditRelation(
                    player,
                    faction,
                    target,
                    oldRelation,
                    AuditOutcome.FAILED,
                    result != null
                            ? result.getMessage()
                            : "internal-error"
            );

            sendMessage(
                    sender,
                    messagePrefix
                            + ".failed",
                    "{reason}",
                    result != null
                            ? result.getMessage()
                            : "Erreur interne"
            );
            return;
        }

        auditRelation(
                player,
                faction,
                target,
                oldRelation,
                AuditOutcome.SUCCESS,
                "result=" + String.valueOf(result)
        );

        sendSuccess(
                sender,
                target,
                result
        );

        if (plugin.getHookManager().hasKchat()) {
            plugin.getHookManager()
                    .getKchatHook()
                    .updateAllNametags();
        }
    }

    private void auditRelation(
            Player player,
            Faction faction,
            Faction target,
            Relation oldRelation,
            AuditOutcome outcome,
            String details
    ) {
        if (plugin.getLogManager() == null) {
            return;
        }

        OperationContext context =
                OperationContext.actor(
                        player.getUniqueId(),
                        player.getName(),
                        OperationSource.COMMAND
                );

        plugin.getLogManager()
                .audit(
                        context,
                        AuditCategory.RELATION,
                        "RELATION_"
                                + desiredRelation.name(),
                        outcome,
                        faction.getId(),
                        null,
                        target.getName(),
                        "targetFaction="
                                + target.getId()
                                + ";old="
                                + oldRelation.name()
                                + ";new="
                                + desiredRelation.name()
                                + ";"
                                + details
                );
    }

    private RelationResult applyRelation(
            Faction faction,
            Faction target
    ) {
        switch (desiredRelation) {
            case ALLY:
                return plugin.getRelationManager()
                        .requestAlly(
                                faction,
                                target
                        );

            case TRUCE:
                return plugin.getRelationManager()
                        .requestTruce(
                                faction,
                                target
                        );

            case ENEMY:
                /*
                 * V2: utilise enfin le chemin métier prévu par RelationManager
                 * au lieu du setEnemy() brut qui contournait les limites.
                 */
                return plugin.getRelationManager()
                        .declareEnemy(
                                faction,
                                target
                        );

            case NEUTRAL:
                return plugin.getRelationManager()
                        .setNeutral(
                                faction,
                                target
                        );

            default:
                return null;
        }
    }

    private void sendSuccess(
            CommandSender sender,
            Faction target,
            RelationResult result
    ) {
        /*
         * REQUEST_SENT ne produit volontairement pas de broadcast dans la
         * faction demandeuse: seul l'acteur reçoit la confirmation ici.
         *
         * Pour SUCCESS, RelationManager a déjà diffusé le changement aux
         * factions concernées avec les clés relation.* adaptées. Ne pas
         * renvoyer un second message à l'acteur évite les doublons.
         */
        if (result != RelationResult.REQUEST_SENT) {
            return;
        }

        String key =
                desiredRelation == Relation.ALLY
                        ? "ally.request-sent"
                        : "truce.request-sent";

        sendMessage(
                sender,
                key,
                "{faction}",
                target.getName()
        );
    }
}
