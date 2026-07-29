package me.krunsh.kfaction.managers;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.hooks.VaultHook;

import org.bukkit.entity.Player;

/**
 * Gestionnaire de l'économie des factions
 * Gère les comptes de faction et les transactions
 * Intègre Vault pour les transactions de joueurs
 */
public class EconomyManager {
    
    private final Kfaction plugin;
    
    public EconomyManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        plugin.getLogger().info("EconomyManager initialisé");
    }
    
    /**
     * Ferme le manager
     */
    public void shutdown() {
        // Rien à faire
    }
    
    // ==================== PLAYER ECONOMY (Vault) ====================
    
    /**
     * Vérifie si un joueur a assez d'argent
     * @param player Le joueur
     * @param amount Le montant
     * @return true si suffisant
     */
    public boolean has(Player player, double amount) {
        VaultHook vault = plugin.getHookManager().getVaultHook();
        if (vault == null || !vault.isEnabled()) {
            return true; // Pas d'économie = toujours vrai
        }
        return vault.has(player, amount);
    }
    
    /**
     * Retire de l'argent du compte d'un joueur
     * @param player Le joueur
     * @param amount Le montant
     * @return true si réussi
     */
    public boolean withdraw(Player player, double amount) {
        VaultHook vault = plugin.getHookManager().getVaultHook();
        if (vault == null || !vault.isEnabled()) {
            return true; // Pas d'économie = succès silencieux
        }
        return vault.withdraw(player, amount);
    }
    
    /**
     * Dépose de l'argent sur le compte d'un joueur
     * @param player Le joueur
     * @param amount Le montant
     * @return true si réussi
     */
    public boolean deposit(Player player, double amount) {
        VaultHook vault = plugin.getHookManager().getVaultHook();
        if (vault == null || !vault.isEnabled()) {
            return true; // Pas d'économie = succès silencieux
        }
        return vault.deposit(player, amount);
    }
    
    /**
     * Obtient le solde d'un joueur
     * @param player Le joueur
     * @return Le solde
     */
    public double getBalance(Player player) {
        VaultHook vault = plugin.getHookManager().getVaultHook();
        if (vault == null || !vault.isEnabled()) {
            return 0.0;
        }
        return vault.getBalance(player);
    }
    
    // ==================== FACTION ECONOMY ====================
    
    /**
     * Obtient le solde d'une faction
     * @param faction La faction
     * @return Le solde
     */
    public double getBalance(Faction faction) {
        return faction != null ? faction.getBalance() : 0.0;
    }
    
    /**
     * Dépose de l'argent dans le compte d'une faction
     * @param faction La faction
     * @param amount Le montant
     * @return true si réussi
     */
    public boolean deposit(Faction faction, double amount) {
        if (faction == null || amount <= 0) return false;
        faction.setBank(faction.getBank() + amount);
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
    
    /**
     * Retire de l'argent du compte d'une faction
     * @param faction La faction
     * @param amount Le montant
     * @return true si réussi
     */
    public boolean withdraw(Faction faction, double amount) {
        if (faction == null || amount <= 0) return false;
        if (faction.getBank() < amount) return false;
        faction.setBank(faction.getBank() - amount);
        plugin.getStorageManager().markDirty(faction);
        return true;
    }
    
    /**
     * Vérifie si une faction peut se permettre un montant
     * @param faction La faction
     * @param amount Le montant
     * @return true si suffisant
     */
    public boolean canAfford(Faction faction, double amount) {
        return faction != null && faction.getBank() >= amount;
    }
    
    /**
     * Transfère de l'argent d'une faction à une autre
     * @param from Faction source
     * @param to Faction destination
     * @param amount Montant
     * @return true si réussi
     */
    public boolean transfer(Faction from, Faction to, double amount) {
        if (!canAfford(from, amount)) return false;
        if (!withdraw(from, amount)) return false;
        return deposit(to, amount);
    }
}
