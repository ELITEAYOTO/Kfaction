package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.PermissionAction;

public class DepositCommand extends SubCommand {
    public DepositCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "deposit.not-in-faction");
            return;
        }
        
        if (args.length < 1) {
            sendMessage(sender, "deposit.usage");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[0]);
            if (amount <= 0) {
                sendMessage(sender, "deposit.invalid-amount");
                return;
            }
        } catch (NumberFormatException e) {
            sendMessage(sender, "deposit.invalid-amount");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.DEPOSIT)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        if (!plugin.getEconomyManager().has(player, amount)) {
            sendMessage(sender, "deposit.not-enough-money");
            return;
        }
        
        plugin.getEconomyManager().withdraw(player, amount);
        faction.deposit(amount);
        plugin.getLogManager().log(faction.getId(), LogType.ECONOMY_DEPOSIT, player, null, String.valueOf(amount));
        sendMessage(sender, "deposit.success", "{amount}", String.valueOf(amount));
    }
    
    @Override public String getName() { return "deposit"; }
    @Override public String getDescription() { return "Déposer de l'argent dans la banque de faction"; }
    @Override public String getUsage() { return "<montant>"; }
}
