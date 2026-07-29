package me.krunsh.kfaction.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;

/**
 * Gère le coffre virtuel partagé de faction
 * 
 * Anti-duplication:
 * - Un seul objet Inventory par faction (partagé entre les viewers)
 * - Sauvegarde à chaque fermeture d'inventaire
 * - Sérialisation Base64 pour le stockage JSON
 */
public class FactionChestManager {
    
    private final Kfaction plugin;
    
    // Cache des inventaires ouverts: factionId -> Inventory
    private final Map<String, Inventory> openChests = new HashMap<>();
    
    // Suivi des joueurs ayant un coffre ouvert: playerUUID -> factionId
    private final Map<UUID, String> playerChestMap = new HashMap<>();
    
    private boolean saveOnClose = true;
    
    public FactionChestManager(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        // Charger config du chest depuis levels.yml
        java.io.File file = new java.io.File(plugin.getDataFolder(), "levels.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            saveOnClose = config.getBoolean("chest.save-on-close", true);
        }
        plugin.getLogger().info("FactionChestManager initialisé");
    }
    
    /**
     * Ouvre le coffre de faction pour un joueur
     * @param player Le joueur
     * @param faction La faction
     * @return true si ouvert avec succès
     */
    public boolean openChest(org.bukkit.entity.Player player, Faction faction) {
        if (!faction.hasChest()) {
            player.sendMessage("§cVotre faction n'a pas encore débloqué le coffre! §7(Niveau 1 requis)");
            return false;
        }
        
        // Vérifier la permission faction
        if (!faction.hasPermission(player.getUniqueId(), 
                me.krunsh.kfaction.data.PermissionAction.CHEST)) {
            player.sendMessage("§cVous n'avez pas la permission d'accéder au coffre de faction.");
            return false;
        }
        
        int chestSize = faction.getChestSize();
        
        // Obtenir ou créer l'inventaire partagé
        Inventory chest = openChests.get(faction.getId());
        
        if (chest == null) {
            // Créer l'inventaire
            chest = Bukkit.createInventory(
                new FactionChestHolder(faction.getId()), 
                chestSize, 
                "§6Coffre Faction §8- §e" + faction.getName()
            );
            
            // Charger le contenu depuis la sauvegarde
            loadChestContents(faction, chest);
            
            openChests.put(faction.getId(), chest);
        } else if (chest.getSize() != chestSize) {
            // Redimensionnement nécessaire (ex: upgrade niv 1 -> niv 3)
            Inventory newChest = Bukkit.createInventory(
                new FactionChestHolder(faction.getId()),
                chestSize,
                "§6Coffre Faction §8- §e" + faction.getName()
            );
            
            // Copier l'ancien contenu
            for (int i = 0; i < Math.min(chest.getSize(), chestSize); i++) {
                ItemStack item = chest.getItem(i);
                if (item != null) {
                    newChest.setItem(i, item);
                }
            }
            
            // Fermer l'ancien pour tous les viewers
            for (org.bukkit.entity.HumanEntity viewer : new java.util.ArrayList<>(chest.getViewers())) {
                viewer.closeInventory();
            }
            
            chest = newChest;
            openChests.put(faction.getId(), chest);
        }
        
        // Enregistrer le joueur
        playerChestMap.put(player.getUniqueId(), faction.getId());
        
        // Ouvrir l'inventaire (anti-dupe: tous partagent le MÊME objet Inventory)
        player.openInventory(chest);
        
        plugin.debug("Coffre ouvert pour " + player.getName() + " (faction: " + faction.getName() + 
            ", taille: " + chestSize + ")");
        
        return true;
    }
    
    /**
     * Appelé quand un joueur ferme un inventaire
     * Sauvegarde le contenu du coffre
     */
    public void handleClose(org.bukkit.entity.Player player, Inventory inventory) {
        String factionId = playerChestMap.remove(player.getUniqueId());
        if (factionId == null) return;
        
        // Vérifier que c'est bien un coffre de faction
        if (!(inventory.getHolder() instanceof FactionChestHolder)) return;
        
        // Si plus personne ne regarde, sauvegarder et nettoyer le cache
        if (inventory.getViewers().size() <= 1) { // 1 car le joueur est encore dans la liste
            Faction faction = plugin.getFactionManager().getFaction(factionId);
            if (faction != null && saveOnClose) {
                saveChestContents(faction, inventory);
            }
            
            // Nettoyer le cache si plus de viewers
            if (inventory.getViewers().size() <= 1) {
                openChests.remove(factionId);
            }
        }
    }
    
    /**
     * Vérifie si un inventaire est un coffre de faction
     */
    public boolean isFactionChest(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof FactionChestHolder;
    }
    
    /**
     * Obtient l'ID de faction depuis un coffre
     */
    public String getFactionIdFromChest(Inventory inventory) {
        if (inventory.getHolder() instanceof FactionChestHolder) {
            return ((FactionChestHolder) inventory.getHolder()).getFactionId();
        }
        return null;
    }
    
    /**
     * Sauvegarde immédiate de tous les coffres ouverts
     * Appelé au shutdown du plugin
     */
    public void saveAll() {
        for (Map.Entry<String, Inventory> entry : openChests.entrySet()) {
            Faction faction = plugin.getFactionManager().getFaction(entry.getKey());
            if (faction != null) {
                saveChestContents(faction, entry.getValue());
            }
        }
        openChests.clear();
        playerChestMap.clear();
    }
    
    /**
     * Charge le contenu d'un coffre depuis le Base64 stocké
     */
    private void loadChestContents(Faction faction, Inventory chest) {
        String b64 = faction.getChestContentsB64();
        if (b64 == null || b64.isEmpty()) return;
        
        try {
            ItemStack[] items = itemStackArrayFromBase64(b64);
            if (items != null) {
                for (int i = 0; i < Math.min(items.length, chest.getSize()); i++) {
                    if (items[i] != null) {
                        chest.setItem(i, items[i]);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de chargement du coffre de " + faction.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Sauvegarde le contenu d'un coffre en Base64
     */
    private void saveChestContents(Faction faction, Inventory chest) {
        try {
            String b64 = itemStackArrayToBase64(chest.getContents());
            faction.setChestContentsB64(b64);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur de sauvegarde du coffre de " + faction.getName() + ": " + e.getMessage());
        }
    }
    
    // === Sérialisation Base64 ===
    
    /**
     * Sérialise un tableau d'ItemStack en Base64
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        
        dataOutput.writeInt(items.length);
        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }
        dataOutput.close();
        
        return Base64Coder.encodeLines(outputStream.toByteArray());
    }
    
    /**
     * Désérialise un Base64 en tableau d'ItemStack
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        
        int length = dataInput.readInt();
        ItemStack[] items = new ItemStack[length];
        
        for (int i = 0; i < length; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }
        
        dataInput.close();
        return items;
    }
    
    /**
     * InventoryHolder personnalisé pour identifier les coffres de faction
     * Aussi utilisé comme anti-dupe: marque l'inventaire comme appartenant à une faction
     */
    public static class FactionChestHolder implements InventoryHolder {
        
        private final String factionId;
        
        public FactionChestHolder(String factionId) {
            this.factionId = factionId;
        }
        
        public String getFactionId() {
            return factionId;
        }
        
        @Override
        public Inventory getInventory() {
            return null; // Pas utilisé
        }
    }
}
