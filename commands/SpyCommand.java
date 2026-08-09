package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f spy - Active/désactive l'espionnage du chat d'une faction (admin)
 * Usage: /f spy <faction> - Espionne une faction spécifique
 *        /f spy          - Arrête l'espionnage actuel
 */
public class SpyCommand extends SubCommand {
    
    public SpyCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        
        // Vérifier permission admin
        if (!player.hasPermission("kfaction.admin.spy")) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        // Si pas d'argument, arrêter le spy ou afficher l'aide
        if (args.length == 0) {
            if (fPlayer.isSpyingAnyFaction()) {
                String oldFaction = fPlayer.getSpyingFactionId();
                Faction faction = plugin.getFactionManager().getFaction(oldFaction);
                String factionName = (faction != null) ? faction.getName() : oldFaction;
                
                fPlayer.stopSpying();
                sendMessage(sender, "spy.disabled", "{faction}", factionName);
            } else {
                sendMessage(sender, "spy.usage");
            }
            return;
        }
        
        // Rechercher la faction
        String factionName = args[0];
        Faction targetFaction = plugin.getFactionManager().getFactionByName(factionName);
        
        if (targetFaction == null) {
            // Essayer avec le tag
            targetFaction = plugin.getFactionManager().getFactionByTag(factionName);
        }
        
        if (targetFaction == null) {
            sendMessage(sender, "spy.faction-not-found", "{faction}", factionName);
            return;
        }
        
        // Si c'est sa propre faction, refuser
        if (targetFaction.getId().equals(fPlayer.getFactionId())) {
            sendMessage(sender, "spy.own-faction");
            return;
        }
        
        // Si déjà en train de spy cette faction, arrêter
        if (fPlayer.isSpyingFaction(targetFaction.getId())) {
            fPlayer.stopSpying();
            sendMessage(sender, "spy.disabled", "{faction}", targetFaction.getName());
            return;
        }
        
        // Définir la nouvelle faction à spy
        fPlayer.setSpyingFactionId(targetFaction.getId());
        sendMessage(sender, "spy.enabled", "{faction}", targetFaction.getName());
    }
    
    @Override public String getName() { return "spy"; }
    @Override public String getDescription() { return "Espionner le chat d'une faction (admin)"; }
    @Override public String getUsage() { return "[faction]"; }
}
