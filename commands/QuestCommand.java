package me.krunsh.kfaction.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.progression.ProgressionStatus;
import me.krunsh.kfaction.progression.QuestProgressView;

/**
 * /f quest
 *
 * Toutes les quêtes de la tranche verrouillée sont obligatoires.
 */
public final class QuestCommand
        extends SubCommand {

    public QuestCommand(
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
                    "§cLa progression faction est désactivée: "
                            + "progression.yml est absent, invalide ou désactivé."
            );
            return;
        }

        if (args.length > 0) {
            player.sendMessage(
                    "§eLes catégories sont uniquement visuelles: "
                            + "toutes les quêtes du niveau sont obligatoires."
            );
        }

        showQuests(
                player,
                faction,
                manager
        );
    }

    private void showQuests(
            Player player,
            Faction faction,
            QuestManager manager
    ) {
        List<QuestProgressView> views =
                manager.getQuestViews(faction);

        MemberTierDefinition tier =
                manager.getCurrentTier(faction);

        ProgressionStatus status =
                manager.getStatus(faction);

        player.sendMessage(
                "§6§m---------§r §6⚡ Quêtes Faction §6§m---------"
        );

        player.sendMessage("");

        player.sendMessage(
                "  §eNiveau: §6"
                        + faction.getLevel()
                        + " §7| §eTranche verrouillée: §6"
                        + (tier == null
                                ? "inconnue"
                                : tier.getDisplayName())
        );

        player.sendMessage(
                "  §eProgression globale: §6"
                        + status.getPercent()
                        + "%"
        );

        if (status.getHealth()
                == ProgressionStatus.Health
                        .BLOCKED_PENDING_REWARD) {
            player.sendMessage(
                    "  §c⚠ Les quêtes restent visibles mais leur progression "
                            + "est suspendue jusqu'à résolution des récompenses pending."
            );
        }

        if (status.getHealth()
                == ProgressionStatus.Health
                        .BLOCKED_STATE_MISMATCH) {
            player.sendMessage(
                    "  §c⚠ État de progression incohérent. "
                            + "Aucune mutation n'est autorisée."
            );
        }

        player.sendMessage("");

        int completed = 0;

        for (QuestProgressView view : views) {
            if (view.isCompleted()) {
                completed++;
            }

            String state =
                    view.isCompleted()
                            ? "§a✓"
                            : "§e"
                                    + view.getProgress()
                                    + "§7/§e"
                                    + view.getRequired();

            player.sendMessage(
                    "  §7- §f"
                            + render(view)
                            + " "
                            + progressBar(
                                    view.getPercent(),
                                    12
                            )
                            + " §7["
                            + state
                            + "§7]"
            );
        }

        if (views.isEmpty()) {
            player.sendMessage(
                    "  §cAucune définition compatible."
            );
        }

        player.sendMessage("");

        player.sendMessage(
                "  §7Progression: §e"
                        + completed
                        + "§7/§e"
                        + views.size()
                        + " §7objectifs obligatoires"
        );

        player.sendMessage(
                "§6§m----------------------------------"
        );
    }

    private String render(
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

    private String progressBar(
            int percent,
            int length
    ) {
        int filled =
                Math.max(
                        0,
                        Math.min(
                                length,
                                percent
                                        * length
                                        / 100
                        )
                );

        StringBuilder result =
                new StringBuilder(
                        "§a"
                );

        for (int i = 0;
                i < length;
                i++) {
            if (i == filled) {
                result.append(
                        "§7"
                );
            }

            result.append("▌");
        }

        return result.toString();
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return "quest";
    }

    @Override
    public String getDescription() {
        return "Voir les quêtes obligatoires";
    }

    @Override
    public String getUsage() {
        return "";
    }
}
