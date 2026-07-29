package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.RewardDefinition;

/**
 * Façade de compatibilité. Les seuils XP et addXp sont neutralisés; la source
 * réelle est QuestManager/progression.yml.
 */
public final class LevelManager {
    private final Kfaction plugin;
    private final AtomicBoolean warnedXpMutation = new AtomicBoolean();
    private YamlConfiguration legacyLevelsConfig;
    private int progressBarLength = 20;
    private String barFilled = "▌";
    private String barEmpty = "▌";
    private String colorFilled = "§a";
    private String colorEmpty = "§7";

    public LevelManager(Kfaction plugin) {
        this.plugin = plugin;
    }

    public void initialize() { loadConfig(); }

    /** Charge uniquement le style historique de barre et conserve le YAML pour rollback. */
    public void loadConfig() {
        plugin.saveResource("levels.yml", false);
        java.io.File file = new java.io.File(plugin.getDataFolder(), "levels.yml");
        legacyLevelsConfig = YamlConfiguration.loadConfiguration(file);
        progressBarLength = legacyLevelsConfig.getInt(
                "settings.progressbar-length", 20);
        barFilled = legacyLevelsConfig.getString(
                "settings.progressbar-filled", "▌");
        barEmpty = legacyLevelsConfig.getString(
                "settings.progressbar-empty", "▌");
        colorFilled = legacyLevelsConfig.getString(
                "settings.progressbar-color-filled", "&a").replace("&", "§");
        colorEmpty = legacyLevelsConfig.getString(
                "settings.progressbar-color-empty", "&7").replace("&", "§");
    }

    public boolean isEnabled() {
        return plugin.getQuestManager() != null
                && plugin.getQuestManager().isEnabled();
    }

    /** L'XP n'est plus une condition de level-up. */
    @Deprecated
    public int addXp(Faction faction, int amount) {
        if (warnedXpMutation.compareAndSet(false, true)) {
            plugin.getLogger().warning("Appel legacy LevelManager.addXp ignoré: "
                    + "le niveau dépend uniquement des quêtes fixes.");
        }
        return 0;
    }

    public boolean canLevelUp(Faction faction) {
        // QuestManager effectue immédiatement la transition atomique.
        return false;
    }

    /** Conservé pour compatibilité binaire; aucune XP n'est requise. */
    public int getXpRequired(int level) { return 0; }
    public int getXpToNextLevel(Faction faction) { return 0; }

    public int getProgressPercent(Faction faction) {
        if (faction == null || plugin.getQuestManager() == null) return 0;
        List<QuestProgressView> views =
                plugin.getQuestManager().getQuestViews(faction);
        if (views.isEmpty()) return 0;
        double total = 0D;
        for (QuestProgressView view : views) total += view.getPercent();
        return Math.max(0, Math.min(100, (int) Math.floor(total / views.size())));
    }

    public String getProgressBar(Faction faction) {
        int percent = getProgressPercent(faction);
        int filled = Math.min(progressBarLength,
                Math.max(0, percent * progressBarLength / 100));
        StringBuilder bar = new StringBuilder(colorFilled);
        for (int i = 0; i < progressBarLength; i++) {
            if (i == filled) bar.append(colorEmpty);
            bar.append(i < filled ? barFilled : barEmpty);
        }
        return bar.toString();
    }

    /** YAML historique exposé seulement pour compatibilité/rollback. */
    public YamlConfiguration getLevelsConfig() {
        return legacyLevelsConfig;
    }

    public int getMaxDefinedLevel() {
        ProgressionConfig config = plugin.getQuestManager() == null
                ? null : plugin.getQuestManager().getActiveConfig();
        return config == null ? 0 : config.getMaxLevel();
    }

    public List<String> getRewardDescriptions(int level) {
        List<String> descriptions = new ArrayList<String>();
        ProgressionConfig config = plugin.getQuestManager() == null
                ? null : plugin.getQuestManager().getActiveConfig();
        LevelDefinition definition = config == null ? null : config.getLevel(level);
        if (definition == null) return descriptions;
        for (RewardDefinition reward : definition.getRewardsOnEnter()) {
            descriptions.add(reward.getDescription().replace("&", "§"));
        }
        return descriptions;
    }
}
