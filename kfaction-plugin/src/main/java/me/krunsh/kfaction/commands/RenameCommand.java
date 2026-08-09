package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.permissions.FactionCapability;

/**
 * /f rename <nouveau_nom>
 */
public class RenameCommand extends SubCommand {

    public RenameCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;

        FPlayer fPlayer = plugin.getFPlayerManager()
                .findLoaded(player.getUniqueId());

        if (fPlayer == null || !fPlayer.hasFaction()) {
            sendMessage(sender, "rename.not-in-faction");
            return;
        }

        if (args.length != 1) {
            sendMessage(sender, "rename.usage");
            return;
        }

        Faction faction = plugin.getFactionManager()
                .getFaction(fPlayer.getFactionId());

        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.RENAME
        )) {
            sendMessage(sender, "general.no-permission");
            return;
        }

        String newName = args[0];

        if (!plugin.getFactionManager().isValidName(newName)) {
            sendMessage(sender, "rename.invalid-name");
            return;
        }

        if (!plugin.getFactionManager().isNameAvailable(newName)) {
            sendMessage(
                    sender,
                    "rename.name-taken",
                    "{name}",
                    newName
            );
            return;
        }

        String oldName = faction.getName();

        if (!plugin.getFactionManager().renameFaction(
                faction,
                newName
        )) {
            sendMessage(sender, "rename.failed");
            return;
        }

        faction.broadcast(
                plugin.getMessageManager().get(
                        "rename.success",
                        "{player}",
                        player.getName(),
                        "{old}",
                        oldName,
                        "{new}",
                        newName
                )
        );
    }

    @Override public String getName() { return "rename"; }
    @Override public String getDescription() { return "Renommer votre faction"; }
    @Override public String getUsage() { return "<nouveau_nom>"; }
}
