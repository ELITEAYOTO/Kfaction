package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.RoleService;

/**
 * /f mod <joueur>
 *
 * Raccourci volontaire vers MODERATOR.
 * OFFICER est maintenant un rang distinct et n'est plus un alias de /f mod.
 */
public class ModCommand extends SubCommand {

    private final RoleService roleService;

    public ModCommand(Kfaction plugin) {
        super(plugin);
        this.roleService = new RoleService(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);

        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "mod.not-in-faction");
            return;
        }

        if (args.length < 1) {
            sendMessage(sender, "mod.usage");
            return;
        }

        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();

        if (!faction.isMember(targetId)) {
            sendMessage(sender, "mod.not-member");
            return;
        }

        FactionRole currentRole = faction.getRole(targetId);

        if (currentRole == FactionRole.LEADER) {
            sendMessage(sender, "mod.cannot-change-leader");
            return;
        }

        if (currentRole == FactionRole.MODERATOR) {
            sendMessage(sender, "mod.already-mod");
            return;
        }

        FactionRole senderRole =
                faction.getRole(
                        player.getUniqueId()
                );

        if (senderRole == null
                || !senderRole.isHigherThan(currentRole)) {
            sendMessage(sender, "mod.hierarchy-denied");
            return;
        }

        FactionCapability requiredCapability =
                currentRole.isLowerThan(
                        FactionRole.MODERATOR
                )
                        ? FactionCapability.PROMOTE
                        : FactionCapability.DEMOTE;

        if (!plugin.getPermissionManager()
                .can(
                        player,
                        requiredCapability
                )) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        OperationResult<FactionRole> result = roleService.setRole(
                faction,
                targetId,
                FactionRole.MODERATOR,
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

        sendMessage(sender, "mod.success", "{player}", safeName(target));

        if (target.isOnline()
                && target.getPlayer() != null) {
            plugin.getMessageManager().send(
                    target.getPlayer(),
                    "mod.promoted",
                    "{player}", player.getName()
            );
        }

        faction.broadcast(plugin.getMessageManager().get(
                "mod.broadcast",
                "{player}", safeName(target),
                "{sender}", player.getName()
        ));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);

            if (faction != null) {
                List<String> completions = new ArrayList<>();
                String partial = args[0].toLowerCase();

                for (UUID memberId : faction.getMembers()) {
                    FPlayer fp = plugin.getFPlayerManager().find(memberId);
                    if (fp != null && fp.getLastKnownName() != null
                            && fp.getLastKnownName().toLowerCase().startsWith(partial)) {
                        completions.add(fp.getLastKnownName());
                    }
                }

                return completions;
            }
        }

        return Collections.emptyList();
    }

    private static String safeName(OfflinePlayer player) {
        return player.getName() != null
                ? player.getName()
                : player.getUniqueId().toString();
    }

    @Override public String getName() { return "mod"; }
    @Override public String getDescription() { return "Définir un membre comme modérateur"; }
    @Override public String getUsage() { return "<joueur>"; }
}
