package me.krunsh.kfaction.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Gère le coffre virtuel partagé de faction.
 */
public class FactionChestManager {

    private final Kfaction plugin;

    private final Map<String, Inventory> openChests = new HashMap<>();
    private final Map<UUID, String> playerChestMap = new HashMap<>();

    private boolean saveOnClose = true;

    public FactionChestManager(Kfaction plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        saveOnClose =
                plugin.getConfigManager()
                        .getBoolean(
                                "faction-chest.save-on-close",
                                true
                        );

        me.krunsh.kfaction.utils.KfactionLogger.debug(
                plugin,
                "FactionChestManager: save-on-close="
                        + saveOnClose
        );
    }

    public boolean openChest(
            org.bukkit.entity.Player player,
            Faction faction
    ) {
        if (!faction.hasChest()) {
            player.sendMessage(
                    "§cVotre faction n'a pas encore débloqué le coffre de faction."
            );
            return false;
        }

        if (!faction.hasPermission(
                player.getUniqueId(),
                me.krunsh.kfaction.data.PermissionAction.CHEST
        )) {
            player.sendMessage(
                    "§cVous n'avez pas la permission d'accéder au coffre de faction."
            );
            return false;
        }

        int chestSize = faction.getChestSize();
        Inventory chest = openChests.get(faction.getId());

        if (chest == null) {
            chest = Bukkit.createInventory(
                    new FactionChestHolder(faction.getId()),
                    chestSize,
                    "§6Coffre Faction §8- §e" + faction.getName()
            );

            loadChestContents(faction, chest);
            openChests.put(faction.getId(), chest);
        } else if (chest.getSize() != chestSize) {
            Inventory newChest = Bukkit.createInventory(
                    new FactionChestHolder(faction.getId()),
                    chestSize,
                    "§6Coffre Faction §8- §e" + faction.getName()
            );

            for (int i = 0;
                    i < Math.min(chest.getSize(), chestSize);
                    i++) {
                ItemStack item = chest.getItem(i);
                if (item != null) {
                    newChest.setItem(i, item);
                }
            }

            for (HumanEntity viewer
                    : new ArrayList<>(chest.getViewers())) {
                viewer.closeInventory();
            }

            chest = newChest;
            openChests.put(faction.getId(), chest);
        }

        playerChestMap.put(
                player.getUniqueId(),
                faction.getId()
        );

        player.openInventory(chest);

        plugin.debug(
                "Coffre ouvert pour " + player.getName()
                        + " (faction: " + faction.getName()
                        + ", taille: " + chestSize + ")"
        );

        return true;
    }

    public void handleClose(
            org.bukkit.entity.Player player,
            Inventory inventory
    ) {
        String factionId =
                playerChestMap.remove(player.getUniqueId());

        if (factionId == null) {
            return;
        }

        if (!(inventory.getHolder()
                instanceof FactionChestHolder)) {
            return;
        }

        if (inventory.getViewers().size() <= 1) {
            Faction faction =
                    plugin.getFactionManager()
                            .getFaction(factionId);

            if (faction != null && saveOnClose) {
                saveChestContents(faction, inventory);
            }

            openChests.remove(factionId);
        }
    }

    /**
     * Ferme puis jette le coffre d'une faction en cours de dissolution.
     *
     * Les maps sont nettoyées AVANT closeInventory() afin que les
     * InventoryCloseEvent déclenchés ne puissent pas resauvegarder un coffre
     * appartenant à une faction déjà condamnée.
     */
    public void closeAndDiscardFactionChest(String factionId) {
        if (factionId == null) {
            return;
        }

        Inventory chest = openChests.remove(factionId);

        Iterator<Map.Entry<UUID, String>> iterator =
                playerChestMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            if (factionId.equals(entry.getValue())) {
                iterator.remove();
            }
        }

        if (chest == null) {
            return;
        }

        for (HumanEntity viewer
                : new ArrayList<>(chest.getViewers())) {
            viewer.closeInventory();
        }
    }

    public boolean isFactionChest(Inventory inventory) {
        return inventory != null
                && inventory.getHolder()
                instanceof FactionChestHolder;
    }

    public String getFactionIdFromChest(Inventory inventory) {
        if (inventory != null
                && inventory.getHolder()
                instanceof FactionChestHolder) {
            return ((FactionChestHolder)
                    inventory.getHolder()).getFactionId();
        }

        return null;
    }

    public void saveAll() {
        for (Map.Entry<String, Inventory> entry
                : openChests.entrySet()) {
            Faction faction =
                    plugin.getFactionManager()
                            .getFaction(entry.getKey());

            if (faction != null) {
                saveChestContents(
                        faction,
                        entry.getValue()
                );
            }
        }

        openChests.clear();
        playerChestMap.clear();
    }

    private void loadChestContents(
            Faction faction,
            Inventory chest
    ) {
        String b64 = faction.getChestContentsB64();

        if (b64 == null || b64.isEmpty()) {
            return;
        }

        try {
            ItemStack[] items =
                    itemStackArrayFromBase64(b64);

            if (items != null) {
                for (int i = 0;
                        i < Math.min(items.length, chest.getSize());
                        i++) {
                    if (items[i] != null) {
                        chest.setItem(i, items[i]);
                    }
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de chargement du coffre de "
                            + faction.getName()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private void saveChestContents(
            Faction faction,
            Inventory chest
    ) {
        try {
            String b64 =
                    itemStackArrayToBase64(
                            chest.getContents()
                    );

            faction.setChestContentsB64(b64);
            plugin.getStorageManager().markDirty(faction);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Erreur de sauvegarde du coffre de "
                            + faction.getName()
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    public static String itemStackArrayToBase64(
            ItemStack[] items
    ) throws Exception {
        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        BukkitObjectOutputStream dataOutput =
                new BukkitObjectOutputStream(outputStream);

        dataOutput.writeInt(items.length);

        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        dataOutput.close();

        return Base64Coder.encodeLines(
                outputStream.toByteArray()
        );
    }

    public static ItemStack[] itemStackArrayFromBase64(
            String data
    ) throws Exception {
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(
                        Base64Coder.decodeLines(data)
                );

        BukkitObjectInputStream dataInput =
                new BukkitObjectInputStream(inputStream);

        int length = dataInput.readInt();
        ItemStack[] items = new ItemStack[length];

        for (int i = 0; i < length; i++) {
            items[i] =
                    (ItemStack) dataInput.readObject();
        }

        dataInput.close();
        return items;
    }

    public static class FactionChestHolder
            implements InventoryHolder {

        private final String factionId;

        public FactionChestHolder(String factionId) {
            this.factionId = factionId;
        }

        public String getFactionId() {
            return factionId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
