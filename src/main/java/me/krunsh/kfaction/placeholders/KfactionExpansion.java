package me.krunsh.kfaction.placeholders;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.MemberView;
import me.krunsh.kfaction.api.v2.PlayerView;
import me.krunsh.kfaction.api.v2.ProgressionView;
import me.krunsh.kfaction.api.v2.TerritoryView;
import me.krunsh.kfaction.data.FLocation;

/**
 * PlaceholderAPI V2.
 *
 * Aucun accès direct à FactionManager/FPlayerManager/ClaimManager/QuestManager.
 */
public final class KfactionExpansion
        extends PlaceholderExpansion {

    private final Kfaction plugin;

    private volatile KfactionApiV2 cachedApi;

    public KfactionExpansion(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "kfaction";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription()
                .getAuthors()
                .toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription()
                .getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(
            Player player,
            String identifier
    ) {
        if (player == null
                || identifier == null) {
            return "";
        }

        KfactionApiV2 api =
                api();

        if (api == null) {
            return "";
        }

        PlayerView playerView =
                api.getPlayer(
                        player.getUniqueId()
                );

        FactionView faction =
                api.getPlayerFaction(
                        player.getUniqueId()
                );

        String key =
                identifier.toLowerCase(
                        Locale.ROOT
                );

        switch (key) {
            case "has_faction":
                return bool(
                        faction != null
                );

            case "faction_name":
                return faction != null
                        ? safe(faction.getName())
                        : "";

            case "faction_tag":
                return faction != null
                        ? safe(faction.getTag())
                        : "";

            case "faction_description":
                return faction != null
                        ? safe(faction.getDescription())
                        : "";

            case "faction_leader":
                return leaderName(faction);

            case "faction_online":
                return faction != null
                        ? String.valueOf(
                                faction.getOnlineMemberCount()
                        )
                        : "0";

            case "faction_members":
                return faction != null
                        ? String.valueOf(
                                faction.getMemberCount()
                        )
                        : "0";

            case "faction_maxmembers":
                return faction != null
                        ? String.valueOf(
                                faction.getMaxMembers()
                        )
                        : String.valueOf(
                                api.getDefaultMaxMembers()
                        );

            case "faction_power":
                return faction != null
                        ? number(faction.getPower())
                        : "0";

            case "faction_maxpower":
                return faction != null
                        ? number(faction.getMaxPower())
                        : "0";

            case "player_power":
                return playerView != null
                        ? number(playerView.getPower())
                        : "0";

            case "player_maxpower":
                return playerView != null
                        ? number(playerView.getMaxPower())
                        : "0";

            case "faction_claims":
                return faction != null
                        ? String.valueOf(
                                faction.getClaimCount()
                        )
                        : "0";

            case "faction_maxclaims":
                return faction != null
                        ? String.valueOf(
                                faction.getMaxClaims()
                        )
                        : "0";

            case "faction_bank":
                return faction != null
                        ? String.format(
                                Locale.US,
                                "%.2f",
                                faction.getBankBalance()
                        )
                        : "0";

            case "faction_allies":
                return faction != null
                        ? String.valueOf(
                                faction.getAllyCount()
                        )
                        : "0";

            case "faction_enemies":
                return faction != null
                        ? String.valueOf(
                                faction.getEnemyCount()
                        )
                        : "0";

            case "faction_truces":
                return faction != null
                        ? String.valueOf(
                                faction.getTruceCount()
                        )
                        : "0";

            case "player_role":
            case "faction_role":
                return playerView != null
                        ? safe(
                                playerView.getRoleDisplayName()
                        )
                        : "";

            case "player_role_prefix":
                return playerView != null
                        ? safe(
                                playerView.getRolePrefix()
                        )
                        : "";

            case "location_faction":
                return territoryName(
                        api,
                        player
                );

            case "location_relation":
                return territoryRelation(
                        api,
                        player
                );

            case "location_zone_id":
                return territoryZoneId(
                        api,
                        player
                );

            case "location_zone_name":
                return territoryZoneName(
                        api,
                        player
                );

            case "faction_warps":
                return faction != null
                        ? String.valueOf(
                                faction.getWarpCount()
                        )
                        : "0";

            case "faction_maxwarps":
                return faction != null
                        ? String.valueOf(
                                faction.getMaxWarps()
                        )
                        : String.valueOf(
                                api.getDefaultMaxWarps()
                        );

            case "player_kills":
                return playerView != null
                        ? String.valueOf(
                                playerView.getKills()
                        )
                        : "0";

            case "player_deaths":
                return playerView != null
                        ? String.valueOf(
                                playerView.getDeaths()
                        )
                        : "0";

            case "player_kdr":
                return kdr(playerView);

            case "player_chatmode":
                return playerView != null
                        && playerView.getChatMode() != null
                        ? playerView.getChatMode()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                        : "public";

            case "faction_raidable":
                return bool(
                        faction != null
                                && faction.isRaidable()
                );

            case "faction_level":
                return faction != null
                        ? String.valueOf(
                                faction.getLevel()
                        )
                        : "0";

            case "faction_xp":
            case "faction_required_xp":
                return "0";

            case "faction_progress_percent":
                return progressionNumber(
                        api,
                        faction,
                        ProgressField.PERCENT
                );

            case "faction_progressbar":
                ProgressionView progress =
                        progression(
                                api,
                                faction
                        );

                return progress != null
                        ? progressBar(
                                progress.getPercent(),
                                20
                        )
                        : "";

            case "faction_category":
                return "";

            case "faction_tier":
                ProgressionView tier =
                        progression(
                                api,
                                faction
                        );

                return tier != null
                        ? safe(tier.getTierId())
                        : "";

            case "faction_tier_display":
                ProgressionView display =
                        progression(
                                api,
                                faction
                        );

                return display != null
                        ? safe(
                                display.getTierDisplayName()
                        )
                        : "";

            case "faction_quests_total":
                return progressionNumber(
                        api,
                        faction,
                        ProgressField.TOTAL
                );

            case "faction_quests_completed":
                return progressionNumber(
                        api,
                        faction,
                        ProgressField.COMPLETED
                );

            case "faction_quests_remaining":
                ProgressionView remaining =
                        progression(
                                api,
                                faction
                        );

                return remaining != null
                        ? String.valueOf(
                                Math.max(
                                        0,
                                        remaining.getTotalQuests()
                                                - remaining.getCompletedQuests()
                                )
                        )
                        : "0";

            case "faction_has_chest":
                return bool(
                        faction != null
                                && faction.isChestUnlocked()
                );

            case "faction_has_fly":
                return bool(
                        faction != null
                                && faction.isFactionFlyEnabled()
                );

            case "faction_has_antisethome":
                return bool(
                        faction != null
                                && faction.isAntiSethomeEnabled()
                );

            case "faction_has_chest_display":
                return featureDisplay(
                        faction != null
                                && faction.isChestUnlocked(),
                        "Débloqué",
                        "Verrouillé"
                );

            case "faction_has_fly_display":
                return featureDisplay(
                        faction != null
                                && faction.isFactionFlyEnabled(),
                        "Débloqué",
                        "Verrouillé"
                );

            case "faction_has_antisethome_display":
                return featureDisplay(
                        faction != null
                                && faction.isAntiSethomeEnabled(),
                        "Actif",
                        "Verrouillé"
                );

            default:
                return handlePermissionPlaceholder(
                        api,
                        key,
                        faction
                );
        }
    }

    private KfactionApiV2 api() {
        KfactionApiV2 current = cachedApi;

        if (current == null) {
            current = KfactionApis.get();

            if (current != null) {
                cachedApi = current;
            }
        }

        return current;
    }

    private String handlePermissionPlaceholder(
            KfactionApiV2 api,
            String identifier,
            FactionView faction
    ) {
        if (!identifier.startsWith("perm_")
                || faction == null) {
            return null;
        }

        String[] parts =
                identifier.substring(5)
                        .split(
                                "_",
                                2
                        );

        if (parts.length != 2) {
            return null;
        }

        Boolean role =
                api.getRolePermission(
                        faction.getId(),
                        parts[0],
                        parts[1]
                );

        if (role != null) {
            return enabledDisplay(
                    role.booleanValue()
            );
        }

        Boolean relation =
                api.getRelationPermission(
                        faction.getId(),
                        parts[0],
                        parts[1]
                );

        return relation != null
                ? enabledDisplay(
                        relation.booleanValue()
                )
                : null;
    }

    private static String leaderName(
            FactionView faction
    ) {
        if (faction == null
                || faction.getLeader() == null) {
            return "";
        }

        UUID leader =
                faction.getLeader();

        for (MemberView member
                : faction.getMembers()) {
            if (member != null
                    && leader.equals(
                            member.getUuid()
                    )) {
                return safe(
                        member.getName()
                );
            }
        }

        return leader.toString();
    }

    private static String territoryName(
            KfactionApiV2 api,
            Player player
    ) {
        TerritoryView territory =
                api.getTerritory(
                        new FLocation(
                                player.getLocation()
                        ),
                        player.getUniqueId()
                );

        if (territory == null) {
            return "Wilderness";
        }

        if (territory.getFactionName() != null) {
            return territory.getFactionName();
        }

        switch (territory.getType()) {
            case SAFEZONE:
                return "SafeZone";

            case WARZONE:
                return "WarZone";

            case GLOBAL_ZONE:
                return territory.getZoneDisplayName() != null
                        ? territory.getZoneDisplayName()
                        : territory.getZoneId() != null
                                ? territory.getZoneId()
                                : "GlobalZone";

            default:
                return "Wilderness";
        }
    }

    private static String territoryZoneId(
            KfactionApiV2 api,
            Player player
    ) {
        TerritoryView territory =
                api.getTerritory(
                        new FLocation(
                                player.getLocation()
                        ),
                        player.getUniqueId()
                );

        return territory != null
                && territory.isGlobalZone()
                && territory.getZoneId() != null
                        ? territory.getZoneId()
                        : "";
    }

    private static String territoryZoneName(
            KfactionApiV2 api,
            Player player
    ) {
        TerritoryView territory =
                api.getTerritory(
                        new FLocation(
                                player.getLocation()
                        ),
                        player.getUniqueId()
                );

        return territory != null
                && territory.isGlobalZone()
                && territory.getZoneDisplayName() != null
                        ? territory.getZoneDisplayName()
                        : "";
    }

    private static String territoryRelation(
            KfactionApiV2 api,
            Player player
    ) {
        TerritoryView territory =
                api.getTerritory(
                        new FLocation(
                                player.getLocation()
                        ),
                        player.getUniqueId()
                );

        return territory != null
                && territory.getRelation() != null
                ? territory.getRelation()
                : "NEUTRAL";
    }

    private static ProgressionView progression(
            KfactionApiV2 api,
            FactionView faction
    ) {
        return faction != null
                ? api.getProgression(
                        faction.getId()
                )
                : null;
    }

    private static String progressionNumber(
            KfactionApiV2 api,
            FactionView faction,
            ProgressField field
    ) {
        ProgressionView progress =
                progression(
                        api,
                        faction
                );

        if (progress == null) {
            return "0";
        }

        switch (field) {
            case COMPLETED:
                return String.valueOf(
                        progress.getCompletedQuests()
                );

            case TOTAL:
                return String.valueOf(
                        progress.getTotalQuests()
                );

            default:
                return String.valueOf(
                        progress.getPercent()
                );
        }
    }

    private static String kdr(
            PlayerView player
    ) {
        if (player == null) {
            return "0";
        }

        if (player.getDeaths() == 0) {
            return String.valueOf(
                    player.getKills()
            );
        }

        return String.format(
                Locale.US,
                "%.2f",
                (double) player.getKills()
                        / player.getDeaths()
        );
    }

    private static String number(
            double value
    ) {
        long rounded =
                Math.round(value);

        if (Math.abs(
                value - rounded
        ) < 0.000001D) {
            return String.valueOf(
                    rounded
            );
        }

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }

    private static String progressBar(
            int percent,
            int length
    ) {
        int filled =
                Math.max(
                        0,
                        Math.min(
                                length,
                                percent * length / 100
                        )
                );

        StringBuilder value =
                new StringBuilder("§a");

        for (int index = 0;
                index < length;
                index++) {
            if (index == filled) {
                value.append("§7");
            }

            value.append("▌");
        }

        return value.toString();
    }

    private static String enabledDisplay(
            boolean enabled
    ) {
        return enabled
                ? "§a✔ Activé"
                : "§c✖ Désactivé";
    }

    private static String featureDisplay(
            boolean enabled,
            String enabledText,
            String disabledText
    ) {
        return enabled
                ? "§a✔ " + enabledText
                : "§c✖ " + disabledText;
    }

    private static String bool(
            boolean value
    ) {
        return value
                ? "true"
                : "false";
    }

    private static String safe(
            String value
    ) {
        return value != null
                ? value
                : "";
    }

    private enum ProgressField {
        PERCENT,
        COMPLETED,
        TOTAL
    }
}
