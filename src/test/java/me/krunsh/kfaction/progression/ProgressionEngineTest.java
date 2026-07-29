package me.krunsh.kfaction.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.Test;

public class ProgressionEngineTest {
    @Test
    public void oneActionProgressesEveryMatchingRequiredQuest() {
        ProgressionConfig config = config(2L, 3L, null);
        FactionProgressState state = started(config);

        ProgressionUpdate update = ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, null, 1));

        assertEquals(Arrays.asList("stone_a", "stone_b"),
                update.getProgressedQuestIds());
        assertEquals(1L, state.getProgress("stone_a"));
        assertEquals(1L, state.getProgress("stone_b"));
        assertFalse(update.isLevelComplete());
    }

    @Test
    public void exactCitIsRequiredButLevelNbtIsNotPartOfIdentity() {
        ProgressionConfig config = config(1L, null, "azurite");
        FactionProgressState state = started(config);

        assertFalse(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, "jaspe", 1))
                .hasProgress());
        assertTrue(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, "azurite", 1))
                .isLevelComplete());
    }

    @Test
    public void completedQuestDoesNotConsumeMoreLogicalProgress() {
        ProgressionConfig config = config(1L, null, null);
        FactionProgressState state = started(config);
        ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, null, 50));
        ProgressionUpdate second = ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, null, 50));

        assertEquals(50L, state.getProgress("stone_a"));
        assertFalse(second.hasProgress());
    }

    @Test
    public void runtimeConditionsAreFailClosedForWorldAndRegions() {
        ProgressionConfig config = config(1L, null, null);
        FactionProgressState state = started(config);
        QuestDefinition original = config.getLevel(1).getTier("solo")
                .getQuests().get("stone_a");
        QuestConditions restricted = new QuestConditions(false, true, false,
                Collections.singleton("world"), Collections.singleton("mine"),
                Collections.singleton("spawn"), 0, 0, true, true, true, false);
        ProgressionConfig restrictedConfig = replaceQuest(config,
                new QuestDefinition(original.getId(), original.getType(),
                        original.getCategory(), original.getTarget(), original.getAmount(),
                        original.getDisplayName(), original.getLore(),
                        original.getIconMaterial(), original.getIconData(),
                        original.getIconCit(), restricted));

        assertFalse(ProgressionEngine.apply(restrictedConfig, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, null, 1,
                        "world", Collections.singleton("spawn"), false, true, false))
                .hasProgress());
        assertTrue(ProgressionEngine.apply(restrictedConfig, state, 1, 1,
                QuestAction.material("MINE", Material.STONE, 0, null, 1,
                        "world", Collections.singleton("mine"), false, true, false))
                .hasProgress());
    }

    @Test
    public void playerKillCooldownAndDailyLimitAreFactionWideAndPersistentState() {
        ProgressionConfig config = playerKillConfig();
        FactionProgressState state = started(config);
        UUID victim = UUID.randomUUID();
        long start = 10L * 86_400_000L + 1_000L;

        assertTrue(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.playerKill(1L, "world",
                        Collections.<String>emptySet(), victim,
                        false, false, false, false, start)).hasProgress());
        assertFalse(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.playerKill(1L, "world",
                        Collections.<String>emptySet(), victim,
                        false, false, false, false, start + 30_000L)).hasProgress());
        assertTrue(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.playerKill(1L, "world",
                        Collections.<String>emptySet(), victim,
                        false, false, false, false, start + 61_000L)).hasProgress());
        assertFalse(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.playerKill(1L, "world",
                        Collections.<String>emptySet(), victim,
                        false, false, false, false, start + 122_000L)).hasProgress());
        assertEquals(1, state.snapshotPvpKillLedger().size());
    }

    @Test
    public void playerKillExclusionsAreAppliedBeforeWritingTheLedger() {
        ProgressionConfig config = playerKillConfig();
        FactionProgressState state = started(config);
        ProgressionUpdate update = ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.playerKill(1L, "world",
                        Collections.<String>emptySet(), UUID.randomUUID(),
                        true, false, false, false, 1_000L));

        assertFalse(update.hasProgress());
        assertTrue(state.snapshotPvpKillLedger().isEmpty());
    }

    @Test
    public void customCraftRequiresTheExactRecipeIdIgnoringOnlyCase() {
        ProgressionConfig config = stringConfig();
        FactionProgressState state = started(config);

        assertFalse(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.string("CUSTOM_CRAFT", "other_recipe", 1L,
                        "world", Collections.<String>emptySet())).hasProgress());
        assertTrue(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.string("CUSTOM_CRAFT", "AZURITE_CHESTPLATE", 1L,
                        "world", Collections.<String>emptySet())).isLevelComplete());
    }

    @Test
    public void vanillaCraftCountsProducedItemsAndRequiresExactCit() {
        ProgressionConfig config = materialTypeConfig("CRAFT", Material.BREAD,
                0, "volkarite_bread", 4L);
        FactionProgressState state = started(config);

        assertFalse(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("CRAFT", Material.BREAD, 0,
                        null, 4L)).hasProgress());
        assertTrue(ProgressionEngine.apply(config, state, 1, 1,
                QuestAction.material("CRAFT", Material.BREAD, 0,
                        "volkarite_bread", 4L)).isLevelComplete());
        assertEquals(4L, state.getProgress("target"));
    }

    private static FactionProgressState started(ProgressionConfig config) {
        FactionProgressState state = new FactionProgressState();
        state.beginLevel(1, config.getTier("solo"));
        return state;
    }

    private static ProgressionConfig config(Long first, Long second, String cit) {
        Map<String, MemberTierDefinition> tiers =
                new LinkedHashMap<String, MemberTierDefinition>();
        tiers.put("solo", new MemberTierDefinition("solo", "Solo", 1, 50, 0));
        Map<String, CategoryDefinition> categories =
                new LinkedHashMap<String, CategoryDefinition>();
        categories.put("mine", new CategoryDefinition("mine", "Mine",
                "STONE", 0, null, Collections.<String>emptyList()));
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("stone_a", quest("stone_a", first.longValue(), cit));
        if (second != null) quests.put("stone_b", quest("stone_b", second, cit));
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("solo", new TierLevelDefinition("solo", quests));
        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, new LevelDefinition(1, "Niveau 1", tierLevels,
                Collections.<RewardDefinition>emptyList()));
        return new ProgressionConfig(2, true, 1, true, tiers, categories, levels);
    }

    private static QuestDefinition quest(String id, long amount, String cit) {
        return new QuestDefinition(id, "MINE", "mine",
                QuestTarget.material("STONE", Material.STONE, 0, false, cit),
                amount, id, Collections.<String>emptyList(), "STONE", 0, null,
                new QuestConditions(false, false, false,
                        Collections.<String>emptySet(), Collections.<String>emptySet(),
                        Collections.<String>emptySet(), 0, 0,
                        true, true, true, false));
    }

    private static ProgressionConfig replaceQuest(ProgressionConfig config,
            QuestDefinition replacement) {
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put(replacement.getId(), replacement);
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("solo", new TierLevelDefinition("solo", quests));
        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, new LevelDefinition(1, "Niveau 1", tierLevels,
                Collections.<RewardDefinition>emptyList()));
        return new ProgressionConfig(2, true, 1, true, config.getTiers(),
                config.getCategories(), levels);
    }

    private static ProgressionConfig playerKillConfig() {
        Map<String, MemberTierDefinition> tiers =
                new LinkedHashMap<String, MemberTierDefinition>();
        tiers.put("solo", new MemberTierDefinition("solo", "Solo", 1, 50, 0));
        Map<String, CategoryDefinition> categories =
                new LinkedHashMap<String, CategoryDefinition>();
        categories.put("pvp", new CategoryDefinition("pvp", "PvP",
                "IRON_SWORD", 0, null, Collections.<String>emptyList()));
        QuestDefinition quest = new QuestDefinition("players", "PLAYER_KILL",
                "pvp", QuestTarget.none(), 10L, "Players",
                Collections.<String>emptyList(), "IRON_SWORD", 0, null,
                new QuestConditions(true, false, true,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        60, 2, true, true, true, false));
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("players", quest);
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("solo", new TierLevelDefinition("solo", quests));
        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, new LevelDefinition(1, "Niveau 1", tierLevels,
                Collections.<RewardDefinition>emptyList()));
        return new ProgressionConfig(2, true, 1, true, tiers, categories, levels);
    }

    private static ProgressionConfig stringConfig() {
        Map<String, MemberTierDefinition> tiers =
                new LinkedHashMap<String, MemberTierDefinition>();
        tiers.put("solo", new MemberTierDefinition("solo", "Solo", 1, 50, 0));
        QuestDefinition quest = new QuestDefinition("custom", "CUSTOM_CRAFT",
                "craft", QuestTarget.string("azurite_chestplate"), 1L,
                "Custom", Collections.<String>emptyList(), "WORKBENCH", 0, null,
                new QuestConditions(true, false, true,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        0, 0, true, true, true, false));
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("custom", quest);
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("solo", new TierLevelDefinition("solo", quests));
        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, new LevelDefinition(1, "Niveau 1", tierLevels,
                Collections.<RewardDefinition>emptyList()));
        return new ProgressionConfig(2, true, 1, true, tiers,
                Collections.<String, CategoryDefinition>emptyMap(), levels);
    }

    private static ProgressionConfig materialTypeConfig(String type,
            Material material, int data, String cit, long amount) {
        Map<String, MemberTierDefinition> tiers =
                new LinkedHashMap<String, MemberTierDefinition>();
        tiers.put("solo", new MemberTierDefinition("solo", "Solo", 1, 50, 0));
        QuestDefinition quest = new QuestDefinition("target", type,
                "craft", QuestTarget.material(material.name(), material, data,
                        true, cit), amount, "Target",
                Collections.<String>emptyList(), "WORKBENCH", 0, null,
                new QuestConditions(true, false, true,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        0, 0, true, true, true, false));
        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("target", quest);
        Map<String, TierLevelDefinition> tierLevels =
                new LinkedHashMap<String, TierLevelDefinition>();
        tierLevels.put("solo", new TierLevelDefinition("solo", quests));
        Map<Integer, LevelDefinition> levels =
                new LinkedHashMap<Integer, LevelDefinition>();
        levels.put(1, new LevelDefinition(1, "Niveau 1", tierLevels,
                Collections.<RewardDefinition>emptyList()));
        return new ProgressionConfig(2, true, 1, true, tiers,
                Collections.<String, CategoryDefinition>emptyMap(), levels);
    }
}
