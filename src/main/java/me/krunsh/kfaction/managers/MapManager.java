package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Gestionnaire de la carte de factions
 * Génère et affiche la carte des territoires comme le /f map de Factions
 */
public class MapManager {
    
    private final Kfaction plugin;
    
    // Configuration de la carte
    private int mapWidth = 39;  // Impair pour centrer le joueur
    private int mapHeight = 8;
    
    // Symboles configurables
    private String wildernessSymbol = "▇";
    private String safezoneSymbol   = "▒";
    private String warzoneSymbol    = "▓";
    private String claimSymbol      = "█";  // symbole unique pour tout territoire claimé
    private String playerSymbol     = "▲";

    // Couleurs configurables (codes §)
    private String colorWilderness = "§7";
    private String colorSafezone   = "§a";
    private String colorWarzone    = "§4";
    private String colorOwn        = "§2";
    private String colorAlly       = "§9";
    private String colorTruce      = "§e";
    private String colorEnemy      = "§c";
    private String colorNeutral    = "§f";
    private String colorPlayer     = "§d";

    // Hover configurables
    private boolean hoverEnabled    = true;
    private String  hoverPlayer     = "§dMoi";
    private String  hoverWilderness = "";
    private String  hoverSafezone   = "§aSafezone";
    private String  hoverWarzone    = "§4Warzone";
    private String  hoverOwn        = "§2{name}";
    private String  hoverAlly       = "§9Allié: {name}";
    private String  hoverTruce      = "§eTrêve: {name}";
    private String  hoverEnemy      = "§cEnnemi: {name}";
    private String  hoverNeutral    = "§fNeutre: {name}";
    
    // Joueurs avec auto-map activé
    private final Set<UUID> autoMapPlayers = ConcurrentHashMap.newKeySet();

    // Cooldown anti-spam pour l'auto-map (performance 400-600 joueurs)
    private final Map<UUID, Long> autoMapCooldowns = new ConcurrentHashMap<>();
    private static final long AUTO_MAP_COOLDOWN_MS = 1500L;
    
    public MapManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le manager
     */
    public void initialize() {
        mapWidth  = plugin.getConfigManager().getInt("map.width",  39);
        mapHeight = plugin.getConfigManager().getInt("map.height", 8);

        wildernessSymbol = plugin.getConfigManager().getString("map.symbols.wilderness", "▇");
        safezoneSymbol   = plugin.getConfigManager().getString("map.symbols.safezone",   "▒");
        warzoneSymbol    = plugin.getConfigManager().getString("map.symbols.warzone",    "▓");
        claimSymbol      = plugin.getConfigManager().getString("map.symbols.claim",      "█");
        playerSymbol     = plugin.getConfigManager().getString("map.symbols.player",     "▲");

        colorWilderness = plugin.getConfigManager().getString("map.colors.wilderness", "§7");
        colorSafezone   = plugin.getConfigManager().getString("map.colors.safezone",   "§a");
        colorWarzone    = plugin.getConfigManager().getString("map.colors.warzone",     "§4");
        colorOwn        = plugin.getConfigManager().getString("map.colors.own",        "§2");
        colorAlly       = plugin.getConfigManager().getString("map.colors.ally",       "§9");
        colorTruce      = plugin.getConfigManager().getString("map.colors.truce",      "§e");
        colorEnemy      = plugin.getConfigManager().getString("map.colors.enemy",      "§c");
        colorNeutral    = plugin.getConfigManager().getString("map.colors.neutral",    "§f");
        colorPlayer     = plugin.getConfigManager().getString("map.colors.player",     "§d");

        hoverEnabled    = plugin.getConfigManager().getBoolean("map.hover.enabled",    true);
        hoverPlayer     = plugin.getConfigManager().getString("map.hover.player",      "§dMoi");
        hoverWilderness = plugin.getConfigManager().getString("map.hover.wilderness",  "");
        hoverSafezone   = plugin.getConfigManager().getString("map.hover.safezone",    "§aSafezone");
        hoverWarzone    = plugin.getConfigManager().getString("map.hover.warzone",     "§4Warzone");
        hoverOwn        = plugin.getConfigManager().getString("map.hover.own",         "§2{name}");
        hoverAlly       = plugin.getConfigManager().getString("map.hover.ally",        "§9Allié: {name}");
        hoverTruce      = plugin.getConfigManager().getString("map.hover.truce",       "§eTrêve: {name}");
        hoverEnemy      = plugin.getConfigManager().getString("map.hover.enemy",       "§cEnnemi: {name}");
        hoverNeutral    = plugin.getConfigManager().getString("map.hover.neutral",     "§fNeutre: {name}");

        plugin.getLogger().info("MapManager initialisé (" + mapWidth + "x" + mapHeight + ")");
    }
    
    /**
     * Ferme le manager
     */
    public void shutdown() {
        autoMapPlayers.clear();
        autoMapCooldowns.clear();
    }
    
    /**
     * Affiche la carte à un joueur
     * @param player Le joueur
     */
    public void showMap(Player player) {
        FLocation center = new FLocation(player.getLocation());
        showMap(player, center);
    }
    
    /**
     * Affiche la carte centrée sur une position (passage unique — O(W×H) getFactionAt)
     */
    public void showMap(Player player, FLocation center) {
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);

        int halfWidth  = mapWidth  / 2;
        int halfHeight = mapHeight / 2;

        // Passage unique : toutes les factions sont resolues une seule fois
        Faction[][] grid = new Faction[mapHeight][mapWidth];
        for (int zi = 0; zi < mapHeight; zi++) {
            int z = zi - halfHeight;
            for (int xi = 0; xi < mapWidth; xi++) {
                int x = xi - halfWidth;
                grid[zi][xi] = plugin.getClaimManager().getFactionAt(
                    new FLocation(center.getWorldName(), center.getX() + x, center.getZ() + z));
            }
        }

        // Entete avec direction
        String direction = getCardinalDirection(player.getLocation().getYaw());
        player.sendMessage(colorOwn + "-------" + colorOwn + "[" + "§e§l Carte " + colorOwn + "]" + colorOwn + "-------§r §7" + direction);

        // Rendu de la grille
        for (int zi = 0; zi < mapHeight; zi++) {
            int z = zi - halfHeight;
            if (hoverEnabled) {
                List<TextComponent> cells = new ArrayList<>();
                for (int xi = 0; xi < mapWidth; xi++) {
                    int x = xi - halfWidth;
                    TextComponent cell;
                    if (x == 0 && z == 0) {
                        cell = new TextComponent(colorPlayer + playerSymbol);
                        if (!hoverPlayer.isEmpty()) {
                            cell.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(hoverPlayer)));
                        }
                    } else {
                        Faction faction = grid[zi][xi];
                        cell = new TextComponent(getColorForFaction(faction, playerFaction)
                                + getSymbolForFaction(faction));
                        String hoverText = getHoverForFaction(faction, playerFaction);
                        if (!hoverText.isEmpty()) {
                            cell.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(hoverText)));
                        }
                    }
                    cells.add(cell);
                }
                player.spigot().sendMessage(cells.toArray(new BaseComponent[0]));
            } else {
                StringBuilder line = new StringBuilder();
                for (int xi = 0; xi < mapWidth; xi++) {
                    int x = xi - halfWidth;
                    if (x == 0 && z == 0) {
                        line.append(colorPlayer).append(playerSymbol);
                        continue;
                    }
                    Faction faction = grid[zi][xi];
                    line.append(getColorForFaction(faction, playerFaction))
                        .append(getSymbolForFaction(faction));
                }
                player.sendMessage(line.toString());
            }
        }

        // Legende de couleurs (une seule ligne, compacte)
        StringBuilder legend = new StringBuilder("§7 ");
        legend.append(colorOwn).append("■ §7Soi  ");
        legend.append(colorAlly).append("■ §7Allie  ");
        legend.append(colorTruce).append("■ §7Treve  ");
        legend.append(colorEnemy).append("■ §7Ennemi  ");
        legend.append(colorNeutral).append("■ §7Neutre");
        player.sendMessage(legend.toString());

        // Zone actuelle
        Faction currentFaction = grid[halfHeight][halfWidth];
        String  currentColor   = getColorForFaction(currentFaction, playerFaction);
        player.sendMessage("§7Zone: " + currentColor + currentFaction.getName()
            + " §7| Chunk: §f" + center.getX() + ", " + center.getZ());
    }

    /**
     * Affiche la carte avec throttle — à appeler depuis l'auto-map (TerritoryListener).
     * Limite à un affichage toutes les AUTO_MAP_COOLDOWN_MS ms par joueur.
     */
    public void autoShowMap(Player player, FLocation center) {
        long now  = System.currentTimeMillis();
        Long last = autoMapCooldowns.get(player.getUniqueId());
        if (last != null && now - last < AUTO_MAP_COOLDOWN_MS) return;
        autoMapCooldowns.put(player.getUniqueId(), now);
        showMap(player, center);
    }
    
    /**
     * Obtient la couleur pour une faction selon la relation
     */
    private String getColorForFaction(Faction faction, Faction playerFaction) {
        if (faction.isWilderness()) return colorWilderness;
        if (faction.isSafezone())   return colorSafezone;
        if (faction.isWarzone())    return colorWarzone;

        if (playerFaction != null) {
            if (faction.getId().equals(playerFaction.getId())) return colorOwn;
            Relation rel = playerFaction.getRelationTo(faction);
            switch (rel) {
                case ALLY:  return colorAlly;
                case TRUCE: return colorTruce;
                case ENEMY: return colorEnemy;
                default:    return colorNeutral;
            }
        }
        return colorNeutral;
    }

    private String getSymbolForFaction(Faction faction) {
        if (faction.isWilderness()) return wildernessSymbol;
        if (faction.isSafezone())   return safezoneSymbol;
        if (faction.isWarzone())    return warzoneSymbol;
        return claimSymbol;
    }

    private String getHoverForFaction(Faction faction, Faction playerFaction) {
        if (faction.isWilderness()) return hoverWilderness;
        if (faction.isSafezone())   return hoverSafezone;
        if (faction.isWarzone())    return hoverWarzone;

        String template;
        if (playerFaction != null) {
            if (faction.getId().equals(playerFaction.getId())) {
                template = hoverOwn;
            } else {
                Relation rel = playerFaction.getRelationTo(faction);
                switch (rel) {
                    case ALLY:  template = hoverAlly;    break;
                    case TRUCE: template = hoverTruce;   break;
                    case ENEMY: template = hoverEnemy;   break;
                    default:    template = hoverNeutral; break;
                }
            }
        } else {
            template = hoverNeutral;
        }
        return template.replace("{name}", faction.getName());
    }
    
    /**
     * Obtient la direction cardinale depuis un yaw Minecraft.
     * Minecraft : yaw 0 = Sud, 90 = Ouest, 180 = Nord, 270 = Est
     */
    private String getCardinalDirection(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        // Index 0 = S car yaw 0 → Sud en Minecraft
        String[] compass = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        int index = (int) Math.round(yaw / 45.0) % 8;
        return "§e" + compass[index];
    }
    
    // === Auto-map ===
    
    /**
     * Active/désactive l'auto-map pour un joueur
     * @param player Le joueur
     * @return true si activé, false si désactivé
     */
    public boolean toggleAutoMap(Player player) {
        UUID uuid = player.getUniqueId();
        if (autoMapPlayers.contains(uuid)) {
            autoMapPlayers.remove(uuid);
            return false;
        } else {
            autoMapPlayers.add(uuid);
            return true;
        }
    }
    
    /**
     * Vérifie si l'auto-map est activé pour un joueur
     * @param uuid UUID du joueur
     * @return true si activé
     */
    public boolean hasAutoMap(UUID uuid) {
        return autoMapPlayers.contains(uuid);
    }
    
    /**
     * @return Les UUIDs des joueurs avec auto-map
     */
    public Set<UUID> getAutoMapPlayers() {
        return Collections.unmodifiableSet(autoMapPlayers);
    }
    
    /**
     * @return La largeur de la carte
     */
    public int getMapWidth() {
        return mapWidth;
    }
    
    /**
     * @return La hauteur de la carte
     */
    public int getMapHeight() {
        return mapHeight;
    }
}
