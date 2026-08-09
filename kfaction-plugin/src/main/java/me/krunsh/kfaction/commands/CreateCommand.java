package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionCreateEvent;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.hooks.VaultHook;

/**
 * Commande /f create <nom> - Créer une faction.
 *
 * Lot25D:
 * - clé canonique economy.faction-create-cost;
 * - aucune création gratuite silencieuse si un coût > 0 est configuré mais
 *   que Vault est indisponible;
 * - vérification du retrait;
 * - compensation si la création échoue après débit.
 */
public class CreateCommand extends SubCommand {

    public CreateCommand(
            Kfaction plugin
    ) {
        super(plugin);
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        Player player =
                getPlayer(sender);

        if (player == null) {
            return;
        }

        if (args.length < 1) {
            sendMessage(
                    sender,
                    "create.usage"
            );
            return;
        }

        String name =
                args[0];

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        if (fPlayer == null) {
            /*
             * Un joueur en ligne doit normalement déjà être chargé par
             * PlayerConnectionListener. Le fallback explicite reste main-thread.
             */
            fPlayer =
                    plugin.getFPlayerManager()
                            .getOrCreate(
                                    player
                            );
        }

        if (fPlayer == null) {
            sendMessage(
                    sender,
                    "create.failed"
            );
            return;
        }

        if (fPlayer.hasFaction()) {
            sendMessage(
                    sender,
                    "create.already-in-faction"
            );
            return;
        }

        if (!plugin.getFactionManager()
                .isValidName(
                        name
                )) {
            sendMessage(
                    sender,
                    "create.invalid-name"
            );
            return;
        }

        if (!plugin.getFactionManager()
                .isNameAvailable(
                        name
                )) {
            sendMessage(
                    sender,
                    "create.name-taken",
                    "{name}",
                    name
            );
            return;
        }

        double cost =
                Math.max(
                        0.0D,
                        plugin.getConfigManager()
                                .getDouble(
                                        "economy.faction-create-cost",
                                        0.0D
                                )
                );

        VaultHook vault =
                plugin.getHookManager() != null
                && plugin.getHookManager()
                        .hasVault()
                        ? plugin.getHookManager()
                                .getVaultHook()
                        : null;

        if (cost > 0.0D) {
            if (vault == null
                    || !vault.isEnabled()) {
                sendMessage(
                        sender,
                        "create.economy-unavailable"
                );
                return;
            }

            if (!vault.has(
                    player,
                    cost
            )) {
                sendMessage(
                        sender,
                        "create.not-enough-money",
                        "{cost}",
                        vault.format(cost)
                );
                return;
            }
        }

        FactionCreateEvent event =
                new FactionCreateEvent(
                        player,
                        name
                );

        Bukkit.getPluginManager()
                .callEvent(
                        event
                );

        if (event.isCancelled()) {
            String reason =
                    event.getCancelReason();

            if (reason != null
                    && !reason.isEmpty()) {
                player.sendMessage(
                        reason
                );
            } else {
                sendMessage(
                        sender,
                        "create.cancelled"
                );
            }

            return;
        }

        boolean charged =
                false;

        if (cost > 0.0D) {
            charged =
                    vault.withdraw(
                            player,
                            cost
                    );

            if (!charged) {
                sendMessage(
                        sender,
                        "create.transaction-failed"
                );
                return;
            }
        }

        Faction faction =
                plugin.getFactionManager()
                        .createFaction(
                                name,
                                player.getUniqueId()
                        );

        if (faction == null) {
            if (charged
                    && !vault.deposit(
                            player,
                            cost
                    )) {
                plugin.getLogger()
                        .severe(
                                "Impossible de rembourser "
                                        + player.getName()
                                        + " après échec /f create. Montant="
                                        + cost
                        );
            }

            sendMessage(
                    sender,
                    "create.failed"
            );
            return;
        }

        event.setFaction(
                faction
        );

        sendMessage(
                sender,
                "create.success",
                "{name}",
                name
        );

        if (plugin.getConfigManager()
                .getBoolean(
                        "factions.create.broadcast",
                        true
                )) {
            String broadcast =
                    plugin.getMessageManager()
                            .get(
                                    "create.broadcast",
                                    "{player}",
                                    player.getName(),
                                    "{faction}",
                                    name
                            );

            plugin.getServer()
                    .broadcastMessage(
                            broadcast
                    );
        }
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Créer une nouvelle faction";
    }

    @Override
    public String getUsage() {
        return "<nom>";
    }
}
