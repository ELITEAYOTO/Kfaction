package me.krunsh.kfaction.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.managers.QuestManager;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.RewardDefinition;

/** /f level - niveau calculé exclusivement par complétion des quêtes fixes. */
public final class LevelCommand extends SubCommand {
    public LevelCommand(Kfaction plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayerOrWarn(sender);
        if (player == null) return;
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) {
            player.sendMessage("§cVous n'êtes pas dans une faction.");
            return;
        }
        QuestManager manager = plugin.getQuestManager();
        if (manager == null || !manager.isEnabled()) {
            player.sendMessage("§cLe système de progression est désactivé.");
            return;
        }

        List<QuestProgressView> views = manager.getQuestViews(faction);
        int completed = 0;
        for (QuestProgressView view : views) if (view.isCompleted()) completed++;
        int percent = views.isEmpty() ? 0 : completed * 100 / views.size();

        player.sendMessage("§6§m---------§r §6✦ Niveau Faction §6§m---------");
        player.sendMessage("");
        player.sendMessage("  §eFaction: §6" + faction.getName());
        player.sendMessage("  §eNiveau: §6§l" + faction.getLevel());
        player.sendMessage("  §eObjectifs: §a" + completed + "§7/§a"
                + views.size() + " §7(" + percent + "%)");
        player.sendMessage("");
        for (QuestProgressView view : views) {
            String status = view.isCompleted() ? "§a✓" : "§e"
                    + view.getProgress() + "/" + view.getRequired();
            player.sendMessage("    §7- §f"
                    + view.getDefinition().getDisplayName().replace("&", "§")
                    + " §7[" + status + "§7]");
        }

        LevelDefinition next = manager.getActiveConfig()
                .getLevel(faction.getLevel() + 1);
        if (next == null) {
            player.sendMessage("");
            player.sendMessage("  §6Niveau maximal configuré.");
        } else if (!next.getRewardsOnEnter().isEmpty()) {
            player.sendMessage("");
            player.sendMessage("  §e§lRécompenses du niveau "
                    + next.getNumber() + ":");
            for (RewardDefinition reward : next.getRewardsOnEnter()) {
                player.sendMessage("    §7→ "
                        + reward.getDescription().replace("&", "§"));
            }
        }
        player.sendMessage("");
        player.sendMessage("§6§m----------------------------------");
    }

    @Override public String getName() { return "level"; }
    @Override public String getDescription() { return "Voir le niveau de la faction"; }
    @Override public String getUsage() { return ""; }
}
