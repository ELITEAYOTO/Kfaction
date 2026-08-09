package me.krunsh.kfaction.api.v2;

/** Snapshot d'un claim et de son éventuel groupe. */
public final class ClaimView {

    private final ChunkView chunk;
    private final String groupId;

    public ClaimView(ChunkView chunk, String groupId) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk cannot be null");
        }
        this.chunk = chunk;
        this.groupId = normalize(groupId);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public ChunkView getChunk() { return chunk; }
    public String getGroupId() { return groupId; }
}
