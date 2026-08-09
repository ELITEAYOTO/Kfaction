package me.krunsh.kfaction.progression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Migration conservatrice de la progression.
 *
 * - schema 0/1 -> migration legacy complète;
 * - schema 2 -> schema 3 additive, sans reset de quête;
 * - schema 3 -> aucun travail.
 */
public final class ProgressionMigrationService {

    private final Kfaction plugin;
    private final File backupFolder;

    public ProgressionMigrationService(
            Kfaction plugin
    ) {
        this.plugin = plugin;

        String stamp =
                new SimpleDateFormat(
                        "yyyyMMdd-HHmmss",
                        Locale.ROOT
                ).format(
                        new Date()
                );

        this.backupFolder =
                new File(
                        plugin.getDataFolder(),
                        "migration-backups/progression-v3-"
                                + stamp
                );
    }

    public Result migrate(
            Faction faction,
            ProgressionConfig config
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return Result.SKIPPED;
        }

        FactionProgressState state =
                faction.getProgressionState();

        int schema =
                state.getSchemaVersion();

        if (schema
                >= FactionProgressState
                        .CURRENT_SCHEMA_VERSION) {
            return Result.SKIPPED;
        }

        /*
         * Schema 2 -> 3:
         * migration additive uniquement.
         *
         * NE PAS rappeler beginLevel(), sinon la progression courante serait
         * archivée/reset par erreur.
         */
        if (schema == 2) {
            FactionProgressState.Snapshot before =
                    state.snapshot();

            state.upgradeToCurrentSchema();

            if (!plugin.getStorageManager()
                    .saveFactionNow(faction)) {
                state.restore(before);

                plugin.getLogger().severe(
                        "Migration progression schema 2 -> 3 annulée pour "
                                + faction.getName()
                                + ": sauvegarde durable impossible."
                );

                return Result.SAVE_FAILED;
            }

            plugin.getLogger().info(
                    "Progression schema 2 -> 3 migrée sans reset pour "
                            + faction.getName()
            );

            return Result.MIGRATED;
        }

        if (config == null) {
            return Result.INCOMPATIBLE;
        }

        int oldLevel =
                faction.getLevel();

        int level =
                Math.max(
                        config.getStartingLevel(),
                        oldLevel
                );

        LevelDefinition levelDefinition =
                config.getLevel(level);

        MemberTierDefinition tier =
                config.findTier(
                        faction.getMemberCount()
                );

        if (levelDefinition == null
                || tier == null
                || levelDefinition.getTier(
                        tier.getId()
                ) == null) {
            plugin.getLogger().severe(
                    "Migration progression refusée pour "
                            + faction.getName()
                            + ": niveau "
                            + level
                            + " / "
                            + faction.getMemberCount()
                            + " membres non couverts par progression.yml."
            );

            return Result.INCOMPATIBLE;
        }

        if (!backupOriginal(faction)) {
            return Result.BACKUP_FAILED;
        }

        FactionProgressState.Snapshot before =
                state.snapshot();

        state.beginLevel(
                level,
                tier
        );

        faction.setLevel(level);

        TierLevelDefinition active =
                levelDefinition.getTier(
                        tier.getId()
                );

        LegacyProgressMapper.apply(
                faction.getActiveQuests(),
                faction.getCurrentXp(),
                active,
                state
        );

        if (!plugin.getStorageManager()
                .saveFactionNow(faction)) {
            state.restore(before);
            faction.setLevel(oldLevel);

            plugin.getLogger().severe(
                    "Migration progression legacy annulée pour "
                            + faction.getName()
                            + ": sauvegarde durable impossible."
            );

            return Result.SAVE_FAILED;
        }

        plugin.getLogger().info(
                "Progression legacy migrée pour "
                        + faction.getName()
                        + " (niveau "
                        + level
                        + ", tranche "
                        + tier.getId()
                        + "); anciennes données conservées pour rollback."
        );

        return Result.MIGRATED;
    }

    private boolean backupOriginal(
            Faction faction
    ) {
        File source =
                new File(
                        plugin.getDataFolder(),
                        "data/factions/"
                                + faction.getId()
                                + ".json"
                );

        if (!source.isFile()) {
            return true;
        }

        try {
            if (!backupFolder.exists()
                    && !backupFolder.mkdirs()) {
                throw new IOException(
                        "création du dossier impossible"
                );
            }

            Files.copy(
                    source.toPath(),
                    new File(
                            backupFolder,
                            source.getName()
                    ).toPath(),
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return true;

        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Backup pré-migration impossible pour "
                            + faction.getName()
                            + ": "
                            + exception.getMessage()
            );

            return false;
        }
    }

    public enum Result {
        MIGRATED,
        SKIPPED,
        INCOMPATIBLE,
        BACKUP_FAILED,
        SAVE_FAILED
    }
}
