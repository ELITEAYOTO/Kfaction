package me.krunsh.kfaction.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;

/**
 * Commande /f menu - Ouvre le menu principal de faction
 */
public class MenuCommand extends SubCommand {
    
    public MenuCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        
        // Vérifier si le joueur est dans une faction
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer == null || !fPlayer.hasFaction()) {
            sendMessage(sender, "error.no-faction");
            return;
        }
        
        // Vérifier si Kgui est disponible et initialisé
        if (plugin.getHookManager().hasKgui() && plugin.getHookManager().getKguiHook().isAvailable()) {
            boolean opened = plugin.getHookManager().getKguiHook().openFactionMenu(player);
            if (!opened) {
                // Fallback si le menu n'a pas pu être ouvert
                showFallbackMenu(player);
            }
        } else {
            // Fallback: afficher les commandes disponibles
            showFallbackMenu(player);
        }
    }
    
    /**
     * Affiche un menu textuel si Kgui n'est pas disponible
     */
    private void showFallbackMenu(Player player) {
        sendMessage(player, "menu.no-gui");
        player.sendMessage("§6§l━━━━━━ Menu Faction ━━━━━━");
        player.sendMessage("");
        player.sendMessage("§e/f show §7- Voir les infos de ta faction");
        player.sendMessage("§e/f map §7- Afficher la carte");
        player.sendMessage("§e/f home §7- Téléportation au home");
        player.sendMessage("§e/f warp list §7- Liste des warps");
        player.sendMessage("§e/f claim §7- Claim un chunk");
        player.sendMessage("§e/f invite <joueur> §7- Inviter un joueur");
        player.sendMessage("§e/f deposit <montant> §7- Déposer dans la banque");
        player.sendMessage("§e/f help §7- Aide complète");
        player.sendMessage("");
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    @Override public String getName() { return "menu"; }
    @Override public String getDescription() { return "Ouvre le menu de faction"; }
    @Override public String getUsage() { return ""; }
}
