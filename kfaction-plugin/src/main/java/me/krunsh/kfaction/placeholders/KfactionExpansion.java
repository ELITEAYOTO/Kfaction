package me.krunsh.kfaction.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.utils.FactionRolePresentation;

/**
 * PlaceholderAPI V2.
 *
 * Aucun accès direct à FactionManager/FPlayerManager/ClaimManager/QuestManager.
 *
 * Ajouts TAB :
 *
 * %kfaction_online_member_1%
 * %kfaction_online_member_2%
 * ...
 *
 * Valeur combinée :
 * "&cLeader &8» &fKrunsh_"
 *
 * Variantes :
 * %kfaction_online_member_1_name%
 * %kfaction_online_member_1_role%
 *
 * Les membres sont triés :
 * LEADER > COLEADER > MODERATOR > OFFICER > MEMBER > RECRUIT,
 * puis alphabétiquement pour un même rôle.
 *
 * Un index sans membre retourne "".
 */
public final class KfactionExpansion
        extends PlaceholderExpansion {

    private final Kfaction plugin;

    private volatile KfactionApiV2 cachedApi;

    private static final long VIEW_CACHE_TTL_NANOS =
            TimeUnit.MILLISECONDS.toNanos(100L);
    private static final long VIEW_CACHE_SWEEP_NANOS =
            TimeUnit.SECONDS.toNanos(5L);
    private static final int MAX_VIEW_CACHE_ENTRIES = 2048;

    private final ConcurrentMap<UUID, CachedPlayerView> playerViews =
            new ConcurrentHashMap<UUID, CachedPlayerView>();
    private final ConcurrentMap<String, CachedFactionView> factionViews =
            new ConcurrentHashMap<String, CachedFactionView>();
    private final AtomicLong nextCacheSweepNanos = new AtomicLong();

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

        long now = System.nanoTime();
        PlayerView playerView = cachedPlayerView(
                api,
                player.getUniqueId(),
                now
        );
        CachedFactionView factionCache = cachedFactionView(
                api,
                playerView != null ? playerView.getFactionId() : null,
                now
        );
        FactionView faction = factionCache != null
                ? factionCache.view
                : null;

        String key =
                identifier.toLowerCase(
                        Locale.ROOT
                );

        /*
         * Placeholders indexés de membres connectés.
         *
         * On les traite avant le switch principal pour supporter
         * n'importe quel index sans ajouter des dizaines de case.
         */
        String onlineMember =
                resolveOnlineMemberPlaceholder(
                        factionCache != null
                                ? factionCache.sortedOnlineMembers
                                : Collections.<MemberView>emptyList(),
                        key
                );

        if (onlineMember != null) {
            return onlineMember;
        }

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

    /**
     * Placeholders :
     *
     * online_member_1
     * online_member_1_name
     * online_member_1_role
     *
     * Retourne null si le placeholder n'appartient pas à cette famille.
     * Retourne "" si le placeholder est valide mais que le slot est vide.
     */
    static String resolveOnlineMemberPlaceholder(
            FactionView faction,
            String key
    ) {
        return resolveOnlineMemberPlaceholder(
                sortedOnlineMembers(faction),
                key
        );
    }

    private static String resolveOnlineMemberPlaceholder(
            List<MemberView> members,
            String key
    ) {
        if (key == null
                || !key.startsWith(
                        "online_member_"
                )) {
            return null;
        }

        String remainder =
                key.substring(
                        "online_member_".length()
                );

        OnlineMemberField field =
                OnlineMemberField.LINE;

        if (remainder.endsWith(
                "_name"
        )) {
            field =
                    OnlineMemberField.NAME;

            remainder =
                    remainder.substring(
                            0,
                            remainder.length()
                                    - "_name".length()
                    );

        } else if (remainder.endsWith(
                "_role"
        )) {
            field =
                    OnlineMemberField.ROLE;

            remainder =
                    remainder.substring(
                            0,
                            remainder.length()
                                    - "_role".length()
                    );
        }

        int index =
                parsePositiveInt(
                        remainder
                );

        if (index <= 0) {
            return "";
        }

        int zeroBased =
                index - 1;

        if (zeroBased >= members.size()) {
            return "";
        }

        MemberView member =
                members.get(
                        zeroBased
                );

        String name =
                safe(
                        member.getName()
                );

        FactionRole role =
                parseRole(
                        member.getRole()
                );

        String roleName =
                role != null
                        ? role.getDisplayName()
                        : safe(
                                member.getRole()
                        );

        switch (field) {
            case NAME:
                return name;

            case ROLE:
                return roleName;

            default:
                return roleColor(role)
                        + roleName
                        + " &8» &f"
                        + name;
        }
    }

    private static List<MemberView> sortedOnlineMembers(
            FactionView faction
    ) {
        if (faction == null
                || faction.getMembers() == null
                || faction.getMembers().isEmpty()) {

            return Collections.emptyList();
        }

        List<MemberView> result =
                new ArrayList<MemberView>();

        for (MemberView member
                : faction.getMembers()) {

            if (member == null
                    || !member.isOnline()) {
                continue;
            }

            result.add(
                    member
            );
        }

        Collections.sort(
                result,
                new Comparator<MemberView>() {
                    @Override
                    public int compare(
                            MemberView left,
                            MemberView right
                    ) {
                        int leftPriority =
                                rolePriority(
                                        left == null
                                                ? null
                                                : left.getRole()
                                );

                        int rightPriority =
                                rolePriority(
                                        right == null
                                                ? null
                                                : right.getRole()
                                );

                        int priority =
                                Integer.compare(
                                        rightPriority,
                                        leftPriority
                                );

                        if (priority != 0) {
                            return priority;
                        }

                        String leftName =
                                left == null
                                        ? ""
                                        : safe(
                                                left.getName()
                                        );

                        String rightName =
                                right == null
                                        ? ""
                                        : safe(
                                                right.getName()
                                        );

                        return leftName.compareToIgnoreCase(
                                rightName
                        );
                    }
                }
        );

        return result;
    }

    private static int rolePriority(
            String rawRole
    ) {
        FactionRole role =
                parseRole(
                        rawRole
                );

        return role != null
                ? role.getPriority()
                : 0;
    }

    private static FactionRole parseRole(
            String rawRole
    ) {
        if (rawRole == null
                || rawRole.trim().isEmpty()) {
            return null;
        }

        FactionRole parsed =
                FactionRole.parse(
                        rawRole
                );

        if (parsed != null) {
            return parsed;
        }

        try {
            return FactionRole.valueOf(
                    rawRole.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String roleColor(
            FactionRole role
    ) {
        return FactionRolePresentation.color(role);
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

    private PlayerView cachedPlayerView(
            KfactionApiV2 api,
            UUID playerId,
            long now
    ) {
        CachedPlayerView cached = playerViews.get(playerId);
        if (cached != null && cached.expiresAtNanos > now) {
            return cached.view;
        }

        PlayerView fresh = api.getPlayer(playerId);
        playerViews.put(
                playerId,
                new CachedPlayerView(fresh, now + VIEW_CACHE_TTL_NANOS)
        );
        sweepViewCaches(now);
        return fresh;
    }

    private CachedFactionView cachedFactionView(
            KfactionApiV2 api,
            String factionId,
            long now
    ) {
        if (factionId == null || factionId.trim().isEmpty()) {
            return null;
        }

        CachedFactionView cached = factionViews.get(factionId);
        if (cached != null && cached.expiresAtNanos > now) {
            return cached;
        }

        FactionView fresh = api.getFaction(factionId);
        CachedFactionView replacement = new CachedFactionView(
                fresh,
                sortedOnlineMembers(fresh),
                now + VIEW_CACHE_TTL_NANOS
        );
        factionViews.put(factionId, replacement);
        sweepViewCaches(now);
        return replacement;
    }

    private void sweepViewCaches(long now) {
        long scheduled = nextCacheSweepNanos.get();
        if (now < scheduled
                || !nextCacheSweepNanos.compareAndSet(
                        scheduled,
                        now + VIEW_CACHE_SWEEP_NANOS
                )) {
            return;
        }

        for (java.util.Map.Entry<UUID, CachedPlayerView> entry
                : playerViews.entrySet()) {
            CachedPlayerView value = entry.getValue();
            if (value == null || value.expiresAtNanos <= now) {
                playerViews.remove(entry.getKey(), value);
            }
        }

        for (java.util.Map.Entry<String, CachedFactionView> entry
                : factionViews.entrySet()) {
            CachedFactionView value = entry.getValue();
            if (value == null || value.expiresAtNanos <= now) {
                factionViews.remove(entry.getKey(), value);
            }
        }

        if (playerViews.size() > MAX_VIEW_CACHE_ENTRIES) {
            playerViews.clear();
        }
        if (factionViews.size() > MAX_VIEW_CACHE_ENTRIES) {
            factionViews.clear();
        }
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

    private static int parsePositiveInt(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {
            return -1;
        }

        try {
            int parsed =
                    Integer.parseInt(
                            value.trim()
                    );

            return parsed > 0
                    ? parsed
                    : -1;

        } catch (NumberFormatException ignored) {
            return -1;
        }
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

    private enum OnlineMemberField {
        LINE,
        NAME,
        ROLE
    }

    private static final class CachedPlayerView {
        private final PlayerView view;
        private final long expiresAtNanos;

        private CachedPlayerView(PlayerView view, long expiresAtNanos) {
            this.view = view;
            this.expiresAtNanos = expiresAtNanos;
        }
    }

    private static final class CachedFactionView {
        private final FactionView view;
        private final List<MemberView> sortedOnlineMembers;
        private final long expiresAtNanos;

        private CachedFactionView(
                FactionView view,
                List<MemberView> sortedOnlineMembers,
                long expiresAtNanos
        ) {
            this.view = view;
            this.sortedOnlineMembers = sortedOnlineMembers;
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
