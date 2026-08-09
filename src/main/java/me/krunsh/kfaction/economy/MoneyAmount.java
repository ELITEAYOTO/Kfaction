package me.krunsh.kfaction.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montant monétaire exact de Kfaction.
 *
 * Kfaction utilise volontairement 2 décimales fixes en interne:
 * 1 unité = 100 minor units.
 *
 * Aucun calcul métier de banque faction ne repose sur double.
 */
public final class MoneyAmount
        implements Comparable<MoneyAmount> {

    public static final int SCALE = 2;
    public static final long MINOR_FACTOR = 100L;

    public static final MoneyAmount ZERO =
            new MoneyAmount(0L);

    private final long minorUnits;

    private MoneyAmount(long minorUnits) {
        this.minorUnits = minorUnits;
    }

    public static MoneyAmount ofMinor(long minorUnits) {
        if (minorUnits < 0L) {
            throw new IllegalArgumentException(
                    "minorUnits cannot be negative"
            );
        }

        return minorUnits == 0L
                ? ZERO
                : new MoneyAmount(minorUnits);
    }

    /**
     * Parsing strict pour les commandes/API texte.
     *
     * Refuse:
     * - NaN / Infinity;
     * - notation scientifique;
     * - valeurs négatives;
     * - plus de 2 décimales.
     */
    public static MoneyAmount parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException(
                    "amount cannot be null"
            );
        }

        String normalized =
                input.trim()
                        .replace(',', '.');

        if (normalized.isEmpty()
                || !normalized.matches(
                        "^[0-9]+(?:\\.[0-9]{1,2})?$"
                )) {
            throw new IllegalArgumentException(
                    "invalid money format"
            );
        }

        try {
            BigDecimal decimal =
                    new BigDecimal(normalized)
                            .setScale(
                                    SCALE,
                                    RoundingMode.UNNECESSARY
                            );

            BigDecimal minor =
                    decimal.movePointRight(
                            SCALE
                    );

            return ofMinor(
                    minor.longValueExact()
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "amount out of range",
                    exception
            );
        }
    }

    /**
     * Pont V1 / Vault.
     *
     * Les anciennes valeurs double sont arrondies HALF_UP à 2 décimales
     * uniquement lors de la migration/compatibilité.
     */
    public static MoneyAmount fromLegacyDouble(
            double amount
    ) {
        if (Double.isNaN(amount)
                || Double.isInfinite(amount)
                || amount <= 0.0D) {
            return ZERO;
        }

        BigDecimal decimal =
                BigDecimal.valueOf(amount)
                        .setScale(
                                SCALE,
                                RoundingMode.HALF_UP
                        );

        try {
            return ofMinor(
                    decimal.movePointRight(
                            SCALE
                    ).longValueExact()
            );
        } catch (ArithmeticException exception) {
            return ofMinor(Long.MAX_VALUE);
        }
    }

    public long getMinorUnits() {
        return minorUnits;
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public double toDouble() {
        return BigDecimal.valueOf(
                minorUnits,
                SCALE
        ).doubleValue();
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(
                minorUnits,
                SCALE
        );
    }

    public String toPlainString() {
        return toBigDecimal()
                .setScale(
                        SCALE,
                        RoundingMode.UNNECESSARY
                )
                .toPlainString();
    }

    public MoneyAmount plus(
            MoneyAmount other
    ) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "other cannot be null"
            );
        }

        return ofMinor(
                Math.addExact(
                        minorUnits,
                        other.minorUnits
                )
        );
    }

    public MoneyAmount minus(
            MoneyAmount other
    ) {
        if (other == null
                || other.minorUnits > minorUnits) {
            throw new ArithmeticException(
                    "insufficient amount"
            );
        }

        return ofMinor(
                minorUnits
                        - other.minorUnits
        );
    }

    @Override
    public int compareTo(
            MoneyAmount other
    ) {
        if (other == null) {
            return 1;
        }

        return Long.compare(
                minorUnits,
                other.minorUnits
        );
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MoneyAmount
                && minorUnits
                == ((MoneyAmount) object)
                        .minorUnits;
    }

    @Override
    public int hashCode() {
        return (int) (
                minorUnits
                        ^ (minorUnits >>> 32)
        );
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}
