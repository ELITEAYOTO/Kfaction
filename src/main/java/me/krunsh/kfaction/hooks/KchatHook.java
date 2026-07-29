package me.krunsh.kfaction.hooks;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * Hook pour Kchat (système de chat)
 * Fournit les tags de faction et gère les nametags
 */
public class KchatHook {
    
    private final Kfaction plugin;
    
    // Références pour reflection vers Kchat
    private Plugin kchatPlugin;
    private Object nametagManager;
    private Method updateNametagMethod;
    private Method refreshAllNametagsMethod;
    private boolean initialized = false;
    
    public KchatHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le hook
     */
    public void initialize() {
        try {
            kchatPlugin = Bukkit.getPluginManager().getPlugin("Kchat");
            if (kchatPlugin == null || !kchatPlugin.isEnabled()) {
                plugin.getLogger().warning("Kchat non trouvé - hook désactivé");
                return;
            }
            
            // Obtenir le NametagManager via reflection
            Method getNametagManagerMethod = kchatPlugin.getClass().getMethod("getNametagManager");
            nametagManager = getNametagManagerMethod.invoke(kchatPlugin);
            
            if (nametagManager != null) {
                // Cache les méthodes pour performance
                updateNametagMethod = nametagManager.getClass().getMethod("updateNametag", Player.class);
                refreshAllNametagsMethod = nametagManager.getClass().getMethod("refreshAllNametags");
                
                initialized = true;
                plugin.getLogger().info("Kchat hook initialisé avec succès");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'initialisation du hook Kchat: " + e.getMessage());
            initialized = false;
        }
    }
    
    /**
     * Vérifie si le hook est initialisé
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Met à jour le nametag d'un joueur spécifique
     * @param player Le joueur dont le nametag doit être mis à jour
     */
    public void updatePlayerNametag(Player player) {
        if (!initialized || updateNametagMethod == null || player == null) return;
        
        try {
            updateNametagMethod.invoke(nametagManager, player);
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("[Debug] Erreur updatePlayerNametag: " + e.getMessage());
            }
        }
    }
    
    /**
     * Met à jour les nametags de tous les joueurs en ligne
     * Utile après un changement de relation entre factions
     */
    public void updateAllNametags() {
        if (!initialized || refreshAllNametagsMethod == null) return;
        
        try {
            refreshAllNametagsMethod.invoke(nametagManager);
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("[Debug] Erreur updateAllNametags: " + e.getMessage());
            }
        }
    }
    
    /**
     * Obtient le tag de faction d'un joueur
     * @param player Le joueur
     * @return Le tag ou chaîne vide
     */
    public String getFactionTag(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) return "";
        return faction.getTag();
    }
    
    /**
     * Obtient le nom de faction d'un joueur
     * @param player Le joueur
     * @return Le nom ou chaîne vide
     */
    public String getFactionName(Player player) {
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) return "";
        return faction.getName();
    }
    
    /**
     * Obtient le préfixe de rôle d'un joueur
     * @param player Le joueur
     * @return Le préfixe de rôle
     */
    public String getRolePrefix(Player player) {
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        if (fPlayer == null || fPlayer.getRole() == null) return "";
        return fPlayer.getRole().getPrefix();
    }
    
    /**
     * Obtient le tag coloré selon la relation avec un autre joueur
     * @param player Le joueur dont on veut le tag
     * @param viewer Le joueur qui regarde
     * @return Le tag avec la couleur de relation
     */
    public String getColoredTag(Player player, Player viewer) {
        Faction playerFaction = plugin.getFactionManager().getPlayerFaction(player);
        if (playerFaction == null) return "";
        
        Faction viewerFaction = plugin.getFactionManager().getPlayerFaction(viewer);
        if (viewerFaction == null) {
            return "&f" + playerFaction.getTag();
        }
        
        Relation relation = viewerFaction.getRelationTo(playerFaction);
        return relation.getColorPrefix() + playerFaction.getTag();
    }
    
    /**
     * Obtient le format de chat pour un joueur
     * @param player Le joueur
     * @param format Le format original
     * @return Le format modifié avec les infos de faction
     */
    public String formatChat(Player player, String format) {
        String factionTag = getFactionTag(player);
        String rolePfx = getRolePrefix(player);
        
        format = format.replace("{faction}", factionTag);
        format = format.replace("{faction_role}", rolePfx);
        
        return format;
    }
}
