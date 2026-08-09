package me.krunsh.kfaction.utils;

/** Format d'affichage du power: le gameplay n'expose pas de fractions. */
public final class PowerDisplay {
    private PowerDisplay() {}

    public static String format(double power) {
        return Long.toString(Math.round(power));
    }
}
