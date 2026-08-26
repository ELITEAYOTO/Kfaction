package me.krunsh.kfaction.managers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.krunsh.kfaction.Kfaction;

/** Attache les nouvelles valeurs embarquées sans réécrire le YAML utilisateur. */
final class BundledYamlDefaults {

    private BundledYamlDefaults() {
    }

    static void apply(
            Kfaction plugin,
            FileConfiguration target,
            String resourceName
    ) {
        InputStream stream = plugin.getResource(resourceName);
        if (stream == null) {
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            target.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException exception) {
            plugin.getLogger().warning(
                    "Impossible de charger les valeurs par défaut de "
                            + resourceName + ": " + exception.getMessage()
            );
        }
    }
}
