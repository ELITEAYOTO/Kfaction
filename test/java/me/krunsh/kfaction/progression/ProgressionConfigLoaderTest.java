package me.krunsh.kfaction.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProgressionConfigLoaderTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void loadsDynamicTiersLevelsAndMinecraftDataValues() throws Exception {
        ProgressionConfigLoader.LoadResult result = load(validYaml());

        assertTrue(messages(result), result.isValid());
        ProgressionConfig config = result.getConfig();
        assertEquals(2, config.getTiers().size());
        assertEquals("small", config.findTier(5).getId());
        assertEquals("medium", config.findTier(6).getId());
        assertEquals(2, config.getMaxLevel());

        QuestDefinition quest = config.getLevel(1).getTier("small")
                .getQuests().get("mine-stone");
        assertEquals("MINE", quest.getType());
        assertEquals(6, quest.getTarget().getData());
        assertTrue(quest.getTarget().isDataSpecified());
        assertEquals(50000L, quest.getAmount());
        assertFalse(quest.getConditions().isCountPlayerPlacedBlocks());
    }

    @Test
    public void rejectsTierGapAndMissingCoverage() throws Exception {
        String yaml = validYaml()
                .replace("min-players: 6", "min-players: 7")
                .replace("max-players: 50", "max-players: 10");
        ProgressionConfigLoader.LoadResult result = load(yaml);

        assertFalse(result.isValid());
        assertContains(result, "trou entre les tranches");
        assertContains(result, "aucune tranche ne couvre");
    }

    @Test
    public void rejectsUnknownTypeMaterialOptionAndZeroAmount() throws Exception {
        String yaml = validYaml()
                .replace("type: MINE", "type: MINNE")
                .replace("target: STONE:6", "target: STONES:6")
                .replace("amount: 50000", "amount: 0")
                .replace("display: \"Pierre\"", "display: \"Pierre\"\n"
                        + "            typo-option: true");
        ProgressionConfigLoader.LoadResult result = load(yaml);

        assertFalse(result.isValid());
        assertContains(result, "type de quête inconnu");
        assertContains(result, "option inconnue");
        assertContains(result, "strictement positif");
    }

    @Test
    public void rejectsDuplicateQuestKeyBeforeSnakeYamlCanOverwriteIt() throws Exception {
        String yaml = validYaml().replace(
                "          mine-stone:\n"
                + "            type: MINE",
                "          mine-stone:\n"
                + "            type: MINE\n"
                + "            type: BREAK");
        ProgressionConfigLoader.LoadResult result = load(yaml);

        assertFalse(result.isValid());
        assertContains(result, "clé dupliquée");
    }

    @Test
    public void rejectsMissingTierDefinitionAtAnyLevel() throws Exception {
        String yaml = validYaml().replace(
                "      medium:\n"
                + "        quests:\n"
                + "          mine-stone:\n"
                + "            type: MINE\n"
                + "            category: mining\n"
                + "            target: STONE\n"
                + "            amount: 1000000\n"
                + "            display: \"Pierre II\"\n",
                "");
        ProgressionConfigLoader.LoadResult result = load(yaml);

        assertFalse(result.isValid());
        assertContains(result, "définition obligatoire manquante");
    }

    private ProgressionConfigLoader.LoadResult load(String yaml) throws Exception {
        File file = temporary.newFile("progression-" + System.nanoTime() + ".yml");
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        return new ProgressionConfigLoader(QuestTypeRegistry.builtIns(),
                ValidationEnvironment.PERMISSIVE, 50).load(file);
    }

    private void assertContains(ProgressionConfigLoader.LoadResult result,
            String expected) {
        String messages = messages(result);
        assertTrue("Missing <" + expected + "> in " + messages,
                messages.contains(expected));
    }

    private String messages(ProgressionConfigLoader.LoadResult result) {
        StringBuilder builder = new StringBuilder();
        for (ValidationIssue issue : result.getIssues()) {
            builder.append(issue.toString()).append('\n');
        }
        return builder.toString();
    }

    private String validYaml() {
        return "schema-version: 2\n"
                + "settings:\n"
                + "  enabled: true\n"
                + "  starting-level: 1\n"
                + "  broadcast-level-up: true\n"
                + "categories:\n"
                + "  mining:\n"
                + "    display: \"Minage\"\n"
                + "    icon:\n"
                + "      material: DIAMOND_PICKAXE\n"
                + "member-tiers:\n"
                + "  small:\n"
                + "    display: \"1-5\"\n"
                + "    min-players: 1\n"
                + "    max-players: 5\n"
                + "  medium:\n"
                + "    display: \"6-50\"\n"
                + "    min-players: 6\n"
                + "    max-players: 50\n"
                + "levels:\n"
                + "  1:\n"
                + "    display: \"Niveau 1\"\n"
                + "    rewards-on-enter: []\n"
                + "    tiers:\n"
                + "      small:\n"
                + "        quests:\n"
                + "          mine-stone:\n"
                + "            type: MINE\n"
                + "            category: mining\n"
                + "            target: STONE:6\n"
                + "            amount: 50000\n"
                + "            display: \"Pierre\"\n"
                + "      medium:\n"
                + "        quests:\n"
                + "          mine-stone:\n"
                + "            type: MINE\n"
                + "            category: mining\n"
                + "            target: STONE:6\n"
                + "            amount: 500000\n"
                + "            display: \"Pierre\"\n"
                + "  2:\n"
                + "    display: \"Niveau 2\"\n"
                + "    rewards-on-enter:\n"
                + "      - id: warp-1\n"
                + "        type: warps_increase\n"
                + "        value: 1\n"
                + "        description: \"+1 warp\"\n"
                + "    tiers:\n"
                + "      small:\n"
                + "        quests:\n"
                + "          mine-stone:\n"
                + "            type: MINE\n"
                + "            category: mining\n"
                + "            target: STONE\n"
                + "            amount: 100000\n"
                + "            display: \"Pierre II\"\n"
                + "      medium:\n"
                + "        quests:\n"
                + "          mine-stone:\n"
                + "            type: MINE\n"
                + "            category: mining\n"
                + "            target: STONE\n"
                + "            amount: 1000000\n"
                + "            display: \"Pierre II\"\n";
    }
}
