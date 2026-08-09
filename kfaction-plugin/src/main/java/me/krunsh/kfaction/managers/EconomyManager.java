package me.krunsh.kfaction.managers;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.economy.MoneyAmount;
import me.krunsh.kfaction.hooks.VaultHook;
import me.krunsh.kfaction.services.EconomyService;

/**
 * Adapter Economy V2.
 *
 * Les commandes métier doivent utiliser getService().
 * Les anciennes méthodes restent disponibles pour compatibilité V1.
 */
public class EconomyManager {

    private final Kfaction plugin;
    private final EconomyService service;

    public EconomyManager(Kfaction plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;
        this.service =
                new EconomyService(
                        plugin,
                        this
                );
    }

    public void initialize() {
        service.initialize();

        plugin.getLogger().info(
                "EconomyManager V2 initialisé "
                        + "(minor units + transactional service)"
        );
    }

    public void shutdown() {
        service.shutdown();
    }

    public EconomyService getService() {
        return service;
    }

    // ============================================================
    // Player economy / Vault adapter
    // ============================================================

    public boolean isPlayerEconomyAvailable() {
        VaultHook vault =
                getVault();

        return vault != null
                && vault.isEnabled();
    }

    /**
     * Compatibilité V1:
     * sans économie, has() reste true comme auparavant.
     *
     * EconomyService n'utilise jamais ce comportement silencieux:
     * il vérifie isPlayerEconomyAvailable() avant une transaction.
     */
    public boolean has(
            Player player,
            double amount
    ) {
        VaultHook vault =
                getVault();

        if (vault == null
                || !vault.isEnabled()) {
            return true;
        }

        return vault.has(
                player,
                amount
        );
    }

    public boolean withdraw(
            Player player,
            double amount
    ) {
        VaultHook vault =
                getVault();

        if (vault == null
                || !vault.isEnabled()) {
            return true;
        }

        return vault.withdraw(
                player,
                amount
        );
    }

    public boolean deposit(
            Player player,
            double amount
    ) {
        VaultHook vault =
                getVault();

        if (vault == null
                || !vault.isEnabled()) {
            return true;
        }

        return vault.deposit(
                player,
                amount
        );
    }

    public double getBalance(
            Player player
    ) {
        VaultHook vault =
                getVault();

        if (vault == null
                || !vault.isEnabled()) {
            return 0.0D;
        }

        return vault.getBalance(player);
    }

    public String format(
            double amount
    ) {
        VaultHook vault =
                getVault();

        if (vault == null
                || !vault.isEnabled()) {
            return MoneyAmount
                    .fromLegacyDouble(amount)
                    .toPlainString();
        }

        return vault.format(amount);
    }

    // ============================================================
    // Faction economy compatibility
    // ============================================================

    public double getBalance(
            Faction faction
    ) {
        return faction != null
                ? faction.getBalance()
                : 0.0D;
    }

    public long getBalanceMinor(
            Faction faction
    ) {
        return faction != null
                ? faction.getBankMinor()
                : 0L;
    }

    public boolean deposit(
            Faction faction,
            double amount
    ) {
        if (faction == null) {
            return false;
        }

        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        if (!money.isPositive()) {
            return false;
        }

        boolean success =
                faction.tryDepositMinor(
                        money.getMinorUnits()
                );

        if (success) {
            plugin.getStorageManager()
                    .markDirty(faction);
        }

        return success;
    }

    public boolean withdraw(
            Faction faction,
            double amount
    ) {
        if (faction == null) {
            return false;
        }

        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        if (!money.isPositive()) {
            return false;
        }

        boolean success =
                faction.tryWithdrawMinor(
                        money.getMinorUnits()
                );

        if (success) {
            plugin.getStorageManager()
                    .markDirty(faction);
        }

        return success;
    }

    public boolean canAfford(
            Faction faction,
            double amount
    ) {
        if (faction == null) {
            return false;
        }

        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        return money.isPositive()
                && faction.getBankMinor()
                        >= money.getMinorUnits();
    }

    /**
     * Wrapper V1 atomique sur le domaine faction.
     *
     * Pour les nouveaux appels applicatifs, utiliser EconomyService.
     */
    public boolean transfer(
            Faction from,
            Faction to,
            double amount
    ) {
        if (from == null
                || to == null
                || from == to) {
            return false;
        }

        MoneyAmount money =
                MoneyAmount.fromLegacyDouble(
                        amount
                );

        if (!money.isPositive()
                || !from.tryWithdrawMinor(
                        money.getMinorUnits()
                )) {
            return false;
        }

        if (!to.tryDepositMinor(
                money.getMinorUnits()
        )) {
            if (!from.tryDepositMinor(
                    money.getMinorUnits()
            )) {
                plugin.getLogger().severe(
                        "[Economy] rollback faction transfer impossible "
                                + "from="
                                + from.getId()
                                + " to="
                                + to.getId()
                );
            }

            return false;
        }

        plugin.getStorageManager()
                .markDirty(from);

        plugin.getStorageManager()
                .markDirty(to);

        return true;
    }

    private VaultHook getVault() {
        return plugin.getHookManager() != null
                ? plugin.getHookManager()
                        .getVaultHook()
                : null;
    }
}
