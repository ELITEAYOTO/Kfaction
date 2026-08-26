package me.krunsh.kfaction.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationSource;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.permissions.FactionCapability;
import me.krunsh.kfaction.services.claim.ClaimBatchResult;
import me.krunsh.kfaction.zones.ZoneDefinition;

/**
 * Listener des changements de chunk.
 *
 * L'auto-claim joueur passe désormais entièrement par ClaimService V2.
 */
public class TerritoryListener implements Listener {

    private final Kfaction plugin;
    private final Map<UUID, String> lastFactionAt;

    private boolean useTitles;

    public TerritoryListener(Kfaction plugin) {
        this.plugin = plugin;
        this.lastFactionAt =
                new ConcurrentHashMap<UUID, String>();
        this.useTitles = true;
    }

    public void loadConfig() {
        useTitles =
                plugin.getConfigManager()
                        .getBoolean(
                                "territory.use-titles",
                                false
                        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPlayerMove(
            PlayerMoveEvent event
    ) {
        Location from =
                event.getFrom();

        Location to =
                event.getTo();

        if (to == null) {
            return;
        }

        if ((from.getBlockX() >> 4)
                == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4)
                == (to.getBlockZ() >> 4)
                && from.getWorld() == to.getWorld()) {
            return;
        }

        Player player =
                event.getPlayer();

        FLocation toLocation =
                new FLocation(to);

        FPlayer fPlayer =
                plugin.getFPlayerManager()
                        .findLoaded(
                                player.getUniqueId()
                        );

        /*
         * Map V2:
         * mutations du nouveau chunk d'abord, rendu ensuite.
         *
         * Auto-claim + auto-map affiche donc le claim final immédiatement,
         * jamais l'ancien état Wilderness du même mouvement.
         */
        if (fPlayer != null
                && fPlayer.isAutoClaimEnabled()) {
            handleAutoClaim(
                    player,
                    fPlayer,
                    toLocation
            );
        }

        if (plugin.getClaimManager()
                .isAdminAutoClaiming(
                        player.getUniqueId()
                )) {
            handleAdminAutoClaim(
                    player,
                    toLocation
            );
        }

        if (plugin.getClaimManager()
                .isAdminAutoUnclaiming(
                        player.getUniqueId()
                )) {
            handleAdminAutoUnclaim(
                    player,
                    toLocation
            );
        }

        if (fPlayer != null
                && fPlayer.isMapAutoUpdateEnabled()) {
            plugin.getMapManager()
                    .autoShowMap(
                            player,
                            toLocation
                    );
        }

        ZoneDefinition globalZone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getDefinitionAt(
                                toLocation
                        );

        Faction factionTo =
                globalZone == null
                        ? plugin.getClaimManager()
                                .getFactionAt(
                                        toLocation
                                )
                        : plugin.getFactionManager()
                                .getWilderness();

        String territoryIdentity =
                globalZone != null
                        ? "zone:"
                                + globalZone.getId()
                        : "faction:"
                                + factionTo.getId();

        String lastId =
                lastFactionAt.put(
                        player.getUniqueId(),
                        territoryIdentity
                );

        if (territoryIdentity.equals(
                lastId
        )) {
            return;
        }

        Faction playerFaction =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player
                        );

        if (useTitles) {
            sendTerritoryTitle(
                    player,
                    globalZone,
                    factionTo,
                    playerFaction
            );
        } else {
            player.sendMessage(
                    plugin.getTerritoryManager()
                            .getZoneEnterMessage(
                                    toLocation,
                                    playerFaction
                            )
            );
        }
    }

    private void sendTerritoryTitle(
            Player player,
            ZoneDefinition globalZone,
            Faction factionAt,
            Faction playerFaction
    ) {
        String title;
        String subtitle = "";

        if (globalZone != null) {
            title =
                    globalZone.getTitle();

            subtitle =
                    globalZone.getSubtitle();

        } else if (factionAt.isWilderness()) {
            title =
                    plugin.getMessageManager()
                            .get(
                                    "territory.title.wilderness"
                            );

            subtitle =
                    plugin.getMessageManager()
                            .get(
                                    "territory.subtitle.wilderness"
                            );

        } else {
            String colorCode =
                    plugin.getTerritoryManager()
                            .getFactionDisplayColor(
                                    factionAt,
                                    playerFaction
                            );

            title =
                    colorCode
                            + factionAt.getName();

            if (!factionAt.getDescription()
                    .isEmpty()) {
                subtitle =
                        "&7"
                                + factionAt.getDescription();
            }
        }

        player.sendTitle(
                ChatColor.translateAlternateColorCodes(
                        '&',
                        title
                ),
                ChatColor.translateAlternateColorCodes(
                        '&',
                        subtitle
                )
        );
    }

    private void handleAdminAutoClaim(
            Player player,
            FLocation location
    ) {
        String zoneId =
                plugin.getClaimManager()
                        .getAdminAutoClaimType(
                                player.getUniqueId()
                        );

        if (zoneId == null) {
            return;
        }

        if (!plugin.getClaimManager()
                .getZoneService()
                .hasDefinition(
                        zoneId
                )) {
            plugin.getClaimManager()
                    .stopAdminAutoClaim(
                            player.getUniqueId()
                    );

            sendZoneMessage(
                    player,
                    "zone-admin.auto-definition-missing",
                    "&c[Zone Auto] Définition inconnue: &e{zone}&c. Mode désactivé.",
                    "{zone}",
                    zoneId
            );
            return;
        }

        String currentZone =
                plugin.getClaimManager()
                        .getZoneService()
                        .getZoneIdAt(
                                location
                        );

        if (zoneId.equals(
                currentZone
        )) {
            return;
        }

        OperationContext context =
                OperationContext.admin(
                        player.getUniqueId(),
                        player.getName()
                );

        OperationResult<String> result =
                plugin.getClaimManager()
                        .setZone(
                                location,
                                zoneId,
                                context
                        );

        if (!result.isSuccessful()) {
            sendZoneMessage(
                    player,
                    "zone-admin.auto-failure",
                    "&c[Zone Auto] Échec: &7{reason}",
                    "{reason}",
                    result.getDetail() != null
                            ? result.getDetail()
                            : result.getStatus()
                                    .name()
            );
            return;
        }

        ZoneDefinition definition =
                plugin.getClaimManager()
                        .getZoneService()
                        .getDefinition(
                                zoneId
                        );

        sendZoneMessage(
                player,
                "zone-admin.auto-walk-claim",
                "&a[Zone Auto] &f{zone} &7→ {x}, {z}",
                "{zone}",
                definition != null
                        ? definition.getDisplayName()
                        : zoneId,
                "{x}",
                location.getX(),
                "{z}",
                location.getZ()
        );
    }

    private void handleAdminAutoUnclaim(
            Player player,
            FLocation location
    ) {
        String zoneId =
                plugin.getClaimManager()
                        .getAdminAutoUnclaimType(
                                player.getUniqueId()
                        );

        if (zoneId == null) {
            return;
        }

        String current =
                plugin.getClaimManager()
                        .getZoneService()
                        .getZoneIdAt(
                                location
                        );

        if (!zoneId.equals(
                current
        )) {
            return;
        }

        OperationContext context =
                OperationContext.admin(
                        player.getUniqueId(),
                        player.getName()
                );

        OperationResult<String> result =
                plugin.getClaimManager()
                        .clearZone(
                                location,
                                zoneId,
                                context
                        );

        if (!result.isSuccess()) {
            return;
        }

        sendZoneMessage(
                player,
                "zone-admin.auto-walk-unclaim",
                "&c[Zone Auto-Unclaim] &f{zone} &7→ {x}, {z}",
                "{zone}",
                zoneId,
                "{x}",
                location.getX(),
                "{z}",
                location.getZ()
        );
    }

    private void sendZoneMessage(
            Player player,
            String path,
            String fallback,
            Object... replacements
    ) {
        String message =
                fallback;

        if (plugin.getMessageManager() != null
                && plugin.getMessageManager()
                        .has(path)) {
            message =
                    plugin.getMessageManager()
                            .get(
                                    path,
                                    replacements
                            );
        } else {
            for (int index = 0;
                    replacements != null
                            && index + 1 < replacements.length;
                    index += 2) {
                message =
                        message.replace(
                                String.valueOf(
                                        replacements[index]
                                ),
                                String.valueOf(
                                        replacements[index + 1]
                                )
                        );
            }

            message =
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            message
                    );
        }

        player.sendMessage(message);
    }

    private void handleAutoClaim(
            Player player,
            FPlayer fPlayer,
            FLocation location
    ) {
        if (!fPlayer.hasFaction()) {
            disableAutoClaim(fPlayer);
            return;
        }

        Faction faction =
                plugin.getFactionManager()
                        .getFaction(
                                fPlayer.getFactionId()
                        );

        if (faction == null) {
            disableAutoClaim(fPlayer);
            return;
        }

        if (!plugin.getPermissionManager().can(
                player,
                FactionCapability.AUTO_CLAIM
        )) {
            plugin.getMessageManager()
                    .send(
                            player,
                            "general.no-permission"
                    );

            disableAutoClaim(fPlayer);
            return;
        }

        OperationResult<ClaimBatchResult> result =
                plugin.getClaimManager()
                        .getService()
                        .claimSingle(
                                player,
                                faction,
                                location,
                                OperationContext.actor(
                                        player.getUniqueId(),
                                        player.getName(),
                                        OperationSource.SYSTEM
                                )
                        );

        if (result.isSuccess()) {
            plugin.getMessageManager()
                    .send(
                            player,
                            "claim.claimed",
                            "{x}",
                            String.valueOf(
                                    location.getX()
                            ),
                            "{z}",
                            String.valueOf(
                                    location.getZ()
                            )
                    );

            plugin.getLogManager()
                    .log(
                            faction.getId(),
                            LogType.TERRITORY_CLAIM,
                            player,
                            null,
                            "AUTO ["
                                    + location.getWorldName()
                                    + ", "
                                    + location.getX()
                                    + ", "
                                    + location.getZ()
                                    + "]"
                    );
        } else if (result.getStatus()
                == OperationResult.Status.FORBIDDEN) {
            disableAutoClaim(fPlayer);
        }
    }

    private void disableAutoClaim(
            FPlayer fPlayer
    ) {
        if (fPlayer == null
                || !fPlayer.isAutoClaimEnabled()) {
            return;
        }

        fPlayer.setAutoClaimEnabled(false);

        plugin.getStorageManager()
                .markDirty(fPlayer);
    }

    public void cleanup(
            UUID uuid
    ) {
        lastFactionAt.remove(uuid);

        if (plugin.getMapManager() != null) {
            plugin.getMapManager()
                    .clearAutoState(uuid);
        }
    }
}
