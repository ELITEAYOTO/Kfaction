package me.krunsh.kfaction.managers;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.RewardType;
import me.krunsh.kfaction.progression.RewardDefinition;

/**
 * Gère l'application des récompenses de niveaux
 * Chaque fois qu'une faction gagne un niveau, les récompenses sont automatiquement appliquées
 */
public class RewardManager {
    
    private final Kfaction plugin;
    
    public RewardManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        plugin.getLogger().info("RewardManager initialisé");
    }

    /**
     * Applique une récompense progression v2. La clé d'idempotence et la
     * persistance sont gérées par QuestManager autour de cet appel.
     */
    public ApplyResult applyProgressionReward(Faction faction, int level,
            RewardDefinition definition) {
        if (faction == null || definition == null) return ApplyResult.INVALID;
        RewardType type = RewardType.fromConfigKey(definition.getType());
        if (type == null) return ApplyResult.INVALID;
        if (!applyReward(faction, type, definition.getValue(), level)) {
            return ApplyResult.UNAVAILABLE;
        }
        String description = definition.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            faction.broadcast("§6§l★ §eNiveau " + level + " §7→ "
                    + description.replace("&", "§"));
        }
        return ApplyResult.APPLIED;
    }
    
    /**
     * Applique toutes les récompenses pour un niveau donné
     * Ne réapplique pas les récompenses déjà données
     * @param faction La faction
     * @param level Le niveau atteint
     */
    public void applyLevelRewards(Faction faction, int level) {
        LevelManager levelManager = plugin.getLevelManager();
        if (levelManager == null) return;
        
        YamlConfiguration config = levelManager.getLevelsConfig();
        if (config == null) return;
        
        ConfigurationSection levelSection = config.getConfigurationSection("levels." + level);
        if (levelSection == null) {
            // Si au-delà du max défini, donner PlayerPoints par défaut
            if (level > levelManager.getMaxDefinedLevel()) {
                applyPlayerPointsReward(faction, level, 100);
            }
            return;
        }
        
        List<Map<?, ?>> rewards = levelSection.getMapList("rewards");
        int rewardIndex = 0;
        
        for (Map<?, ?> rewardMap : rewards) {
            String rewardKey = "level_" + level + "_reward_" + rewardIndex;
            
            // Ne pas réappliquer
            if (faction.hasAppliedReward(rewardKey)) {
                rewardIndex++;
                continue;
            }
            
            String typeStr = String.valueOf(rewardMap.get("type"));
            RewardType type = RewardType.fromConfigKey(typeStr);
            
            if (type == null) {
                plugin.getLogger().warning("Type de récompense inconnu: " + typeStr + " (niveau " + level + ")");
                rewardIndex++;
                continue;
            }
            
            Object valueObj = rewardMap.get("value");
            String description = rewardMap.containsKey("description") ? 
                String.valueOf(rewardMap.get("description")).replace("&", "§") : "";
            
            boolean applied = applyReward(faction, type, valueObj, level);
            
            if (applied) {
                faction.addAppliedReward(rewardKey);
                
                // Notification
                if (!description.isEmpty()) {
                    faction.broadcast("§6§l★ §eNiveau " + level + " §7→ " + description);
                }
                
                plugin.debug("Récompense appliquée: " + type.getConfigKey() + 
                    " (valeur: " + valueObj + ") pour " + faction.getName() + " (niv " + level + ")");
            }
            
            rewardIndex++;
        }
    }
    
    /**
     * Applique une récompense spécifique
     */
    private boolean applyReward(Faction faction, RewardType type, Object value, int level) {
        switch (type) {
            case FACTION_CHEST_UNLOCK:
                int chestSize = toInt(value, 36);
                if (faction.getChestSize() == 0) {
                    faction.setChestSize(chestSize);
                }
                return true;
                
            case FACTION_CHEST_RESIZE:
                int newSize = toInt(value, 54);
                faction.setChestSize(newSize);
                return true;
                
            case ANTI_SETHOME:
                faction.setAntiSethomeEnabled(toBool(value, true));
                return true;
                
            case WARPS_INCREASE:
                int warpsInc = toInt(value, 1);
                faction.addExtraWarps(warpsInc);
                return true;
                
            case FACTION_FLY:
                faction.setFactionFlyEnabled(toBool(value, true));
                return true;
                
            case CLAIM_DAMAGE_REDUCTION:
                // Future: réduction des dégâts en territoire
                return true;
                
            case MEMBERS_LIMIT_INCREASE:
                int membersInc = toInt(value, 1);
                faction.addExtraMembers(membersInc);
                return true;
                
            case FACTION_POWER_INCREASE:
                double powerInc = toDouble(value, 5.0);
                faction.addExtraPowerBoost(powerInc);
                // Appliquer directement au powerBoost total
                faction.setPowerBoost(faction.getPowerBoost() + powerInc);
                return true;
                
            case PLAYERPOINTS_LEADER:
                return applyPlayerPointsReward(faction, level, toInt(value, 100));
                
            default:
                return false;
        }
    }
    
    /**
     * Donne des PlayerPoints au chef de faction (via reflection pour éviter dépendance compile-time)
     */
    private boolean applyPlayerPointsReward(Faction faction, int level, int points) {
        if (faction.getLeader() == null) return false;
        
        try {
            org.bukkit.plugin.Plugin ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (ppPlugin != null && ppPlugin.isEnabled()) {
                // Appel via reflection: ppPlugin.getAPI().give(uuid, amount)
                java.lang.reflect.Method getApiMethod = ppPlugin.getClass().getMethod("getAPI");
                Object ppApi = getApiMethod.invoke(ppPlugin);
                
                if (ppApi != null) {
                    java.lang.reflect.Method giveMethod = ppApi.getClass().getMethod("give", java.util.UUID.class, int.class);
                    giveMethod.invoke(ppApi, faction.getLeader(), points);
                    
                    // Notifier le leader s'il est en ligne
                    Player leader = Bukkit.getPlayer(faction.getLeader());
                    if (leader != null && leader.isOnline()) {
                        leader.sendMessage("§a§l✦ §eVous avez reçu §a" + points + 
                            " PlayerPoints §een tant que chef de faction! §7(Niv." + level + ")");
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.debug("PlayerPoints non disponible: " + e.getMessage());
        }
        
        plugin.getLogger().warning("PlayerPoints non disponible pour donner " + points + 
            " points au chef de " + faction.getName());
        return false;
    }

    public enum ApplyResult {
        APPLIED,
        UNAVAILABLE,
        INVALID
    }
    
    // === Helpers de conversion ===
    
    private int toInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); } 
        catch (NumberFormatException e) { return fallback; }
    }
    
    private double toDouble(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } 
        catch (NumberFormatException e) { return fallback; }
    }
    
    private boolean toBool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
