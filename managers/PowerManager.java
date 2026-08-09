package me.krunsh.kfaction.managers;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.policy.FactionPowerMath;
import me.krunsh.kfaction.policy.PowerBonusPolicy;
import me.krunsh.kfaction.utils.KfactionLogger;

/**
 * Gestionnaire du système de power.
 *
 * Lot25D:
 * - toutes les mutations de FPlayer se font sur le Bukkit main thread;
 * - aucun Player/permission Bukkit n'est lu depuis une tâche async;
 * - les bonus de permission augmentent réellement le plafond runtime;
 * - clés config/messages alignées sur les resources finales.
 */
public class PowerManager {

    private final Kfaction plugin;
    private BukkitTask regenTask;

    private double startPower;
    private double maxPower;
    private double minPower;
    private double powerPerKill;
    private double powerLossOnDeath;
    private double regenPerMinute;
    private boolean offlineRegen;

    private boolean permissionBonusesEnabled;
    private double bonusVip;
    private double bonusMvp;
    private double bonusLegend;

    public PowerManager(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();
        startRegenTask();

        KfactionLogger.debug(
                plugin,
                "PowerManager: regen="
                        + regenPerMinute
                        + "/min, offline="
                        + offlineRegen
        );
    }

    public void shutdown() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    public void loadConfig() {
        startPower =
                plugin.getConfigManager()
                        .getDouble(
                                "power.start",
                                10.0D
                        );

        maxPower =
                plugin.getConfigManager()
                        .getDouble(
                                "power.max",
                                10.0D
                        );

        minPower =
                plugin.getConfigManager()
                        .getDouble(
                                "power.min",
                                -10.0D
                        );

        powerPerKill =
                plugin.getConfigManager()
                        .getDouble(
                                "power.per-kill",
                                1.0D
                        );

        powerLossOnDeath =
                plugin.getConfigManager()
                        .getDouble(
                                "power.loss-per-death",
                                2.0D
                        );

        regenPerMinute =
                plugin.getConfigManager()
                        .getDouble(
                                "power.regen-per-minute",
                                0.1D
                        );

        offlineRegen =
                plugin.getConfigManager()
                        .getBoolean(
                                "power.offline-regen",
                                false
                        );

        permissionBonusesEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "power.bonus.enabled",
                                true
                        );

        bonusVip =
                plugin.getConfigManager()
                        .getDouble(
                                "power.bonus.vip",
                                0.0D
                        );

        bonusMvp =
                plugin.getConfigManager()
                        .getDouble(
                                "power.bonus.mvp",
                                0.0D
                        );

        bonusLegend =
                plugin.getConfigManager()
                        .getDouble(
                                "power.bonus.legend",
                                0.0D
                        );
    }

    // ============================================================
    // Player power
    // ============================================================

    public double getPlayerPower(
            UUID uuid
    ) {
        if (uuid == null) {
            return startPower;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                uuid
                        );

        if (fPlayer == null
                && Bukkit.isPrimaryThread()) {
            fPlayer =
                    plugin.getFPlayerManager()
                            .find(
                                    uuid
                            );
        }

        return fPlayer != null
                ? fPlayer.getPower()
                : startPower;
    }

    public double getPlayerMaxPower(
            UUID uuid
    ) {
        double base =
                maxPower;

        if (uuid == null
                || !Bukkit.isPrimaryThread()) {
            return base;
        }

        Player player =
                plugin.getServer()
                        .getPlayer(
                                uuid
                        );

        if (player != null) {
            base =
                    PowerBonusPolicy.apply(
                            base,
                            permissionBonusesEnabled,
                            player.hasPermission(
                                    "kfaction.power.bonus.vip"
                            ),
                            bonusVip,
                            player.hasPermission(
                                    "kfaction.power.bonus.mvp"
                            ),
                            bonusMvp,
                            player.hasPermission(
                                    "kfaction.power.bonus.legend"
                            ),
                            bonusLegend
                    );
        }

        return base;
    }

    public void setPlayerPower(
            UUID uuid,
            double power
    ) {
        if (uuid == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            final UUID capturedUuid =
                    uuid;

            final double capturedPower =
                    power;

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    setPlayerPower(
                                            capturedUuid,
                                            capturedPower
                                    );
                                }
                            }
                    );

            return;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .find(
                                uuid
                        );

        if (fPlayer == null) {
            return;
        }

        double effectiveMax =
                getPlayerMaxPower(
                        uuid
                );

        fPlayer.setPowerWithEffectiveMax(
                power,
                minPower,
                effectiveMax
        );

        plugin.getStorageManager()
                .markDirty(
                        fPlayer
                );
    }

    public void addPlayerPower(
            UUID uuid,
            double amount
    ) {
        setPlayerPower(
                uuid,
                getPlayerPower(uuid)
                        + amount
        );
    }

    public void removePlayerPower(
            UUID uuid,
            double amount
    ) {
        setPlayerPower(
                uuid,
                getPlayerPower(uuid)
                        - amount
        );
    }

    // ============================================================
    // Faction power
    // ============================================================

    public double getFactionPower(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0.0D;
        }

        double total =
                FactionPowerMath.start(
                        faction.getFactionPowerBoost()
                );

        for (UUID memberUuid
                : faction.getMembers()) {
            total =
                    FactionPowerMath.addMember(
                            total,
                            getPlayerPower(
                                    memberUuid
                            )
                    );
        }

        return total;
    }

    public double getFactionMaxPower(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0.0D;
        }

        double total =
                FactionPowerMath.start(
                        faction.getFactionPowerBoost()
                );

        for (UUID memberUuid
                : faction.getMembers()) {
            total =
                    FactionPowerMath.addMember(
                            total,
                            getPlayerMaxPower(
                                    memberUuid
                            )
                    );
        }

        return total;
    }

    // ============================================================
    // Events
    // ============================================================

    public void onPlayerKill(
            UUID killerUuid,
            UUID victimUuid
    ) {
        if (!Bukkit.isPrimaryThread()) {
            final UUID capturedKiller =
                    killerUuid;

            final UUID capturedVictim =
                    victimUuid;

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    onPlayerKill(
                                            capturedKiller,
                                            capturedVictim
                                    );
                                }
                            }
                    );

            return;
        }

        addPlayerPower(
                killerUuid,
                powerPerKill
        );

        Player killer =
                plugin.getServer()
                        .getPlayer(
                                killerUuid
                        );

        if (killer != null) {
            killer.sendMessage(
                    plugin.getMessageManager()
                            .get(
                                    "power.gain-kill",
                                    "{amount}",
                                    format(powerPerKill)
                            )
            );
        }
    }

    public void onPlayerDeath(
            UUID uuid
    ) {
        if (!Bukkit.isPrimaryThread()) {
            final UUID captured =
                    uuid;

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            new Runnable() {
                                @Override
                                public void run() {
                                    onPlayerDeath(
                                            captured
                                    );
                                }
                            }
                    );

            return;
        }

        removePlayerPower(
                uuid,
                powerLossOnDeath
        );

        Player player =
                plugin.getServer()
                        .getPlayer(
                                uuid
                        );

        if (player != null) {
            player.sendMessage(
                    plugin.getMessageManager()
                            .get(
                                    "power.loss-death",
                                    "{amount}",
                                    format(
                                            powerLossOnDeath
                                    )
                            )
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                uuid
                        );

        if (fPlayer != null
                && fPlayer.hasFaction()) {
            Faction faction =
                    plugin.getFactionManager()
                            .getFaction(
                                    fPlayer.getFactionId()
                            );

            if (faction != null
                    && plugin.getClaimManager()
                            .isRaidable(
                                    faction
                            )) {
                faction.broadcast(
                        plugin.getMessageManager()
                                .get(
                                        "power.faction-raidable"
                                )
                );
            }
        }
    }

    // ============================================================
    // Regeneration
    // ============================================================

    private void startRegenTask() {
        if (regenTask != null) {
            regenTask.cancel();
        }

        /*
         * IMPORTANT:
         * synchrone volontairement.
         *
         * regeneratePower lit les permissions Bukkit des joueurs et mute les
         * FPlayer; ces opérations appartiennent au main thread.
         */
        regenTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        regeneratePower();
                                    }
                                },
                                20L * 60L,
                                20L * 60L
                        );
    }

    private void regeneratePower() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Power regeneration must run on Bukkit primary thread"
            );
        }

        for (FPlayer fPlayer
                : plugin.getFPlayerManager()
                        .getAllPlayers()) {
            if (fPlayer == null) {
                continue;
            }

            if (!offlineRegen
                    && !fPlayer.isOnline()) {
                continue;
            }

            double current =
                    fPlayer.getPower();

            double effectiveMax =
                    getPlayerMaxPower(
                            fPlayer.getUuid()
                    );

            if (current >= effectiveMax) {
                continue;
            }

            double newPower =
                    Math.min(
                            current
                                    + regenPerMinute,
                            effectiveMax
                    );

            if (newPower > current) {
                fPlayer.setPowerWithEffectiveMax(
                        newPower,
                        minPower,
                        effectiveMax
                );

                plugin.getStorageManager()
                        .markDirty(
                                fPlayer
                        );
            }
        }
    }

    public void regenerate(
            UUID uuid,
            double amount
    ) {
        addPlayerPower(
                uuid,
                amount
        );
    }

    // ============================================================
    // Config getters
    // ============================================================

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

    private static String format(
            double value
    ) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }
}
