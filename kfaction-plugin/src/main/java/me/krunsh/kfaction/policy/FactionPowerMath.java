package me.krunsh.kfaction.policy;

/**
 * Agrégation sans état de la power. Le boost est compté une seule fois, puis
 * chaque membre apporte sa valeur individuelle.
 */
public final class FactionPowerMath {

    private FactionPowerMath() {
    }

    public static double start(double configuredBoost) {
        return configuredBoost;
    }

    public static double addMember(double runningTotal, double memberPower) {
        return runningTotal + memberPower;
    }
}
