package me.krunsh.kfaction.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionLog;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Hook pour Kgui (système de menus)
 * Utilise la Content Provider API pour fournir du contenu dynamique aux menus paginés
 */
public class KguiHook {
    
    private final Kfaction plugin;
    
    // Références Kgui via reflection
    private Object kguiInstance;
    private Object guiManager;
    private Object menuManager;
    private Object paginationManager;
    private Object contentProviderManager;
    
    // Cache des méthodes pour performance
    private Method openMenuMethod;
    private Method hasMenuMethod;
    private Method closeMenuMethod;
    private Method refreshMenuMethod;
    private Method getMenuMethod;
    private Method initializeForPlayerMethod;
    private Method registerProviderMethod;
    private Constructor<?> paginationItemConstructor;
    private Constructor<?> dynamicItemBuilderConstructor;
    
    // DynamicItem.Builder methods
    private Method builderMaterialMethod;
    private Method builderNameMethod;
    private Method builderLoreMethod;
    private Method builderGlowMethod;
    private Method builderDataMethod;
    private Method builderCustomDataMethod;
    private Method builderClickActionMethod;
    private Method builderSkullOwnerMethod;
    private Method builderBuildMethod;
    
    private boolean initialized = false;
    private boolean contentProviderApiEnabled = false;
    
    public KguiHook(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialise le hook vers Kgui
     */
    public void initialize() {
        try {
            Plugin kgui = Bukkit.getPluginManager().getPlugin("Kgui");
            if (kgui == null || !kgui.isEnabled()) {
                plugin.getLogger().warning("Kgui not found or not enabled");
                return;
            }
            
            this.kguiInstance = kgui;
            
            // Obtenir le GuiManager via getInstance().getGuiManager()
            Class<?> kguiClass = kgui.getClass();
            Method getGuiManagerMethod = kguiClass.getMethod("getGuiManager");
            this.guiManager = getGuiManagerMethod.invoke(kgui);
            
            // Obtenir le MenuManager
            Method getMenuManagerMethod = kguiClass.getMethod("getMenuManager");
            this.menuManager = getMenuManagerMethod.invoke(kgui);
            
            // Cache les méthodes GuiManager
            Class<?> guiManagerClass = guiManager.getClass();
            this.openMenuMethod = guiManagerClass.getMethod("openMenu", Player.class, String.class);
            this.closeMenuMethod = guiManagerClass.getMethod("closeMenu", Player.class);
            this.refreshMenuMethod = guiManagerClass.getMethod("refreshMenu", Player.class);
            
            // Cache la méthode MenuManager.getMenu pour vérifier si un menu existe
            Class<?> menuManagerClass = menuManager.getClass();
            this.hasMenuMethod = menuManagerClass.getMethod("getMenu", String.class);
            this.getMenuMethod = this.hasMenuMethod; // Same method
            
            // Obtenir le PaginationManager pour les menus dynamiques
            Method getPaginationManagerMethod = kguiClass.getMethod("getPaginationManager");
            this.paginationManager = getPaginationManagerMethod.invoke(kgui);
            
            if (paginationManager != null) {
                Class<?> paginationManagerClass = paginationManager.getClass();
                
                // Trouver la classe PaginationItem (nested class)
                Class<?> paginationItemClass = null;
                for (Class<?> nestedClass : paginationManagerClass.getDeclaredClasses()) {
                    if (nestedClass.getSimpleName().equals("PaginationItem")) {
                        paginationItemClass = nestedClass;
                        break;
                    }
                }
                
                if (paginationItemClass != null) {
                    // Cache le constructeur PaginationItem
                    this.paginationItemConstructor = paginationItemClass.getConstructor(
                        String.class,   // material
                        short.class,    // data
                        String.class,   // name
                        List.class,     // lore
                        boolean.class,  // glow
                        Map.class,      // placeholders
                        List.class      // clickActions
                    );
                    
                    // Cache la méthode initializeForPlayer
                    // Trouver MenuData class
                    Class<?> menuDataClass = Class.forName("me.krunsh.kgui.menu.MenuData");
                    this.initializeForPlayerMethod = paginationManagerClass.getMethod(
                        "initializeForPlayer", Player.class, menuDataClass, List.class
                    );
                    
                    me.krunsh.kfaction.utils.KfactionLogger.debug(plugin, "Kgui pagination support: ACTIVE");
                }
            }
            
            // === CONTENT PROVIDER API (nouvelle approche) ===
            initializeContentProviderApi(kgui, kguiClass);
            
            initialized = true;
            me.krunsh.kfaction.utils.KfactionLogger.debug(plugin, "KguiHook: ACTIVE");
            
        } catch (Exception e) {
            initialized = false;
            plugin.getLogger().warning("Failed to initialize KguiHook: " + e.getMessage());
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "Stacktrace KguiHook",
                        e
                );
            }
        }
    }
    
    /**
     * Initialise la Content Provider API de Kgui pour les menus paginés dynamiques.
     * Enregistre les providers: kfaction_logs, kfaction_members, kfaction_warps, kfaction_claims
     */
    private void initializeContentProviderApi(Plugin kgui, Class<?> kguiClass) {
        try {
            // Obtenir le ContentProviderManager
            Method getContentProviderManagerMethod = kguiClass.getMethod("getContentProviderManager");
            this.contentProviderManager = getContentProviderManagerMethod.invoke(kgui);
            
            if (contentProviderManager == null) {
                plugin.getLogger().warning("ContentProviderManager not found - using legacy injection");
                return;
            }
            
            // Obtenir la méthode register
            Class<?> contentProviderManagerClass = contentProviderManager.getClass();
            Class<?> dynamicContentProviderClass = Class.forName("me.krunsh.kgui.api.DynamicContentProvider");
            this.registerProviderMethod = contentProviderManagerClass.getMethod("register", String.class, dynamicContentProviderClass);
            
            // Obtenir DynamicItem.Builder
            Class<?> dynamicItemClass = Class.forName("me.krunsh.kgui.api.DynamicItem");
            Class<?> builderClass = Class.forName("me.krunsh.kgui.api.DynamicItem$Builder");
            this.dynamicItemBuilderConstructor = builderClass.getConstructor();
            
            // Cache les méthodes du Builder pour créer des items
            this.builderMaterialMethod = builderClass.getMethod("material", String.class);
            this.builderNameMethod = builderClass.getMethod("name", String.class);
            this.builderLoreMethod = builderClass.getMethod("lore", List.class);
            this.builderGlowMethod = builderClass.getMethod("glow", boolean.class);
            this.builderDataMethod = builderClass.getMethod("data", int.class);
            this.builderCustomDataMethod = builderClass.getMethod("data", String.class, String.class);
            this.builderClickActionMethod = builderClass.getMethod("clickAction", String.class);
            this.builderSkullOwnerMethod   = builderClass.getMethod("skullOwner", String.class);
            this.builderBuildMethod = builderClass.getMethod("build");
            
            // Enregistrer les providers
            registerLogsProvider(dynamicContentProviderClass);
            registerMembersProvider(dynamicContentProviderClass);
            registerWarpsProvider(dynamicContentProviderClass);
            registerClaimsProvider(dynamicContentProviderClass);
            
            this.contentProviderApiEnabled = true;
            me.krunsh.kfaction.utils.KfactionLogger.debug(plugin, "Kgui Content Provider API: ACTIVE");
            
        } catch (ClassNotFoundException e) {
            // ContentProvider API non disponible (ancienne version de Kgui)
            me.krunsh.kfaction.utils.KfactionLogger.debug(plugin, "Kgui Content Provider API absent: fallback legacy injection.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize Content Provider API: " + e.getMessage());
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "Stacktrace KguiHook",
                        e
                );
            }
        }
    }
    
    /**
     * Enregistre le provider pour les logs de faction
     */
    @SuppressWarnings("unchecked")
    private void registerLogsProvider(Class<?> providerInterface) throws Exception {
        // Créer un proxy pour l'interface DynamicContentProvider
        Object logsProvider = java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                
                if ("getProviderId".equals(methodName)) {
                    return "kfaction_logs";
                }
                
                if ("getContent".equals(methodName)) {
                    Player player = (Player) args[0];
                    Map<String, String> providerArgs = args[1] != null ? (Map<String, String>) args[1] : new HashMap<>();
                    return getLogsContent(player, providerArgs);
                }
                
                if ("onClick".equals(methodName)) {
                    // Logs are read-only, no click action
                    return null;
                }
                
                return null;
            }
        );
        
        registerProviderMethod.invoke(contentProviderManager, "kfaction_logs", logsProvider);
    }
    
    /**
     * Enregistre le provider pour les membres de faction
     */
    @SuppressWarnings("unchecked")
    private void registerMembersProvider(Class<?> providerInterface) throws Exception {
        Object membersProvider = java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                
                if ("getProviderId".equals(methodName)) {
                    return "kfaction_members";
                }
                
                if ("getContent".equals(methodName)) {
                    Player player = (Player) args[0];
                    Map<String, String> providerArgs = args[1] != null ? (Map<String, String>) args[1] : new HashMap<>();
                    return getMembersContent(player, providerArgs);
                }
                
                if ("onClick".equals(methodName)) {
                    return null;
                }
                
                return null;
            }
        );
        
        registerProviderMethod.invoke(contentProviderManager, "kfaction_members", membersProvider);
    }
    
    /**
     * Enregistre le provider pour les warps de faction
     */
    @SuppressWarnings("unchecked")
    private void registerWarpsProvider(Class<?> providerInterface) throws Exception {
        Object warpsProvider = java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                
                if ("getProviderId".equals(methodName)) {
                    return "kfaction_warps";
                }
                
                if ("getContent".equals(methodName)) {
                    Player player = (Player) args[0];
                    Map<String, String> providerArgs = args[1] != null ? (Map<String, String>) args[1] : new HashMap<>();
                    return getWarpsContent(player, providerArgs);
                }
                
                if ("onClick".equals(methodName)) {
                    // Click sur un warp = téléportation
                    Player player = (Player) args[0];
                    Object dynamicItem = args[1];
                    return handleWarpClick(player, dynamicItem);
                }
                
                return null;
            }
        );
        
        registerProviderMethod.invoke(contentProviderManager, "kfaction_warps", warpsProvider);
    }
    
    /**
     * Enregistre le provider pour les territoires claim de la faction
     */
    @SuppressWarnings("unchecked")
    private void registerClaimsProvider(Class<?> providerInterface) throws Exception {
        Object claimsProvider = java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                String methodName = method.getName();
                
                if ("getProviderId".equals(methodName)) {
                    return "kfaction_claims";
                }
                
                if ("getContent".equals(methodName)) {
                    Player player = (Player) args[0];
                    Map<String, String> providerArgs = args[1] != null ? (Map<String, String>) args[1] : new HashMap<>();
                    return getClaimsContent(player, providerArgs);
                }
                
                if ("onClick".equals(methodName)) {
                    return null;
                }
                
                return null;
            }
        );
        
        registerProviderMethod.invoke(contentProviderManager, "kfaction_claims", claimsProvider);
    }
    
    /**
     * Génère le contenu dynamique pour les logs de faction
     */
    private List<Object> getLogsContent(Player player, Map<String, String> args) {
        List<Object> items = new ArrayList<>();
        
        try {
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return items;
            
            // Récupérer les logs depuis le LogManager
            List<FactionLog> logs = plugin.getLogManager().getLogs(faction.getId());
            if (logs == null || logs.isEmpty()) return items;
            
            // Optionnellement filtrer par type
            String filterType = args.get("type");
            if (filterType != null && !filterType.isEmpty()) {
                logs = filterLogsByType(logs, filterType);
            }
            
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            
            for (FactionLog log : logs) {
                Object item = createDynamicItem(log, timeFormat);
                if (item != null) {
                    items.add(item);
                }
            }
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error generating logs content: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * Génère le contenu dynamique pour les membres de faction
     */
    private List<Object> getMembersContent(Player player, Map<String, String> args) {
        List<Object> items = new ArrayList<>();
        
        try {
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return items;
            
            // Optionnellement filtrer par rang
            String filterRole = args.get("role");
            
            for (UUID memberUuid : faction.getMembers()) {
                FactionRole role = faction.getRole(memberUuid);
                
                if (filterRole != null && !filterRole.isEmpty()) {
                    if (role == null || !role.name().equalsIgnoreCase(filterRole)) {
                        continue;
                    }
                }
                
                // Obtenir le nom du joueur
                String memberName = Bukkit.getOfflinePlayer(memberUuid).getName();
                if (memberName == null) memberName = "Inconnu";
                
                Object item = createMemberDynamicItem(memberUuid, memberName, role);
                if (item != null) {
                    items.add(item);
                }
            }
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error generating members content: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * Génère le contenu dynamique pour les warps de faction
     */
    private List<Object> getWarpsContent(Player player, Map<String, String> args) {
        List<Object> items = new ArrayList<>();
        
        try {
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return items;
            
            for (Map.Entry<String, Location> entry : faction.getWarps().entrySet()) {
                String warpName = entry.getKey();
                Location location = entry.getValue();
                
                Object item = createWarpDynamicItem(warpName, location);
                if (item != null) {
                    items.add(item);
                }
            }
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error generating warps content: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * Génère le contenu dynamique pour les territoires claim de la faction
     */
    private List<Object> getClaimsContent(Player player, Map<String, String> args) {
        List<Object> items = new ArrayList<>();
        
        try {
            Faction faction = plugin.getFactionManager().getPlayerFaction(player);
            if (faction == null) return items;
            
            Set<FLocation> claims = faction.getClaims();
            if (claims == null || claims.isEmpty()) return items;
            
            for (FLocation loc : claims) {
                Object item = createClaimDynamicItem(loc, faction);
                if (item != null) {
                    items.add(item);
                }
            }
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error generating claims content: " + e.getMessage());
            }
        }
        
        return items;
    }
    
    /**
     * Crée un DynamicItem depuis un FactionLog
     */
    private Object createDynamicItem(FactionLog log, SimpleDateFormat timeFormat) {
        try {
            Object builder = dynamicItemBuilderConstructor.newInstance();
            
            builder = builderMaterialMethod.invoke(builder, getMaterialForLogType(log.getType()));
            
            int data = (log.getType() != null && 
                (log.getType() == LogType.MEMBER_JOIN || log.getType() == LogType.MEMBER_LEAVE || 
                 log.getType() == LogType.MEMBER_KICK || log.getType() == LogType.MEMBER_PROMOTE || 
                 log.getType() == LogType.MEMBER_DEMOTE)) ? 3 : 0;
            builder = builderDataMethod.invoke(builder, data);
            
            String name = formatLogName(log, timeFormat);
            builder = builderNameMethod.invoke(builder, name);
            
            List<String> lore = formatLogLore(log);
            builder = builderLoreMethod.invoke(builder, lore);
            
            // Stocker le timestamp pour référence
            builder = builderCustomDataMethod.invoke(builder, "log_timestamp", String.valueOf(log.getTimestamp()));
            
            return builderBuildMethod.invoke(builder);
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error creating dynamic item for log: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Crée un DynamicItem pour un membre de faction
     */
    private Object createMemberDynamicItem(UUID memberUuid, String memberName, FactionRole role) {
        try {
            Object builder = dynamicItemBuilderConstructor.newInstance();
            
            builder = builderMaterialMethod.invoke(builder, "SKULL_ITEM");
            builder = builderDataMethod.invoke(builder, 3);
            // Appliquer le skin du joueur
            if (builderSkullOwnerMethod != null) {
                builder = builderSkullOwnerMethod.invoke(builder, memberName);
            }
            
            String roleName = role != null ? role.getDisplayName() : "Membre";
            String name = "§e" + memberName + " §7[" + roleName + "]";
            builder = builderNameMethod.invoke(builder, name);
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Rang: §f" + roleName);
            // Online status
            Player online = Bukkit.getPlayer(memberUuid);
            if (online != null && online.isOnline()) {
                lore.add("§7Status: §aEn ligne");
            } else {
                lore.add("§7Status: §cHors ligne");
            }
            lore.add("");
            builder = builderLoreMethod.invoke(builder, lore);
            
            // Stocker l'UUID pour les actions
            builder = builderCustomDataMethod.invoke(builder, "member_uuid", memberUuid.toString());
            builder = builderCustomDataMethod.invoke(builder, "member_name", memberName);
            
            return builderBuildMethod.invoke(builder);
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error creating dynamic item for member: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Crée un DynamicItem pour un warp de faction
     */
    private Object createWarpDynamicItem(String warpName, Location location) {
        try {
            Object builder = dynamicItemBuilderConstructor.newInstance();
            
            builder = builderMaterialMethod.invoke(builder, "ENDER_PEARL");
            
            String name = "§6" + warpName;
            builder = builderNameMethod.invoke(builder, name);
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (location != null && location.getWorld() != null) {
                lore.add("§7Monde: §f" + location.getWorld().getName());
                lore.add("§7Position: §f" + (int) location.getX() + ", " + (int) location.getY() + ", " + (int) location.getZ());
            }
            lore.add("");
            lore.add("§aClick pour téléporter");
            builder = builderLoreMethod.invoke(builder, lore);
            
            // Stocker le nom du warp pour le clic
            builder = builderCustomDataMethod.invoke(builder, "warp_name", warpName);
            
            // Action de clic
            builder = builderClickActionMethod.invoke(builder, "[console] f warp " + warpName + " %player%");
            
            return builderBuildMethod.invoke(builder);
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error creating dynamic item for warp: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Gère le clic sur un warp
     */
    private Object handleWarpClick(Player player, Object dynamicItem) {
        // L'action de clic est gérée par les clickActions du DynamicItem
        return null;
    }
    
    /**
     * Crée un DynamicItem représentant un territoire claim de faction
     */
    private Object createClaimDynamicItem(FLocation loc, Faction faction) {
        try {
            Object builder = dynamicItemBuilderConstructor.newInstance();
            
            builder = builderMaterialMethod.invoke(builder, "GRASS");
            
            String name = "§aChunk §f[" + loc.getX() + ", " + loc.getZ() + "]";
            builder = builderNameMethod.invoke(builder, name);
            
            int blockX = loc.getX() * 16;
            int blockZ = loc.getZ() * 16;
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Monde:    §f" + loc.getWorldName());
            lore.add("§7Chunk:    §fX=" + loc.getX() + "  Z=" + loc.getZ());
            lore.add("§7Blocs:    §fX " + blockX + " à " + (blockX + 15));
            lore.add("           §fZ " + blockZ + " à " + (blockZ + 15));
            lore.add("");
            lore.add("§7Faction:  §f" + faction.getName());
            builder = builderLoreMethod.invoke(builder, lore);
            
            // Données pour référence
            builder = builderCustomDataMethod.invoke(builder, "chunk_world", loc.getWorldName());
            builder = builderCustomDataMethod.invoke(builder, "chunk_x", String.valueOf(loc.getX()));
            builder = builderCustomDataMethod.invoke(builder, "chunk_z", String.valueOf(loc.getZ()));
            
            return builderBuildMethod.invoke(builder);
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error creating dynamic item for claim: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Filtre les logs par type
     */
    private List<FactionLog> filterLogsByType(List<FactionLog> logs, String typeFilter) {
        List<FactionLog> filtered = new ArrayList<>();
        for (FactionLog log : logs) {
            if (log.getType() != null && log.getType().name().equalsIgnoreCase(typeFilter)) {
                filtered.add(log);
            }
        }
        return filtered;
    }
    
    /**
     * Vérifie si la Content Provider API est disponible
     */
    public boolean isContentProviderApiEnabled() {
        return contentProviderApiEnabled;
    }
    
    /**
     * Vérifie si Kgui est disponible et initialisé
     */
    public boolean isAvailable() {
        return initialized;
    }
    
    /**
     * Ouvre un menu Kgui pour un joueur
     * @param player Le joueur
     * @param menuId L'identifiant du menu
     * @return true si le menu a été ouvert
     */
    public boolean openMenu(Player player, String menuId) {
        if (!initialized) return false;
        
        try {
            Object result = openMenuMethod.invoke(guiManager, player, menuId);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error opening menu " + menuId + ": " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Vérifie si un menu existe
     * @param menuId L'identifiant du menu
     * @return true si le menu existe
     */
    public boolean menuExists(String menuId) {
        if (!initialized) return false;
        
        try {
            Object menu = hasMenuMethod.invoke(menuManager, menuId);
            return menu != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Ferme le menu d'un joueur
     * @param player Le joueur
     */
    public void closeMenu(Player player) {
        if (!initialized) return;
        
        try {
            closeMenuMethod.invoke(guiManager, player);
        } catch (Exception e) {
            // Fallback
            player.closeInventory();
        }
    }
    
    /**
     * Rafraîchit le menu d'un joueur
     * @param player Le joueur
     */
    public void refreshMenu(Player player) {
        if (!initialized) return;
        
        try {
            refreshMenuMethod.invoke(guiManager, player);
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error refreshing menu: " + e.getMessage());
            }
        }
    }
    
    /**
     * Ouvre le menu principal de faction pour un joueur
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openFactionMenu(Player player) {
        // Essayer d'abord le menu kfaction_menu, puis faction_menu
        if (menuExists("kfaction_menu")) {
            return openMenu(player, "kfaction_menu");
        }
        if (menuExists("faction_menu")) {
            return openMenu(player, "faction_menu");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des membres de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openMembersMenu(Player player) {
        if (menuExists("kfaction_members")) {
            return openMenu(player, "kfaction_members");
        }
        if (menuExists("faction_members")) {
            return openMenu(player, "faction_members");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des permissions de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openPermissionsMenu(Player player) {
        if (menuExists("kfaction_permissions")) {
            return openMenu(player, "kfaction_permissions");
        }
        if (menuExists("faction_permissions")) {
            return openMenu(player, "faction_permissions");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des logs de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openLogsMenu(Player player) {
        if (menuExists("kfaction_logs")) {
            return openMenu(player, "kfaction_logs");
        }
        if (menuExists("faction_logs")) {
            return openMenu(player, "faction_logs");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des territoires claim de la faction
     */
    public boolean openClaimsMenu(Player player) {
        if (menuExists("kfaction_claims")) {
            return openMenu(player, "kfaction_claims");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des logs de faction avec contenu dynamique
     * @param player Le joueur
     * @param logs Liste des logs à afficher
     * @return true si le menu a été ouvert
     */
    public boolean openLogsMenuWithContent(Player player, List<FactionLog> logs) {
        // Utiliser le menu par défaut
        String menuId = menuExists("kfaction_logs") ? "kfaction_logs" : 
                        menuExists("faction_logs") ? "faction_logs" : null;
        return openLogsMenuWithContent(player, logs, menuId);
    }
    
    /**
     * Ouvre un menu de logs specifique avec contenu dynamique
     * @param player Le joueur
     * @param logs Liste des logs à afficher
     * @param targetMenuId Le menu à ouvrir (faction_logs, faction_logs_members, etc.)
     * @return true si le menu a été ouvert
     */
    public boolean openLogsMenuWithContent(Player player, List<FactionLog> logs, String targetMenuId) {
        if (!initialized || paginationItemConstructor == null || initializeForPlayerMethod == null) {
            // Fallback: ouvrir le menu statique
            return openMenu(player, targetMenuId != null ? targetMenuId : "faction_logs");
        }
        
        // Vérifier que le menu existe
        String menuId = targetMenuId;
        if (menuId == null || !menuExists(menuId)) {
            // Fallback vers le menu principal
            menuId = menuExists("faction_logs") ? "faction_logs" : null;
        }
        
        if (menuId == null) {
            return false;
        }
        
        try {
            // Obtenir le MenuData
            Object menuData = getMenuMethod.invoke(menuManager, menuId);
            if (menuData == null) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] MenuData null for menu: " + menuId);
                }
                return openMenu(player, menuId);
            }
            
            // Debug: afficher nombre de logs
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Opening logs menu with " + logs.size() + " logs for player " + player.getName());
            }
            
            // Créer les PaginationItems à partir des logs
            List<Object> paginationItems = new ArrayList<>();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            
            for (FactionLog log : logs) {
                String material = getMaterialForLogType(log.getType());
                short data = (short) (material.equals("SKULL_ITEM") ? 3 : 0);
                String name = formatLogName(log, timeFormat);
                List<String> lore = formatLogLore(log);
                boolean glow = false;
                Map<String, String> placeholders = new HashMap<>();
                List<String> clickActions = new ArrayList<>(); // Lecture seule
                
                Object item = paginationItemConstructor.newInstance(
                    material, data, name, lore, glow, placeholders, clickActions
                );
                paginationItems.add(item);
            }
            
            // Initialiser la pagination avec les items
            initializeForPlayerMethod.invoke(paginationManager, player, menuData, paginationItems);
            
            // Ouvrir le menu
            return openMenu(player, menuId);
            
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().warning("Error opening logs menu with content: " + e.getMessage());
                plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "Stacktrace KguiHook",
                        e
                );
            }
            // Fallback
            return openMenu(player, menuId);
        }
    }
    
    /**
     * Retourne le Material en fonction du type de log
     */
    private String getMaterialForLogType(LogType type) {
        if (type == null) return "PAPER";
        
        switch (type) {
            case MEMBER_JOIN:
            case MEMBER_LEAVE:
            case MEMBER_KICK:
            case MEMBER_PROMOTE:
            case MEMBER_DEMOTE:
                return "SKULL_ITEM";
            case TERRITORY_CLAIM:
            case TERRITORY_UNCLAIM:
                return "GRASS";
            case TERRITORY_SETHOME:
                return "BED";
            case TERRITORY_SETWARP:
            case TERRITORY_DELWARP:
                return "SIGN";
            case ECONOMY_DEPOSIT:
            case ECONOMY_WITHDRAW:
                return "GOLD_INGOT";
            case TP_HOME:
            case TP_WARP:
                return "ENDER_PEARL";
            default:
                return "PAPER";
        }
    }
    
    /**
     * Formate le nom affiché d'un log
     */
    private String formatLogName(FactionLog log, SimpleDateFormat timeFormat) {
        String time = timeFormat.format(new Date(log.getTimestamp()));
        String player = log.getPlayerName() != null ? log.getPlayerName() : "Système";
        return "§8[" + time + "] §e" + player;
    }
    
    /**
     * Formate le lore d'un log
     * Infos: Type d'action, cible (si applicable), détails, temps relatif
     */
    private List<String> formatLogLore(FactionLog log) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        // Description de l'action
        StringBuilder desc = new StringBuilder();
        desc.append("§f").append(log.getType().getDisplayName());
        if (log.hasTarget()) {
            desc.append(" §7→ §e").append(log.getTargetName());
        }
        lore.add(desc.toString());
        
        // Détails si présents
        if (log.getDetails() != null && !log.getDetails().isEmpty()) {
            lore.add("§7(" + log.getDetails() + ")");
        }
        
        lore.add("");
        lore.add("§7Il y a: §f" + log.formatTime());
        return lore;
    }
    
    /**
     * Retourne le nom d'affichage d'un type de log
     */
    private String getLogTypeDisplayName(LogType type) {
        if (type == null) return "Inconnu";
        
        switch (type) {
            case MEMBER_JOIN: return "Membre rejoint";
            case MEMBER_LEAVE: return "Membre parti";
            case MEMBER_KICK: return "Expulsion";
            case MEMBER_PROMOTE: return "Promotion";
            case MEMBER_DEMOTE: return "Rétrogradation";
            case TERRITORY_CLAIM: return "Claim";
            case TERRITORY_UNCLAIM: return "Unclaim";
            case TERRITORY_SETHOME: return "SetHome";
            case TERRITORY_SETWARP: return "SetWarp";
            case TERRITORY_DELWARP: return "DelWarp";
            case ECONOMY_DEPOSIT: return "Dépôt";
            case ECONOMY_WITHDRAW: return "Retrait";
            case TP_HOME: return "TP Home";
            case TP_WARP: return "TP Warp";
            default: return type.name();
        }
    }
    
    /**
     * Ouvre le menu des warps de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openWarpsMenu(Player player) {
        if (menuExists("kfaction_warps")) {
            return openMenu(player, "kfaction_warps");
        }
        if (menuExists("faction_warps")) {
            return openMenu(player, "faction_warps");
        }
        return false;
    }
    
    /**
     * Ouvre le menu des relations de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openRelationsMenu(Player player) {
        if (menuExists("kfaction_relations")) {
            return openMenu(player, "kfaction_relations");
        }
        if (menuExists("faction_relations")) {
            return openMenu(player, "faction_relations");
        }
        return false;
    }
    
    /**
     * Ouvre le menu de la banque de faction
     * @param player Le joueur
     * @return true si le menu a été ouvert
     */
    public boolean openBankMenu(Player player) {
        if (menuExists("kfaction_bank")) {
            return openMenu(player, "kfaction_bank");
        }
        if (menuExists("faction_bank")) {
            return openMenu(player, "faction_bank");
        }
        return false;
    }
}
