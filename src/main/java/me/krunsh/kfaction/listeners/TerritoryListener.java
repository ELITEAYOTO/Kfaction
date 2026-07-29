package me.krunsh.kfaction.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.managers.ClaimManager.ClaimResult;

/**
 * Listener pour les changements de zone territoriale — optimisé pour 1000+ joueurs
 * 
 * Optimisations:
 * - Détection de changement de chunk par coordonnées arithmétiques (pas de getChunk())
 * - Config use-titles cachée
 * - ConcurrentHashMap au lieu de HashMap pour le lastFactionAt
 */
public class TerritoryListener implements Listener {
    
    private final Kfaction plugin;
    
    // Cache de la dernière faction par joueur pour détecter les changements
    private final Map<UUID, String> lastFactionAt;
    
    // Config cachée
    private boolean useTitles;
    
    public TerritoryListener(Kfaction plugin) {
        this.plugin = plugin;
        this.lastFactionAt = new ConcurrentHashMap<>();
        this.useTitles = true;
    }
    
    /**
     * Charge la config cachée. À appeler après initialize() du plugin.
     */
    public void loadConfig() {
        useTitles = plugin.getConfigManager().getBoolean("territory.use-titles", true);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Optimisation: détection de changement de chunk par bit-shift
        // Évite l'appel coûteux getChunk() qui peut charger un chunk non-chargé
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) 
            && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        
        Player player = event.getPlayer();
        FLocation toLoc = new FLocation(to);
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        // Auto-map si activé (avec throttle pour les serveurs chargés)
        if (fPlayer.isMapAutoUpdateEnabled()) {
            plugin.getMapManager().autoShowMap(player, toLoc);
        }
        
        // Auto-claim si activé
        if (fPlayer.isAutoClaimEnabled()) {
            handleAutoClaim(player, fPlayer, toLoc);
        }

        // Admin auto-claim si activé
        if (plugin.getClaimManager().isAdminAutoClaiming(player.getUniqueId())) {
            handleAdminAutoClaim(player, toLoc);
        }

        if (plugin.getClaimManager().isAdminAutoUnclaiming(player.getUniqueId())) {
            handleAdminAutoUnclaim(player, toLoc);
        }
        
        // Obtenir les factions (seul toLoc est nécessaire pour la comparaison — 
        // on compare avec le cache lastFactionAt au lieu de recréer fromLoc)
        Faction factionTo = plugin.getClaimManager().getFactionAt(toLoc);
        String factionToId = factionTo.getId();
        
        // Vérifier si on change de faction via le cache (évite de créer fromLoc + lookup)
        String lastId = lastFactionAt.put(player.getUniqueId(), factionToId);
        if (factionToId.equals(lastId)) {
            return;
        }
        
        // Zone change détecté — envoyer le message d'entrée
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
        
        if (useTitles) {
            sendTerritoryTitle(player, factionTo, playerFaction);
        } else {
            String enterMessage = plugin.getTerritoryManager().getZoneEnterMessage(factionTo, playerFaction);
            player.sendMessage(enterMessage);
        }
    }
    
    /**
     * Envoie un titre de territoire au joueur
     */
    private void sendTerritoryTitle(Player player, Faction factionAt, Faction playerFaction) {
        String title;
        String subtitle = "";
        
        if (factionAt.isWilderness()) {
            title = plugin.getMessageManager().get("territory.title.wilderness");
        } else if (factionAt.isSafezone()) {
            title = plugin.getMessageManager().get("territory.title.safezone");
            subtitle = plugin.getMessageManager().get("territory.subtitle.safezone");
        } else if (factionAt.isWarzone()) {
            title = plugin.getMessageManager().get("territory.title.warzone");
            subtitle = plugin.getMessageManager().get("territory.subtitle.warzone");
        } else {
            // Faction normale - couleur selon relation
            String colorCode = "&f";
            if (playerFaction != null) {
                Relation relation = playerFaction.getRelationTo(factionAt);
                colorCode = relation.getColorPrefix();
            }
            
            title = ChatColor.translateAlternateColorCodes('&', 
                colorCode + factionAt.getName());
            
            if (!factionAt.getDescription().isEmpty()) {
                subtitle = ChatColor.translateAlternateColorCodes('&',
                    "&7" + factionAt.getDescription());
            }
        }
        
        // Envoyer le titre (1.8 compatible)
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', title),
            ChatColor.translateAlternateColorCodes('&', subtitle)
        );
    }
    
    /**
     * Gère l'auto-claim admin lors du déplacement
     */
    private void handleAdminAutoClaim(Player player, FLocation location) {
        String type = plugin.getClaimManager().getAdminAutoClaimType(player.getUniqueId());
        if (type == null) return;

        // Ne pas reclaim si déjà du bon type
        Faction current = plugin.getClaimManager().getFactionAt(location);
        if (type.equals("warzone") && current.isWarzone()) return;
        if (type.equals("safezone") && current.isSafezone()) return;

        if (type.equals("warzone")) {
            plugin.getClaimManager().claimWarzone(location);
            player.sendMessage("§4[Auto-Claim] §7Warzone: " + location.getX() + ", " + location.getZ());
        } else {
            plugin.getClaimManager().claimSafezone(location);
            player.sendMessage("§a[Auto-Claim] §7Safezone: " + location.getX() + ", " + location.getZ());
        }
    }

    /** Gère l'unclaim staff automatique sans toucher aux autres propriétaires. */
    private void handleAdminAutoUnclaim(Player player, FLocation location) {
        String type = plugin.getClaimManager().getAdminAutoUnclaimType(player.getUniqueId());
        if (type == null || !plugin.getClaimManager().unclaimZone(type, location)) return;

        String factionId = type.equals("warzone") ? Faction.WARZONE_ID : Faction.SAFEZONE_ID;
        plugin.getLogManager().log(factionId, LogType.TERRITORY_UNCLAIM, player,
            "ADMIN AUTO " + type + " [" + location.getWorldName() + ", "
                + location.getX() + ", " + location.getZ() + "]");
        player.sendMessage("§c[Auto-Unclaim] §7" + type + ": "
            + location.getX() + ", " + location.getZ());
    }

    /**
     * Gère l'auto-claim lors du déplacement
     */
    private void handleAutoClaim(Player player, FPlayer fPlayer, FLocation location) {
        if (!fPlayer.hasFaction()) {
            fPlayer.setAutoClaimEnabled(false);
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            fPlayer.setAutoClaimEnabled(false);
            return;
        }
        
        // Vérifier la permission
        if (!faction.hasPermission(fPlayer.getUuid(), me.krunsh.kfaction.data.PermissionAction.CLAIM)) {
            plugin.getMessageManager().send(player, "general.no-permission");
            fPlayer.setAutoClaimEnabled(false);
            return;
        }
        
        // Tenter de claim
        ClaimResult result = plugin.getClaimManager().claim(faction, location);
        if (result.isSuccess()) {
            plugin.getMessageManager().send(player, "claim.claimed",
                "{x}", String.valueOf(location.getX()),
                "{z}", String.valueOf(location.getZ()));
        }
    }
    
    /**
     * Nettoie le cache pour un joueur déconnecté
     */
    public void cleanup(UUID uuid) {
        lastFactionAt.remove(uuid);
    }
}
