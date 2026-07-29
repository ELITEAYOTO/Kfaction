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
import me.krunsh.kfaction.managers.RelationManager.RelationResult;

public class AllyCommand extends SubCommand {
    public AllyCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "ally.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "ally.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.ALLY)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[0]);
        if (targetFaction == null) {
            sendMessage(sender, "ally.faction-not-found");
            return;
        }
        
        if (targetFaction.getId().equals(faction.getId())) {
            sendMessage(sender, "ally.cannot-self");
            return;
        }
        
        // Déclencher l'event avant le changement de relation
        Relation oldRelation = faction.getRelationTo(targetFaction);
        FactionRelationChangeEvent event = new FactionRelationChangeEvent(
            player, faction, targetFaction, oldRelation, Relation.ALLY);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "ally.cancelled");
            return;
        }
        
        RelationResult result = plugin.getRelationManager().requestAlly(faction, targetFaction);
        if (result.isSuccess()) {
            sendMessage(sender, result == RelationResult.SUCCESS ? "ally.success" : "ally.request-sent", 
                "{faction}", targetFaction.getName());
            
            // Mettre à jour les nametags via Kchat si disponible
            if (plugin.getHookManager().hasKchat()) {
                plugin.getHookManager().getKchatHook().updateAllNametags();
            }
        } else {
            sendMessage(sender, "ally.failed", "{reason}", result.getMessage());
        }
    }
    
    @Override public String getName() { return "ally"; }
    @Override public String getDescription() { return "Demander une alliance"; }
    @Override public String getUsage() { return "<faction>"; }
}
