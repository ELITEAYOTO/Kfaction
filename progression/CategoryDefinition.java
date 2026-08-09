package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Métadonnées GUI d'une catégorie non codée en dur. */
public final class CategoryDefinition {
    private final String id;
    private final String displayName;
    private final String iconMaterial;
    private final int iconData;
    private final String iconCit;
    private final List<String> lore;

    public CategoryDefinition(String id, String displayName, String iconMaterial,
            int iconData, String iconCit, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.iconData = iconData;
        this.iconCit = iconCit;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getIconMaterial() { return iconMaterial; }
    public int getIconData() { return iconData; }
    public String getIconCit() { return iconCit; }
    public List<String> getLore() { return lore; }
}
