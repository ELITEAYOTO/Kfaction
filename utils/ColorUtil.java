package me.krunsh.kfaction.utils;

import org.bukkit.ChatColor;

/**
 * Utilitaire pour la gestion des couleurs
 */
public final class ColorUtil {
    
    private ColorUtil() {
        // Utility class
    }
    
    /**
     * Traduit les codes couleur (&) en codes Minecraft (§)
     * @param text Le texte avec codes &
     * @return Le texte avec codes §
     */
    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Supprime tous les codes couleur d'un texte
     * @param text Le texte coloré
     * @return Le texte sans couleurs
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }
    
    /**
     * Vérifie si un texte contient des codes couleur
     * @param text Le texte à vérifier
     * @return true si contient des couleurs
     */
    public static boolean hasColor(String text) {
        if (text == null) return false;
        return text.contains("&") || text.contains("§");
    }
    
    /**
     * Crée une barre de progression colorée
     * @param current Valeur actuelle
     * @param max Valeur maximum
     * @param length Longueur de la barre en caractères
     * @param filledColor Couleur de la partie remplie
     * @param emptyColor Couleur de la partie vide
     * @return La barre de progression
     */
    public static String progressBar(double current, double max, int length, 
                                      ChatColor filledColor, ChatColor emptyColor) {
        double percent = Math.min(1.0, Math.max(0.0, current / max));
        int filled = (int) (length * percent);
        int empty = length - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append(emptyColor);
        for (int i = 0; i < empty; i++) bar.append("█");
        
        return bar.toString();
    }
    
    /**
     * Crée une barre de progression simple (vert/rouge)
     * @param current Valeur actuelle
     * @param max Valeur maximum
     * @return La barre de progression
     */
    public static String progressBar(double current, double max) {
        return progressBar(current, max, 10, ChatColor.GREEN, ChatColor.RED);
    }
    
    /**
     * Retourne une couleur basée sur un pourcentage (vert -> jaune -> rouge)
     * @param percent Pourcentage (0.0 - 1.0)
     * @return La couleur correspondante
     */
    public static ChatColor getColorByPercent(double percent) {
        if (percent >= 0.75) return ChatColor.GREEN;
        if (percent >= 0.50) return ChatColor.YELLOW;
        if (percent >= 0.25) return ChatColor.GOLD;
        return ChatColor.RED;
    }
}
