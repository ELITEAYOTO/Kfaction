package me.krunsh.kfaction.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.zones.ZoneDefinition;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Map V2.
 *
 * Principes:
 * - nord toujours en haut;
 * - aucune lecture/chargement de Chunk Bukkit;
 * - un seul lookup territoire par cellule;
 * - auto-map déclenchée uniquement lors d'un changement de chunk;
 * - aucune dépendance au yaw pour déclencher un refresh;
 * - FPlayer est la source canonique du toggle auto-map.
 */
public final class MapManager {

    private enum ClaimedSymbolMode {
        FIXED,
        TAG_INITIAL,
        NAME_INITIAL
    }

    private enum Facing {
        SOUTH("S", "▼"),
        SOUTH_WEST("SO", "↙"),
        WEST("O", "◀"),
        NORTH_WEST("NO", "↖"),
        NORTH("N", "▲"),
        NORTH_EAST("NE", "↗"),
        EAST("E", "▶"),
        SOUTH_EAST("SE", "↘");

        private final String label;
        private final String defaultSymbol;

        Facing(
                String label,
                String defaultSymbol
        ) {
            this.label = label;
            this.defaultSymbol = defaultSymbol;
        }
    }

    private final Kfaction plugin;

    /*
     * Protection supplémentaire contre les appels externes répétés.
     * TerritoryListener filtre déjà les mouvements dans le même chunk.
     */
    private final ConcurrentMap<UUID, FLocation>
            lastAutoCenters;

    private int mapWidth;
    private int mapHeight;

    private boolean hoverEnabled;
    private boolean legendEnabled;
    private boolean autoMapEnabled;
    private boolean uniformCellSymbols;

    private ClaimedSymbolMode claimedSymbolMode;
    private String uniformCellSymbol;

    private String wildernessSymbol;
    private String safezoneSymbol;
    private String warzoneSymbol;
    private String claimSymbol;

    private String colorWilderness;
    private String colorSafezone;
    private String colorWarzone;
    private String colorOwn;
    private String colorAlly;
    private String colorTruce;
    private String colorEnemy;
    private String colorNeutral;
    private String colorPlayer;

    private String headerTemplate;
    private String footerTemplate;

    private String legendOwn;
    private String legendAlly;
    private String legendTruce;
    private String legendEnemy;
    private String legendNeutral;
    private String legendWilderness;
    private String legendSafezone;
    private String legendWarzone;
    private String legendPlayer;

    private String hoverPlayer;
    private String hoverWilderness;
    private String hoverSafezone;
    private String hoverWarzone;
    private String hoverGlobalZone;
    private String hoverOwn;
    private String hoverAlly;
    private String hoverTruce;
    private String hoverEnemy;
    private String hoverNeutral;

    private final String[] facingSymbols;

    public MapManager(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        this.lastAutoCenters =
                new ConcurrentHashMap<UUID, FLocation>();

        this.facingSymbols =
                new String[Facing.values().length];

        applyDefaults();
    }

    public void initialize() {
        reload();

        plugin.getLogger().info(
                "MapManager V2 initialisé ("
                        + mapWidth
                        + "x"
                        + mapHeight
                        + ", auto=chunk-change)"
        );
    }

    public void reload() {
        mapWidth =
                makeOdd(
                        clamp(
                                plugin.getConfigManager()
                                        .getInt(
                                                "map.width",
                                                31
                                        ),
                                9,
                                39
                        )
                );

        mapHeight =
                makeOdd(
                        clamp(
                                plugin.getConfigManager()
                                        .getInt(
                                                "map.height",
                                                9
                                        ),
                                5,
                                15
                        )
                );

        hoverEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "map.hover.enabled",
                                true
                        );

        legendEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "map.legend.enabled",
                                true
                        );

        autoMapEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "map.auto.enabled",
                                true
                        );

        claimedSymbolMode =
                parseClaimedSymbolMode(
                        plugin.getConfigManager()
                                .getString(
                                        "map.claimed-symbol-mode",
                                        "FIXED"
                                )
                );

        uniformCellSymbols =
                plugin.getConfigManager()
                        .getBoolean(
                                "map.uniform-cells.enabled",
                                true
                        );

        uniformCellSymbol =
                normalizeCellSymbol(
                        plugin.getConfigManager()
                                .getString(
                                        "map.uniform-cells.symbol",
                                        "■"
                                ),
                        "■"
                );

        wildernessSymbol =
                symbol(
                        "map.symbols.wilderness",
                        "·"
                );

        safezoneSymbol =
                symbol(
                        "map.symbols.safezone",
                        "S"
                );

        warzoneSymbol =
                symbol(
                        "map.symbols.warzone",
                        "W"
                );

        claimSymbol =
                symbol(
                        "map.symbols.claim",
                        "■"
                );

        colorWilderness =
                color(
                        "map.colors.wilderness",
                        "&8"
                );

        colorSafezone =
                color(
                        "map.colors.safezone",
                        "&a"
                );

        colorWarzone =
                color(
                        "map.colors.warzone",
                        "&4"
                );

        colorOwn =
                color(
                        "map.colors.own",
                        "&2"
                );

        colorAlly =
                color(
                        "map.colors.ally",
                        "&d"
                );

        colorTruce =
                color(
                        "map.colors.truce",
                        "&e"
                );

        colorEnemy =
                color(
                        "map.colors.enemy",
                        "&c"
                );

        colorNeutral =
                color(
                        "map.colors.neutral",
                        "&f"
                );

        colorPlayer =
                color(
                        "map.colors.player",
                        "&b"
                );

        headerTemplate =
                text(
                        "map.header",
                        "&8&m-------&r &6&lCarte des territoires "
                                + "&8&m-------&r &7Face: &e{direction}"
                );

        footerTemplate =
                text(
                        "map.footer",
                        "&7Zone: {zone_color}{zone_name} "
                                + "&8| &7Relation: {relation_color}{relation} "
                                + "&8| &7Chunk: &f{x}, {z}"
                );

        legendOwn =
                text(
                        "map.legend.own",
                        "Soi"
                );

        legendAlly =
                text(
                        "map.legend.ally",
                        "Allié"
                );

        legendTruce =
                text(
                        "map.legend.truce",
                        "Trêve"
                );

        legendEnemy =
                text(
                        "map.legend.enemy",
                        "Ennemi"
                );

        legendNeutral =
                text(
                        "map.legend.neutral",
                        "Neutre"
                );

        legendWilderness =
                text(
                        "map.legend.wilderness",
                        "Wilderness"
                );

        legendSafezone =
                text(
                        "map.legend.safezone",
                        "SafeZone"
                );

        legendWarzone =
                text(
                        "map.legend.warzone",
                        "WarZone"
                );

        legendPlayer =
                text(
                        "map.legend.player",
                        "Toi"
                );

        hoverPlayer =
                text(
                        "map.hover.player",
                        "&bToi\n&7Chunk: &f{x}, {z}\n"
                                + "&7Zone: {zone_color}{zone_name}"
                );

        hoverWilderness =
                text(
                        "map.hover.wilderness",
                        "&8Wilderness\n&7Chunk: &f{x}, {z}"
                );

        hoverSafezone =
                text(
                        "map.hover.safezone",
                        "&aSafeZone\n&7Chunk: &f{x}, {z}"
                );

        hoverWarzone =
                text(
                        "map.hover.warzone",
                        "&4WarZone\n&7Chunk: &f{x}, {z}"
                );

        hoverGlobalZone =
                text(
                        "map.hover.global-zone",
                        "{zone_color}{zone_name}\n&7ID: &f{zone_id}\n"
                                + "&7Chunk: &f{x}, {z}\n&7PvP: &f{pvp}"
                );

        hoverOwn =
                text(
                        "map.hover.own",
                        "&2{name} &8[{tag}]\n"
                                + "&7Relation: &aSoi\n"
                                + "&7Claims: &f{claims}\n"
                                + "&7Membres: &f{members}\n"
                                + "&7Chunk: &f{x}, {z}"
                );

        hoverAlly =
                text(
                        "map.hover.ally",
                        "&d{name} &8[{tag}]\n"
                                + "&7Relation: &dAllié\n"
                                + "&7Claims: &f{claims}\n"
                                + "&7Chunk: &f{x}, {z}"
                );

        hoverTruce =
                text(
                        "map.hover.truce",
                        "&e{name} &8[{tag}]\n"
                                + "&7Relation: &eTrêve\n"
                                + "&7Claims: &f{claims}\n"
                                + "&7Chunk: &f{x}, {z}"
                );

        hoverEnemy =
                text(
                        "map.hover.enemy",
                        "&c{name} &8[{tag}]\n"
                                + "&7Relation: &cEnnemi\n"
                                + "&7Claims: &f{claims}\n"
                                + "&7Chunk: &f{x}, {z}"
                );

        hoverNeutral =
                text(
                        "map.hover.neutral",
                        "&f{name} &8[{tag}]\n"
                                + "&7Relation: &fNeutre\n"
                                + "&7Claims: &f{claims}\n"
                                + "&7Chunk: &f{x}, {z}"
                );

        loadFacingSymbols();

        lastAutoCenters.clear();
    }

    public void shutdown() {
        lastAutoCenters.clear();
    }

    // ============================================================
    // Render
    // ============================================================

    public void showMap(
            Player player
    ) {
        if (player == null) {
            return;
        }

        showMap(
                player,
                new FLocation(
                        player.getLocation()
                )
        );
    }

    public void showMap(
            Player player,
            FLocation center
    ) {
        if (player == null
                || center == null) {
            return;
        }

        Faction playerFaction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player
                        );

        int halfWidth =
                mapWidth / 2;

        int halfHeight =
                mapHeight / 2;

        /*
         * Snapshot runtime:
         * aucun Bukkit Chunk n'est chargé.
         */
        Faction[][] grid =
                new Faction[mapHeight][mapWidth];

        ZoneDefinition[][] zoneGrid =
                new ZoneDefinition[mapHeight][mapWidth];

        for (int row = 0;
                row < mapHeight;
                row++) {
            int dz =
                    row - halfHeight;

            for (int column = 0;
                    column < mapWidth;
                    column++) {
                int dx =
                        column - halfWidth;

                FLocation cellLocation =
                        new FLocation(
                                center.getWorldName(),
                                center.getX() + dx,
                                center.getZ() + dz
                        );

                ZoneDefinition zone =
                        plugin.getClaimManager()
                                .getZoneDefinitionAt(
                                        cellLocation
                                );

                zoneGrid[row][column] =
                        zone;

                grid[row][column] =
                        zone == null
                                ? plugin.getClaimManager()
                                        .getPlayerFactionAt(
                                                cellLocation
                                        )
                                : plugin.getFactionManager()
                                        .getWilderness();
            }
        }

        Facing facing =
                getFacing(
                        player.getLocation()
                                .getYaw()
                );

        player.sendMessage(
                replace(
                        headerTemplate,
                        "{direction}",
                        facing.label
                )
        );

        for (int row = 0;
                row < mapHeight;
                row++) {
            int dz =
                    row - halfHeight;

            if (hoverEnabled) {
                sendHoverRow(
                        player,
                        playerFaction,
                        center,
                        grid[row],
                        zoneGrid[row],
                        dz,
                        halfWidth,
                        facing
                );
            } else {
                sendLegacyRow(
                        player,
                        playerFaction,
                        grid[row],
                        zoneGrid[row],
                        dz,
                        halfWidth,
                        facing
                );
            }
        }

        if (legendEnabled) {
            sendLegend(player);
        }

        Faction current =
                grid[halfHeight][halfWidth];

        ZoneDefinition currentZone =
                zoneGrid[halfHeight][halfWidth];

        RelationView relation =
                currentZone != null
                        ? new RelationView(
                                "Zone globale",
                                colorize(
                                        currentZone.getColor()
                                )
                        )
                        : relationView(
                                current,
                                playerFaction
                        );

        String footer =
                footerTemplate;

        footer =
                replace(
                        footer,
                        "{zone_color}",
                        currentZone != null
                                ? colorize(
                                        currentZone.getColor()
                                )
                                : colorForFaction(
                                        current,
                                        playerFaction
                                )
                );

        footer =
                replace(
                        footer,
                        "{zone_name}",
                        currentZone != null
                                ? currentZone.getDisplayName()
                                : displayFactionName(
                                        current
                                )
                );

        footer =
                replace(
                        footer,
                        "{relation_color}",
                        relation.color
                );

        footer =
                replace(
                        footer,
                        "{relation}",
                        relation.label
                );

        footer =
                replace(
                        footer,
                        "{x}",
                        String.valueOf(
                                center.getX()
                        )
                );

        footer =
                replace(
                        footer,
                        "{z}",
                        String.valueOf(
                                center.getZ()
                        )
                );

        player.sendMessage(footer);
    }

    private void sendHoverRow(
            Player player,
            Faction playerFaction,
            FLocation center,
            Faction[] row,
            ZoneDefinition[] zones,
            int dz,
            int halfWidth,
            Facing facing
    ) {
        List<BaseComponent> components =
                new ArrayList<BaseComponent>(
                        row.length * 2
                );

        for (int column = 0;
                column < row.length;
                column++) {
            int dx =
                    column - halfWidth;

            int chunkX =
                    center.getX() + dx;

            int chunkZ =
                    center.getZ() + dz;

            Faction faction =
                    row[column];

            ZoneDefinition zone =
                    zones != null
                            && column < zones.length
                                    ? zones[column]
                                    : null;

            boolean playerCell =
                    dx == 0
                            && dz == 0;

            String legacyCell;
            String hover;

            if (playerCell) {
                legacyCell =
                        colorPlayer
                                + getFacingSymbol(
                                        facing
                                );

                hover =
                        zone != null
                                ? renderZoneHover(
                                        hoverPlayer,
                                        zone,
                                        chunkX,
                                        chunkZ
                                )
                                : renderHover(
                                        hoverPlayer,
                                        faction,
                                        playerFaction,
                                        chunkX,
                                        chunkZ
                                );

            } else {
                if (zone != null) {
                    legacyCell =
                            colorize(
                                    zone.getColor()
                            )
                                    + cellSymbol(
                                            colorize(
                                                    zone.getMapSymbol()
                                            )
                                    );

                    hover =
                            renderZoneHover(
                                    hoverGlobalZone,
                                    zone,
                                    chunkX,
                                    chunkZ
                            );
                } else {
                    legacyCell =
                            colorForFaction(
                                    faction,
                                    playerFaction
                            )
                                    + cellSymbol(
                                            symbolForFaction(
                                                    faction
                                            )
                                    );

                    hover =
                            renderHover(
                                    hoverTemplate(
                                            faction,
                                            playerFaction
                                    ),
                                    faction,
                                    playerFaction,
                                    chunkX,
                                    chunkZ
                            );
                }
            }

            appendLegacyCell(
                    components,
                    legacyCell,
                    hover
            );
        }

        player.spigot()
                .sendMessage(
                        components.toArray(
                                new BaseComponent[
                                        components.size()
                                ]
                        )
                );
    }

    private void sendLegacyRow(
            Player player,
            Faction playerFaction,
            Faction[] row,
            ZoneDefinition[] zones,
            int dz,
            int halfWidth,
            Facing facing
    ) {
        StringBuilder line =
                new StringBuilder(
                        row.length * 3
                );

        for (int column = 0;
                column < row.length;
                column++) {
            int dx =
                    column - halfWidth;

            if (dx == 0
                    && dz == 0) {
                line.append(colorPlayer)
                        .append(
                                getFacingSymbol(
                                        facing
                                )
                        );

                continue;
            }

            Faction faction =
                    row[column];

            ZoneDefinition zone =
                    zones != null
                            && column < zones.length
                                    ? zones[column]
                                    : null;

            if (zone != null) {
                line.append(
                        colorize(
                                zone.getColor()
                        )
                );

                line.append(
                        cellSymbol(
                                colorize(
                                        zone.getMapSymbol()
                                )
                        )
                );
            } else {
                line.append(
                        colorForFaction(
                                faction,
                                playerFaction
                        )
                );

                line.append(
                        cellSymbol(
                                symbolForFaction(
                                        faction
                                )
                        )
                );
            }
        }

        player.sendMessage(
                line.toString()
        );
    }

    private void sendLegend(
            Player player
    ) {
        String claimed =
                cellSymbol(
                        claimedSymbolMode
                                == ClaimedSymbolMode.FIXED
                                ? claimSymbol
                                : "#"
                );

        player.sendMessage(
                "§8"
                        + claimed
                        + " "
                        + colorOwn
                        + legendOwn
                        + " §8| "
                        + colorAlly
                        + legendAlly
                        + " §8| "
                        + colorTruce
                        + legendTruce
                        + " §8| "
                        + colorEnemy
                        + legendEnemy
                        + " §8| "
                        + colorNeutral
                        + legendNeutral
        );

        player.sendMessage(
                colorWilderness
                        + cellSymbol(
                                wildernessSymbol
                        )
                        + " "
                        + legendWilderness
                        + " §8| "
                        + colorPlayer
                        + "▲ "
                        + legendPlayer
        );

        StringBuilder zonesLine =
                new StringBuilder();

        for (ZoneDefinition zone
                : plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionList()) {
            if (zonesLine.length() > 0) {
                zonesLine.append(
                        " §8| "
                );
            }

            zonesLine.append(
                    colorize(
                            zone.getColor()
                    )
            ).append(
                    cellSymbol(
                            colorize(
                                    zone.getMapSymbol()
                            )
                    )
            ).append(
                    " "
            ).append(
                    colorize(
                            zone.getColor()
                                    + zone.getDisplayName()
                    )
            );
        }

        if (zonesLine.length() > 0) {
            player.sendMessage(
                    zonesLine.toString()
            );
        }
    }

    // ============================================================
    // Auto-map
    // ============================================================

    /**
     * Aucun throttle temporel.
     *
     * La seule condition est un centre de chunk réellement différent.
     */
    public void autoShowMap(
            Player player,
            FLocation center
    ) {
        if (!autoMapEnabled
                || player == null
                || center == null) {
            return;
        }

        UUID playerId =
                player.getUniqueId();

        FLocation previous =
                lastAutoCenters.put(
                        playerId,
                        center
                );

        if (center.equals(previous)) {
            return;
        }

        showMap(
                player,
                center
        );
    }

    public void clearAutoState(
            UUID playerId
    ) {
        if (playerId != null) {
            lastAutoCenters.remove(
                    playerId
            );
        }
    }

    /**
     * Compatibilité V1.
     * La source canonique est maintenant FPlayer.mapAutoUpdateEnabled.
     */
    public boolean toggleAutoMap(
            Player player
    ) {
        if (player == null) {
            return false;
        }

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .getOrCreate(player);

        boolean enabled =
                !fPlayer.isMapAutoUpdateEnabled();

        fPlayer.setMapAutoUpdateEnabled(
                enabled
        );

        plugin.getStorageManager()
                .markDirty(fPlayer);

        if (!enabled) {
            clearAutoState(
                    player.getUniqueId()
            );
        }

        return enabled;
    }

    public boolean hasAutoMap(
            UUID playerId
    ) {
        FPlayer fPlayer =
                playerId != null
                        ? plugin.getFPlayerManager()
                                .findLoaded(playerId)
                        : null;

        return fPlayer != null
                && fPlayer.isMapAutoUpdateEnabled();
    }

    public Set<UUID> getAutoMapPlayers() {
        Set<UUID> result =
                new HashSet<UUID>();

        for (FPlayer fPlayer
                : plugin.getFPlayerManager()
                        .getAllPlayers()) {
            if (fPlayer != null
                    && fPlayer
                            .isMapAutoUpdateEnabled()) {
                result.add(
                        fPlayer.getUuid()
                );
            }
        }

        return Collections.unmodifiableSet(
                result
        );
    }

    public boolean isAutoMapEnabledGlobally() {
        return autoMapEnabled;
    }

    // ============================================================
    // Faction rendering
    // ============================================================

    private String colorForFaction(
            Faction faction,
            Faction playerFaction
    ) {
        if (faction == null
                || faction.isWilderness()) {
            return colorWilderness;
        }

        if (faction.isSafezone()) {
            return colorSafezone;
        }

        if (faction.isWarzone()) {
            return colorWarzone;
        }

        if (playerFaction == null) {
            return colorNeutral;
        }

        if (faction.getId()
                .equals(
                        playerFaction.getId()
                )) {
            return colorOwn;
        }

        Relation relation =
                playerFaction.getRelationTo(
                        faction
                );

        if (relation == Relation.ALLY) {
            return colorAlly;
        }

        if (relation == Relation.TRUCE) {
            return colorTruce;
        }

        if (relation == Relation.ENEMY) {
            return colorEnemy;
        }

        return colorNeutral;
    }

    private String symbolForFaction(
            Faction faction
    ) {
        if (faction == null
                || faction.isWilderness()) {
            return wildernessSymbol;
        }

        if (faction.isSafezone()) {
            return safezoneSymbol;
        }

        if (faction.isWarzone()) {
            return warzoneSymbol;
        }

        if (claimedSymbolMode
                == ClaimedSymbolMode.FIXED) {
            return claimSymbol;
        }

        String source =
                claimedSymbolMode
                        == ClaimedSymbolMode.TAG_INITIAL
                        ? faction.getTag()
                        : faction.getName();

        String initial =
                firstVisibleCharacter(
                        source
                );

        return initial != null
                ? initial
                : claimSymbol;
    }

    private String cellSymbol(
            String configuredSymbol
    ) {
        return selectCellSymbol(
                uniformCellSymbols,
                uniformCellSymbol,
                configuredSymbol
        );
    }

    private String hoverTemplate(
            Faction faction,
            Faction playerFaction
    ) {
        if (faction == null
                || faction.isWilderness()) {
            return hoverWilderness;
        }

        if (faction.isSafezone()) {
            return hoverSafezone;
        }

        if (faction.isWarzone()) {
            return hoverWarzone;
        }

        if (playerFaction != null
                && faction.getId()
                        .equals(
                                playerFaction.getId()
                        )) {
            return hoverOwn;
        }

        Relation relation =
                playerFaction != null
                        ? playerFaction.getRelationTo(
                                faction
                        )
                        : Relation.NEUTRAL;

        if (relation == Relation.ALLY) {
            return hoverAlly;
        }

        if (relation == Relation.TRUCE) {
            return hoverTruce;
        }

        if (relation == Relation.ENEMY) {
            return hoverEnemy;
        }

        return hoverNeutral;
    }

    private String renderHover(
            String template,
            Faction faction,
            Faction playerFaction,
            int chunkX,
            int chunkZ
    ) {
        if (template == null
                || template.isEmpty()) {
            return "";
        }

        RelationView relation =
                relationView(
                        faction,
                        playerFaction
                );

        String result =
                template;

        result =
                replace(
                        result,
                        "{name}",
                        displayFactionName(
                                faction
                        )
                );

        result =
                replace(
                        result,
                        "{tag}",
                        safeTag(faction)
                );

        result =
                replace(
                        result,
                        "{claims}",
                        String.valueOf(
                                faction != null
                                        && !faction.isSystemFaction()
                                        ? faction.getClaimCount()
                                        : 0
                        )
                );

        result =
                replace(
                        result,
                        "{members}",
                        String.valueOf(
                                faction != null
                                        && !faction.isSystemFaction()
                                        ? faction.getMemberCount()
                                        : 0
                        )
                );

        result =
                replace(
                        result,
                        "{relation}",
                        relation.label
                );

        result =
                replace(
                        result,
                        "{relation_color}",
                        relation.color
                );

        result =
                replace(
                        result,
                        "{zone_color}",
                        colorForFaction(
                                faction,
                                playerFaction
                        )
                );

        result =
                replace(
                        result,
                        "{zone_name}",
                        displayFactionName(
                                faction
                        )
                );

        result =
                replace(
                        result,
                        "{x}",
                        String.valueOf(
                                chunkX
                        )
                );

        result =
                replace(
                        result,
                        "{z}",
                        String.valueOf(
                                chunkZ
                        )
                );

        return result;
    }

    private String renderZoneHover(
            String template,
            ZoneDefinition zone,
            int chunkX,
            int chunkZ
    ) {
        if (template == null
                || template.isEmpty()
                || zone == null) {
            return "";
        }

        String result =
                template;

        result =
                replace(
                        result,
                        "{zone_id}",
                        zone.getId()
                );

        result =
                replace(
                        result,
                        "{zone_name}",
                        zone.getDisplayName()
                );

        result =
                replace(
                        result,
                        "{zone_color}",
                        colorize(
                                zone.getColor()
                        )
                );

        result =
                replace(
                        result,
                        "{pvp}",
                        zone.isPvpAllowed()
                                ? "Oui"
                                : "Non"
                );

        result =
                replace(
                        result,
                        "{x}",
                        String.valueOf(
                                chunkX
                        )
                );

        result =
                replace(
                        result,
                        "{z}",
                        String.valueOf(
                                chunkZ
                        )
                );

        return result;
    }

    private RelationView relationView(
            Faction faction,
            Faction playerFaction
    ) {
        if (faction == null
                || faction.isWilderness()) {
            return new RelationView(
                    "Wilderness",
                    colorWilderness
            );
        }

        if (faction.isSafezone()) {
            return new RelationView(
                    "SafeZone",
                    colorSafezone
            );
        }

        if (faction.isWarzone()) {
            return new RelationView(
                    "WarZone",
                    colorWarzone
            );
        }

        if (playerFaction != null
                && faction.getId()
                        .equals(
                                playerFaction.getId()
                        )) {
            return new RelationView(
                    "Soi",
                    colorOwn
            );
        }

        Relation relation =
                playerFaction != null
                        ? playerFaction.getRelationTo(
                                faction
                        )
                        : Relation.NEUTRAL;

        if (relation == Relation.ALLY) {
            return new RelationView(
                    "Allié",
                    colorAlly
            );
        }

        if (relation == Relation.TRUCE) {
            return new RelationView(
                    "Trêve",
                    colorTruce
            );
        }

        if (relation == Relation.ENEMY) {
            return new RelationView(
                    "Ennemi",
                    colorEnemy
            );
        }

        return new RelationView(
                "Neutre",
                colorNeutral
        );
    }

    // ============================================================
    // Direction
    // ============================================================

    private Facing getFacing(
            float yaw
    ) {
        float normalized =
                yaw % 360.0F;

        if (normalized < 0.0F) {
            normalized += 360.0F;
        }

        int index =
                ((int) Math.floor(
                        (normalized + 22.5F)
                                / 45.0F
                )) % 8;

        return Facing.values()[index];
    }

    private String getFacingSymbol(
            Facing facing
    ) {
        String value =
                facingSymbols[
                        facing.ordinal()
                ];

        return value != null
                && !value.isEmpty()
                ? value
                : facing.defaultSymbol;
    }

    private void loadFacingSymbols() {
        Facing[] values =
                Facing.values();

        for (int i = 0;
                i < values.length;
                i++) {
            Facing facing =
                    values[i];

            facingSymbols[i] =
                    normalizeCellSymbol(
                            symbol(
                                    "map.symbols.player."
                                            + facing.name()
                                                    .toLowerCase(
                                                            Locale.ROOT
                                                    ),
                                    facing.defaultSymbol
                            ),
                            facing.defaultSymbol
                    );
        }
    }

    // ============================================================
    // Config helpers
    // ============================================================

    private void applyDefaults() {
        mapWidth = 31;
        mapHeight = 9;

        hoverEnabled = true;
        legendEnabled = true;
        autoMapEnabled = true;
        uniformCellSymbols = true;

        claimedSymbolMode =
                ClaimedSymbolMode.FIXED;

        uniformCellSymbol = "■";

        wildernessSymbol = "·";
        safezoneSymbol = "S";
        warzoneSymbol = "W";
        claimSymbol = "■";

        colorWilderness = "§8";
        colorSafezone = "§a";
        colorWarzone = "§4";
        colorOwn = "§2";
        colorAlly = "§d";
        colorTruce = "§e";
        colorEnemy = "§c";
        colorNeutral = "§f";
        colorPlayer = "§b";

        headerTemplate = "";
        footerTemplate = "";

        legendOwn = "Soi";
        legendAlly = "Allié";
        legendTruce = "Trêve";
        legendEnemy = "Ennemi";
        legendNeutral = "Neutre";
        legendWilderness = "Wilderness";
        legendSafezone = "SafeZone";
        legendWarzone = "WarZone";
        legendPlayer = "Toi";

        hoverPlayer = "";
        hoverWilderness = "";
        hoverSafezone = "";
        hoverWarzone = "";
        hoverGlobalZone = "";
        hoverOwn = "";
        hoverAlly = "";
        hoverTruce = "";
        hoverEnemy = "";
        hoverNeutral = "";

        for (Facing facing : Facing.values()) {
            facingSymbols[facing.ordinal()] =
                    facing.defaultSymbol;
        }
    }

    private String symbol(
            String path,
            String fallback
    ) {
        String value =
                plugin.getConfigManager()
                        .getString(
                                path,
                                fallback
                        );

        if (value == null
                || value.isEmpty()) {
            return fallback;
        }

        return colorize(value);
    }

    private String color(
            String path,
            String fallback
    ) {
        return colorize(
                plugin.getConfigManager()
                        .getString(
                                path,
                                fallback
                        )
        );
    }

    private String text(
            String path,
            String fallback
    ) {
        String value =
                plugin.getConfigManager()
                        .getString(
                                path,
                                fallback
                        );

        if (value == null) {
            value = fallback;
        }

        /*
         * YAML "\n" ou texte littéral "\\n".
         */
        value =
                value.replace(
                        "\\n",
                        "\n"
                );

        return colorize(value);
    }

    private static String colorize(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return ChatColor
                .translateAlternateColorCodes(
                        '&',
                        value
                );
    }

    static String normalizeCellSymbol(
            String source,
            String fallback
    ) {
        String glyph = firstVisibleGlyph(source);

        if (glyph != null) {
            return glyph;
        }

        glyph = firstVisibleGlyph(fallback);

        return glyph != null
                ? glyph
                : "■";
    }

    static String selectCellSymbol(
            boolean uniform,
            String uniformSymbol,
            String configuredSymbol
    ) {
        return uniform
                ? normalizeCellSymbol(
                        uniformSymbol,
                        "■"
                )
                : configuredSymbol;
    }

    private static ClaimedSymbolMode
            parseClaimedSymbolMode(
                    String value
            ) {
        if (value == null) {
            return ClaimedSymbolMode.TAG_INITIAL;
        }

        try {
            return ClaimedSymbolMode.valueOf(
                    value.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );
        } catch (IllegalArgumentException exception) {
            return ClaimedSymbolMode.TAG_INITIAL;
        }
    }

    private static String firstVisibleCharacter(
            String source
    ) {
        String glyph = firstVisibleGlyph(source);

        return glyph != null
                ? glyph.toUpperCase(Locale.ROOT)
                : null;
    }

    private static String firstVisibleGlyph(
            String source
    ) {
        if (source == null
                || source.trim().isEmpty()) {
            return null;
        }

        String stripped =
                ChatColor.stripColor(
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                source
                        )
                );

        if (stripped == null) {
            return null;
        }

        stripped = stripped.trim();

        if (stripped.isEmpty()) {
            return null;
        }

        int codePoint = stripped.codePointAt(0);

        return new String(
                Character.toChars(codePoint)
        );
    }

    private static String displayFactionName(
            Faction faction
    ) {
        if (faction == null
                || faction.isWilderness()) {
            return "Wilderness";
        }

        if (faction.isSafezone()) {
            return "SafeZone";
        }

        if (faction.isWarzone()) {
            return "WarZone";
        }

        return faction.getName() != null
                ? faction.getName()
                : faction.getId();
    }

    private static String safeTag(
            Faction faction
    ) {
        if (faction == null
                || faction.getTag() == null
                || faction.getTag()
                        .trim()
                        .isEmpty()) {
            return "-";
        }

        return faction.getTag();
    }

    private static void appendLegacyCell(
            List<BaseComponent> destination,
            String legacyCell,
            String hover
    ) {
        BaseComponent[] cell =
                TextComponent.fromLegacyText(
                        legacyCell != null
                                ? legacyCell
                                : ""
                );

        HoverEvent hoverEvent =
                hover != null
                && !hover.isEmpty()
                        ? new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(
                                        hover
                                )
                        )
                        : null;

        for (BaseComponent component : cell) {
            if (hoverEvent != null) {
                component.setHoverEvent(
                        hoverEvent
                );
            }

            destination.add(component);
        }
    }

    private static String replace(
            String input,
            String key,
            String value
    ) {
        return input != null
                ? input.replace(
                        key,
                        value != null
                                ? value
                                : ""
                )
                : "";
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private static int makeOdd(
            int value
    ) {
        return value % 2 == 0
                ? value + 1
                : value;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    private static final class RelationView {

        private final String label;
        private final String color;

        private RelationView(
                String label,
                String color
        ) {
            this.label = label;
            this.color = color;
        }
    }
}
