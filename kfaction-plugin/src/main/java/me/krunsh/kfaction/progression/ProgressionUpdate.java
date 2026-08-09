package me.krunsh.kfaction.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Résultat immuable d'une action appliquée au niveau courant. */
public final class ProgressionUpdate {
    public static final ProgressionUpdate NONE =
            new ProgressionUpdate(Collections.<String>emptyList(),
                    Collections.<String>emptyList(), false);

    private final List<String> progressedQuestIds;
    private final List<String> newlyCompletedQuestIds;
    private final boolean levelComplete;

    public ProgressionUpdate(List<String> progressedQuestIds,
            List<String> newlyCompletedQuestIds, boolean levelComplete) {
        this.progressedQuestIds = Collections.unmodifiableList(
                new ArrayList<String>(progressedQuestIds));
        this.newlyCompletedQuestIds = Collections.unmodifiableList(
                new ArrayList<String>(newlyCompletedQuestIds));
        this.levelComplete = levelComplete;
    }

    public List<String> getProgressedQuestIds() { return progressedQuestIds; }
    public List<String> getNewlyCompletedQuestIds() { return newlyCompletedQuestIds; }
    public boolean isLevelComplete() { return levelComplete; }
    public boolean hasProgress() { return !progressedQuestIds.isEmpty(); }
}
