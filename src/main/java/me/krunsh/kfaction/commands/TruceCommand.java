package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionRelationChangeEvent;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;
import me.krunsh.kfaction.data.Relation;

public class TruceCommand extends SubCommand {
    public TruceCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "truce.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "truce.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.TRUCE)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[0]);
        if (targetFaction == null) {
            sendMessage(sender, "truce.faction-not-found");
            return;
        }
        
        if (targetFaction.getId().equals(faction.getId())) {
            sendMessage(sender, "truce.cannot-self");
            return;
        }
        
        // Déclencher l'event avant la demande de trêve
        Relation oldRelation = faction.getRelationTo(targetFaction);
        FactionRelationChangeEvent event = new FactionRelationChangeEvent(
            player, faction, targetFaction, oldRelation, Relation.TRUCE);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "truce.cancelled");
            return;
        }
        
        plugin.getRelationManager().requestTruce(faction, targetFaction);
        sendMessage(sender, "truce.request-sent", "{faction}", targetFaction.getName());
        
        // Mettre à jour les nametags via Kchat si disponible
        if (plugin.getHookManager().hasKchat()) {
            plugin.getHookManager().getKchatHook().updateAllNametags();
        }
    }
    
    @Override public String getName() { return "truce"; }
    @Override public String getDescription() { return "Demander une trêve"; }
    @Override public String getUsage() { return "<faction>"; }
}
