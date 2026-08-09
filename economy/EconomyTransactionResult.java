package me.krunsh.kfaction.economy;

/**
 * Résultat immutable d'une transaction EconomyService.
 */
public final class EconomyTransactionResult {

    public enum Type {
        DEPOSIT_TO_FACTION,
        WITHDRAW_FROM_FACTION,
        FACTION_TRANSFER
    }

    private final Type type;
    private final MoneyAmount amount;
    private final MoneyAmount factionBalanceBefore;
    private final MoneyAmount factionBalanceAfter;
    private final String otherFactionId;

    public EconomyTransactionResult(
            Type type,
            MoneyAmount amount,
            MoneyAmount factionBalanceBefore,
            MoneyAmount factionBalanceAfter,
            String otherFactionId
    ) {
        if (type == null
                || amount == null
                || factionBalanceBefore == null
                || factionBalanceAfter == null) {
            throw new IllegalArgumentException(
                    "EconomyTransactionResult fields cannot be null"
            );
        }

        this.type = type;
        this.amount = amount;
        this.factionBalanceBefore =
                factionBalanceBefore;
        this.factionBalanceAfter =
                factionBalanceAfter;
        this.otherFactionId =
                normalize(otherFactionId);
    }

    public Type getType() {
        return type;
    }

    public MoneyAmount getAmount() {
        return amount;
    }

    public MoneyAmount getFactionBalanceBefore() {
        return factionBalanceBefore;
    }

    public MoneyAmount getFactionBalanceAfter() {
        return factionBalanceAfter;
    }

    public String getOtherFactionId() {
        return otherFactionId;
    }

    private static String normalize(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}
