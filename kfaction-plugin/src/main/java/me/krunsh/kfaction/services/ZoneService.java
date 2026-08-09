package me.krunsh.kfaction.services;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.audit.AuditCategory;
import me.krunsh.kfaction.audit.AuditOutcome;
import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.core.operation.OperationResult;
import me.krunsh.kfaction.core.operation.OperationResult.Status;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.managers.ClaimManager;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.utils.KfactionLogger;
import me.krunsh.kfaction.zones.GlobalZoneType;
import me.krunsh.kfaction.zones.ZoneDefinition;
import me.krunsh.kfaction.zones.ZoneDefinition.DefaultPolicy;

/**
 * Global Zones V2.2.
 *
 * Source de vérité runtime:
 *
 *   FLocation -> zoneId String
 *
 * Les définitions sont chargées dynamiquement depuis:
 *
 *   zones.<zoneId>
 *
 * SafeZone / WarZone restent uniquement des façades de compatibilité pour
 * les anciennes APIs qui attendent encore GlobalZoneType/Faction système.
 */
public final class ZoneService {

    private static final int PAYLOAD_SCHEMA = 2;

    private static final String SAFEZONE_ID = "safezone";
    private static final String WARZONE_ID = "warzone";

    private final Kfaction plugin;
    private final ClaimManager claimManager;

    private final Map<FLocation, String> zones;

    private volatile Map<String, ZoneDefinition> definitions;
    private volatile List<String> configurationIssues;
    private volatile int maxTotalChunks;

    private volatile boolean initialized;

    public ZoneService(
            Kfaction plugin,
            ClaimManager claimManager
    ) {
        if (plugin == null
                || claimManager == null) {
            throw new IllegalArgumentException(
                    "plugin/claimManager cannot be null"
            );
        }

        this.plugin = plugin;
        this.claimManager = claimManager;

        this.zones =
                new ConcurrentHashMap<FLocation, String>();

        this.definitions =
                Collections.emptyMap();

        this.configurationIssues =
                Collections.emptyList();

        this.maxTotalChunks =
                200000;

        this.initialized = false;
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    public void initialize() {
        if (initialized) {
            return;
        }

        reloadDefinitions();

        String payload =
                plugin.getStorageManager()
                        .loadGlobalZonesPayload();

        if (payload != null
                && !payload.trim().isEmpty()) {
            restorePayload(payload);
        }

        initialized = true;

        KfactionLogger.debug(
                plugin,
                "ZoneService V2.2: "
                        + zones.size()
                        + " chunks, "
                        + definitions.size()
                        + " définitions."
        );
    }

    public void reload() {
        reloadDefinitions();
    }

    /**
     * Recharge uniquement zones.* directement depuis config.yml sur disque.
     *
     * Contrairement à ConfigManager#reload(), cette méthode ne remplace pas
     * la configuration globale vue par les autres managers: elle évite donc
     * un état partiellement reloadé hors du périmètre Global Zones.
     */
    public void reloadFromDisk() {
        File file =
                new File(
                        plugin.getDataFolder(),
                        "config.yml"
                );

        if (!file.isFile()) {
            reloadDefinitions();
            return;
        }

        YamlConfiguration disk =
                YamlConfiguration.loadConfiguration(
                        file
                );

        reloadDefinitions(
                disk.getConfigurationSection(
                        "zones"
                )
        );
    }

    public void shutdown() {
        zones.clear();
        definitions =
                Collections.emptyMap();
        configurationIssues =
                Collections.emptyList();
        initialized = false;
    }

    // ============================================================
    // Definitions
    // ============================================================

    public Map<String, ZoneDefinition> getDefinitions() {
        return definitions;
    }

    public List<ZoneDefinition> getDefinitionList() {
        return Collections.unmodifiableList(
                new ArrayList<ZoneDefinition>(
                        definitions.values()
                )
        );
    }

    public Set<String> getZoneIds() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(
                        definitions.keySet()
                )
        );
    }

    public boolean hasDefinition(
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        return normalized != null
                && definitions.containsKey(
                        normalized
                );
    }

    public boolean hasAssignedZoneId(
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        return normalized != null
                && containsAssignedZoneId(
                        normalized
                );
    }

    public boolean isKnownOrAssignedZoneId(
            String zoneId
    ) {
        return hasDefinition(zoneId)
                || hasAssignedZoneId(zoneId);
    }

    public ZoneDefinition getDefinition(
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        if (normalized == null) {
            return null;
        }

        ZoneDefinition definition =
                definitions.get(
                        normalized
                );

        /*
         * Une assignation persistée peut survivre à la suppression
         * accidentelle de sa section config.
         */
        if (definition == null
                && containsAssignedZoneId(
                        normalized
                )) {
            return ZoneDefinition.orphan(
                    normalized
            );
        }

        return definition;
    }

    public ZoneDefinition getDefinitionAt(
            FLocation location
    ) {
        String zoneId =
                getZoneIdAt(
                        location
                );

        if (zoneId == null) {
            return null;
        }

        ZoneDefinition definition =
                definitions.get(
                        zoneId
                );

        /*
         * Ici l'ID vient forcément de la map d'assignation: aucun scan O(n)
         * n'est nécessaire pour savoir s'il s'agit d'une zone orpheline.
         */
        return definition != null
                ? definition
                : ZoneDefinition.orphan(
                        zoneId
                );
    }

    public List<String> getConfigurationIssues() {
        return configurationIssues;
    }

    public Set<String> getOrphanZoneIds() {
        Set<String> result =
                new LinkedHashSet<String>();

        for (String zoneId
                : zones.values()) {
            if (!definitions.containsKey(
                    zoneId
            )) {
                result.add(zoneId);
            }
        }

        return Collections.unmodifiableSet(
                result
        );
    }

    // ============================================================
    // Lookup
    // ============================================================

    public String getZoneIdAt(
            FLocation location
    ) {
        return location != null
                ? zones.get(location)
                : null;
    }

    /**
     * Legacy: custom zone => null.
     */
    @Deprecated
    public GlobalZoneType getZoneAt(
            FLocation location
    ) {
        return GlobalZoneType.parse(
                getZoneIdAt(location)
        );
    }

    public boolean hasZone(
            FLocation location
    ) {
        return location != null
                && zones.containsKey(
                        location
                );
    }

    public boolean isZone(
            FLocation location,
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        return location != null
                && normalized != null
                && normalized.equals(
                        zones.get(location)
                );
    }

    /**
     * Legacy SafeZone/WarZone wrapper.
     */
    public boolean isZone(
            FLocation location,
            GlobalZoneType type
    ) {
        return type != null
                && isZone(
                        location,
                        type.getConfigKey()
                );
    }

    public int getTotalZoneChunks() {
        return zones.size();
    }

    public int getMaxTotalChunks() {
        return maxTotalChunks;
    }

    public int count(
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        if (normalized == null) {
            return 0;
        }

        int count = 0;

        for (String value
                : zones.values()) {
            if (normalized.equals(
                    value
            )) {
                count++;
            }
        }

        return count;
    }

    public int count(
            GlobalZoneType type
    ) {
        return type != null
                ? count(type.getConfigKey())
                : 0;
    }

    public List<FLocation> getLocations(
            String zoneId
    ) {
        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        if (normalized == null) {
            return Collections.emptyList();
        }

        List<FLocation> result =
                new ArrayList<FLocation>();

        for (Map.Entry<FLocation, String> entry
                : zones.entrySet()) {
            if (normalized.equals(
                    entry.getValue()
            )) {
                result.add(
                        entry.getKey()
                );
            }
        }

        return result;
    }

    public List<FLocation> getLocations(
            GlobalZoneType type
    ) {
        return type != null
                ? getLocations(
                        type.getConfigKey()
                )
                : Collections.<FLocation>emptyList();
    }

    // ============================================================
    // Mutation dynamique
    // ============================================================

    public OperationResult<String> setZone(
            FLocation location,
            String zoneId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Global Zone mutation must run on Bukkit primary thread"
            );
        }

        String normalized =
                ZoneDefinition.normalizeId(
                        zoneId
                );

        if (location == null
                || normalized == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Zone/location/context invalide"
            );
        }

        if (!definitions.containsKey(
                normalized
        )) {
            return failure(
                    Status.NOT_FOUND,
                    "Zone inconnue: "
                            + normalized
            );
        }

        String current =
                zones.get(location);

        if (normalized.equals(
                current
        )) {
            return OperationResult.noChange(
                    "zone.already-set"
            );
        }

        int maxChunks =
                maxTotalChunks;

        if (current == null
                && zones.size()
                >= maxChunks) {
            return failure(
                    Status.LIMIT_REACHED,
                    "Limite globale de zones atteinte: "
                            + maxChunks
            );
        }

        /*
         * Une zone globale est prioritaire sur un claim joueur.
         */
        claimManager.removePlayerClaimForZone(
                location
        );

        zones.put(
                location,
                normalized
        );

        dirty();

        audit(
                context,
                "set "
                        + normalized
                        + " "
                        + location.getKey()
                        + (current != null
                                ? " replacing="
                                        + current
                                : "")
        );

        return OperationResult.success(
                normalized
        );
    }

    public OperationResult<String> clearZone(
            FLocation location,
            String expectedZoneId,
            OperationContext context
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return failure(
                    Status.UNAVAILABLE,
                    "Global Zone mutation must run on Bukkit primary thread"
            );
        }

        if (location == null
                || context == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Zone/location/context invalide"
            );
        }

        String expected =
                expectedZoneId != null
                        ? ZoneDefinition.normalizeId(
                                expectedZoneId
                        )
                        : null;

        if (expectedZoneId != null
                && expected == null) {
            return failure(
                    Status.INVALID_INPUT,
                    "Zone attendue invalide"
            );
        }

        String current =
                zones.get(location);

        if (current == null) {
            return OperationResult.noChange(
                    "zone.not-set"
            );
        }

        if (expected != null
                && !expected.equals(
                        current
                )) {
            return failure(
                    Status.CONFLICT,
                    "Le chunk est "
                            + current
                            + ", pas "
                            + expected
            );
        }

        if (!zones.remove(
                location,
                current
        )) {
            return failure(
                    Status.CONFLICT,
                    "La zone a changé pendant la mutation"
            );
        }

        dirty();

        audit(
                context,
                "clear "
                        + current
                        + " "
                        + location.getKey()
        );

        return OperationResult.success(
                current
        );
    }

    // ============================================================
    // Legacy mutation wrappers
    // ============================================================

    public OperationResult<GlobalZoneType> setZone(
            FLocation location,
            GlobalZoneType type,
            OperationContext context
    ) {
        if (type == null) {
            return legacyFailure(
                    Status.INVALID_INPUT,
                    "Zone legacy invalide"
            );
        }

        OperationResult<String> result =
                setZone(
                        location,
                        type.getConfigKey(),
                        context
                );

        return toLegacyResult(
                result,
                type
        );
    }

    public OperationResult<GlobalZoneType> clearZone(
            FLocation location,
            GlobalZoneType expectedType,
            OperationContext context
    ) {
        OperationResult<String> result =
                clearZone(
                        location,
                        expectedType != null
                                ? expectedType.getConfigKey()
                                : null,
                        context
                );

        GlobalZoneType value =
                result.hasValue()
                        ? GlobalZoneType.parse(
                                result.getValue()
                        )
                        : expectedType;

        return toLegacyResult(
                result,
                value
        );
    }

    /**
     * Migration idempotente des anciennes claims SafeZone/WarZone.
     */
    public boolean importLegacy(
            FLocation location,
            GlobalZoneType type
    ) {
        if (location == null
                || type == null) {
            return false;
        }

        String previous =
                zones.putIfAbsent(
                        location,
                        type.getConfigKey()
                );

        return previous == null;
    }

    public void finishLegacyImport(
            int imported
    ) {
        if (imported <= 0) {
            return;
        }

        dirty();

        KfactionLogger.warn(
                plugin,
                "Migration zones: "
                        + imported
                        + " anciens claims SafeZone/WarZone importés."
        );
    }

    // ============================================================
    // Rules
    // ============================================================

    public boolean isActionAllowed(
            String zoneId,
            TerritoryAction action
    ) {
        if (zoneId == null
                || action == null) {
            return true;
        }

        ZoneDefinition definition =
                getDefinition(
                        zoneId
                );

        /*
         * ID assigné mais définition supprimée => fail-closed via orphan().
         * ID arbitraire non assigné/configuré => false.
         */
        return definition != null
                && definition.isActionAllowed(
                        action
                );
    }

    public boolean isActionAllowed(
            GlobalZoneType type,
            TerritoryAction action
    ) {
        return type == null
                || isActionAllowed(
                        type.getConfigKey(),
                        action
                );
    }

    public boolean isPvpAllowed(
            String zoneId
    ) {
        if (zoneId == null) {
            return true;
        }

        ZoneDefinition definition =
                getDefinition(
                        zoneId
                );

        return definition != null
                && definition.isPvpAllowed();
    }

    public boolean isPvpAllowed(
            GlobalZoneType type
    ) {
        return type == null
                || isPvpAllowed(
                        type.getConfigKey()
                );
    }

    // ============================================================
    // Config
    // ============================================================

    private void reloadDefinitions() {
        ConfigurationSection root =
                plugin.getConfigManager()
                        .getConfig()
                        .getConfigurationSection(
                                "zones"
                        );

        reloadDefinitions(root);
    }

    private void reloadDefinitions(
            ConfigurationSection root
    ) {
        Map<String, ZoneDefinition> loaded =
                new LinkedHashMap<String, ZoneDefinition>();

        List<String> issues =
                new ArrayList<String>();

        maxTotalChunks =
                root != null
                        ? Math.max(
                                1,
                                root.getInt(
                                        "max-total-chunks",
                                        200000
                                )
                        )
                        : 200000;

        if (zones.size() > maxTotalChunks) {
            issues.add(
                    "zones.max-total-chunks="
                            + maxTotalChunks
                            + " est inférieur aux "
                            + zones.size()
                            + " chunks déjà persistés; les existants sont conservés, "
                            + "mais aucune nouvelle zone ne pourra dépasser la limite."
            );
        }

        if (root != null) {
            for (String rawId
                    : root.getKeys(false)) {
                if ("max-total-chunks".equalsIgnoreCase(
                        rawId
                )
                        || "admin-auto".equalsIgnoreCase(
                                rawId
                        )
                        || "settings".equalsIgnoreCase(
                                rawId
                        )) {
                    continue;
                }

                ConfigurationSection section =
                        root.getConfigurationSection(
                                rawId
                        );

                if (section == null) {
                    continue;
                }

                String normalized =
                        ZoneDefinition.normalizeId(
                                rawId
                        );

                if (normalized == null
                        || !normalized.equals(
                                rawId
                                        .trim()
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        )) {
                    issues.add(
                            "zones."
                                    + rawId
                                    + ": id invalide; utiliser [a-z0-9_-], 1..32 caractères."
                    );
                    continue;
                }

                if (loaded.containsKey(
                        normalized
                )) {
                    issues.add(
                            "zones."
                                    + rawId
                                    + ": ID dupliqué après normalisation ('"
                                    + normalized
                                    + "')."
                    );
                    continue;
                }

                ZoneDefinition definition =
                        loadDefinition(
                                normalized,
                                section,
                                issues
                        );

                if (definition != null) {
                    loaded.put(
                            normalized,
                            definition
                    );
                }
            }
        }

        /*
         * Compatibilité: SafeZone/WarZone existent toujours même si une vieille
         * config ne contient pas encore les nouvelles sections complètes.
         */
        if (!loaded.containsKey(
                SAFEZONE_ID
        )) {
            loaded.put(
                    SAFEZONE_ID,
                    defaultSafezone()
            );
            issues.add(
                    "zones.safezone absent: définition de compatibilité injectée."
            );
        }

        if (!loaded.containsKey(
                WARZONE_ID
        )) {
            loaded.put(
                    WARZONE_ID,
                    defaultWarzone()
            );
            issues.add(
                    "zones.warzone absent: définition de compatibilité injectée."
            );
        }

        definitions =
                Collections.unmodifiableMap(
                        loaded
                );

        configurationIssues =
                Collections.unmodifiableList(
                        issues
                );

        if (!issues.isEmpty()) {
            for (String issue
                    : issues) {
                KfactionLogger.warn(
                        plugin,
                        "Zones: "
                                + issue
                );
            }
        }

        Set<String> orphanIds =
                getOrphanZoneIds();

        if (!orphanIds.isEmpty()) {
            KfactionLogger.warn(
                    plugin,
                    "Zones persistées sans définition config: "
                            + orphanIds
            );
        }

        KfactionLogger.debug(
                plugin,
                "Zones dynamiques chargées: "
                        + loaded.keySet()
        );
    }

    private ZoneDefinition loadDefinition(
            String zoneId,
            ConfigurationSection section,
            List<String> issues
    ) {
        String base =
                "zones."
                        + zoneId;

        String displayName =
                section.getString(
                        "display-name",
                        defaultDisplayName(
                                zoneId
                        )
                );

        String color =
                section.getString(
                        "color",
                        defaultColor(
                                zoneId
                        )
                );

        String mapSymbol =
                section.getString(
                        "map-symbol",
                        defaultSymbol(
                                zoneId
                        )
                );

        if (mapSymbol == null
                || mapSymbol.trim().isEmpty()) {
            issues.add(
                    base
                            + ".map-symbol vide; fallback '?' utilisé."
            );
            mapSymbol = "?";
        }

        String title =
                section.getString(
                        "title",
                        color
                                + displayName
                );

        String subtitle =
                section.getString(
                        "subtitle",
                        ""
                );

        String enterMessage =
                section.getString(
                        "enter-message",
                        color
                                + "~ "
                                + displayName
                );

        boolean pvp =
                section.getBoolean(
                        "pvp",
                        WARZONE_ID.equals(
                                zoneId
                        )
                );

        String rawPolicy =
                section.getString(
                        "default-policy",
                        SAFEZONE_ID.equals(zoneId)
                                ? "ALLOW"
                                : "DENY"
                );

        DefaultPolicy policy =
                DefaultPolicy.parse(
                        rawPolicy,
                        null
                );

        if (policy == null) {
            issues.add(
                    base
                            + ".default-policy invalide: "
                            + rawPolicy
                            + " (ALLOW ou DENY attendu)"
            );

            policy =
                    SAFEZONE_ID.equals(zoneId)
                            ? DefaultPolicy.ALLOW
                            : DefaultPolicy.DENY;
        }

        EnumSet<TerritoryAction> allowed =
                parseActions(
                        section.getStringList(
                                "allowed-actions"
                        ),
                        base
                                + ".allowed-actions",
                        issues
                );

        EnumSet<TerritoryAction> denied =
                parseActions(
                        section.getStringList(
                                "denied-actions"
                        ),
                        base
                                + ".denied-actions",
                        issues
                );

        Set<TerritoryAction> overlap =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        overlap.addAll(allowed);
        overlap.retainAll(denied);

        if (!overlap.isEmpty()) {
            issues.add(
                    base
                            + ": actions présentes dans ALLOW et DENY "
                            + overlap
                            + "; DENY sera prioritaire."
            );
        }

        return new ZoneDefinition(
                zoneId,
                displayName,
                color,
                mapSymbol,
                title,
                subtitle,
                enterMessage,
                pvp,
                policy,
                allowed,
                denied,
                true
        );
    }

    private EnumSet<TerritoryAction> parseActions(
            List<String> values,
            String path,
            List<String> issues
    ) {
        EnumSet<TerritoryAction> result =
                EnumSet.noneOf(
                        TerritoryAction.class
                );

        if (values == null) {
            return result;
        }

        for (String value
                : values) {
            TerritoryAction action =
                    TerritoryAction.fromConfigKey(
                            value
                    );

            if (action == null) {
                issues.add(
                        path
                                + ": TerritoryAction inconnue '"
                                + value
                                + "'."
                );
                continue;
            }

            result.add(
                    action
            );
        }

        return result;
    }

    private ZoneDefinition defaultSafezone() {
        EnumSet<TerritoryAction> denied =
                EnumSet.of(
                        TerritoryAction.BLOCK_PLACE,
                        TerritoryAction.BLOCK_BREAK,
                        TerritoryAction.SPAWNER_PLACE,
                        TerritoryAction.SPAWNER_BREAK,
                        TerritoryAction.TNT_PLACE,
                        TerritoryAction.TNT_IGNITE,
                        TerritoryAction.BUCKET_EMPTY,
                        TerritoryAction.BUCKET_FILL,
                        TerritoryAction.FLINT_AND_STEEL,
                        TerritoryAction.SET_HOME,
                        TerritoryAction.ENDER_PEARL,
                        TerritoryAction.PISTON,
                        TerritoryAction.FLUID_FLOW,
                        TerritoryAction.FIRE_SPREAD,
                        TerritoryAction.EXPLOSION_BLOCK_DAMAGE,
                        TerritoryAction.ENTITY_GRIEF,
                        TerritoryAction.WITHER_SPAWN
                );

        return new ZoneDefinition(
                SAFEZONE_ID,
                "SafeZone",
                "&a",
                "S",
                "&aSafeZone",
                "&7Zone protégée",
                "&a~ SafeZone",
                false,
                DefaultPolicy.ALLOW,
                Collections.<TerritoryAction>emptySet(),
                denied,
                false
        );
    }

    private ZoneDefinition defaultWarzone() {
        EnumSet<TerritoryAction> allowed =
                EnumSet.of(
                        TerritoryAction.ENTER,
                        TerritoryAction.SWITCH,
                        TerritoryAction.CONTAINER_OPEN,
                        TerritoryAction.CONTAINER_DEPOSIT,
                        TerritoryAction.CONTAINER_WITHDRAW,
                        TerritoryAction.HOPPER,
                        TerritoryAction.ANVIL,
                        TerritoryAction.ENCHANT,
                        TerritoryAction.ENDER_PEARL,
                        TerritoryAction.PISTON,
                        TerritoryAction.FLUID_FLOW,
                        TerritoryAction.FIRE_SPREAD,
                        TerritoryAction.EXPLOSION_BLOCK_DAMAGE
                );

        return new ZoneDefinition(
                WARZONE_ID,
                "WarZone",
                "&4",
                "W",
                "&4WarZone",
                "&cPvP activé - Zone dangereuse",
                "&4~ WarZone &c(PvP activé)",
                true,
                DefaultPolicy.DENY,
                allowed,
                Collections.<TerritoryAction>emptySet(),
                false
        );
    }

    private static String defaultDisplayName(
            String zoneId
    ) {
        if (SAFEZONE_ID.equals(
                zoneId
        )) {
            return "SafeZone";
        }

        if (WARZONE_ID.equals(
                zoneId
        )) {
            return "WarZone";
        }

        return zoneId;
    }

    private static String defaultColor(
            String zoneId
    ) {
        if (SAFEZONE_ID.equals(
                zoneId
        )) {
            return "&a";
        }

        if (WARZONE_ID.equals(
                zoneId
        )) {
            return "&4";
        }

        return "&f";
    }

    private static String defaultSymbol(
            String zoneId
    ) {
        if (SAFEZONE_ID.equals(
                zoneId
        )) {
            return "S";
        }

        if (WARZONE_ID.equals(
                zoneId
        )) {
            return "W";
        }

        return "?";
    }

    // ============================================================
    // Persistence payload
    // ============================================================

    public String capturePayloadJson() {
        JsonObject root =
                new JsonObject();

        root.addProperty(
                "schema",
                PAYLOAD_SCHEMA
        );

        JsonArray entries =
                new JsonArray();

        Map<String, String> sorted =
                new TreeMap<String, String>();

        for (Map.Entry<FLocation, String> entry
                : zones.entrySet()) {
            sorted.put(
                    entry.getKey()
                            .getKey(),
                    entry.getValue()
            );
        }

        for (Map.Entry<String, String> entry
                : sorted.entrySet()) {
            JsonObject zone =
                    new JsonObject();

            zone.addProperty(
                    "location",
                    entry.getKey()
            );

            zone.addProperty(
                    "zone",
                    entry.getValue()
            );

            entries.add(zone);
        }

        root.add(
                "entries",
                entries
        );

        return root.toString();
    }

    private void restorePayload(
            String payload
    ) {
        JsonElement parsed =
                new JsonParser()
                        .parse(payload);

        if (parsed == null
                || !parsed.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Global zones payload invalide"
            );
        }

        JsonObject root =
                parsed.getAsJsonObject();

        int payloadSchema =
                root.has("schema")
                        ? root.get("schema")
                                .getAsInt()
                        : 1;

        if (payloadSchema < 1
                || payloadSchema > PAYLOAD_SCHEMA) {
            throw new IllegalStateException(
                    "Global zones payload schema non supporté: "
                            + payloadSchema
            );
        }

        JsonArray entries =
                root.has("entries")
                        && root.get("entries")
                                .isJsonArray()
                        ? root.getAsJsonArray(
                                "entries"
                        )
                        : new JsonArray();

        int maxChunks =
                maxTotalChunks;

        Map<FLocation, String> restored =
                new LinkedHashMap<FLocation, String>();

        boolean migratedSchema1 = false;

        for (JsonElement element
                : entries) {
            if (element == null
                    || !element.isJsonObject()) {
                continue;
            }

            JsonObject entry =
                    element.getAsJsonObject();

            if (!entry.has(
                    "location"
            )) {
                continue;
            }

            String rawZoneId = null;

            if (entry.has(
                    "zone"
            )) {
                rawZoneId =
                        entry.get(
                                "zone"
                        ).getAsString();
            } else if (entry.has(
                    "type"
            )) {
                /*
                 * Payload schema 1:
                 * SAFEZONE / WARZONE.
                 */
                GlobalZoneType legacy =
                        GlobalZoneType.parse(
                                entry.get(
                                        "type"
                                ).getAsString()
                        );

                if (legacy != null) {
                    rawZoneId =
                            legacy.getConfigKey();
                    migratedSchema1 = true;
                }
            }

            String zoneId =
                    ZoneDefinition.normalizeId(
                            rawZoneId
                    );

            FLocation location =
                    FLocation.fromKey(
                            entry.get(
                                    "location"
                            ).getAsString()
                    );

            if (location == null
                    || zoneId == null) {
                continue;
            }

            restored.put(
                    location,
                    zoneId
            );

            if (restored.size()
                    > maxChunks) {
                throw new IllegalStateException(
                        "Global zones payload dépasse la limite "
                                + maxChunks
                );
            }
        }

        zones.clear();
        zones.putAll(
                restored
        );

        if (migratedSchema1) {
            dirty();

            KfactionLogger.success(
                    plugin,
                    "Global Zones payload migré schema 1 -> 2."
            );
        }

        if (!getOrphanZoneIds()
                .isEmpty()) {
            KfactionLogger.warn(
                    plugin,
                    "Zones persistées sans définition config: "
                            + getOrphanZoneIds()
            );
        }
    }

    // ============================================================
    // Helpers / metrics
    // ============================================================

    private boolean containsAssignedZoneId(
            String zoneId
    ) {
        for (String assigned
                : zones.values()) {
            if (zoneId.equals(
                    assigned
            )) {
                return true;
            }
        }

        return false;
    }

    private void dirty() {
        plugin.getStorageManager()
                .markGlobalZonesDirty();
    }

    private void audit(
            OperationContext context,
            String details
    ) {
        String action =
                details != null
                && details.toLowerCase(
                        java.util.Locale.ROOT
                ).startsWith(
                        "clear "
                )
                        ? "GLOBAL_ZONE_CLEAR"
                        : "GLOBAL_ZONE_SET";

        if (plugin.getLogManager() != null) {
            plugin.getLogManager()
                    .audit(
                            context,
                            AuditCategory.GLOBAL_ZONE,
                            action,
                            AuditOutcome.SUCCESS,
                            null,
                            null,
                            null,
                            details
                    );
        }

        KfactionLogger.debug(
                plugin,
                "[GlobalZone] actor="
                        + (context.hasActorName()
                                ? context.getActorName()
                                : "SYSTEM")
                        + " source="
                        + context.getSource()
                        + " correlation="
                        + context.getCorrelationId()
                        + " "
                        + details
        );
    }

    private static OperationResult<String> failure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "zone.failed",
                detail
        );
    }

    private static OperationResult<GlobalZoneType> legacyFailure(
            Status status,
            String detail
    ) {
        return OperationResult.failure(
                status,
                "zone.failed",
                detail
        );
    }

    private static OperationResult<GlobalZoneType> toLegacyResult(
            OperationResult<String> result,
            GlobalZoneType legacyValue
    ) {
        if (result == null) {
            return legacyFailure(
                    Status.FAILED,
                    "Résultat zone absent"
            );
        }

        if (result.isSuccess()) {
            return OperationResult.success(
                    legacyValue
            );
        }

        if (result.getStatus()
                == Status.NO_CHANGE) {
            return OperationResult.noChange(
                    result.getMessageKey()
            );
        }

        return OperationResult.failure(
                result.getStatus(),
                result.getMessageKey(),
                result.getDetail()
        );
    }
}
