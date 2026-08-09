package me.krunsh.kfaction.api.v2;

/** Requête bornée commune aux lectures paginées Kfaction. */
public final class PageRequest {

    public static final int DEFAULT_LIMIT = 45;
    public static final int MAX_LIMIT = 100;

    private final int offset;
    private final int limit;

    public PageRequest(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.offset = offset;
        this.limit = limit;
    }

    public static PageRequest first() {
        return new PageRequest(0, DEFAULT_LIMIT);
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }
}
