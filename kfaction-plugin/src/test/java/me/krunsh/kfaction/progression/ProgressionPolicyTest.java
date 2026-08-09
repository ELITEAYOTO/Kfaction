package me.krunsh.kfaction.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.Test;

public class ProgressionPolicyTest {
    @Test
    public void tierIncreaseKeepsRawProgressAndCanUncompleteQuest() {
        ProgressionConfig config = config();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("small"));
        state.addProgress("stone", 50L);

        assertTrue(ProgressionPolicy.isComplete(config, state, 1, 5));
        assertFalse(ProgressionPolicy.isComplete(config, state, 1, 6));
        assertEquals(50L, state.getProgress("stone"));
        assertEquals("medium", state.getLockedTierId());
    }

    @Test
    public void readOnlyViewsDoNotMutateLockedTier() {
        ProgressionConfig config =
                config();

        FactionProgressState state =
                new FactionProgressState();

        state.beginLevel(
                1,
                config.getTier("small")
        );

        assertEquals(
                "small",
                state.getLockedTierId()
        );

        java.util.List<QuestProgressView> views =
                ProgressionPolicy.viewsReadOnly(
                        config,
                        state,
                        1,
                        6
                );

        /*
         * Le snapshot reflète la difficulté medium actuelle...
         */
        assertEquals(
                500L,
                views.get(0)
                        .getRequired()
        );

        /*
         * ...sans écrire le lock medium dans l'état.
         */
        assertEquals(
                "small",
                state.getLockedTierId()
        );
    }

    @Test
    public void tierNeverDropsBeforeNextLevel() {
        ProgressionConfig config = config();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("small"));

        ProgressionPolicy.refreshLockedTier(config, state, 6);
        assertEquals("medium", state.getLockedTierId());
        ProgressionPolicy.refreshLockedTier(config, state, 2);
        assertEquals("medium", state.getLockedTierId());
    }

    @Test
    public void nextLevelRecalculatesTierAndArchivesPreviousRawProgress() {
        ProgressionConfig config = config();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("medium"));
        state.addProgress("stone", 700L);

        ProgressionPolicy.beginNextLevel(config, state, 2, 3);

        assertEquals("small", state.getLockedTierId());
        assertEquals(0L, state.getProgress("stone"));
        assertEquals(Long.valueOf(700L),
                state.snapshotArchivedProgress().get("level.1.stone"));
    }

    @Test
    public void allQuestsAreRequiredSimultaneously() {
        ProgressionConfig config = configWithTwoQuests();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("small"));
        state.addProgress("stone", 50L);

        assertFalse(ProgressionPolicy.isComplete(config, state, 1, 1));
        state.addProgress("gold", 5L);
        assertTrue(ProgressionPolicy.isComplete(config, state, 1, 1));
    }

    @Test
    public void rawProgressAdditionSaturatesInsteadOfOverflowing() {
        FactionProgressState state = new FactionProgressState();
        state.setProgress("stone", Long.MAX_VALUE - 2L);
        assertEquals(Long.MAX_VALUE, state.addProgress("stone", 10L));
    }

    @Test
    public void preparedTransitionCanBeRolledBackWithoutLosingRawState() {
        ProgressionConfig config = config();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("small"));
        state.addProgress("stone", 42L);
        FactionProgressState.Snapshot before = state.snapshot();

        state.setPendingTransition("1->2:PREPARED");
        state.addPendingReward("level_2_reward_chest");
        ProgressionPolicy.beginNextLevel(config, state, 2, 3);
        state.restore(before);

        assertEquals(1, state.getLevelStarted());
        assertEquals(42L, state.getProgress("stone"));
        assertEquals("small", state.getLockedTierId());
        assertTrue(state.getPendingRewards().isEmpty());
        assertEquals(null, state.getPendingTransition());
    }

    @Test
    public void pendingRewardsSurviveBeginningTheNextLevel() {
        ProgressionConfig config = config();
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("small"));
        state.addPendingReward("level_2_reward_external");

        ProgressionPolicy.beginNextLevel(config, state, 2, 3);

        assertTrue(state.getPendingRewards()
                .contains("level_2_reward_external"));
    }

    private ProgressionConfig config() {
        return config(false);
    }

    private ProgressionConfig configWithTwoQuests() {
        return config(true);
    }

    private ProgressionConfig config(boolean includeGold) {
        Map<String, MemberTierDefinition> tiers =
                new LinkedHashMap<String, MemberTierDefinition>();
        tiers.put("small", new MemberTierDefinition("small", "1-5", 1, 5, 0));
        tiers.put("medium", new MemberTierDefinition("medium", "6-50", 6, 50, 1));

        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, level(1, includeGold));
        levels.put(2, level(2, includeGold));
        return new ProgressionConfig(2, true, 1, true, tiers,
                Collections.<String, CategoryDefinition>emptyMap(), levels);
    }

    private LevelDefinition level(int number, boolean includeGold) {
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("small", tier("small", 50L, includeGold));
        tierLevels.put("medium", tier("medium", 500L, includeGold));
        return new LevelDefinition(number, "Level " + number, tierLevels,
                Collections.<RewardDefinition>emptyList());
    }

    private TierLevelDefinition tier(String id, long amount, boolean includeGold) {
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("stone", quest("stone", Material.STONE, amount));
        if (includeGold) quests.put("gold", quest("gold", Material.GOLD_ORE, 5L));
        return new TierLevelDefinition(id, quests);
    }

    private QuestDefinition quest(String id, Material material, long amount) {
        return new QuestDefinition(id, "MINE", "mining",
                QuestTarget.material(material.name(), material, 0, false, null),
                amount, id, Collections.<String>emptyList(), "PAPER", 0, null,
                new QuestConditions(false, false, false,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        0, 0, true, true, true, false));
    }
}
