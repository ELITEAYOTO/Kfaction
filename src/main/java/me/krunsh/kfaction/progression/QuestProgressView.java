package me.krunsh.kfaction.progression;

/** Vue calculée d'une quête; aucun booléen completed n'est persisté. */
public final class QuestProgressView {
    private final QuestDefinition definition;
    private final long progress;

    public QuestProgressView(QuestDefinition definition, long progress) {
        this.definition = definition;
        this.progress = Math.max(0L, progress);
    }

    public QuestDefinition getDefinition() { return definition; }
    public long getProgress() { return progress; }
    public long getRequired() { return definition.getAmount(); }
    public boolean isCompleted() { return progress >= definition.getAmount(); }
    public long getRemaining() {
        return isCompleted() ? 0L : definition.getAmount() - progress;
    }
    public int getPercent() {
        if (definition.getAmount() <= 0L) return 100;
        return (int) Math.min(100L,
                (progress >= Long.MAX_VALUE / 100L
                        ? 100L : progress * 100L / definition.getAmount()));
    }
}
