package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerLeaveFactionEvent;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;

public class LeaveCommand extends SubCommand {
    public LeaveCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "leave.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "leave.leader-cannot-leave");
            return;
        }
        
        // Déclencher l'event
        PlayerLeaveFactionEvent event = new PlayerLeaveFactionEvent(player, faction, PlayerLeaveFactionEvent.LeaveReason.LEAVE);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "leave.cancelled");
            return;
        }
        
        // Log avant de quitter (on a encore le factionId)
        String factionId = faction.getId();
        
        faction.removeMember(player.getUniqueId());
        fPlayer.setFactionId(null);
        plugin.getFPlayerManager().notifyFactionChange(player.getUniqueId(), factionId, null);
        sendMessage(sender, "leave.success", "{name}", faction.getName());
        
        // Log de l'action
        plugin.getLogManager().log(factionId, LogType.MEMBER_LEAVE, player, null);
        
        // Mettre à jour les nametags via Kchat si disponible
        if (plugin.getHookManager().hasKchat()) {
            plugin.getHookManager().getKchatHook().updatePlayerNametag(player);
        }
    }
    
    @Override public String getName() { return "leave"; }
    @Override public String getDescription() { return "Quitter la faction"; }
    @Override public String getUsage() { return ""; }
}
