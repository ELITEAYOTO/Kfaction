package me.krunsh.kfaction.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import me.krunsh.kfaction.permissions.TerritoryAction;

/**
 * Groupe logique de claims d'une faction.
 *
 * Les règles sont tri-state:
 * - INHERIT: retombe sur l'ACL normale de la faction;
 * - ALLOW: autorisation explicite dans ce groupe;
 * - DENY: refus explicite dans ce groupe.
 *
 * Le groupe ne possède pas lui-même les chunks: Faction garde l'association
 * FLocation -> groupId afin d'avoir une seule source de vérité territoriale.
 */
public final class ClaimGroup {

    public enum Rule {
        INHERIT,
        ALLOW,
        DENY;

        public static Rule parse(String value) {
            if (value == null) {
                return null;
            }

            String normalized =
                    value.trim()
                            .toUpperCase(Locale.ROOT);

            if ("DEFAULT".equals(normalized)
                    || "RESET".equals(normalized)) {
                return INHERIT;
            }

            try {
                return Rule.valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private final String id;

    private final Map<FactionRole, Map<TerritoryAction, Rule>> roleRules;
    private final Map<Relation, Map<TerritoryAction, Rule>> relationRules;

    public ClaimGroup(String id) {
        this.id = normalizeId(id);

        if (this.id == null) {
            throw new IllegalArgumentException(
                    "ClaimGroup id cannot be empty"
            );
        }

        this.roleRules =
                new EnumMap<FactionRole, Map<TerritoryAction, Rule>>(
                        FactionRole.class
                );

        this.relationRules =
                new EnumMap<Relation, Map<TerritoryAction, Rule>>(
                        Relation.class
                );
    }

    public String getId() {
        return id;
    }

    public Rule getRoleRule(
            FactionRole role,
            TerritoryAction action
    ) {
        if (role == null || action == null) {
            return Rule.INHERIT;
        }

        Map<TerritoryAction, Rule> rules =
                roleRules.get(role);

        if (rules == null) {
            return Rule.INHERIT;
        }

        Rule rule =
                rules.get(action);

        return rule != null
                ? rule
                : Rule.INHERIT;
    }

    public void setRoleRule(
            FactionRole role,
            TerritoryAction action,
            Rule rule
    ) {
        if (role == null
                || action == null
                || role == FactionRole.LEADER) {
            return;
        }

        Rule safeRule =
                rule != null
                        ? rule
                        : Rule.INHERIT;

        if (safeRule == Rule.INHERIT) {
            Map<TerritoryAction, Rule> rules =
                    roleRules.get(role);

            if (rules != null) {
                rules.remove(action);

                if (rules.isEmpty()) {
                    roleRules.remove(role);
                }
            }

            return;
        }

        Map<TerritoryAction, Rule> rules =
                roleRules.get(role);

        if (rules == null) {
            rules =
                    new EnumMap<TerritoryAction, Rule>(
                            TerritoryAction.class
                    );

            roleRules.put(
                    role,
                    rules
            );
        }

        rules.put(
                action,
                safeRule
        );
    }

    public Rule getRelationRule(
            Relation relation,
            TerritoryAction action
    ) {
        if (relation == null
                || action == null
                || relation == Relation.MEMBER) {
            return Rule.INHERIT;
        }

        Map<TerritoryAction, Rule> rules =
                relationRules.get(relation);

        if (rules == null) {
            return Rule.INHERIT;
        }

        Rule rule =
                rules.get(action);

        return rule != null
                ? rule
                : Rule.INHERIT;
    }

    public void setRelationRule(
            Relation relation,
            TerritoryAction action,
            Rule rule
    ) {
        if (relation == null
                || action == null
                || relation == Relation.MEMBER) {
            return;
        }

        Rule safeRule =
                rule != null
                        ? rule
                        : Rule.INHERIT;

        if (safeRule == Rule.INHERIT) {
            Map<TerritoryAction, Rule> rules =
                    relationRules.get(relation);

            if (rules != null) {
                rules.remove(action);

                if (rules.isEmpty()) {
                    relationRules.remove(relation);
                }
            }

            return;
        }

        Map<TerritoryAction, Rule> rules =
                relationRules.get(relation);

        if (rules == null) {
            rules =
                    new EnumMap<TerritoryAction, Rule>(
                            TerritoryAction.class
                    );

            relationRules.put(
                    relation,
                    rules
            );
        }

        rules.put(
                action,
                safeRule
        );
    }

    public Map<FactionRole, Map<TerritoryAction, Rule>>
            getRoleRulesSnapshot() {
        Map<FactionRole, Map<TerritoryAction, Rule>> copy =
                new EnumMap<FactionRole, Map<TerritoryAction, Rule>>(
                        FactionRole.class
                );

        for (Map.Entry<FactionRole, Map<TerritoryAction, Rule>> entry
                : roleRules.entrySet()) {
            copy.put(
                    entry.getKey(),
                    Collections.unmodifiableMap(
                            new EnumMap<TerritoryAction, Rule>(
                                    entry.getValue()
                            )
                    )
            );
        }

        return Collections.unmodifiableMap(copy);
    }

    public Map<Relation, Map<TerritoryAction, Rule>>
            getRelationRulesSnapshot() {
        Map<Relation, Map<TerritoryAction, Rule>> copy =
                new EnumMap<Relation, Map<TerritoryAction, Rule>>(
                        Relation.class
                );

        for (Map.Entry<Relation, Map<TerritoryAction, Rule>> entry
                : relationRules.entrySet()) {
            copy.put(
                    entry.getKey(),
                    Collections.unmodifiableMap(
                            new EnumMap<TerritoryAction, Rule>(
                                    entry.getValue()
                            )
                    )
            );
        }

        return Collections.unmodifiableMap(copy);
    }

    public boolean hasOverrides() {
        return !roleRules.isEmpty()
                || !relationRules.isEmpty();
    }

    public int getOverrideCount() {
        int count = 0;

        for (Map<TerritoryAction, Rule> rules
                : roleRules.values()) {
            count += rules.size();
        }

        for (Map<TerritoryAction, Rule> rules
                : relationRules.values()) {
            count += rules.size();
        }

        return count;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(Locale.ROOT);

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    @Override
    public String toString() {
        return "ClaimGroup{" +
                "id='" + id + '\'' +
                ", overrides=" + getOverrideCount() +
                '}';
    }
}
