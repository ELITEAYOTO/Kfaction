package me.krunsh.kfaction.managers;

/**
 * Formate les messages d'entrée de territoire avant la conversion des
 * couleurs Bukkit.
 *
 * Les alias historiques sont volontairement acceptés afin qu'un ancien
 * messages.yml ne puisse plus afficher un placeholder brut aux joueurs.
 */
final class TerritoryMessageFormatter {

    private TerritoryMessageFormatter() {
    }

    static String formatFaction(
            String template,
            String faction,
            String relation,
            String relationColor,
            String description
    ) {
        String formatted = template != null
                ? template
                : "{relation_color}~ {faction}";

        formatted = formatted
                .replace("{faction}", safe(faction))
                .replace("{relation}", safe(relation))
                .replace("{description}", safe(description));

        String color = safe(relationColor);

        return formatted
                .replace("{relation_color}", color)
                .replace("<relation_color>", color)
                .replace("{color}", color)
                .replace("<color>", color);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
