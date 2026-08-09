package me.krunsh.kfaction.utils;

import java.util.concurrent.TimeUnit;

/**
 * Utilitaire pour la gestion du temps
 */
public final class TimeUtil {
    
    private TimeUtil() {
        // Utility class
    }
    
    /**
     * Formate une durée en millisecondes en texte lisible
     * @param millis Durée en millisecondes
     * @return Texte formaté (ex: "2j 5h 30m")
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("j ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 && days == 0) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    /**
     * Formate une durée en secondes en texte lisible
     * @param seconds Durée en secondes
     * @return Texte formaté
     */
    public static String formatSeconds(long seconds) {
        return formatDuration(seconds * 1000);
    }
    
    /**
     * Formate un timestamp en "il y a X temps"
     * @param timestamp Le timestamp en millisecondes
     * @return Texte relatif (ex: "il y a 2h")
     */
    public static String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) return "dans le futur";
        
        if (diff < 60000) return "il y a quelques secondes";
        if (diff < 3600000) return "il y a " + (diff / 60000) + "m";
        if (diff < 86400000) return "il y a " + (diff / 3600000) + "h";
        return "il y a " + (diff / 86400000) + "j";
    }
    
    /**
     * Parse une durée depuis un texte (ex: "1d", "30m", "2h")
     * @param input Le texte à parser
     * @return La durée en millisecondes, ou -1 si invalide
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        
        input = input.toLowerCase().trim();
        
        try {
            if (input.endsWith("s")) {
                return Long.parseLong(input.replace("s", "")) * 1000;
            } else if (input.endsWith("m")) {
                return Long.parseLong(input.replace("m", "")) * 60000;
            } else if (input.endsWith("h")) {
                return Long.parseLong(input.replace("h", "")) * 3600000;
            } else if (input.endsWith("d") || input.endsWith("j")) {
                String num = input.replaceAll("[dj]", "");
                return Long.parseLong(num) * 86400000;
            } else if (input.endsWith("w")) {
                return Long.parseLong(input.replace("w", "")) * 604800000;
            }
            
            // Pas de suffixe = secondes par défaut
            return Long.parseLong(input) * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * Vérifie si un cooldown est terminé
     * @param lastUse Timestamp de la dernière utilisation
     * @param cooldownMs Durée du cooldown en ms
     * @return true si le cooldown est terminé
     */
    public static boolean isCooldownOver(long lastUse, long cooldownMs) {
        return System.currentTimeMillis() - lastUse >= cooldownMs;
    }
    
    /**
     * Calcule le temps restant d'un cooldown
     * @param lastUse Timestamp de la dernière utilisation
     * @param cooldownMs Durée du cooldown en ms
     * @return Temps restant en ms, ou 0 si terminé
     */
    public static long getRemainingCooldown(long lastUse, long cooldownMs) {
        long remaining = (lastUse + cooldownMs) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
