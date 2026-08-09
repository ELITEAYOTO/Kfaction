package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Page immutable dont la taille est imposée par {@link PageRequest}. */
public final class PageView<T> {

    private final List<T> items;
    private final int offset;
    private final int limit;
    private final int total;

    public PageView(List<T> items, int offset, int limit, int total) {
        if (offset < 0 || limit < 1 || limit > PageRequest.MAX_LIMIT || total < 0) {
            throw new IllegalArgumentException("invalid page metadata");
        }
        List<T> safe = items != null ? items : Collections.<T>emptyList();
        if (safe.size() > limit) {
            throw new IllegalArgumentException("items exceed page limit");
        }
        this.items = Collections.unmodifiableList(new ArrayList<T>(safe));
        this.offset = offset;
        this.limit = limit;
        this.total = total;
    }

    public static <T> PageView<T> empty(PageRequest request) {
        PageRequest safe = request != null ? request : PageRequest.first();
        return new PageView<T>(Collections.<T>emptyList(), safe.getOffset(), safe.getLimit(), 0);
    }

    public List<T> getItems() {
        return items;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public int getTotal() {
        return total;
    }

    public boolean hasPrevious() {
        return offset > 0;
    }

    public boolean hasNext() {
        return offset + items.size() < total;
    }

    public int getNextOffset() {
        return hasNext() ? offset + items.size() : offset;
    }
}
