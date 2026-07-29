package me.krunsh.kfaction.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Hook pour Kclassement (système de F-Top)
 * Fournit les données de valeur des factions
 */
public class KclassementHook {
    
    private final Kfaction plugin;
    
    public KclassementHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le hook
     */
    public void initialize() {
        // TODO: S'enregistrer comme provider de classement F-Top
    }
    
    /**
     * Calcule la valeur totale d'une faction
     * @param faction La faction
     * @return La valeur totale
     */
    public double calculateFactionValue(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return 0;
        
        double value = 0;
        
        // Valeur de la banque
        value += faction.getBank();
        
        // Valeur du power
        value += plugin.getPowerManager().getFactionPower(faction) * 
                 plugin.getConfigManager().getDouble("ftop.power-multiplier", 100);
        
        // Valeur des claims
        value += faction.getClaimCount() * 
                 plugin.getConfigManager().getDouble("ftop.claim-value", 500);
        
        // Valeur des membres
        value += faction.getMemberCount() *
                 plugin.getConfigManager().getDouble("ftop.member-value", 1000);
        
        // TODO: Valeur des spawners (nécessite Kspawners)
        // TODO: Valeur des blocs (nécessite scan de chunks)
        
        return value;
    }
    
    /**
     * Obtient le classement des factions par valeur
     * @param limit Nombre maximum de factions
     * @return Liste ordonnée par valeur décroissante
     */
    public List<Map.Entry<Faction, Double>> getTopFactions(int limit) {
        Map<Faction, Double> values = new HashMap<>();
        
        for (Faction faction : plugin.getFactionManager().getPlayerFactions()) {
            values.put(faction, calculateFactionValue(faction));
        }
        
        // Trier par valeur décroissante
        List<Map.Entry<Faction, Double>> sorted = new ArrayList<>(values.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        // Limiter le nombre
        if (sorted.size() > limit) {
            sorted = sorted.subList(0, limit);
        }
        
        return sorted;
    }
    
    /**
     * Obtient le rang d'une faction
     * @param faction La faction
     * @return Le rang (1-based) ou -1 si non classée
     */
    public int getFactionRank(Faction faction) {
        if (faction == null || faction.isSystemFaction()) return -1;
        
        double targetValue = calculateFactionValue(faction);
        int rank = 1;
        
        for (Faction other : plugin.getFactionManager().getPlayerFactions()) {
            if (!other.getId().equals(faction.getId())) {
                if (calculateFactionValue(other) > targetValue) {
                    rank++;
                }
            }
        }
        
        return rank;
    }
}
