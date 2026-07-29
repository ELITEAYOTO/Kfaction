package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.PermissionAction;

public class WithdrawCommand extends SubCommand {
    public WithdrawCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "withdraw.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "withdraw.usage");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[0]);
            if (amount <= 0) {
                sendMessage(sender, "withdraw.invalid-amount");
                return;
            }
        } catch (NumberFormatException e) {
            sendMessage(sender, "withdraw.invalid-amount");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.WITHDRAW)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        if (faction.getBalance() < amount) {
            sendMessage(sender, "withdraw.not-enough-bank");
            return;
        }
        
        faction.withdraw(amount);
        plugin.getEconomyManager().deposit(player, amount);
        plugin.getLogManager().log(faction.getId(), LogType.ECONOMY_WITHDRAW, player, null, String.valueOf(amount));
        sendMessage(sender, "withdraw.success", "{amount}", String.valueOf(amount));
    }
    
    @Override public String getName() { return "withdraw"; }
    @Override public String getDescription() { return "Retirer de l'argent de la banque de faction"; }
    @Override public String getUsage() { return "<montant>"; }
}
