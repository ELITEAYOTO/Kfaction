package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Commande /f bank - Affiche le solde de la banque de faction
 */
public class BankCommand extends SubCommand {
    
    public BankCommand(Kfaction plugin) { 
        super(plugin); 
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        if (player == null) return;
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "bank.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        double balance = faction.getBank();
        sendMessage(sender, "bank.balance", 
            "{faction}", faction.getName(),
            "{balance}", formatMoney(balance));
    }
    
    /**
     * Formate un montant avec séparateurs de milliers
     */
    private String formatMoney(double amount) {
        if (amount == (long) amount) {
            return String.format("%,d", (long) amount);
        }
        return String.format("%,.2f", amount);
    }
    
    @Override public String getName() { return "bank"; }
    @Override public String getDescription() { return "Voir le solde de la banque de faction"; }
    @Override public String getUsage() { return ""; }
}
