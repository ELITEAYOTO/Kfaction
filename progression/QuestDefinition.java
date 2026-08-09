package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Définition immuable d'une quête obligatoire. */
public final class QuestDefinition {
    private final String id;
    private final String type;
    private final String category;
    private final QuestTarget target;
    private final long amount;
    private final String displayName;
    private final List<String> lore;
    private final String iconMaterial;
    private final int iconData;
    private final String iconCit;
    private final QuestConditions conditions;

    public QuestDefinition(String id, String type, String category,
            QuestTarget target, long amount, String displayName, List<String> lore,
            String iconMaterial, int iconData, String iconCit,
            QuestConditions conditions) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.target = target;
        this.amount = amount;
        this.displayName = displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<String>(lore));
        this.iconMaterial = iconMaterial;
        this.iconData = iconData;
        this.iconCit = iconCit;
        this.conditions = conditions;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public QuestTarget getTarget() { return target; }
    public long getAmount() { return amount; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public String getIconMaterial() { return iconMaterial; }
    public int getIconData() { return iconData; }
    public String getIconCit() { return iconCit; }
    public QuestConditions getConditions() { return conditions; }
}
