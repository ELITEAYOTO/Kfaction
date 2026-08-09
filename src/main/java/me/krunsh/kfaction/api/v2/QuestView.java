package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot immutable d'une quête de progression.
 */
public final class QuestView {

    private final String id;
    private final String displayName;

    private final String iconMaterial;
    private final int iconData;

    private final List<String> lore;

    private final long progress;
    private final long required;
    private final long remaining;
    private final int percent;

    private final boolean completed;

    public QuestView(
            String id,
            String displayName,
            String iconMaterial,
            int iconData,
            List<String> lore,
            long progress,
            long required,
            long remaining,
            int percent,
            boolean completed
    ) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.iconData = iconData;

        this.lore =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                lore != null
                                        ? lore
                                        : Collections.<String>emptyList()
                        )
                );

        this.progress = Math.max(0L, progress);
        this.required = Math.max(0L, required);
        this.remaining = Math.max(0L, remaining);
        this.percent = Math.max(0, Math.min(100, percent));
        this.completed = completed;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconMaterial() {
        return iconMaterial;
    }

    public int getIconData() {
        return iconData;
    }

    public List<String> getLore() {
        return lore;
    }

    public long getProgress() {
        return progress;
    }

    public long getRequired() {
        return required;
    }

    public long getRemaining() {
        return remaining;
    }

    public int getPercent() {
        return percent;
    }

    public boolean isCompleted() {
        return completed;
    }
}
