package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.krunsh.kfaction.Kfaction;

/**
 * Commande admin séparée /kfaction pour la gestion staff
 * 
 * Usage:
 *   /kfaction reload - Recharge la configuration
 *   /kfaction debug [on|off] - Active/désactive le mode debug
 *   /kfaction bypass - Toggle le bypass admin
 *   /kfaction setpower <joueur> <power> - Définir le power d'un joueur
 *   /kfaction disband <faction> - Dissoudre une faction
 *   /kfaction forcejoin <joueur> <faction> - Forcer un joueur à rejoindre
 *   /kfaction forceleave <joueur> - Forcer un joueur à quitter
 *   /kfaction forceleader <joueur> - Forcer un joueur comme leader
 *   /kfaction setrole <joueur> <role> - Définir le rôle d'un joueur
 *   /kfaction inspect <faction> - Inspecter une faction
 *   /kfaction rename <faction> <nouveau_nom> - Renommer une faction
 *   /kfaction settag <faction> <tag> - Définir le tag d'une faction
 *   /kfaction setbalance <faction> <montant> - Définir le solde
 *   /kfaction lock <faction> - Verrouiller une faction
 *   /kfaction unlock <faction> - Déverrouiller une faction
 *   /kfaction teleport <faction> - Se téléporter au home de faction
 *   /kfaction claim [faction] - Claim admin
 *   /kfaction unclaim - Unclaim admin
 *   /kfaction help - Afficher l'aide
 * 
 * Permission: kfaction.admin
 */
public class KfactionAdminCommand implements CommandExecutor, TabCompleter {
    
    private final Kfaction plugin;
    private final AdminCommand delegateCommand;
    
    public KfactionAdminCommand(Kfaction plugin) {
        this.plugin = plugin;
        this.delegateCommand = new AdminCommand(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier la permission admin
        if (!sender.hasPermission("kfaction.admin")) {
            sender.sendMessage("§c✖ Tu n'as pas la permission d'utiliser cette commande.");
            return true;
        }
        
        // Supporte "/kfaction <cmd>" ET "/kfaction admin <cmd>" (rétrocompatibilité)
        String[] delegateArgs = args;
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            delegateArgs = new String[args.length - 1];
            System.arraycopy(args, 1, delegateArgs, 0, delegateArgs.length);
        }
        
        delegateCommand.execute(sender, delegateArgs);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("kfaction.admin")) {
            return new ArrayList<>();
        }
        
        // Supporte "/kfaction admin <cmd>" en tab-complétion
        String[] delegateArgs = args;
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            delegateArgs = new String[args.length - 1];
            System.arraycopy(args, 1, delegateArgs, 0, delegateArgs.length);
        }
        
        return delegateCommand.tabComplete(sender, delegateArgs);
    }
}
