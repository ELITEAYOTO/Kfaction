package me.krunsh.kfaction.hooks;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.krunsh.kfaction.Kfaction;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;

/**
 * Hook pour Vault (système économique)
 */
public class VaultHook {
    
    private final Kfaction plugin;
    private Economy economy;
    private Chat chat;
    
    public VaultHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise la connexion à Vault
     * @return true si succès
     */
    public boolean initialize() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager()
                .getRegistration(Economy.class);
        
        if (rsp == null) {
            return false;
        }
        
        economy = rsp.getProvider();

        RegisteredServiceProvider<Chat> chatRegistration =
                Bukkit.getServicesManager().getRegistration(Chat.class);
        chat = chatRegistration != null
                ? chatRegistration.getProvider()
                : null;

        return economy != null;
    }
    
    /**
     * @return true si l'économie est disponible
     */
    public boolean isEnabled() {
        return economy != null;
    }
    
    /**
     * Obtient le solde d'un joueur
     * @param player Le joueur
     * @return Le solde
     */
    public double getBalance(Player player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    /**
     * Lecture utilisée par /f show pour les membres déconnectés.
     * La commande est un chemin utilisateur ponctuel, jamais un hot-path.
     */
    public double getBalance(OfflinePlayer player) {
        if (economy == null || player == null) return 0;
        return economy.getBalance(player);
    }

    public boolean hasChat() {
        return chat != null;
    }

    public String getPrimaryGroup(OfflinePlayer player) {
        if (chat == null || player == null) return null;
        return chat.getPrimaryGroup((String) null, player);
    }
    
    /**
     * Retire de l'argent à un joueur
     * @param player Le joueur
     * @param amount Le montant
     * @return true si succès
     */
    public boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Donne de l'argent à un joueur
     * @param player Le joueur
     * @param amount Le montant
     * @return true si succès
     */
    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Vérifie si un joueur a assez d'argent
     * @param player Le joueur
     * @param amount Le montant
     * @return true si le joueur a assez
     */
    public boolean has(Player player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }
    
    /**
     * Formate un montant selon la devise
     * @param amount Le montant
     * @return Le montant formaté
     */
    public String format(double amount) {
        if (economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}
