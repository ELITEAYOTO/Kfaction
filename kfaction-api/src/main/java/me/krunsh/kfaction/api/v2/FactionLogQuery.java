package me.krunsh.kfaction.api.v2;

import java.util.UUID;

/** Filtre borné pour l'historique faction. */
public final class FactionLogQuery {

    private final PageRequest page;
    private final String type;
    private final UUID actorId;

    public FactionLogQuery(PageRequest page, String type, UUID actorId) {
        this.page = page != null ? page : PageRequest.first();
        this.type = normalize(type);
        this.actorId = actorId;
    }

    public static FactionLogQuery recent() {
        return new FactionLogQuery(PageRequest.first(), null, null);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public PageRequest getPage() { return page; }
    public String getType() { return type; }
    public UUID getActorId() { return actorId; }
}
