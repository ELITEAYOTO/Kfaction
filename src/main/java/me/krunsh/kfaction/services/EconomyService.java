package me.krunsh.kfaction.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.event.FactionEconomyEvent;
import me.krunsh.kfaction.api.event.FactionEconomyEvent.Phase;
import me.krunsh.kfaction.api.event.FactionEconomyEvent.Type;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.economy.EconomyTransactionResult;
import me.krunsh.kfaction.economy.MoneyAmount;
import me.krunsh.kfaction.managers.EconomyManager;
import me.krunsh.kfaction.permissions.FactionCapability;

/**
 * Service applicatif Economy V2.
 *
 * Pipeline:
 * validate -> PRE event -> external Vault mutation -> domain mutation
 * -> dirty -> log -> POST event.
 *
 * En cas d'échec entre Vault et le domaine, compensation immédiate.
 */
public final class EconomyService {

    private final Kfaction plugin;
    private final EconomyManager manager;

    public EconomyService(
            Kfaction plugin,
            EconomyManager manager
    ) {
        if (plugin == null
                || manager == null) {
            throw new IllegalArgumentException(
                    "plugin/manager cannot be null"
            );
        }

        this.plugin = plugin;
        this.manager = manager;
    }

    public void initialize() {
        // Aucun listener/task permanent.
    }

    public void shutdown() {
        // Aucun listener/task permanent.
    }

    // ============================================================
    // Parsing / formatting
    // ============================================================

    public OperationResult<MoneyAmount> parseAmount(
            String input
    ) {
        final MoneyAmount amount;

        try {
            amount =
                    MoneyAmount.parse(input);
        } catch (IllegalArgumentException exception) {
            return failure(
                    Status.INVALID_INPUT,
                    "Montant invalide. Maximum 2 décimales."
            );
        }

        if (!amount.isPositive()) {
            return failure(
                    Status.INVALID_INPUT,
                    "Le montant doit être supérieur à zéro"
            );
        }

        MoneyAmount max =
                getMaxTransaction();

        if (amount.compareTo(max) > 0) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Montant maximum par transaction: "
                            + max.toPlainString()
            );
        }

        return OperationResult.success(amount);
    }

    public String format(
            MoneyAmount amount
    ) {
        if (amount == null) {
            return MoneyAmount.ZERO
                    .toPlainString();
        }

        return manager.isPlayerEconomyAvailable()
                ? manager.format(
                        amount.toDouble()
                )
                : amount.toPlainString();
    }

    // ============================================================
    // Player <-> Faction
    // ============================================================

    public OperationResult<EconomyTransactionResult>
            depositToFaction(
                    Player actor,
                    Faction faction,
                    MoneyAmount amount,
                    OperationContext context
            ) {
        OperationResult<Void> common =
                validate(
                        actor,
                        faction,
                        amount,
                        context,
                        FactionCapability.DEPOSIT_MONEY
                );

        if (!common.isSuccess()) {
            return copyFailure(common);
        }

        if (!manager.isPlayerEconomyAvailable()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Aucun provider économique Vault disponible"
            );
        }

        long before =
                faction.getBankMinor();

        final long after;

        try {
            after =
                    Math.addExact(
                            before,
                            amount.getMinorUnits()
                    );
        } catch (ArithmeticException exception) {
            return failure(
                    Status.LIMIT_REACHED,
                    "La banque faction dépasserait la capacité maximale"
            );
        }

        if (after > getMaxFactionBank()
                .getMinorUnits()) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Limite de banque faction atteinte"
            );
        }

        if (!manager.has(
                actor,
                amount.toDouble()
        )) {
            return failure(
                    Status.FORBIDDEN,
                    "Solde joueur insuffisant"
            );
        }

        if (firePre(
                Type.DEPOSIT_TO_FACTION,
                actor,
                faction,
                null,
                amount,
                before,
                after,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Dépôt annulé par un listener"
            );
        }

        if (!manager.withdraw(
                actor,
                amount.toDouble()
        )) {
            return failure(
                    Status.FAILED,
                    "Le provider économique a refusé le retrait joueur"
            );
        }

        if (!faction.tryDepositMinor(
                amount.getMinorUnits()
        )) {
            boolean refunded =
                    manager.deposit(
                            actor,
                            amount.toDouble()
                    );

            if (!refunded) {
                plugin.getLogger().severe(
                        "[ECONOMY-CRITICAL] compensation joueur impossible "
                                + "après échec dépôt faction. player="
                                + actor.getUniqueId()
                                + " amount="
                                + amount.toPlainString()
                );
            }

            return failure(
                    Status.FAILED,
                    refunded
                            ? "Transaction annulée et joueur remboursé"
                            : "Échec critique: remboursement joueur refusé"
            );
        }

        plugin.getStorageManager()
                .markDirty(faction);

        log(
                faction,
                LogType.ECONOMY_DEPOSIT,
                actor,
                amount,
                before,
                after
        );

        firePost(
                Type.DEPOSIT_TO_FACTION,
                actor,
                faction,
                null,
                amount,
                before,
                after,
                context
        );

        return OperationResult.success(
                new EconomyTransactionResult(
                        EconomyTransactionResult.Type
                                .DEPOSIT_TO_FACTION,
                        amount,
                        MoneyAmount.ofMinor(before),
                        MoneyAmount.ofMinor(after),
                        null
                )
        );
    }

    public OperationResult<EconomyTransactionResult>
            withdrawFromFaction(
                    Player actor,
                    Faction faction,
                    MoneyAmount amount,
                    OperationContext context
            ) {
        OperationResult<Void> common =
                validate(
                        actor,
                        faction,
                        amount,
                        context,
                        FactionCapability.WITHDRAW_MONEY
                );

        if (!common.isSuccess()) {
            return copyFailure(common);
        }

        if (!manager.isPlayerEconomyAvailable()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Aucun provider économique Vault disponible"
            );
        }

        long before =
                faction.getBankMinor();

        if (before
                < amount.getMinorUnits()) {
            return failure(
                    Status.FORBIDDEN,
                    "Banque faction insuffisante"
            );
        }

        long after =
                before
                        - amount.getMinorUnits();

        if (firePre(
                Type.WITHDRAW_FROM_FACTION,
                actor,
                faction,
                null,
                amount,
                before,
                after,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Retrait annulé par un listener"
            );
        }

        if (!faction.tryWithdrawMinor(
                amount.getMinorUnits()
        )) {
            return failure(
                    Status.CONFLICT,
                    "Le solde faction a changé pendant la transaction"
            );
        }

        if (!manager.deposit(
                actor,
                amount.toDouble()
        )) {
            boolean restored =
                    faction.tryDepositMinor(
                            amount.getMinorUnits()
                    );

            if (!restored) {
                plugin.getLogger().severe(
                        "[ECONOMY-CRITICAL] rollback banque faction impossible "
                                + "faction="
                                + faction.getId()
                                + " amount="
                                + amount.toPlainString()
                );
            }

            return failure(
                    Status.FAILED,
                    restored
                            ? "Provider économique refusé, banque restaurée"
                            : "Échec critique: banque non restaurée"
            );
        }

        plugin.getStorageManager()
                .markDirty(faction);

        log(
                faction,
                LogType.ECONOMY_WITHDRAW,
                actor,
                amount,
                before,
                after
        );

        firePost(
                Type.WITHDRAW_FROM_FACTION,
                actor,
                faction,
                null,
                amount,
                before,
                after,
                context
        );

        return OperationResult.success(
                new EconomyTransactionResult(
                        EconomyTransactionResult.Type
                                .WITHDRAW_FROM_FACTION,
                        amount,
                        MoneyAmount.ofMinor(before),
                        MoneyAmount.ofMinor(after),
                        null
                )
        );
    }

    // ============================================================
    // Faction -> Faction API V2
    // ============================================================

    public OperationResult<EconomyTransactionResult>
            transferFactionToFaction(
                    Faction from,
                    Faction to,
                    MoneyAmount amount,
                    OperationContext context
            ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La transaction doit être exécutée sur le thread principal"
            );
        }

        if (from == null
                || to == null
                || from == to
                || from.isSystemFaction()
                || to.isSystemFaction()
                || amount == null
                || !amount.isPositive()
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Transfert faction invalide"
            );
        }

        if (amount.compareTo(
                getMaxTransaction()
        ) > 0) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Montant supérieur à la limite par transaction"
            );
        }

        long before =
                from.getBankMinor();

        if (before < amount.getMinorUnits()) {
            return failure(
                    Status.FORBIDDEN,
                    "Banque source insuffisante"
            );
        }

        final long targetAfter;

        try {
            targetAfter =
                    Math.addExact(
                            to.getBankMinor(),
                            amount.getMinorUnits()
                    );
        } catch (ArithmeticException exception) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Banque destination trop élevée"
            );
        }

        if (targetAfter
                > getMaxFactionBank()
                        .getMinorUnits()) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Limite de banque destination atteinte"
            );
        }

        long after =
                before
                        - amount.getMinorUnits();

        if (firePre(
                Type.FACTION_TRANSFER,
                null,
                from,
                to,
                amount,
                before,
                after,
                context
        )) {
            return failure(
                    Status.CANCELLED,
                    "Transfert annulé par un listener"
            );
        }

        if (!from.tryWithdrawMinor(
                amount.getMinorUnits()
        )) {
            return failure(
                    Status.CONFLICT,
                    "Solde source modifié"
            );
        }

        if (!to.tryDepositMinor(
                amount.getMinorUnits()
        )) {
            boolean restored =
                    from.tryDepositMinor(
                            amount.getMinorUnits()
                    );

            if (!restored) {
                plugin.getLogger().severe(
                        "[ECONOMY-CRITICAL] rollback transfert faction impossible "
                                + "from="
                                + from.getId()
                                + " to="
                                + to.getId()
                );
            }

            return failure(
                    Status.FAILED,
                    "Transfert non commit, rollback exécuté"
            );
        }

        plugin.getStorageManager()
                .markDirty(from);

        plugin.getStorageManager()
                .markDirty(to);

        firePost(
                Type.FACTION_TRANSFER,
                null,
                from,
                to,
                amount,
                before,
                after,
                context
        );

        return OperationResult.success(
                new EconomyTransactionResult(
                        EconomyTransactionResult.Type
                                .FACTION_TRANSFER,
                        amount,
                        MoneyAmount.ofMinor(before),
                        MoneyAmount.ofMinor(after),
                        to.getId()
                )
        );
    }

    // ============================================================
    // Validation / events
    // ============================================================

    private OperationResult<Void> validate(
            Player actor,
            Faction faction,
            MoneyAmount amount,
            OperationContext context,
            FactionCapability capability
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "La transaction doit être exécutée sur le thread principal"
            );
        }

        if (actor == null
                || faction == null
                || faction.isSystemFaction()
                || amount == null
                || !amount.isPositive()
                || context == null
                || capability == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Paramètres économiques invalides"
            );
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                actor.getUniqueId()
                        );

        if (fPlayer == null
                || !fPlayer.hasFaction()
                || !faction.getId()
                        .equals(
                                fPlayer.getFactionId()
                        )
                || !faction.isMember(
                        actor.getUniqueId()
                )) {
            return failure(
                    Status.FORBIDDEN,
                    "Vous n'êtes plus membre de cette faction"
            );
        }

        if (!plugin.getPermissionManager()
                .can(
                        actor,
                        capability
                )) {
            return failure(
                    Status.FORBIDDEN,
                    "Permission faction refusée"
            );
        }

        if (amount.compareTo(
                getMaxTransaction()
        ) > 0) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Montant supérieur à la limite par transaction"
            );
        }

        return OperationResult.success();
    }

    private boolean firePre(
            Type type,
            Player player,
            Faction faction,
            Faction otherFaction,
            MoneyAmount amount,
            long before,
            long after,
            OperationContext context
    ) {
        FactionEconomyEvent event =
                new FactionEconomyEvent(
                        Phase.PRE,
                        type,
                        player,
                        faction,
                        otherFaction,
                        amount,
                        before,
                        after,
                        context
                );

        Bukkit.getPluginManager()
                .callEvent(event);

        return event.isCancelled();
    }

    private void firePost(
            Type type,
            Player player,
            Faction faction,
            Faction otherFaction,
            MoneyAmount amount,
            long before,
            long after,
            OperationContext context
    ) {
        Bukkit.getPluginManager()
                .callEvent(
                        new FactionEconomyEvent(
                                Phase.POST,
                                type,
                                player,
                                faction,
                                otherFaction,
                                amount,
                                before,
                                after,
                                context
                        )
                );
    }

    private void log(
            Faction faction,
            LogType type,
            Player actor,
            MoneyAmount amount,
            long before,
            long after
    ) {
        plugin.getLogManager()
                .log(
                        faction.getId(),
                        type,
                        actor,
                        null,
                        "amount="
                                + amount.toPlainString()
                                + " before="
                                + MoneyAmount.ofMinor(before)
                                        .toPlainString()
                                + " after="
                                + MoneyAmount.ofMinor(after)
                                        .toPlainString()
                );
    }

    private MoneyAmount getMaxTransaction() {
        return parseConfigAmount(
                "economy.max-transaction",
                "1000000000.00"
        );
    }

    private MoneyAmount getMaxFactionBank() {
        return parseConfigAmount(
                "economy.max-faction-bank",
                "92233720368547758.07"
        );
    }

    private MoneyAmount parseConfigAmount(
            String path,
            String fallback
    ) {
        String configured =
                plugin.getConfigManager()
                        .getString(
                                path,
                                fallback
                        );

        try {
            MoneyAmount parsed =
                    MoneyAmount.parse(
                            configured
                    );

            return parsed.isPositive()
                    ? parsed
                    : MoneyAmount.parse(
                            fallback
                    );
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Montant config invalide "
                            + path
                            + "="
                            + configured
                            + ", fallback="
                            + fallback
            );

            return MoneyAmount.parse(
                    fallback
            );
        }
    }

    private static <T> OperationResult<T> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "economy.failed",
                detail
        );
    }

    private static <T> OperationResult<T> copyFailure(
            OperationResult<?> source
    ) {
        return OperationResult.failure(
                source.getStatus(),
                source.hasMessageKey()
                        ? source.getMessageKey()
                        : "economy.failed",
                source.getDetail()
        );
    }
}
