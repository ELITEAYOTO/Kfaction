package me.krunsh.kfaction.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Bloque /sethome pour les ennemis dans les claims de factions 
 * qui ont débloqué la récompense Anti-Sethome (Niveau 2)
 * 
 * Hook Essentials: intercepte la commande pré-traitement
 */
public class AntiSethomeListener implements Listener {
    
    private final Kfaction plugin;
    
    // Config
    private boolean blockEnemies = true;
    private boolean blockNeutrals = false;
    
    public AntiSethomeListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "levels.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            blockEnemies = config.getBoolean("anti-sethome.block-enemies", true);
            blockNeutrals = config.getBoolean("anti-sethome.block-neutrals", false);
        }
    }
    
    /**
     * Intercepte les commandes /sethome et variantes d'Essentials
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();
        
        // Détecter les variantes de /sethome
        if (!isSethomeCommand(message)) return;
        
        // Admin bypass
        if (plugin.isBypassing(player.getUniqueId())) return;
        if (player.hasPermission("kfaction.admin.bypass")) return;
        
        // Vérifier le chunk actuel
        FLocation fLoc = new FLocation(player.getLocation());
        Faction claimOwner = plugin.getClaimManager().getFactionAt(fLoc);
        
        // Si le chunk n'est pas claim, laisser passer
        if (claimOwner == null || claimOwner.isWilderness()) return;
        
        // Factions système (safezone/warzone) - bloquer pour tous
        if (claimOwner.isSafezone() || claimOwner.isWarzone()) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas définir un home dans cette zone.");
            return;
        }
        
        // Vérifier si la faction propriétaire a l'anti-sethome
        if (!claimOwner.isAntiSethomeEnabled()) return;
        
        // Vérifier la relation du joueur avec la faction propriétaire
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
        
        // Si le joueur est dans la faction propriétaire, laisser passer
        if (playerFaction != null && playerFaction.getId().equals(claimOwner.getId())) return;
        
        // Déterminer la relation
        Relation relation;
        if (playerFaction == null) {
            relation = Relation.NEUTRAL;
        } else {
            relation = claimOwner.getRelationTo(playerFaction);
        }
        
        boolean shouldBlock = false;
        
        switch (relation) {
            case ENEMY:
                shouldBlock = blockEnemies;
                break;
            case NEUTRAL:
                shouldBlock = blockNeutrals;
                break;
            case ALLY:
            case TRUCE:
                shouldBlock = false; // Alliés et trêve = autorisés
                break;
            case MEMBER:
                shouldBlock = false; // Propre faction
                break;
            default:
                shouldBlock = blockNeutrals; // Par sécurité
                break;
        }
        
        if (shouldBlock) {
            event.setCancelled(true);
            player.sendMessage("§c§l✦ §cVous ne pouvez pas /sethome dans le territoire de §e" + 
                claimOwner.getName() + "§c! §7(Anti-Sethome activé)");
            
            plugin.debug("Anti-sethome: bloqué " + player.getName() + " dans le claim de " + 
                claimOwner.getName() + " (relation: " + relation + ")");
        }
    }
    
    /**
     * Détecte les variantes de /sethome
     */
    private boolean isSethomeCommand(String message) {
        // Essentials standard
        if (message.startsWith("/sethome")) return true;
        if (message.startsWith("/esethome")) return true;
        if (message.startsWith("/essentials:sethome")) return true;
        
        // Avec préfixe serveur
        if (message.matches("^/[a-z]*:?sethome.*")) return true;
        
        return false;
    }
}
