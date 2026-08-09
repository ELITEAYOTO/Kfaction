package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.RewardDefinition;

/**
 * Façade LevelManager V2.
 *
 * L'ancienne XP est neutralisée.
 * La source réelle est ProgressionService via QuestManager.
 */
public final class LevelManager {

    private final Kfaction plugin;

    private final AtomicBoolean warnedXpMutation =
            new AtomicBoolean();

    private YamlConfiguration legacyLevelsConfig;

    private int progressBarLength = 20;

    private String barFilled = "▌";
    private String barEmpty = "▌";

    private String colorFilled = "§a";
    private String colorEmpty = "§7";

    public LevelManager(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();
    }

    /**
     * Charge uniquement le style d'affichage depuis config.yml.
     *
     * Le gameplay niveaux/quêtes/récompenses vit exclusivement dans
     * progression.yml.
     *
     * levels.yml n'est plus créé ni lu.
     */
    public void loadConfig() {
        legacyLevelsConfig = null;

        progressBarLength =
                Math.max(
                        5,
                        Math.min(
                                60,
                                plugin.getConfigManager()
                                        .getInt(
                                                "progression-ui.progress-bar.length",
                                                20
                                        )
                        )
                );

        barFilled =
                plugin.getConfigManager()
                        .getString(
                                "progression-ui.progress-bar.filled",
                                "▌"
                        );

        barEmpty =
                plugin.getConfigManager()
                        .getString(
                                "progression-ui.progress-bar.empty",
                                "▌"
                        );

        colorFilled =
                plugin.getConfigManager()
                        .getString(
                                "progression-ui.progress-bar.filled-color",
                                "&a"
                        )
                        .replace(
                                "&",
                                "§"
                        );

        colorEmpty =
                plugin.getConfigManager()
                        .getString(
                                "progression-ui.progress-bar.empty-color",
                                "&7"
                        )
                        .replace(
                                "&",
                                "§"
                        );

        me.krunsh.kfaction.utils.KfactionLogger.debug(
                plugin,
                "LevelManager V2: style progression chargé depuis config.yml."
        );
    }

    public boolean isEnabled() {
        return plugin.getQuestManager() != null
                && plugin.getQuestManager()
                        .isEnabled();
    }

    /**
     * L'XP n'est plus une condition de level-up.
     */
    @Deprecated
    public int addXp(
            Faction faction,
            int amount
    ) {
        if (warnedXpMutation
                .compareAndSet(
                        false,
                        true
                )) {
            plugin.getLogger().warning(
                    "Appel legacy LevelManager.addXp ignoré: "
                            + "le niveau dépend uniquement des quêtes fixes."
            );
        }

        return 0;
    }

    public boolean canLevelUp(
            Faction faction
    ) {
        /*
         * Le service transitionne immédiatement dès que le niveau est
         * complété. Il n'existe donc pas d'état manuel "can level up".
         */
        return false;
    }

    @Deprecated
    public int getXpRequired(
            int level
    ) {
        return 0;
    }

    @Deprecated
    public int getXpToNextLevel(
            Faction faction
    ) {
        return 0;
    }

    public int getProgressPercent(
            Faction faction
    ) {
        if (faction == null
                || plugin.getQuestManager() == null) {
            return 0;
        }

        ProgressionStatus status =
                plugin.getQuestManager()
                        .getStatus(faction);

        return status.getPercent();
    }

    public String getProgressBar(
            Faction faction
    ) {
        return getProgressBarForPercent(
                getProgressPercent(faction)
        );
    }

    public String getProgressBarForPercent(
            int percent
    ) {
        int safePercent =
                Math.max(
                        0,
                        Math.min(
                                100,
                                percent
                        )
                );

        int filled =
                Math.min(
                        progressBarLength,
                        Math.max(
                                0,
                                safePercent
                                        * progressBarLength
                                        / 100
                        )
                );

        StringBuilder bar =
                new StringBuilder(
                        colorFilled
                );

        for (int i = 0;
                i < progressBarLength;
                i++) {
            if (i == filled) {
                bar.append(
                        colorEmpty
                );
            }

            bar.append(
                    i < filled
                            ? barFilled
                            : barEmpty
            );
        }

        return bar.toString();
    }

    /**
     * Compatibilité binaire uniquement.
     *
     * @return toujours null: levels.yml n'est plus une source runtime.
     */
    @Deprecated
    public YamlConfiguration getLevelsConfig() {
        return null;
    }

    public int getMaxDefinedLevel() {
        ProgressionConfig config =
                plugin.getQuestManager() == null
                        ? null
                        : plugin.getQuestManager()
                                .getActiveConfig();

        return config == null
                ? 0
                : config.getMaxLevel();
    }

    public List<String> getRewardDescriptions(
            int level
    ) {
        List<String> descriptions =
                new ArrayList<String>();

        ProgressionConfig config =
                plugin.getQuestManager() == null
                        ? null
                        : plugin.getQuestManager()
                                .getActiveConfig();

        LevelDefinition definition =
                config == null
                        ? null
                        : config.getLevel(level);

        if (definition == null) {
            return descriptions;
        }

        for (RewardDefinition reward
                : definition.getRewardsOnEnter()) {
            descriptions.add(
                    reward.getDescription()
                            .replace(
                                    "&",
                                    "§"
                            )
            );
        }

        return descriptions;
    }
}
