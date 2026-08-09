package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerJoinFactionEvent;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.services.MembershipService;
import me.krunsh.kfaction.services.MembershipService.ChangeReason;

public class JoinCommand extends SubCommand {

    private final MembershipService membershipService;

    public JoinCommand(Kfaction plugin) {
        super(plugin);
        this.membershipService = new MembershipService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) {
            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager().getOrCreate(player);

        if (fPlayer.hasFaction()) {
            sendMessage(sender, "join.already-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "join.usage");
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFactionByName(args[0]);

        if (faction == null) {
            sendMessage(sender, "join.faction-not-found");
            return;
        }

        long expirationMs =
                plugin.getConfigManager()
                        .getLong(
                                "factions.invite-expiration",
                                300
                        ) * 1000L;

        boolean invited = faction.hasInvite(
                player.getUniqueId(),
                expirationMs
        );

        if (!faction.isOpen() && !invited) {
            sendMessage(sender, "join.no-invite");
            return;
        }

        PlayerJoinFactionEvent.JoinReason joinReason =
                faction.isOpen()
                        ? PlayerJoinFactionEvent.JoinReason.OPEN
                        : PlayerJoinFactionEvent.JoinReason.INVITED;

        PlayerJoinFactionEvent event =
                new PlayerJoinFactionEvent(
                        player,
                        faction,
                        joinReason
                );

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            sendMessage(sender, "join.cancelled");
            return;
        }

        OperationResult<FactionRole> result =
                membershipService.join(
                        faction,
                        player.getUniqueId(),
                        event.getInitialRole(),
                        ChangeReason.JOIN,
                        OperationContext.actor(
                                player.getUniqueId(),
                                player.getName(),
                                OperationSource.COMMAND
                        ),
                        false
                );

        if (!result.isSuccessful()) {
            if (result.getStatus()
                    == OperationResult.Status.LIMIT_REACHED) {
                player.sendMessage(
                        "§cCette faction a atteint sa limite de membres."
                );
            } else {
                player.sendMessage(
                        "§cImpossible de rejoindre la faction: "
                                + result.getStatus().name()
                );
            }
            return;
        }

        sendMessage(
                sender,
                "join.success",
                "{faction}", faction.getName()
        );

        faction.broadcast(
                plugin.getMessageManager().get(
                        "join.broadcast",
                        "{player}", player.getName()
                )
        );
    }

    @Override public String getName() { return "join"; }
    @Override public String getDescription() { return "Rejoindre une faction"; }
    @Override public String getUsage() { return "<faction>"; }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String prefix = args[0].toLowerCase();

            for (Faction faction
                    : plugin.getFactionManager()
                            .getPlayerFactions()) {
                if (faction.getName()
                        .toLowerCase()
                        .startsWith(prefix)) {
                    completions.add(faction.getName());
                }
            }

            return completions;
        }

        return Collections.emptyList();
    }
}
