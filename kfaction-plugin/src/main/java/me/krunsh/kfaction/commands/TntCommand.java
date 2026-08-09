package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.PermissionAction;

/**
 * Commande /f tnt - Gestion de la banque de TNT
 * Sous-commandes: deposit, withdraw, info
 */
public class TntCommand extends SubCommand {
    
    public TntCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        // Vérifier si le système TNT est activé
        if (!plugin.getConfigManager().getBoolean("tnt.bank.enabled", false)) {
            sendMessage(sender, "tnt.disabled");
            return;
        }
        
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "general.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        if (args.length < 1) {
            showInfo(sender, faction);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "deposit":
            case "d":
            case "add":
                handleDeposit(sender, player, faction, args);
                break;
                
            case "withdraw":
            case "w":
            case "take":
                handleWithdraw(sender, player, faction, args);
                break;
                
            case "info":
            case "i":
            case "status":
                showInfo(sender, faction);
                break;
                
            default:
                sendMessage(sender, "tnt.usage");
                break;
        }
    }
    
    private void handleDeposit(CommandSender sender, Player player, Faction faction, String[] args) {
        // Vérifier la permission
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.TNT_DEPOSIT)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        int amount;
        if (args.length < 2) {
            // Déposer toute la TNT de l'inventaire
            amount = countTntInInventory(player);
            if (amount <= 0) {
                sendMessage(sender, "tnt.no-tnt-in-inventory");
                return;
            }
        } else {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    sendMessage(sender, "tnt.invalid-amount");
                    return;
                }
            } catch (NumberFormatException e) {
                sendMessage(sender, "tnt.invalid-amount");
                return;
            }
        }
        
        // Vérifier si le joueur a assez de TNT
        int tntInInventory = countTntInInventory(player);
        if (tntInInventory < amount) {
            sendMessage(sender, "tnt.not-enough-tnt", 
                "{have}", String.valueOf(tntInInventory),
                "{need}", String.valueOf(amount));
            return;
        }
        
        // Vérifier la limite de la banque
        int maxTnt = plugin.getConfigManager().getInt("tnt.bank.max-per-faction", 10000);
        int currentTnt = faction.getTntBank();
        if (currentTnt + amount > maxTnt) {
            int canDeposit = maxTnt - currentTnt;
            if (canDeposit <= 0) {
                sendMessage(sender, "tnt.bank-full");
                return;
            }
            amount = canDeposit;
            sendMessage(sender, "tnt.partial-deposit", "{amount}", String.valueOf(amount));
        }
        
        // Retirer la TNT de l'inventaire
        removeTntFromInventory(player, amount);
        
        // Ajouter à la banque
        faction.setTntBank(currentTnt + amount);
        plugin.getStorageManager().markDirty(faction);
        
        sendMessage(sender, "tnt.deposited", 
            "{amount}", String.valueOf(amount),
            "{total}", String.valueOf(faction.getTntBank()));
    }
    
    private void handleWithdraw(CommandSender sender, Player player, Faction faction, String[] args) {
        // Vérifier la permission
        if (!faction.hasPermission(player.getUniqueId(), PermissionAction.TNT_WITHDRAW)) {
            sendMessage(sender, "general.no-permission");
            return;
        }
        
        if (args.length < 2) {
            sendMessage(sender, "tnt.withdraw-usage");
            return;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                sendMessage(sender, "tnt.invalid-amount");
                return;
            }
        } catch (NumberFormatException e) {
            sendMessage(sender, "tnt.invalid-amount");
            return;
        }
        
        // Vérifier si la banque a assez de TNT
        int currentTnt = faction.getTntBank();
        if (currentTnt < amount) {
            sendMessage(sender, "tnt.not-enough", 
                "{have}", String.valueOf(currentTnt),
                "{need}", String.valueOf(amount));
            return;
        }
        
        // Vérifier si l'inventaire peut contenir la TNT
        int freeSlots = countFreeSlots(player);
        int maxWithdraw = freeSlots * 64;
        if (amount > maxWithdraw) {
            if (maxWithdraw <= 0) {
                sendMessage(sender, "tnt.inventory-full");
                return;
            }
            amount = maxWithdraw;
            sendMessage(sender, "tnt.partial-withdraw", "{amount}", String.valueOf(amount));
        }
        
        // Retirer de la banque
        faction.setTntBank(currentTnt - amount);
        plugin.getStorageManager().markDirty(faction);
        
        // Donner la TNT au joueur
        giveTntToPlayer(player, amount);
        
        sendMessage(sender, "tnt.withdrawn", 
            "{amount}", String.valueOf(amount),
            "{total}", String.valueOf(faction.getTntBank()));
    }
    
    private void showInfo(CommandSender sender, Faction faction) {
        int current = faction.getTntBank();
        int max = plugin.getConfigManager().getInt("tnt.bank.max-per-faction", 10000);
        
        sendMessage(sender, "tnt.bank-info",
            "{amount}", String.valueOf(current),
            "{max}", String.valueOf(max));
    }
    
    // === Utilitaires pour la TNT ===
    
    private int countTntInInventory(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TNT) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    private void removeTntFromInventory(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.TNT) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        
        player.updateInventory();
    }
    
    private void giveTntToPlayer(Player player, int amount) {
        int remaining = amount;
        
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack tnt = new ItemStack(Material.TNT, stackSize);
            player.getInventory().addItem(tnt);
            remaining -= stackSize;
        }
        
        player.updateInventory();
    }
    
    private int countFreeSlots(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public String getName() {
        return "tnt";
    }
    
    @Override
    public String getDescription() {
        return "Gérer la banque de TNT de la faction";
    }
    
    @Override
    public String getUsage() {
        return "[deposit|withdraw|info] [montant]";
    }
    
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            for (String sub : new String[]{"deposit", "withdraw", "info"}) {
                if (sub.startsWith(arg)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("deposit") || sub.equals("withdraw")) {
                // Suggérer des montants
                for (String amount : new String[]{"1", "16", "32", "64", "128", "256"}) {
                    completions.add(amount);
                }
            }
        }
        
        return completions;
    }
}
