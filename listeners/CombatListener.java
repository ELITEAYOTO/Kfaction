package me.krunsh.kfaction.listeners;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listener pour le combat PvP entre joueurs
 */
public class CombatListener implements Listener {
    
    private final Kfaction plugin;
    
    public CombatListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Vérifier que c'est du PvP
        if (!(event.getEntity() instanceof Player)) return;
        
        Player defender = (Player) event.getEntity();
        Player attacker = getAttacker(event);
        
        if (attacker == null) return;
        if (attacker.equals(defender)) return;
        
        // Vérifier si le PvP est autorisé
        if (!plugin.getTerritoryManager().canPvP(attacker, defender)) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "pvp.cannot-attack");
            return;
        }
        
        // Appliquer le multiplicateur de dégâts
        double multiplier = plugin.getTerritoryManager().getDamageMultiplier(attacker, defender);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // Mettre à jour les stats du mort
        FPlayer victimFP = plugin.getFPlayerManager().getFPlayer(victim);
        victimFP.addDeath();
        
        // Retirer du power
        plugin.getPowerManager().onPlayerDeath(victim.getUniqueId());
        
        // Si tué par un joueur
        if (killer != null && !killer.equals(victim)) {
            FPlayer killerFP = plugin.getFPlayerManager().getFPlayer(killer);
            killerFP.addKill();
            
            // Ajouter du power au tueur
            plugin.getPowerManager().onPlayerKill(killer.getUniqueId(), victim.getUniqueId());
            
            // Marquer pour sauvegarde
            plugin.getStorageManager().markDirty(killerFP);
        }
        
        plugin.getStorageManager().markDirty(victimFP);
    }
    
    /**
     * Obtient le joueur attaquant depuis un événement de dégâts
     */
    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        
        // Projectile lancé par un joueur
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        
        return null;
    }
}
