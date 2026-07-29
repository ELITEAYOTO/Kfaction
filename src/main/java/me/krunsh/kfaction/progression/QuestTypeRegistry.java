package me.krunsh.kfaction.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Registre extensible des types réellement implémentés. */
public final class QuestTypeRegistry {
    public static final class TypeDefinition {
        private final String id;
        private final QuestTarget.Kind targetKind;

        private TypeDefinition(String id, QuestTarget.Kind targetKind) {
            this.id = id;
            this.targetKind = targetKind;
        }

        public String getId() { return id; }
        public QuestTarget.Kind getTargetKind() { return targetKind; }
    }

    private final Map<String, TypeDefinition> types =
            new LinkedHashMap<String, TypeDefinition>();
    private final Map<String, String> aliases = new LinkedHashMap<String, String>();

    public QuestTypeRegistry register(String id, QuestTarget.Kind targetKind,
            String... acceptedAliases) {
        String canonical = normalize(id);
        if (canonical == null || types.containsKey(canonical)) {
            throw new IllegalArgumentException("Type de quête invalide ou déjà enregistré: " + id);
        }
        types.put(canonical, new TypeDefinition(canonical, targetKind));
        aliases.put(canonical, canonical);
        for (String alias : acceptedAliases) {
            String normalized = normalize(alias);
            if (normalized == null || aliases.containsKey(normalized)) {
                throw new IllegalArgumentException("Alias de quête invalide ou déjà enregistré: " + alias);
            }
            aliases.put(normalized, canonical);
        }
        return this;
    }

    public TypeDefinition resolve(String id) {
        String canonical = aliases.get(normalize(id));
        return canonical == null ? null : types.get(canonical);
    }

    public String canonicalize(String id) {
        TypeDefinition definition = resolve(id);
        return definition == null ? null : definition.getId();
    }

    public Map<String, TypeDefinition> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    public static QuestTypeRegistry builtIns() {
        return new QuestTypeRegistry()
                .register("MINE", QuestTarget.Kind.MATERIAL, "BLOCK_BREAK")
                .register("BREAK", QuestTarget.Kind.MATERIAL)
                .register("HARVEST", QuestTarget.Kind.MATERIAL)
                .register("PLACE", QuestTarget.Kind.MATERIAL)
                .register("SMELT", QuestTarget.Kind.MATERIAL, "ITEM_SMELT")
                .register("CRAFT", QuestTarget.Kind.MATERIAL)
                .register("SELL", QuestTarget.Kind.MATERIAL, "ITEM_SELL")
                .register("MOB_KILL", QuestTarget.Kind.ENTITY, "ENTITY_KILL")
                .register("PLAYER_KILL", QuestTarget.Kind.NONE)
                .register("CUSTOM_CRAFT", QuestTarget.Kind.STRING)
                .register("SPAWNER_BREAK", QuestTarget.Kind.ENTITY)
                .register("SPAWNER_PLACE", QuestTarget.Kind.ENTITY)
                .register("CUSTOM_ORE_MINE", QuestTarget.Kind.STRING)
                .register("ENCHANT", QuestTarget.Kind.MATERIAL)
                .register("FISH", QuestTarget.Kind.MATERIAL)
                .register("BREED", QuestTarget.Kind.ENTITY)
                .register("TAME", QuestTarget.Kind.ENTITY);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? null : normalized;
    }
}
