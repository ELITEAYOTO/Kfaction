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

public class EnemyCommand extends SubCommand {
    public EnemyCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "enemy.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "enemy.usage");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.ENEMY)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        Faction targetFaction = plugin.getFactionManager().getFactionByName(args[0]);
        if (targetFaction == null) {
            sendMessage(sender, "enemy.faction-not-found");
            return;
        }
        
        if (targetFaction.getId().equals(faction.getId())) {
            sendMessage(sender, "enemy.cannot-self");
            return;
        }
        
        // Déclencher l'event avant le changement de relation
        Relation oldRelation = faction.getRelationTo(targetFaction);
        FactionRelationChangeEvent event = new FactionRelationChangeEvent(
            player, faction, targetFaction, oldRelation, Relation.ENEMY);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            sendMessage(sender, "enemy.cancelled");
            return;
        }
        
        plugin.getRelationManager().setEnemy(faction, targetFaction);
        sendMessage(sender, "enemy.success", "{faction}", targetFaction.getName());
        
        // Mettre à jour les nametags via Kchat si disponible
        if (plugin.getHookManager().hasKchat()) {
            plugin.getHookManager().getKchatHook().updateAllNametags();
        }
    }
    
    @Override public String getName() { return "enemy"; }
    @Override public String getDescription() { return "Déclarer une faction ennemie"; }
    @Override public String getUsage() { return "<faction>"; }
}
