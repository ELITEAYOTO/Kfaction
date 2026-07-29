package me.krunsh.kfaction.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionCreateEvent;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f create <nom> - Créer une faction
 */
public class CreateCommand extends SubCommand {
    
    public CreateCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        
        // Vérifier les arguments
        if (args.length < 1) {
            sendMessage(sender, "create.usage");
            return;
        }
        
        String name = args[0];
        
        // Vérifier si le joueur est déjà dans une faction
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer.hasFaction()) {
            sendMessage(sender, "create.already-in-faction");
            return;
        }
        
        // Vérifier la validité du nom
        if (!plugin.getFactionManager().isValidName(name)) {
            sendMessage(sender, "create.invalid-name");
            return;
        }
        
        // Vérifier la disponibilité du nom
        if (!plugin.getFactionManager().isNameAvailable(name)) {
            sendMessage(sender, "create.name-taken", "{name}", name);
            return;
        }
        
        // Vérifier le coût de création
        double cost = plugin.getConfigManager().getDouble("economy.creation-cost", 0);
        if (cost > 0 && plugin.getHookManager().hasVault()) {
            if (!plugin.getHookManager().getVaultHook().has(player, cost)) {
                sendMessage(sender, "create.not-enough-money",
                    "{cost}", plugin.getHookManager().getVaultHook().format(cost));
                return;
            }
        }
        
        // Déclencher l'event AVANT la création (peut être annulé)
        FactionCreateEvent event = new FactionCreateEvent(player, name);
        Bukkit.getPluginManager().callEvent(event);
        
        if (event.isCancelled()) {
            String reason = event.getCancelReason();
            if (reason != null && !reason.isEmpty()) {
                player.sendMessage(reason);
            } else {
                sendMessage(sender, "create.cancelled");
            }
            return;
        }
        
        // Prélever le coût après vérification de l'event
        if (cost > 0 && plugin.getHookManager().hasVault()) {
            plugin.getHookManager().getVaultHook().withdraw(player, cost);
        }
        
        // Créer la faction
        Faction faction = plugin.getFactionManager().createFaction(name, player.getUniqueId());
        
        // Mettre à jour l'event avec la faction créée
        event.setFaction(faction);
        
        if (faction == null) {
            sendMessage(sender, "create.failed");
            return;
        }
        
        // Succès
        sendMessage(sender, "create.success", "{name}", name);
        
        // Broadcast si configuré
        if (plugin.getConfigManager().getBoolean("broadcast.faction-create", true)) {
            String broadcast = plugin.getMessageManager().get("create.broadcast",
                "{player}", player.getName(),
                "{faction}", name);
            plugin.getServer().broadcastMessage(broadcast);
        }
    }
    
    @Override
    public String getName() {
        return "create";
    }
    
    @Override
    public String getDescription() {
        return "Créer une nouvelle faction";
    }
    
    @Override
    public String getUsage() {
        return "<nom>";
    }
}
