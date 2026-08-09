package me.krunsh.kfaction.api.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshot immutable d'un niveau de récompenses.
 */
public final class RewardLevelView {

    public enum State {
        UNLOCKED,
        CURRENT,
        LOCKED
    }

    private final int level;
    private final State state;
    private final List<String> rewards;

    public RewardLevelView(
            int level,
            State state,
            List<String> rewards
    ) {
        this.level = Math.max(0, level);
        this.state = state != null
                ? state
                : State.LOCKED;

        this.rewards =
                Collections.unmodifiableList(
                        new ArrayList<String>(
                                rewards != null
                                        ? rewards
                                        : Collections.<String>emptyList()
                        )
                );
    }

    public int getLevel() {
        return level;
    }

    public State getState() {
        return state;
    }

    public List<String> getRewards() {
        return rewards;
    }

    public boolean isUnlocked() {
        return state == State.UNLOCKED;
    }

    public boolean isCurrent() {
        return state == State.CURRENT;
    }

    public boolean isLocked() {
        return state == State.LOCKED;
    }
}
