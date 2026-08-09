package me.krunsh.kfaction.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.RewardDefinition;

/**
 * /f level
 *
 * Vue synthétique de ProgressionService V2.
 */
public final class LevelCommand
        extends SubCommand {

    public LevelCommand(
            Kfaction plugin
    ) {
        super(plugin);
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        Player player =
                getPlayerOrWarn(sender);

        if (player == null) {
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player
                        );

        if (faction == null) {
            player.sendMessage(
                    "§cVous n'êtes pas dans une faction."
            );
            return;
        }

        QuestManager manager =
                plugin.getQuestManager();

        if (manager == null
                || !manager.isEnabled()) {
            player.sendMessage(
                    "§cLe système de progression est désactivé."
            );
            return;
        }

        ProgressionStatus status =
                manager.getStatus(faction);

        List<QuestProgressView> views =
                manager.getQuestViews(faction);

        player.sendMessage(
                "§6§m---------§r §6✦ Niveau Faction §6§m---------"
        );
        player.sendMessage("");

        player.sendMessage(
                "  §eFaction: §6"
                        + faction.getName()
        );

        player.sendMessage(
                "  §eNiveau: §6§l"
                        + faction.getLevel()
                        + "§r§7/"
                        + status.getMaxLevel()
        );

        player.sendMessage(
                "  §eTranche: §6"
                        + (status.getTierId() != null
                                ? status.getTierId()
                                : "inconnue")
        );

        player.sendMessage(
                "  §eObjectifs: §a"
                        + status.getCompletedQuests()
                        + "§7/§a"
                        + status.getTotalQuests()
                        + " §7("
                        + status.getPercent()
                        + "%)"
        );

        player.sendMessage(
                "  "
                        + plugin.getLevelManager()
                                .getProgressBarForPercent(
                                        status.getPercent()
                                )
        );

        player.sendMessage(
                "  §eÉtat: "
                        + healthColor(status)
                        + status.getHealth()
                                .name()
        );

        if (!status.getPendingRewards()
                .isEmpty()) {
            player.sendMessage("");
            player.sendMessage(
                    "  §c⚠ Progression suspendue: récompense(s) en attente."
            );

            for (String key
                    : status.getPendingRewards()) {
                player.sendMessage(
                        "    §8- §c"
                                + key
                );
            }

            if (status.getPendingTransition() != null) {
                player.sendMessage(
                        "  §7Transition: §f"
                                + status.getPendingTransition()
                );
            }
        }

        player.sendMessage("");

        for (QuestProgressView view : views) {
            String state =
                    view.isCompleted()
                            ? "§a✓"
                            : "§e"
                                    + view.getProgress()
                                    + "/"
                                    + view.getRequired();

            player.sendMessage(
                    "    §7- §f"
                            + render(view)
                            + " §7["
                            + state
                            + "§7]"
            );
        }

        LevelDefinition next =
                manager.getActiveConfig()
                        .getLevel(
                                faction.getLevel()
                                        + 1
                        );

        if (next == null) {
            player.sendMessage("");
            player.sendMessage(
                    "  §6Niveau maximal configuré."
            );

        } else if (!next.getRewardsOnEnter()
                .isEmpty()) {
            player.sendMessage("");
            player.sendMessage(
                    "  §e§lRécompenses du niveau "
                            + next.getNumber()
                            + ":"
            );

            for (RewardDefinition reward
                    : next.getRewardsOnEnter()) {
                player.sendMessage(
                        "    §7→ "
                                + reward.getDescription()
                                        .replace(
                                                "&",
                                                "§"
                                        )
                );
            }
        }

        player.sendMessage("");
        player.sendMessage(
                "§6§m----------------------------------"
        );
    }

    private static String render(
            QuestProgressView view
    ) {
        return view.getDefinition()
                .getDisplayName()
                .replace(
                        "&",
                        "§"
                )
                .replace(
                        "{progress}",
                        String.valueOf(
                                view.getProgress()
                        )
                )
                .replace(
                        "{amount}",
                        String.valueOf(
                                view.getRequired()
                        )
                )
                .replace(
                        "{remaining}",
                        String.valueOf(
                                view.getRemaining()
                        )
                )
                .replace(
                        "{percent}",
                        String.valueOf(
                                view.getPercent()
                        )
                );
    }

    private static String healthColor(
            ProgressionStatus status
    ) {
        switch (status.getHealth()) {
            case READY:
                return "§a";

            case BLOCKED_PENDING_REWARD:
                return "§e";

            case BLOCKED_STATE_MISMATCH:
                return "§c";

            default:
                return "§7";
        }
    }

    @Override
    public String getName() {
        return "level";
    }

    @Override
    public String getDescription() {
        return "Voir le niveau de la faction";
    }

    @Override
    public String getUsage() {
        return "";
    }
}
