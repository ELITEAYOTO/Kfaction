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
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Commande /f coleader <joueur> - Définir un joueur comme sous-chef (coleader)
 * Raccourci pour promouvoir directement au rang de coleader
 */
public class ColeaderCommand extends SubCommand {
    
    public ColeaderCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "coleader.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "coleader.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        // Seul le leader peut nommer un coleader
        if (!faction.isLeader(player.getUniqueId())) {
            sendMessage(sender, "coleader.not-leader");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        
        if (!faction.isMember(targetId)) {
            sendMessage(sender, "coleader.not-member");
            return;
        }
        
        FactionRole currentRole = faction.getRole(targetId);
        if (currentRole == FactionRole.LEADER) {
            sendMessage(sender, "coleader.cannot-change-leader");
            return;
        }
        
        if (currentRole == FactionRole.COLEADER) {
            sendMessage(sender, "coleader.already-coleader");
            return;
        }
        
        // Définir le rôle comme coleader
        faction.setRole(targetId, FactionRole.COLEADER);
        
        sendMessage(sender, "coleader.success", "{player}", target.getName());
        
        // Notifier le joueur promu s'il est en ligne
        if (target.isOnline()) {
            plugin.getMessageManager().send((Player) target, "coleader.promoted", 
                "{player}", player.getName());
        }
        
        // Broadcast à la faction
        faction.broadcast(plugin.getMessageManager().get("coleader.broadcast",
            "{player}", target.getName(),
            "{sender}", player.getName()));
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
                    FPlayer fp = plugin.getFPlayerManager().getFPlayer(memberId);
                    if (fp != null && fp.getLastKnownName() != null) {
                        if (fp.getLastKnownName().toLowerCase().startsWith(partial)) {
                            completions.add(fp.getLastKnownName());
                        }
                    }
                }
                return completions;
            }
        }
        return Collections.emptyList();
    }
    
    @Override public String getName() { return "coleader"; }
    @Override public String getDescription() { return "Définir un membre comme sous-chef"; }
    @Override public String getUsage() { return "<joueur>"; }
}
