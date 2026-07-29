package me.krunsh.kfaction.commands;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.policy.HomeClaimPolicy;

public class SetHomeCommand extends SubCommand {
    public SetHomeCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "sethome.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.SETHOME)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        Location loc = player.getLocation();
        Faction territoryOwner = plugin.getClaimManager().getFactionAt(new FLocation(loc));
        String territoryOwnerId = territoryOwner != null ? territoryOwner.getId() : null;
        boolean explicitStaffBypass = player.hasPermission("kfaction.admin.bypass")
            && plugin.isBypassing(player.getUniqueId());

        if (!HomeClaimPolicy.canSetHome(faction.getId(), territoryOwnerId, explicitStaffBypass)) {
            // Important : ne pas toucher à l'ancien home lors d'un refus.
            sendMessage(sender, "sethome.not-in-territory");
            return;
        }

        faction.setHome(loc);
        plugin.getLogManager().log(faction.getId(), LogType.TERRITORY_SETHOME, player, null, 
            String.format("[%d, %d, %d]%s", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                explicitStaffBypass ? " BYPASS (territoire=" + territoryOwnerId + ")" : ""));
        if (explicitStaffBypass) {
            plugin.getLogger().warning("[AUDIT] " + player.getName()
                + " a utilisé le bypass pour /f sethome de " + faction.getName()
                + " hors de son claim (territoire=" + territoryOwnerId + ")");
        }
        sendMessage(sender, "sethome.success");
    }
    
    @Override public String getName() { return "sethome"; }
    @Override public String getDescription() { return "Définir le home de faction"; }
    @Override public String getUsage() { return ""; }
}
