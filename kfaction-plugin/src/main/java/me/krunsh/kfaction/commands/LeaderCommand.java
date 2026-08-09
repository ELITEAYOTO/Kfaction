package me.krunsh.kfaction.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.services.RoleService;

public class LeaderCommand extends SubCommand {

    private final RoleService roleService;

    public LeaderCommand(Kfaction plugin) {
        super(plugin);
        this.roleService = new RoleService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);

        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "leader.not-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "leader.usage");
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (!faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "leader.not-leader");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();

        if (!faction.isMember(targetId)) {
            sendMessage(sender, "leader.not-member");
            return;
        }

        if (targetId.equals(player.getUniqueId())) {
            sendMessage(sender, "leader.already-leader");
            return;
        }

        OperationResult<FactionRole> result = roleService.transferLeadership(
                faction,
                targetId,
                OperationContext.actor(
                        player.getUniqueId(),
                        player.getName(),
                        OperationSource.COMMAND
                )
        );

        if (!result.isSuccess()) {
            sendMessage(sender, "general.error");
            return;
        }

        sendMessage(sender, "leader.success", "{player}", safeName(target));
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() != null
                ? player.getName()
                : player.getUniqueId().toString();
    }

    @Override public String getName() { return "leader"; }
    @Override public String getDescription() { return "Transférer le leadership"; }
    @Override public String getUsage() { return "<joueur>"; }
}
