package me.krunsh.kfaction.progression;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import me.krunsh.kfaction.data.RewardType;

/** Charge un candidat complet sans toucher à la configuration active. */
public final class ProgressionConfigLoader {
    private static final int SCHEMA_VERSION = 2;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)\\}");
    private static final Set<String> KNOWN_PLACEHOLDERS = setOf(
            "progress", "amount", "remaining", "percent", "target", "tier",
            "level", "faction", "members", "quest", "category");
    private static final Set<String> TOP_KEYS = setOf(
            "schema-version", "settings", "categories", "member-tiers", "levels");
    private static final Set<String> SETTINGS_KEYS = setOf(
            "enabled", "starting-level", "broadcast-level-up");
    private static final Set<String> CATEGORY_KEYS = setOf(
            "display", "lore", "icon");
    private static final Set<String> TIER_KEYS = setOf(
            "display", "min-players", "max-players");
    private static final Set<String> LEVEL_KEYS = setOf(
            "display", "rewards-on-enter", "tiers");
    private static final Set<String> QUEST_KEYS = setOf(
            "type", "category", "target", "sparrowmc-item", "amount",
            "display", "lore", "icon", "conditions");
    private static final Set<String> CONDITION_KEYS = setOf(
            "count-player-placed-blocks", "mature-only", "allow-silk-touch",
            "allowed-worlds", "allowed-regions", "blocked-regions",
            "victim-cooldown-seconds", "max-per-victim-per-day",
            "exclude-same-faction", "exclude-allies", "exclude-npcs",
            "exclude-same-ip");

    private final QuestTypeRegistry typeRegistry;
    private final ValidationEnvironment environment;
    private final int configuredMaxMembers;

    public ProgressionConfigLoader(QuestTypeRegistry typeRegistry,
            ValidationEnvironment environment, int configuredMaxMembers) {
        this.typeRegistry = typeRegistry;
        this.environment = environment == null
                ? ValidationEnvironment.PERMISSIVE : environment;
        this.configuredMaxMembers = Math.max(1, configuredMaxMembers);
    }

    public LoadResult load(File file) {
        List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        if (file == null || !file.isFile()) {
            error(issues, "progression.yml",
                    "fichier manquant. Copiez progression.example.yml puis validez-le.");
            return LoadResult.failure(issues);
        }
        try {
            issues.addAll(YamlDuplicateKeyScanner.scan(file));
        } catch (IOException ex) {
            error(issues, "progression.yml",
                    "pré-scan impossible: " + ex.getMessage());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            error(issues, "progression.yml", "YAML invalide: " + ex.getMessage());
            return LoadResult.failure(issues);
        }

        rejectUnknownKeys(yaml, TOP_KEYS, "progression.yml", issues);
        int schema = yaml.getInt("schema-version", -1);
        if (schema != SCHEMA_VERSION) {
            error(issues, "progression.yml.schema-version",
                    "version attendue " + SCHEMA_VERSION + ", valeur reçue " + schema + ".");
        }

        ConfigurationSection settings = yaml.getConfigurationSection("settings");
        if (settings == null) {
            error(issues, "progression.yml.settings", "section obligatoire manquante.");
            settings = yaml.createSection("__invalid_settings");
        } else rejectUnknownKeys(settings, SETTINGS_KEYS,
                "progression.yml.settings", issues);
        boolean enabled = settings.getBoolean("enabled", true);
        int startingLevel = settings.getInt("starting-level", 1);
        if (startingLevel < 1) {
            error(issues, "progression.yml.settings.starting-level",
                    "doit être supérieur ou égal à 1.");
        }
        boolean broadcast = settings.getBoolean("broadcast-level-up", true);

        Map<String, CategoryDefinition> categories = parseCategories(yaml, issues);
        Map<String, MemberTierDefinition> tiers = parseTiers(yaml, issues);
        Map<Integer, LevelDefinition> levels =
                parseLevels(yaml, tiers, categories, startingLevel, issues);

        if (hasErrors(issues)) return LoadResult.failure(issues);
        return LoadResult.success(new ProgressionConfig(schema, enabled,
                startingLevel, broadcast, tiers, categories, levels), issues);
    }

    private Map<String, CategoryDefinition> parseCategories(YamlConfiguration yaml,
            List<ValidationIssue> issues) {
        LinkedHashMap<String, CategoryDefinition> result =
                new LinkedHashMap<String, CategoryDefinition>();
        ConfigurationSection root = yaml.getConfigurationSection("categories");
        if (root == null) {
            error(issues, "progression.yml.categories",
                    "au moins une catégorie d'affichage est obligatoire.");
            return result;
        }
        Map<String, String> insensitive = new HashMap<String, String>();
        for (String id : root.getKeys(false)) {
            String path = "progression.yml.categories." + id;
            if (!validId(id)) {
                error(issues, path, "identifiant invalide.");
                continue;
            }
            rejectCaseCollision(id, insensitive, path, issues);
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                error(issues, path, "doit être une section.");
                continue;
            }
            rejectUnknownKeys(section, CATEGORY_KEYS, path, issues);
            ConfigurationSection icon = section.getConfigurationSection("icon");
            String materialName = icon == null ? "PAPER"
                    : icon.getString("material", "PAPER");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material == Material.AIR) {
                errorWithSuggestion(issues, path + ".icon.material",
                        "matériau inexistant \"" + materialName + "\"",
                        materialName, materialNames());
                materialName = "PAPER";
            }
            int data = icon == null ? 0 : icon.getInt("data", 0);
            validateData(path + ".icon.data", data, material, issues);
            result.put(id, new CategoryDefinition(id,
                    section.getString("display", id), materialName, data,
                    icon == null ? null : emptyToNull(icon.getString("sparrowmc-item")),
                    section.getStringList("lore")));
        }
        return result;
    }

    private Map<String, MemberTierDefinition> parseTiers(YamlConfiguration yaml,
            List<ValidationIssue> issues) {
        ConfigurationSection root = yaml.getConfigurationSection("member-tiers");
        LinkedHashMap<String, MemberTierDefinition> result =
                new LinkedHashMap<String, MemberTierDefinition>();
        if (root == null) {
            error(issues, "progression.yml.member-tiers",
                    "section obligatoire manquante.");
            return result;
        }
        List<TierCandidate> candidates = new ArrayList<TierCandidate>();
        Map<String, String> insensitive = new HashMap<String, String>();
        for (String id : root.getKeys(false)) {
            String path = "progression.yml.member-tiers." + id;
            if (!validId(id)) {
                error(issues, path, "identifiant invalide.");
                continue;
            }
            rejectCaseCollision(id, insensitive, path, issues);
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                error(issues, path, "doit être une section.");
                continue;
            }
            rejectUnknownKeys(section, TIER_KEYS, path, issues);
            int min = section.getInt("min-players", Integer.MIN_VALUE);
            int max = section.getInt("max-players", Integer.MIN_VALUE);
            if (min < 1) error(issues, path + ".min-players",
                    "entier supérieur ou égal à 1 requis.");
            if (max < min) error(issues, path + ".max-players",
                    "doit être supérieur ou égal à min-players.");
            candidates.add(new TierCandidate(id, section.getString("display", id),
                    min, max));
        }
        Collections.sort(candidates, new Comparator<TierCandidate>() {
            @Override public int compare(TierCandidate a, TierCandidate b) {
                int byMin = Integer.compare(a.min, b.min);
                return byMin != 0 ? byMin : a.id.compareTo(b.id);
            }
        });
        int expectedMin = 1;
        int rank = 0;
        for (TierCandidate candidate : candidates) {
            String path = "progression.yml.member-tiers." + candidate.id;
            if (candidate.min < expectedMin) {
                error(issues, path + ".min-players",
                        "chevauche la tranche précédente; valeur attendue "
                        + expectedMin + ".");
            } else if (candidate.min > expectedMin) {
                error(issues, path + ".min-players",
                        "trou entre les tranches; valeur attendue "
                        + expectedMin + ".");
            }
            result.put(candidate.id, new MemberTierDefinition(candidate.id,
                    candidate.display, candidate.min, candidate.max, rank++));
            if (candidate.max < Integer.MAX_VALUE) expectedMin = candidate.max + 1;
        }
        if (!candidates.isEmpty()
                && candidates.get(candidates.size() - 1).max < configuredMaxMembers) {
            error(issues, "progression.yml.member-tiers",
                    "aucune tranche ne couvre les factions de "
                    + (candidates.get(candidates.size() - 1).max + 1) + " à "
                    + configuredMaxMembers + " membres.");
        }
        return result;
    }

    private Map<Integer, LevelDefinition> parseLevels(YamlConfiguration yaml,
            Map<String, MemberTierDefinition> tiers,
            Map<String, CategoryDefinition> categories, int startingLevel,
            List<ValidationIssue> issues) {
        LinkedHashMap<Integer, LevelDefinition> result =
                new LinkedHashMap<Integer, LevelDefinition>();
        ConfigurationSection root = yaml.getConfigurationSection("levels");
        if (root == null) {
            error(issues, "progression.yml.levels", "section obligatoire manquante.");
            return result;
        }
        List<Integer> numbers = new ArrayList<Integer>();
        for (String key : root.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                if (level < startingLevel) {
                    error(issues, "progression.yml.levels." + key,
                            "niveau inférieur au niveau de départ " + startingLevel + ".");
                } else numbers.add(level);
            } catch (NumberFormatException ex) {
                error(issues, "progression.yml.levels." + key,
                        "l'identifiant d'un niveau doit être un entier.");
            }
        }
        Collections.sort(numbers);
        int expected = startingLevel;
        for (Integer number : numbers) {
            if (number != expected) {
                error(issues, "progression.yml.levels." + number,
                        "niveau non contigu; niveau attendu " + expected + ".");
                expected = number;
            }
            expected++;
            String path = "progression.yml.levels." + number;
            ConfigurationSection section = root.getConfigurationSection(String.valueOf(number));
            if (section == null) continue;
            rejectUnknownKeys(section, LEVEL_KEYS, path, issues);
            Map<String, TierLevelDefinition> tierLevels =
                    parseTierLevels(section, path, tiers, categories, issues);
            List<RewardDefinition> rewards =
                    parseRewards(section, path, issues);
            result.put(number, new LevelDefinition(number,
                    section.getString("display", "Niveau " + number),
                    tierLevels, rewards));
        }
        if (!numbers.contains(startingLevel)) {
            error(issues, "progression.yml.levels." + startingLevel,
                    "niveau de départ manquant.");
        }
        return result;
    }

    private Map<String, TierLevelDefinition> parseTierLevels(
            ConfigurationSection level, String levelPath,
            Map<String, MemberTierDefinition> tiers,
            Map<String, CategoryDefinition> categories,
            List<ValidationIssue> issues) {
        LinkedHashMap<String, TierLevelDefinition> result =
                new LinkedHashMap<String, TierLevelDefinition>();
        ConfigurationSection root = level.getConfigurationSection("tiers");
        if (root == null) {
            error(issues, levelPath + ".tiers", "section obligatoire manquante.");
            return result;
        }
        for (String configuredTier : root.getKeys(false)) {
            if (!tiers.containsKey(configuredTier)) {
                error(issues, levelPath + ".tiers." + configuredTier,
                        "tranche inconnue.");
            }
        }
        for (String tierId : tiers.keySet()) {
            String path = levelPath + ".tiers." + tierId;
            ConfigurationSection tier = root.getConfigurationSection(tierId);
            if (tier == null) {
                error(issues, path,
                        "définition obligatoire manquante pour cette tranche.");
                continue;
            }
            ConfigurationSection quests = tier.getConfigurationSection("quests");
            if (quests == null || quests.getKeys(false).isEmpty()) {
                error(issues, path + ".quests",
                        "au moins une quête obligatoire est requise.");
                continue;
            }
            rejectUnknownKeys(tier, setOf("quests"), path, issues);
            LinkedHashMap<String, QuestDefinition> definitions =
                    new LinkedHashMap<String, QuestDefinition>();
            Map<String, String> insensitive = new HashMap<String, String>();
            for (String questId : quests.getKeys(false)) {
                String questPath = path + ".quests." + questId;
                if (!validId(questId)) {
                    error(issues, questPath, "identifiant invalide.");
                    continue;
                }
                rejectCaseCollision(questId, insensitive, questPath, issues);
                ConfigurationSection quest = quests.getConfigurationSection(questId);
                if (quest == null) {
                    error(issues, questPath, "doit être une section.");
                    continue;
                }
                QuestDefinition parsed = parseQuest(questId, quest, questPath,
                        categories, issues);
                if (parsed != null) definitions.put(questId, parsed);
            }
            result.put(tierId, new TierLevelDefinition(tierId, definitions));
        }
        return result;
    }

    private QuestDefinition parseQuest(String id, ConfigurationSection section,
            String path, Map<String, CategoryDefinition> categories,
            List<ValidationIssue> issues) {
        rejectUnknownKeys(section, QUEST_KEYS, path, issues);
        QuestTypeRegistry.TypeDefinition type =
                typeRegistry.resolve(section.getString("type"));
        if (type == null) {
            errorWithSuggestion(issues, path + ".type",
                    "type de quête inconnu \"" + section.getString("type") + "\"",
                    section.getString("type"), typeRegistry.getTypes().keySet());
        }
        String category = section.getString("category", "").trim();
        if (!categories.containsKey(category)) {
            errorWithSuggestion(issues, path + ".category",
                    "catégorie inconnue \"" + category + "\"",
                    category, categories.keySet());
        }
        long amount = section.getLong("amount", Long.MIN_VALUE);
        if (amount <= 0L) {
            error(issues, path + ".amount", "entier strictement positif requis.");
        }
        QuestTarget target = type == null
                ? parseUnknownTypeTarget(section, path, issues)
                : parseTarget(type, section, path, issues);
        QuestConditions conditions = parseConditions(type == null ? "" : type.getId(),
                section.getConfigurationSection("conditions"), path + ".conditions",
                issues);
        String display = section.getString("display", id);
        validatePlaceholders(display, path + ".display", issues);
        List<String> lore = section.getStringList("lore");
        for (int i = 0; i < lore.size(); i++) {
            validatePlaceholders(lore.get(i), path + ".lore[" + i + "]", issues);
        }
        ConfigurationSection icon = section.getConfigurationSection("icon");
        String iconMaterial = icon == null ? "PAPER"
                : icon.getString("material", "PAPER");
        Material material = Material.matchMaterial(iconMaterial);
        if (material == null || material == Material.AIR) {
            errorWithSuggestion(issues, path + ".icon.material",
                    "matériau inexistant \"" + iconMaterial + "\"",
                    iconMaterial, materialNames());
            iconMaterial = "PAPER";
        }
        int iconData = icon == null ? 0 : icon.getInt("data", 0);
        validateData(path + ".icon.data", iconData, material, issues);
        if (type == null) return null;
        return new QuestDefinition(id, type.getId(), category, target, amount,
                display, lore, iconMaterial, iconData,
                icon == null ? null : emptyToNull(icon.getString("sparrowmc-item")),
                conditions);
    }

    private QuestTarget parseUnknownTypeTarget(ConfigurationSection section,
            String path, List<ValidationIssue> issues) {
        // Continue l'audit comme cible matérielle pour remonter le maximum
        // d'erreurs en une seule validation, sans appliquer ce candidat.
        QuestTypeRegistry.TypeDefinition materialType = typeRegistry.resolve("MINE");
        return parseTarget(materialType, section, path, issues);
    }

    private QuestTarget parseTarget(QuestTypeRegistry.TypeDefinition type,
            ConfigurationSection section, String path,
            List<ValidationIssue> issues) {
        if (type.getTargetKind() == QuestTarget.Kind.NONE) return QuestTarget.none();
        String raw = section.getString("target");
        if (raw == null || raw.trim().isEmpty()) {
            error(issues, path + ".target", "cible obligatoire manquante.");
            return QuestTarget.none();
        }
        raw = raw.trim();
        if (type.getTargetKind() == QuestTarget.Kind.STRING) {
            return QuestTarget.string(raw);
        }
        if (type.getTargetKind() == QuestTarget.Kind.ENTITY) {
            try {
                return QuestTarget.entity(raw,
                        EntityType.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                errorWithSuggestion(issues, path + ".target",
                        "entité inexistante \"" + raw + "\"",
                        raw, entityNames());
                return QuestTarget.none();
            }
        }

        String materialPart = raw;
        int data = 0;
        boolean dataSpecified = false;
        int separator = raw.lastIndexOf(':');
        if (separator > 0) {
            materialPart = raw.substring(0, separator);
            String dataPart = raw.substring(separator + 1);
            try {
                data = Integer.parseInt(dataPart);
                dataSpecified = true;
            } catch (NumberFormatException ex) {
                error(issues, path + ".target",
                        "data value non numérique dans \"" + raw + "\".");
            }
        }
        Material material = Material.matchMaterial(materialPart);
        if (material == null || material == Material.AIR) {
            errorWithSuggestion(issues, path + ".target",
                    "matériau inexistant \"" + materialPart + "\"",
                    materialPart, materialNames());
            return QuestTarget.none();
        }
        if (dataSpecified) validateData(path + ".target", data, material, issues);
        return QuestTarget.material(raw, material, data, dataSpecified,
                section.getString("sparrowmc-item"));
    }

    private QuestConditions parseConditions(String type,
            ConfigurationSection section, String path,
            List<ValidationIssue> issues) {
        boolean mine = "MINE".equals(type);
        boolean harvest = "HARVEST".equals(type);
        boolean pvp = "PLAYER_KILL".equals(type);
        if (section != null) rejectUnknownKeys(section, CONDITION_KEYS, path, issues);
        boolean countPlaced = section == null ? !mine
                : section.getBoolean("count-player-placed-blocks", !mine);
        boolean matureOnly = section == null ? harvest
                : section.getBoolean("mature-only", harvest);
        boolean allowSilk = section != null
                && section.getBoolean("allow-silk-touch", false);
        Set<String> worlds = stringSet(section, "allowed-worlds");
        Set<String> allowedRegions = stringSet(section, "allowed-regions");
        Set<String> blockedRegions = stringSet(section, "blocked-regions");
        int cooldown = section == null ? (pvp ? 900 : 0)
                : section.getInt("victim-cooldown-seconds", pvp ? 900 : 0);
        int maxDaily = section == null ? (pvp ? 3 : 0)
                : section.getInt("max-per-victim-per-day", pvp ? 3 : 0);
        if (cooldown < 0) error(issues, path + ".victim-cooldown-seconds",
                "ne peut pas être négatif.");
        if (maxDaily < 0) error(issues, path + ".max-per-victim-per-day",
                "ne peut pas être négatif.");
        if (!pvp && section != null) {
            for (String key : Arrays.asList("victim-cooldown-seconds",
                    "max-per-victim-per-day", "exclude-same-faction",
                    "exclude-allies", "exclude-npcs", "exclude-same-ip")) {
                if (section.contains(key)) {
                    error(issues, path + "." + key,
                            "option compatible uniquement avec PLAYER_KILL.");
                }
            }
        }
        for (String world : worlds) {
            ValidationEnvironment.Status status = environment.worldExists(world);
            if (status == ValidationEnvironment.Status.INVALID) {
                error(issues, path + ".allowed-worlds",
                        "monde inexistant \"" + world + "\".");
            } else if (status == ValidationEnvironment.Status.UNKNOWN) {
                warning(issues, path + ".allowed-worlds",
                        "existence du monde non vérifiable au chargement: " + world + ".");
            }
        }
        return new QuestConditions(countPlaced, matureOnly, allowSilk, worlds,
                allowedRegions, blockedRegions, cooldown, maxDaily,
                section == null || section.getBoolean("exclude-same-faction", true),
                section == null || section.getBoolean("exclude-allies", true),
                section == null || section.getBoolean("exclude-npcs", true),
                section != null && section.getBoolean("exclude-same-ip", false));
    }

    private List<RewardDefinition> parseRewards(ConfigurationSection level,
            String path, List<ValidationIssue> issues) {
        List<RewardDefinition> result = new ArrayList<RewardDefinition>();
        List<Map<?, ?>> maps = level.getMapList("rewards-on-enter");
        Set<String> ids = new HashSet<String>();
        int index = 0;
        for (Map<?, ?> map : maps) {
            String rewardPath = path + ".rewards-on-enter[" + index + "]";
            String id = map.get("id") == null ? null : String.valueOf(map.get("id"));
            if (!validId(id)) {
                error(issues, rewardPath + ".id",
                        "identifiant stable obligatoire.");
                index++;
                continue;
            }
            if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                error(issues, rewardPath + ".id",
                        "identifiant de récompense dupliqué: " + id + ".");
            }
            String type = map.get("type") == null ? null
                    : String.valueOf(map.get("type"));
            if (RewardType.fromConfigKey(type) == null) {
                error(issues, rewardPath + ".type",
                        "type de récompense inconnu \"" + type + "\".");
            }
            if (!map.containsKey("value")) {
                error(issues, rewardPath + ".value", "valeur obligatoire manquante.");
            }
            result.add(new RewardDefinition(id, type, map.get("value"),
                    map.get("description") == null ? ""
                            : String.valueOf(map.get("description"))));
            index++;
        }
        return result;
    }

    private void validateData(String path, int data, Material material,
            List<ValidationIssue> issues) {
        int max = material != null && material.isBlock() ? 15 : Short.MAX_VALUE;
        if (data < 0 || data > max) {
            error(issues, path, "data value hors limites 0.." + max + ".");
        }
    }

    private void validatePlaceholders(String value, String path,
            List<ValidationIssue> issues) {
        if (value == null) return;
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!KNOWN_PLACEHOLDERS.contains(placeholder)) {
                errorWithSuggestion(issues, path,
                        "placeholder inconnu {" + placeholder + "}",
                        placeholder, KNOWN_PLACEHOLDERS);
            }
        }
    }

    private void rejectUnknownKeys(ConfigurationSection section,
            Set<String> allowed, String path, List<ValidationIssue> issues) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) {
                errorWithSuggestion(issues, path + "." + key,
                        "option inconnue", key, allowed);
            }
        }
    }

    private void rejectCaseCollision(String id, Map<String, String> seen,
            String path, List<ValidationIssue> issues) {
        String lower = id.toLowerCase(Locale.ROOT);
        String previous = seen.get(lower);
        if (previous != null && !previous.equals(id)) {
            error(issues, path, "collision de casse avec \"" + previous + "\".");
        } else seen.put(lower, id);
    }

    private void errorWithSuggestion(List<ValidationIssue> issues, String path,
            String message, String value, Iterable<String> candidates) {
        String closest = closest(value, candidates);
        error(issues, path, message
                + (closest == null ? "." : ". Valeur proche: \"" + closest + "\"."));
    }

    private String closest(String value, Iterable<String> candidates) {
        if (value == null) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = editDistance(normalized,
                    candidate.toUpperCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= Math.max(2, normalized.length() / 3) ? best : null;
    }

    private int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int replace = previous[j - 1]
                        + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(current[j - 1] + 1,
                        previous[j] + 1), replace);
            }
            previous = current;
        }
        return previous[b.length()];
    }

    private Set<String> stringSet(ConfigurationSection section, String key) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (section == null) return result;
        for (String value : section.getStringList(key)) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        }
        return result;
    }

    private Set<String> materialNames() {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (Material material : Material.values()) names.add(material.name());
        return names;
    }

    private Set<String> entityNames() {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (EntityType type : EntityType.values()) names.add(type.name());
        return names;
    }

    private boolean validId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    private boolean hasErrors(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) return true;
        }
        return false;
    }

    private void error(List<ValidationIssue> issues, String path, String message) {
        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, message));
    }

    private void warning(List<ValidationIssue> issues, String path, String message) {
        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, message));
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static final class TierCandidate {
        private final String id;
        private final String display;
        private final int min;
        private final int max;

        private TierCandidate(String id, String display, int min, int max) {
            this.id = id;
            this.display = display;
            this.min = min;
            this.max = max;
        }
    }

    public static final class LoadResult {
        private final ProgressionConfig config;
        private final List<ValidationIssue> issues;

        private LoadResult(ProgressionConfig config, List<ValidationIssue> issues) {
            this.config = config;
            this.issues = Collections.unmodifiableList(
                    new ArrayList<ValidationIssue>(issues));
        }

        public static LoadResult success(ProgressionConfig config,
                List<ValidationIssue> issues) {
            return new LoadResult(config, issues);
        }

        public static LoadResult failure(List<ValidationIssue> issues) {
            return new LoadResult(null, issues);
        }

        public boolean isValid() { return config != null; }
        public ProgressionConfig getConfig() { return config; }
        public List<ValidationIssue> getIssues() { return issues; }
    }
}
