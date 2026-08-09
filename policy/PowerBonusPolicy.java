package me.krunsh.kfaction.policy;

/**
 * Politique pure des bonus de power accordés par permission.
 *
 * Les boosts persistants de faction et les récompenses de niveau ne passent
 * pas ici : le commutateur contrôle uniquement VIP, MVP et Legend.
 */
public final class PowerBonusPolicy {
    private PowerBonusPolicy() {
    }

    public static double apply(double base, boolean enabled,
                               boolean vip, double vipAmount,
                               boolean mvp, double mvpAmount,
                               boolean legend, double legendAmount) {
        if (!enabled) {
            return base;
        }
        double result = base;
        if (vip) {
            result += vipAmount;
        }
        if (mvp) {
            result += mvpAmount;
        }
        if (legend) {
            result += legendAmount;
        }
        return result;
    }
}
