package me.krunsh.kfaction.placeholders;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.progression.MemberTierDefinition;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.utils.PowerDisplay;

/**
 * Expansion PlaceholderAPI pour Kfaction
 * Fournit tous les placeholders %kfaction_xxx%
 */
public class KfactionExpansion extends PlaceholderExpansion {
    
    private final Kfaction plugin;
    
    public KfactionExpansion(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getIdentifier() {
        return "kfaction";
    }
    
    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        Faction faction = fPlayer.hasFaction() ? 
            plugin.getFactionManager().getFaction(fPlayer.getFactionId()) : null;
        
        // === Placeholders joueur ===
        switch (identifier.toLowerCase()) {
            // Faction basic
            case "has_faction":
                return fPlayer.hasFaction() ? "true" : "false";
            
            case "faction_name":
                return faction != null ? faction.getName() : "";
            
            case "faction_tag":
                return faction != null ? faction.getTag() : "";
            
            case "faction_description":
                return faction != null ? faction.getDescription() : "";
            
            // Leader
            case "faction_leader":
                if (faction == null || faction.getLeader() == null) return "";
                FPlayer leader = plugin.getFPlayerManager().getFPlayer(faction.getLeader());
                return leader != null ? leader.getLastKnownName() : "";
            
            // Membres
            case "faction_online":
                return faction != null ? String.valueOf(faction.getOnlinePlayers().size()) : "0";
            
            case "faction_members":
                return faction != null ? String.valueOf(faction.getMemberCount()) : "0";
            
            case "faction_maxmembers":
                return String.valueOf(plugin.getConfigManager().getInt("factions.members.max-per-faction", 50));
            
            // Power
            case "faction_power":
                if (faction == null) return "0";
                return PowerDisplay.format(plugin.getPowerManager().getFactionPower(faction));
            
            case "faction_maxpower":
                if (faction == null) return "0";
                return PowerDisplay.format(plugin.getPowerManager().getFactionMaxPower(faction));
            
            case "player_power":
                return PowerDisplay.format(fPlayer.getPower());
            
            case "player_maxpower":
                return PowerDisplay.format(plugin.getPowerManager().getPlayerMaxPower(player.getUniqueId()));
            
            // Claims
            case "faction_claims":
                return faction != null ? String.valueOf(faction.getClaimCount()) : "0";
            
            case "faction_maxclaims":
                if (faction == null) return "0";
                return String.valueOf(plugin.getClaimManager().getMaxClaims(faction));
            
            // Économie
            case "faction_bank":
                return faction != null ? String.format("%.2f", faction.getBank()) : "0";
            
            // Relations
            case "faction_allies":
                return faction != null ? String.valueOf(faction.getAllies().size()) : "0";
            
            case "faction_enemies":
                return faction != null ? String.valueOf(faction.getEnemies().size()) : "0";
            
            case "faction_truces":
                if (faction == null) return "0";
                int truceCount = 0;
                for (Relation r : faction.getAllRelations().values()) {
                    if (r == Relation.TRUCE) truceCount++;
                }
                return String.valueOf(truceCount);
            
            // Rôle
            case "player_role":
                return fPlayer.getRole() != null ? fPlayer.getRole().getDisplayName() : "";
            
            case "player_role_prefix":
                return fPlayer.getRole() != null ? fPlayer.getRole().getPrefix() : "";
            
            // Location
            case "location_faction":
                FLocation loc = new FLocation(player.getLocation());
                Faction atLoc = plugin.getClaimManager().getFactionAt(loc);
                return atLoc != null ? atLoc.getName() : "Wilderness";
            
            case "location_relation":
                if (faction == null) return "NEUTRAL";
                FLocation pLoc = new FLocation(player.getLocation());
                Faction atPLoc = plugin.getClaimManager().getFactionAt(pLoc);
                if (atPLoc == null) return "NEUTRAL";
                if (atPLoc.getId().equals(faction.getId())) return "MEMBER";
                Relation rel = faction.getRelationTo(atPLoc.getId());
                return rel != null ? rel.name() : "NEUTRAL";
            
            // Warps
            case "faction_warps":
                return faction != null ? String.valueOf(faction.getWarpCount()) : "0";
            
            case "faction_maxwarps":
                return String.valueOf(plugin.getConfigManager().getInt("warps.max-per-faction", 1));
            
            // Kills/Deaths joueur
            case "player_kills":
                return String.valueOf(fPlayer.getKills());
            
            case "player_deaths":
                return String.valueOf(fPlayer.getDeaths());
            
            case "player_kdr":
                int deaths = fPlayer.getDeaths();
                if (deaths == 0) return String.valueOf(fPlayer.getKills());
                return String.format("%.2f", (double) fPlayer.getKills() / deaths);
            
            // Chat mode
            case "player_chatmode":
                return fPlayer.getChatMode().name().toLowerCase();
            
            // Raidable
            case "faction_raidable":
                if (faction == null) return "false";
                return plugin.getClaimManager().isRaidable(faction) ? "true" : "false";
            
            // === Level System ===
            case "faction_level":
                return faction != null ? String.valueOf(faction.getLevel()) : "0";
            
            case "faction_xp":
                return "0"; // alias legacy: l'XP n'est plus utilisée
            
            case "faction_required_xp":
                return "0"; // alias legacy

            case "faction_progress_percent":
                return faction == null ? "0"
                        : String.valueOf(plugin.getLevelManager()
                                .getProgressPercent(faction));
            
            case "faction_progressbar":
                if (faction == null) return "";
                return plugin.getLevelManager().getProgressBar(faction);
            
            case "faction_category":
                return ""; // les catégories ne sont plus sélectionnées

            case "faction_tier":
                if (faction == null) return "";
                MemberTierDefinition tier =
                        plugin.getQuestManager().getCurrentTier(faction);
                return tier == null ? "" : tier.getId();

            case "faction_tier_display":
                if (faction == null) return "";
                MemberTierDefinition displayTier =
                        plugin.getQuestManager().getCurrentTier(faction);
                return displayTier == null ? "" : displayTier.getDisplayName();

            case "faction_quests_total":
                return faction == null ? "0" : String.valueOf(
                        plugin.getQuestManager().getQuestViews(faction).size());

            case "faction_quests_completed":
                if (faction == null) return "0";
                int completedQuests = 0;
                for (QuestProgressView view
                        : plugin.getQuestManager().getQuestViews(faction)) {
                    if (view.isCompleted()) completedQuests++;
                }
                return String.valueOf(completedQuests);
            
            case "faction_quests_remaining":
                if (faction == null) return "0";
                int remaining = 0;
                for (QuestProgressView view
                        : plugin.getQuestManager().getQuestViews(faction)) {
                    if (!view.isCompleted()) remaining++;
                }
                return String.valueOf(remaining);
            
            case "faction_has_chest":
                return faction != null && faction.hasChest() ? "true" : "false";
            
            case "faction_has_fly":
                return faction != null && faction.isFactionFlyEnabled() ? "true" : "false";
            
            case "faction_has_antisethome":
                return faction != null && faction.isAntiSethomeEnabled() ? "true" : "false";
            
            // Display versions for GUIs (colored text)
            case "faction_has_chest_display":
                return faction != null && faction.hasChest() ? "§a✔ Débloqué" : "§c✖ Verrouillé";
            
            case "faction_has_fly_display":
                return faction != null && faction.isFactionFlyEnabled() ? "§a✔ Débloqué" : "§c✖ Verrouillé";
            
            case "faction_has_antisethome_display":
                return faction != null && faction.isAntiSethomeEnabled() ? "§a✔ Actif" : "§c✖ Verrouillé";
            
            // Role display
            case "faction_role":
                return fPlayer.getRole() != null ? fPlayer.getRole().getDisplayName() : "";
            
            default:
                // Gestion des placeholders de permission: perm_<role>_<permission>
                return handlePermissionPlaceholder(identifier, faction);
        }
    }
    
    /**
     * Gère les placeholders de permission de format: perm_<role>_<permission>
     * Ex: perm_recruit_build, perm_ally_container
     * @return "§a✔ Activé" ou "§c✖ Désactivé" ou null si non trouvé
     */
    private String handlePermissionPlaceholder(String identifier, Faction faction) {
        if (!identifier.startsWith("perm_") || faction == null) {
            return null;
        }
        
        // Format: perm_<role>_<permission>
        String[] parts = identifier.substring(5).split("_", 2);
        if (parts.length < 2) return null;
        
        String roleStr = parts[0].toUpperCase();
        String permStr = parts[1].toLowerCase();
        
        // Vérifier si c'est un rôle de membre
        me.krunsh.kfaction.data.FactionRole role = parseRole(roleStr);
        if (role != null) {
            me.krunsh.kfaction.data.PermissionAction action = 
                me.krunsh.kfaction.data.PermissionAction.fromConfigKey(permStr);
            if (action == null) return null;
            
            boolean hasPermission = faction.hasPermission(role, action);
            return hasPermission ? "§a✔ Activé" : "§c✖ Désactivé";
        }
        
        // Vérifier si c'est une relation
        Relation relation = parseRelation(roleStr);
        if (relation != null) {
            me.krunsh.kfaction.data.PermissionAction action = 
                me.krunsh.kfaction.data.PermissionAction.fromConfigKey(permStr);
            if (action == null) return null;
            
            boolean hasPermission = faction.hasPermission(relation, action);
            return hasPermission ? "§a✔ Activé" : "§c✖ Désactivé";
        }
        
        return null;
    }
    
    private me.krunsh.kfaction.data.FactionRole parseRole(String str) {
        try {
            return me.krunsh.kfaction.data.FactionRole.valueOf(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private Relation parseRelation(String str) {
        try {
            return Relation.valueOf(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
