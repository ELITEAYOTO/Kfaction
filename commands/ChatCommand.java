package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.FPlayer.ChatMode;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand extends SubCommand {
    public ChatCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "chat.not-in-faction");
            return;
        }
        
        ChatMode currentMode = fPlayer.getChatMode();
        ChatMode newMode;
        
        if (args.length > 0) {
            String modeArg = args[0].toLowerCase();
            // Support des alias courts
            switch (modeArg) {
                case "f":
                case "fac":
                case "faction":
                    newMode = ChatMode.FACTION;
                    break;
                case "p":
                case "pub":
                case "public":
                    newMode = ChatMode.PUBLIC;
                    break;
                case "a":
                case "ally":
                case "allies":
                case "allie":
                    newMode = ChatMode.ALLY;
                    break;
                case "t":
                case "truce":
                case "treve":
                    newMode = ChatMode.TRUCE;
                    break;
                default:
                    sendMessage(sender, "chat.invalid-mode");
                    return;
            }
        } else {
            // Toggle entre PUBLIC et FACTION
            newMode = (currentMode == ChatMode.PUBLIC) ? ChatMode.FACTION : ChatMode.PUBLIC;
        }
        
        fPlayer.setChatMode(newMode);
        sendMessage(sender, "chat.mode-changed", "{mode}", newMode.name());
    }
    
    @Override public String getName() { return "chat"; }
    @Override public String getDescription() { return "Basculer le mode de chat faction"; }
    @Override public String getUsage() { return "[mode]"; }
}
