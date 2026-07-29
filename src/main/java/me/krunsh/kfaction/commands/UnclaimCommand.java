package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.PermissionAction;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class UnclaimCommand extends SubCommand {
    
    private static final int MAX_RADIUS = 5;
    private static final int CONFIRM_THRESHOLD = 2; // Demande confirmation si radius > 2
    
    // Cache des confirmations en attente
    private final Map<UUID, PendingUnclaim> pendingUnclaims = new HashMap<>();
    
    public UnclaimCommand(Kfaction plugin) { 
        super(plugin); 
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "unclaim.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.UNCLAIM)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        // Vérifier si c'est une confirmation
        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            handleConfirm(player, faction);
            return;
        }
        
        // Vérifier si un radius est spécifié
        if (args.length > 0) {
            handleRadiusUnclaim(player, faction, args[0]);
            return;
        }
        
        // Unclaim simple du chunk actuel
        FLocation location = new FLocation(player.getLocation());
        boolean success = plugin.getClaimManager().unclaim(faction, location);
        
        if (success) {
            plugin.getLogManager().log(faction.getId(), LogType.TERRITORY_UNCLAIM, player, null,
                "[" + location.getX() + ", " + location.getZ() + "]");
            sendMessage(sender, "unclaim.success",
                "{x}", String.valueOf(location.getX()),
                "{z}", String.valueOf(location.getZ()));
        } else {
            sendMessage(sender, "unclaim.failed", "{reason}", "Ce chunk ne vous appartient pas");
        }
    }
    
    /**
     * Gère un unclaim avec radius
     */
    private void handleRadiusUnclaim(Player player, Faction faction, String radiusStr) {
        int radius;
        try {
            radius = Integer.parseInt(radiusStr);
        } catch (NumberFormatException e) {
            sendMessage(player, "unclaim-radius.invalid-radius");
            return;
        }
        
        if (radius < 1) {
            sendMessage(player, "unclaim-radius.invalid-radius");
            return;
        }
        
        if (radius > MAX_RADIUS) {
            sendMessage(player, "unclaim-radius.radius-too-large");
            return;
        }
        
        // Calculer les chunks à unclaim
        FLocation center = new FLocation(player.getLocation());
        List<FLocation> chunksToUnclaim = getChunksInRadius(center, radius, faction);
        
        if (chunksToUnclaim.isEmpty()) {
            sendMessage(player, "unclaim.failed", "{reason}", "Aucun chunk à vous dans ce rayon");
            return;
        }
        
        // Si radius > seuil, demander confirmation
        if (radius > CONFIRM_THRESHOLD) {
            PendingUnclaim pending = new PendingUnclaim(chunksToUnclaim, radius, System.currentTimeMillis());
            pendingUnclaims.put(player.getUniqueId(), pending);
            
            // Envoyer message de confirmation avec bouton cliquable
            String confirmMsg = plugin.getMessageManager().get("unclaim-radius.confirm",
                "{count}", String.valueOf(chunksToUnclaim.size()));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', confirmMsg));
            
            TextComponent confirmButton = new TextComponent(ChatColor.translateAlternateColorCodes('&', 
                plugin.getMessageManager().get("unclaim-radius.confirm-button")));
            confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/f unclaim confirm"));
            confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Cliquez pour confirmer l'unclaim").create()));
            player.spigot().sendMessage(confirmButton);
            
            // Expiration après 30 secondes
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                pendingUnclaims.remove(player.getUniqueId());
            }, 600L); // 30 secondes
            
            return;
        }
        
        // Sinon, exécuter directement
        executeRadiusUnclaim(player, faction, chunksToUnclaim, radius);
    }
    
    /**
     * Gère la confirmation d'un unclaim radius
     */
    private void handleConfirm(Player player, Faction faction) {
        PendingUnclaim pending = pendingUnclaims.remove(player.getUniqueId());
        
        if (pending == null) {
            sendMessage(player, "unclaim-radius.cancelled");
            return;
        }
        
        // Vérifier expiration (30 secondes)
        if (System.currentTimeMillis() - pending.timestamp > 30000) {
            sendMessage(player, "unclaim-radius.cancelled");
            return;
        }
        
        executeRadiusUnclaim(player, faction, pending.chunks, pending.radius);
    }
    
    /**
     * Exécute l'unclaim de plusieurs chunks
     */
    private void executeRadiusUnclaim(Player player, Faction faction, List<FLocation> chunks, int radius) {
        int count = 0;
        for (FLocation loc : chunks) {
            if (plugin.getClaimManager().unclaim(faction, loc)) {
                count++;
            }
        }
        
        if (count > 0) {
            plugin.getLogManager().log(faction.getId(), LogType.TERRITORY_UNCLAIM, player, null,
                count + " chunks (rayon " + radius + ")");
            sendMessage(player, "unclaim-radius.success",
                "{count}", String.valueOf(count),
                "{radius}", String.valueOf(radius));
        } else {
            sendMessage(player, "unclaim.failed", "{reason}", "Aucun chunk libéré");
        }
    }
    
    /**
     * Récupère tous les chunks dans un rayon appartenant à la faction
     */
    private List<FLocation> getChunksInRadius(FLocation center, int radius, Faction faction) {
        List<FLocation> chunks = new ArrayList<>();
        String world = center.getWorldName();
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                FLocation loc = new FLocation(world, center.getX() + dx, center.getZ() + dz);
                // Vérifier si ce chunk appartient à la faction
                Faction owner = plugin.getClaimManager().getFactionAt(loc);
                if (owner != null && owner.getId().equals(faction.getId())) {
                    chunks.add(loc);
                }
            }
        }
        
        return chunks;
    }
    
    @Override public String getName() { return "unclaim"; }
    @Override public String getDescription() { return "Retirer le claim du chunk actuel ou en rayon"; }
    @Override public String getUsage() { return "[radius]"; }
    
    /**
     * Classe interne pour stocker les unclaims en attente de confirmation
     */
    private static class PendingUnclaim {
        final List<FLocation> chunks;
        final int radius;
        final long timestamp;
        
        PendingUnclaim(List<FLocation> chunks, int radius, long timestamp) {
            this.chunks = chunks;
            this.radius = radius;
            this.timestamp = timestamp;
        }
    }
}
