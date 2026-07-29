package me.krunsh.kfaction.listeners;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Gère le /f fly - Vol dans le territoire de faction
 * 
 * Désactive automatiquement:
 * - Quand le joueur quitte son territoire
 * - Quand le joueur est en combat
 * - Quand le joueur se déconnecte
 * - Protection anti-chute temporaire
 */
public class FlyListener implements Listener {
    
    private final Kfaction plugin;
    
    // Joueurs avec le fly faction actif
    private final Set<UUID> flyingPlayers = new HashSet<>();
    
    // Joueurs avec protection anti-chute active
    private final Set<UUID> fallProtected = new HashSet<>();
    
    // Config
    private boolean disableInCombat = true;
    private boolean disableOutTerritory = true;
    private boolean preventFallDamage = true;
    private int fallProtectionTicks = 60;
    private float flySpeed = 1.0f;
    
    public FlyListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "levels.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            disableInCombat = config.getBoolean("fly.disable-in-combat", true);
            disableOutTerritory = config.getBoolean("fly.disable-out-territory", true);
            preventFallDamage = config.getBoolean("fly.prevent-fall-damage", true);
            fallProtectionTicks = config.getInt("fly.fall-protection-ticks", 60);
            flySpeed = (float) config.getDouble("fly.speed", 1.0);
        }
    }
    
    /**
     * Active le fly pour un joueur
     * @param player Le joueur
     * @return true si activé avec succès
     */
    public boolean enableFly(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) {
            player.sendMessage("§cVous n'êtes pas dans une faction.");
            return false;
        }
        
        // Vérifier que la faction a débloqué le fly
        if (!faction.isFactionFlyEnabled()) {
            player.sendMessage("§cVotre faction n'a pas encore débloqué le /f fly! §7(Niveau 4 requis)");
            return false;
        }
        
        // Vérifier la permission faction
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.FLY)) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser le fly.");
            return false;
        }
        
        // Vérifier que le joueur est dans le territoire de sa faction
        if (disableOutTerritory) {
            FLocation fLoc = new FLocation(player.getLocation());
            Faction claimOwner = plugin.getClaimManager().getFactionAt(fLoc);
            if (claimOwner == null || !claimOwner.getId().equals(faction.getId())) {
                player.sendMessage("§cVous devez être dans votre territoire pour activer le fly.");
                return false;
            }
        }
        
        // Activer
        player.setAllowFlight(true);
        player.setFlying(true);
        if (flySpeed != 1.0f) {
            player.setFlySpeed(0.1f * flySpeed); // Default MC fly speed = 0.1
        }
        flyingPlayers.add(player.getUniqueId());
        
        player.sendMessage("§a✈ Fly activé! §7(Sera désactivé hors de votre territoire)");
        return true;
    }
    
    /**
     * Désactive le fly pour un joueur
     */
    public void disableFly(Player player, String reason) {
        if (!flyingPlayers.remove(player.getUniqueId())) return;
        
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            // Protection anti-chute
            if (preventFallDamage && player.isFlying()) {
                fallProtected.add(player.getUniqueId());
                
                // Retirer la protection après délai
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    fallProtected.remove(player.getUniqueId());
                }, fallProtectionTicks);
            }
            
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setFlySpeed(0.1f); // Reset à la valeur par défaut
        }
        
        if (reason != null && !reason.isEmpty()) {
            player.sendMessage("§c✈ Fly désactivé! §7(" + reason + ")");
        }
    }
    
    /**
     * Toggle le fly
     */
    public boolean toggleFly(Player player) {
        if (flyingPlayers.contains(player.getUniqueId())) {
            disableFly(player, "Désactivé manuellement");
            return false;
        } else {
            return enableFly(player);
        }
    }
    
    /**
     * Vérifie si un joueur utilise le fly faction
     */
    public boolean isFlying(UUID playerId) {
        return flyingPlayers.contains(playerId);
    }
    
    /**
     * Détection de sortie du territoire
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!disableOutTerritory) return;
        
        // Optimisation: ne vérifier que les changements de chunk
        if (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4 
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!flyingPlayers.contains(player.getUniqueId())) return;
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) {
            disableFly(player, "Vous n'êtes plus dans une faction");
            return;
        }
        
        FLocation fLoc = new FLocation(event.getTo());
        Faction claimOwner = plugin.getClaimManager().getFactionAt(fLoc);
        
        // Désactiver si hors du territoire de la faction
        if (claimOwner == null || !claimOwner.getId().equals(faction.getId())) {
            disableFly(player, "Vous avez quitté votre territoire");
        }
    }
    
    /**
     * Désactiver le fly en combat
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!disableInCombat) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        if (flyingPlayers.contains(player.getUniqueId())) {
            // Vérifier si c'est un dégât de combat (pas chute, feu, etc.)
            switch (event.getCause()) {
                case ENTITY_ATTACK:
                case ENTITY_EXPLOSION:
                case PROJECTILE:
                case MAGIC:
                case POISON:
                case THORNS:
                    disableFly(player, "En combat!");
                    break;
                case FALL:
                    // Protéger de la chute si protection active
                    if (fallProtected.contains(player.getUniqueId())) {
                        event.setCancelled(true);
                        fallProtected.remove(player.getUniqueId());
                    }
                    break;
                default:
                    break;
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL 
                   && fallProtected.contains(player.getUniqueId())) {
            // Protection anti-chute sans fly actif
            event.setCancelled(true);
            fallProtected.remove(player.getUniqueId());
        }
    }
    
    /**
     * Désactiver le fly à la déconnexion
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (flyingPlayers.remove(uuid)) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
                player.setFlySpeed(0.1f);
            }
        }
        fallProtected.remove(uuid);
    }
    
    /**
     * Désactiver le fly lors de téléportation hors territoire
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!disableOutTerritory) return;
        
        Player player = event.getPlayer();
        if (!flyingPlayers.contains(player.getUniqueId())) return;
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) {
            disableFly(player, "Vous n'êtes plus dans une faction");
            return;
        }
        
        FLocation fLoc = new FLocation(event.getTo());
        Faction claimOwner = plugin.getClaimManager().getFactionAt(fLoc);
        
        if (claimOwner == null || !claimOwner.getId().equals(faction.getId())) {
            disableFly(player, "Téléportation hors territoire");
        }
    }
    
    /**
     * Désactive le fly de tous les joueurs (pour shutdown)
     */
    public void disableAll() {
        for (UUID uuid : new HashSet<>(flyingPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                disableFly(player, null);
            }
        }
        flyingPlayers.clear();
        fallProtected.clear();
    }
}
