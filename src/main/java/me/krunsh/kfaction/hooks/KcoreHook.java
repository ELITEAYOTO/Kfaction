package me.krunsh.kfaction.hooks;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Hook pour Kcore (système de combat)
 * Fournit des informations sur les zones pour le combat
 */
public class KcoreHook {
    
    private final Kfaction plugin;
    
    public KcoreHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le hook
     */
    public void initialize() {
        // S'enregistrer auprès de Kcore comme fournisseur de zones
        // TODO: Implémenter l'intégration avec l'API Kcore
    }
    
    /**
     * Vérifie si une location est en zone protégée (safezone)
     * @param location La location
     * @return true si safezone
     */
    public boolean isSafeZone(Location location) {
        return plugin.getClaimManager().isSafezone(location);
    }
    
    /**
     * Vérifie si une location est en zone de combat (warzone)
     * @param location La location
     * @return true si warzone
     */
    public boolean isWarZone(Location location) {
        return plugin.getClaimManager().isWarzone(location);
    }
    
    /**
     * Vérifie si le PvP est autorisé entre deux joueurs
     * @param attacker L'attaquant
     * @param defender Le défenseur
     * @return true si autorisé
     */
    public boolean canPvP(Player attacker, Player defender) {
        return plugin.getTerritoryManager().canPvP(attacker, defender);
    }
    
    /**
     * Obtient le nom de la zone à une location
     * @param location La location
     * @return Le nom de la faction/zone
     */
    public String getZoneName(Location location) {
        Faction faction = plugin.getClaimManager().getFactionAt(location);
        return faction.getName();
    }
}
