package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot immutable d'une définition Global Zone V2.2.
 */
public final class ZoneView {

    private final String id;
    private final String displayName;
    private final String color;
    private final String mapSymbol;
    private final String title;
    private final String subtitle;
    private final String enterMessage;
    private final boolean pvpAllowed;
    private final String defaultPolicy;
    private final List<String> allowedActions;
    private final List<String> deniedActions;
    private final int chunkCount;
    private final boolean configured;

    public ZoneView(
            String id,
            String displayName,
            String color,
            String mapSymbol,
            String title,
            String subtitle,
            String enterMessage,
            boolean pvpAllowed,
            String defaultPolicy,
            List<String> allowedActions,
            List<String> deniedActions,
            int chunkCount,
            boolean configured
    ) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.mapSymbol = mapSymbol;
        this.title = title;
        this.subtitle = subtitle;
        this.enterMessage = enterMessage;
        this.pvpAllowed = pvpAllowed;
        this.defaultPolicy = defaultPolicy;
        this.allowedActions =
                immutableCopy(
                        allowedActions
                );
        this.deniedActions =
                immutableCopy(
                        deniedActions
                );
        this.chunkCount =
                Math.max(
                        0,
                        chunkCount
                );
        this.configured = configured;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getMapSymbol() {
        return mapSymbol;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getEnterMessage() {
        return enterMessage;
    }

    public boolean isPvpAllowed() {
        return pvpAllowed;
    }

    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public List<String> getDeniedActions() {
        return deniedActions;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public boolean isConfigured() {
        return configured;
    }

    private static List<String> immutableCopy(
            List<String> values
    ) {
        if (values == null
                || values.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                new ArrayList<String>(
                        values
                )
        );
    }
}
