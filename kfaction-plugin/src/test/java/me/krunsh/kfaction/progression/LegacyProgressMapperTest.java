package me.krunsh.kfaction.progression;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.Test;

import me.krunsh.kfaction.data.FactionQuest;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.data.QuestType;

public class LegacyProgressMapperTest {
    @Test
    public void matchingCounterIsCopiedAndOrphansRemainArchived() {
        FactionQuest matching = new FactionQuest("stone", QuestType.BLOCK_BREAK,
                QuestCategory.MINEUR, "STONE", "Stone", 100, 25);
        matching.setProgress(37);
        FactionQuest orphan = new FactionQuest("old_gold",
                QuestType.BLOCK_BREAK, QuestCategory.MINEUR,
                "GOLD_ORE", "Gold", 20, 10);
        orphan.setProgress(9);

        Map<String, QuestDefinition> quests =
                new LinkedHashMap<String, QuestDefinition>();
        quests.put("stone", new QuestDefinition("stone", "MINE", "mining",
                QuestTarget.material("STONE", Material.STONE, 0, false, null),
                500L, "Stone", Collections.<String>emptyList(), "STONE", 0,
                null, new QuestConditions(false, false, false,
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        Collections.<String>emptySet(),
                        0, 0, true, true, true, false)));
        FactionProgressState state = new FactionProgressState();

        LegacyProgressMapper.apply(Arrays.asList(matching, orphan), 123,
                new TierLevelDefinition("small", quests), state);

        assertEquals(37L, state.getProgress("stone"));
        assertEquals(Long.valueOf(9L),
                state.snapshotArchivedProgress().get("legacy.old_gold"));
        assertEquals(Long.valueOf(123L),
                state.snapshotArchivedProgress().get("legacy.current-xp"));
    }
}
