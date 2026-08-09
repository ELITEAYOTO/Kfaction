package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;

import me.krunsh.kfaction.Kfaction;

/**
 * Namespace ergonomique /f auto ...
 *
 * Compatibilité:
 * - /f autoclaim
 * - /f ac
 *
 * Nouvelle syntaxe:
 * - /f auto claim
 */
public final class AutoCommand extends SubCommand {

    private final AutoClaimCommand autoClaim;

    public AutoCommand(
            Kfaction plugin
    ) {
        super(plugin);

        this.autoClaim =
                new AutoClaimCommand(
                        plugin
                );
    }

    @Override
    public void execute(
            CommandSender sender,
            String[] args
    ) {
        if (args == null
                || args.length == 0) {
            sender.sendMessage(
                    "§eUsage: §f/f auto claim"
            );
            return;
        }

        String action =
                args[0].toLowerCase(
                        java.util.Locale.ROOT
                );

        if ("claim".equals(action)
                || "autoclaim".equals(action)
                || "ac".equals(action)) {
            autoClaim.execute(
                    sender,
                    Arrays.copyOfRange(
                            args,
                            1,
                            args.length
                    )
            );
            return;
        }

        sender.sendMessage(
                "§cAction auto inconnue: §f"
                        + action
                        + "§c. Utilisez §e/f auto claim§c."
        );
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        List<String> result =
                new ArrayList<String>();

        if (args == null
                || args.length <= 1) {
            String partial =
                    args != null
                    && args.length == 1
                    && args[0] != null
                            ? args[0].toLowerCase(
                                    java.util.Locale.ROOT
                            )
                            : "";

            if ("claim".startsWith(partial)) {
                result.add(
                        "claim"
                );
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "auto";
    }

    @Override
    public String getDescription() {
        return "Actions automatiques de faction";
    }

    @Override
    public String getUsage() {
        return "<claim>";
    }
}
