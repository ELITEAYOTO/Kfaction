package me.krunsh.kfaction.api.v2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Snapshot défensif des permissions de rôles et de relations. */
public final class FactionAclView {

    private final Map<String, Set<String>> rolePermissions;
    private final Map<String, Set<String>> relationPermissions;

    public FactionAclView(Map<String, Set<String>> rolePermissions,
                          Map<String, Set<String>> relationPermissions) {
        this.rolePermissions = immutable(rolePermissions);
        this.relationPermissions = immutable(relationPermissions);
    }

    private static Map<String, Set<String>> immutable(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<String, Set<String>>();
        if (source != null) {
            for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
                if (entry.getKey() == null) continue;
                Set<String> values = entry.getValue() != null
                        ? entry.getValue() : Collections.<String>emptySet();
                copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<String>(values)));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Set<String>> getRolePermissions() { return rolePermissions; }
    public Map<String, Set<String>> getRelationPermissions() { return relationPermissions; }
}
