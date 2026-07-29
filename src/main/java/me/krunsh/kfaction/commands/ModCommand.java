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
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Commande /f mod <joueur> - Définir un joueur comme modérateur
 * Raccourci pour promouvoir directement au rang de modérateur
 */
public class ModCommand extends SubCommand {
    
    public ModCommand(Kfaction plugin) {
        super(plugin);
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
        
        // Vérifier la permission (doit être au moins coleader ou leader)
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.PROMOTE)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        // Vérifier que le joueur est au moins coleader pour dégrader/promouvoir vers mod
        FactionRole senderRole = faction.getRole(player.getUniqueId());
        if (senderRole == null || !senderRole.isAtLeast(FactionRole.COLEADER)) {
            sendMessage(sender, "mod.need-coleader");
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
        
        // Définir le rôle comme modérateur
        faction.setRole(targetId, FactionRole.MODERATOR);
        
        sendMessage(sender, "mod.success", "{player}", target.getName());
        
        // Notifier le joueur promu s'il est en ligne
        if (target.isOnline()) {
            plugin.getMessageManager().send((Player) target, "mod.promoted", 
                "{player}", player.getName());
        }
        
        // Broadcast à la faction
        faction.broadcast(plugin.getMessageManager().get("mod.broadcast",
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
    
    @Override public String getName() { return "mod"; }
    @Override public String getDescription() { return "Définir un membre comme modérateur"; }
    @Override public String getUsage() { return "<joueur>"; }
}
