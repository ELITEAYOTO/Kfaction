package me.krunsh.kfaction.managers;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.policy.FactionPowerMath;
import me.krunsh.kfaction.policy.PowerBonusPolicy;

/**
 * Gestionnaire du système de power
 * Gère la régénération automatique et les modifications de power
 */
public class PowerManager {
    
    private final Kfaction plugin;
    private BukkitTask regenTask;
    
    // Configuration (cachée pour performance)
    private double startPower;
    private double maxPower;
    private double minPower;
    private double powerPerKill;
    private double powerLossOnDeath;
    private double regenPerMinute;
    private boolean offlineRegen;
    
    // Bonus de permission (cachés pour éviter des appels à getDouble sur chaque calcul de power)
    private boolean permissionBonusesEnabled;
    private double bonusVip;
    private double bonusMvp;
    private double bonusLegend;
    
    public PowerManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le manager et lance la régénération
     */
    public void initialize() {
        loadConfig();
        startRegenTask();
        plugin.getLogger().info("PowerManager initialisé (regen: " + regenPerMinute + "/min)");
    }
    
    /**
     * Arrête le manager
     */
    public void shutdown() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }
    
    /**
     * Recharge la configuration
     */
    public void loadConfig() {
        startPower = plugin.getConfigManager().getDouble("power.start", 10.0);
        maxPower = plugin.getConfigManager().getDouble("power.max", 10.0);
        minPower = plugin.getConfigManager().getDouble("power.min", -10.0);
        powerPerKill = plugin.getConfigManager().getDouble("power.per-kill", 1.0);
        powerLossOnDeath = plugin.getConfigManager().getDouble("power.loss-per-death",
            plugin.getConfigManager().getDouble("power.loss-on-death", 2.0));
        regenPerMinute = plugin.getConfigManager().getDouble("power.regen-per-minute", 0.2);
        offlineRegen = plugin.getConfigManager().getBoolean("power.offline-regen", false);
        // Aucun bonus implicite : seuls ceux réellement configurés sont appliqués.
        permissionBonusesEnabled = plugin.getConfigManager().getBoolean(
            "power.bonus.enabled", true);
        bonusVip = plugin.getConfigManager().getDouble("power.bonus.vip", 0.0);
        bonusMvp = plugin.getConfigManager().getDouble("power.bonus.mvp", 0.0);
        bonusLegend = plugin.getConfigManager().getDouble("power.bonus.legend", 0.0);
    }
    
    // === Power joueur ===
    
    /**
     * Obtient le power d'un joueur
     * @param uuid UUID du joueur
     * @return Le power actuel
     */
    public double getPlayerPower(UUID uuid) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(uuid);
        return fPlayer != null ? fPlayer.getPower() : startPower;
    }
    
    /**
     * Obtient le power maximum d'un joueur
     * Peut être modifié par des permissions ou bonus
     * @param uuid UUID du joueur
     * @return Le power maximum
     */
    public double getPlayerMaxPower(UUID uuid) {
        // La source de vérité du maximum de base est power.max. Une ancienne
        // valeur sérialisée ne doit pas figer toutes les factions à 10.
        double base = maxPower;
        
        // Appliquer les bonus de permission (valeurs cachées au loadConfig)
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            base = PowerBonusPolicy.apply(base, permissionBonusesEnabled,
                player.hasPermission("kfaction.power.bonus.vip"), bonusVip,
                player.hasPermission("kfaction.power.bonus.mvp"), bonusMvp,
                player.hasPermission("kfaction.power.bonus.legend"), bonusLegend);
        }
        
        return base;
    }
    
    /**
     * Modifie le power d'un joueur
     * @param uuid UUID du joueur
     * @param power Nouveau power
     */
    public void setPlayerPower(UUID uuid, double power) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(uuid);
        if (fPlayer == null) return;
        
        double max = getPlayerMaxPower(uuid);
        power = Math.max(minPower, Math.min(power, max));
        fPlayer.setPower(power);
        plugin.getStorageManager().markDirty(fPlayer);
    }
    
    /**
     * Ajoute du power à un joueur
     * @param uuid UUID du joueur
     * @param amount Quantité à ajouter
     */
    public void addPlayerPower(UUID uuid, double amount) {
        double current = getPlayerPower(uuid);
        setPlayerPower(uuid, current + amount);
    }
    
    /**
     * Retire du power à un joueur
     * @param uuid UUID du joueur
     * @param amount Quantité à retirer
     */
    public void removePlayerPower(UUID uuid, double amount) {
        double current = getPlayerPower(uuid);
        setPlayerPower(uuid, current - amount);
    }
    
    // === Power faction ===
    
    /**
     * Calcule le power total d'une faction
     * @param faction La faction
     * @return Le power total (somme des membres + boost)
     */
    public double getFactionPower(Faction faction) {
        if (faction == null || faction.isSystemFaction()) {
            return 0;
        }
        
        double total = FactionPowerMath.start(faction.getFactionPowerBoost());
        
        for (UUID memberUuid : faction.getMembers()) {
            total = FactionPowerMath.addMember(total, getPlayerPower(memberUuid));
        }
        
        return total;
    }
    
    /**
     * Calcule le power maximum d'une faction
     * @param faction La faction
     * @return Le power max total
     */
    public double getFactionMaxPower(Faction faction) {
        if (faction == null || faction.isSystemFaction()) {
            return 0;
        }
        
        double total = FactionPowerMath.start(faction.getFactionPowerBoost());
        
        for (UUID memberUuid : faction.getMembers()) {
            total = FactionPowerMath.addMember(total, getPlayerMaxPower(memberUuid));
        }
        
        return total;
    }
    
    // === Événements ===
    
    /**
     * Appelé quand un joueur fait un kill
     * @param killerUuid UUID du tueur
     * @param victimUuid UUID de la victime
     */
    public void onPlayerKill(UUID killerUuid, UUID victimUuid) {
        // Ajouter power au tueur
        addPlayerPower(killerUuid, powerPerKill);
        
        // Notifier
        Player killer = plugin.getServer().getPlayer(killerUuid);
        if (killer != null) {
            String msg = plugin.getMessageManager().get("power.gain-kill")
                    .replace("{amount}", String.format("%.1f", powerPerKill));
            killer.sendMessage(msg);
        }
    }
    
    /**
     * Appelé quand un joueur meurt
     * @param uuid UUID du joueur mort
     */
    public void onPlayerDeath(UUID uuid) {
        // Retirer power
        removePlayerPower(uuid, powerLossOnDeath);
        
        // Notifier
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            String msg = plugin.getMessageManager().get("power.loss-death")
                    .replace("{amount}", String.format("%.1f", powerLossOnDeath));
            player.sendMessage(msg);
        }
        
        // Vérifier si la faction devient raidable
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(uuid);
        if (fPlayer != null && fPlayer.hasFaction()) {
            Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
            if (faction != null && plugin.getClaimManager().isRaidable(faction)) {
                // Notifier la faction
                faction.broadcast(plugin.getMessageManager().get("power.faction-raidable"));
            }
        }
    }
    
    // === Régénération ===
    
    /**
     * Lance la tâche de régénération automatique
     */
    private void startRegenTask() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        
        // Exécuter toutes les minutes
        regenTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            regeneratePower();
        }, 20L * 60, 20L * 60); // Toutes les 60 secondes
    }
    
    /**
     * Régénère le power de tous les joueurs
     */
    private void regeneratePower() {
        for (FPlayer fPlayer : plugin.getFPlayerManager().getAllPlayers()) {
            if (!offlineRegen && !fPlayer.isOnline()) {
                continue;
            }
            
            if (fPlayer.isAtMaxPower()) {
                continue;
            }
            
            double current = fPlayer.getPower();
            double max = getPlayerMaxPower(fPlayer.getUuid());
            double newPower = Math.min(current + regenPerMinute, max);
            
            if (newPower > current) {
                fPlayer.setPower(newPower);
                plugin.getStorageManager().markDirty(fPlayer);
            }
        }
    }
    
    /**
     * Régénère manuellement le power d'un joueur
     * @param uuid UUID du joueur
     * @param amount Quantité à régénérer
     */
    public void regenerate(UUID uuid, double amount) {
        addPlayerPower(uuid, amount);
    }
    
    // === Getters config ===
    
    public double getStartPower() {
        return startPower;
    }
    
    public double getMaxPower() {
        return maxPower;
    }
    
    public double getMinPower() {
        return minPower;
    }
    
    public double getPowerPerKill() {
        return powerPerKill;
    }
    
    public double getPowerLossOnDeath() {
        return powerLossOnDeath;
    }
    
    public double getRegenPerMinute() {
        return regenPerMinute;
    }
}
