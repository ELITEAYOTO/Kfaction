package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.PlayerJoinFactionEvent;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;

public class JoinCommand extends SubCommand {
    public JoinCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (fPlayer.hasFaction()) { sendMessage(sender, "join.already-in-faction"); return; }
        if (args.length < 1) { sendMessage(sender, "join.usage"); return; }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) { sendMessage(sender, "join.faction-not-found"); return; }
        
        // Check invite or open faction
        long expiration = plugin.getConfigManager().getLong("factions.invite-expiration", 300) * 1000;
        PlayerJoinFactionEvent.JoinReason reason = faction.isOpen() ? 
            PlayerJoinFactionEvent.JoinReason.OPEN : PlayerJoinFactionEvent.JoinReason.INVITED;
            
        if (!faction.isOpen() && !fPlayer.hasInviteFrom(faction.getId(), expiration)) {
            sendMessage(sender, "join.no-invite"); return;
        }
        
        // Déclencher l'event
        PlayerJoinFactionEvent event = new PlayerJoinFactionEvent(player, faction, reason);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "join.cancelled");
            return;
        }
        
        fPlayer.clearPendingInvite();
        plugin.getFactionManager().addMember(faction, player.getUniqueId(), event.getInitialRole());
        sendMessage(sender, "join.success", "{faction}", faction.getName());
        faction.broadcast(plugin.getMessageManager().get("join.broadcast", "{player}", player.getName()));
        
        // Log de l'action
        plugin.getLogManager().log(faction.getId(), LogType.MEMBER_JOIN, player, null);
        
        // Mettre à jour les nametags via Kchat si disponible
        if (plugin.getHookManager().hasKchat()) {
            plugin.getHookManager().getKchatHook().updatePlayerNametag(player);
        }
    }
    
    @Override public String getName() { return "join"; }
    @Override public String getDescription() { return "Rejoindre une faction"; }
    @Override public String getUsage() { return "<faction>"; }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (Faction f : plugin.getFactionManager().getPlayerFactions()) {
                if (f.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(f.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
