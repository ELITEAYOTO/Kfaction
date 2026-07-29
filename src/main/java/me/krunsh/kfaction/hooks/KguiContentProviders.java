package me.krunsh.kfaction.hooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.progression.LevelDefinition;
import me.krunsh.kfaction.progression.ProgressionConfig;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.RewardDefinition;

/**
 * Enregistre les ContentProviders dynamiques dans Kgui
 * pour afficher les quêtes et récompenses dans les menus GUI.
 * 
 * Providers enregistrés:
 * - kfaction_quests : Affiche les quêtes actives avec progression
 * - kfaction_rewards : Affiche l'arbre des récompenses par niveau
 * 
 * Utilise reflection pour éviter la dépendance compile-time sur Kgui.
 */
public class KguiContentProviders {
    
    private final Kfaction plugin;
    private boolean registered = false;
    
    public KguiContentProviders(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Tente d'enregistrer les providers dans Kgui.
     * Appelé après le chargement complet de Kfaction.
     */
    public void register() {
        Plugin kguiPlugin = Bukkit.getPluginManager().getPlugin("Kgui");
        if (kguiPlugin == null || !kguiPlugin.isEnabled()) {
            plugin.debug("Kgui non trouvé - ContentProviders non enregistrés");
            return;
        }
        
        try {
            // Obtenir le ContentProviderManager via reflection
            Method getContentProviderManager = kguiPlugin.getClass().getMethod("getContentProviderManager");
            Object cpm = getContentProviderManager.invoke(kguiPlugin);
            
            if (cpm == null) {
                plugin.getLogger().warning("ContentProviderManager non disponible dans Kgui");
                return;
            }
            
            // Charger les classes Kgui API via reflection
            ClassLoader kguiLoader = kguiPlugin.getClass().getClassLoader();
            Class<?> providerInterface = kguiLoader.loadClass("me.krunsh.kgui.api.DynamicContentProvider");
            Class<?> dynamicItemClass = kguiLoader.loadClass("me.krunsh.kgui.api.DynamicItem");
            Class<?> builderClass = kguiLoader.loadClass("me.krunsh.kgui.api.DynamicItem$Builder");
            
            // Méthode register(String, DynamicContentProvider) du ContentProviderManager
            Method registerMethod = cpm.getClass().getMethod("register", String.class, providerInterface);
            
            // Enregistrer le provider de quêtes
            Object questProvider = createQuestProvider(providerInterface, dynamicItemClass, builderClass);
            registerMethod.invoke(cpm, "kfaction_quests", questProvider);
            
            // Enregistrer le provider de récompenses
            Object rewardProvider = createRewardProvider(providerInterface, dynamicItemClass, builderClass);
            registerMethod.invoke(cpm, "kfaction_rewards", rewardProvider);
            
            registered = true;
            plugin.logInfo("&7ContentProviders Kgui enregistrés (kfaction_quests, kfaction_rewards)");
            
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible d'enregistrer les ContentProviders Kgui: " + e.getMessage());
            if (plugin.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Crée un proxy DynamicContentProvider pour les quêtes
     */
    private Object createQuestProvider(Class<?> providerInterface, Class<?> dynamicItemClass, Class<?> builderClass) {
        return java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                if ("getContent".equals(method.getName())) {
                    Player player = (Player) args[0];
                    @SuppressWarnings("unchecked")
                    Map<String, String> providerArgs = (Map<String, String>) args[1];
                    return buildQuestItems(player, providerArgs, builderClass);
                }
                if ("getId".equals(method.getName())) {
                    return "kfaction_quests";
                }
                if ("onClick".equals(method.getName())) {
                    return null;
                }
                // toString, hashCode, equals
                if ("toString".equals(method.getName())) return "KfactionQuestProvider";
                if ("hashCode".equals(method.getName())) return 42;
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
        );
    }
    
    /**
     * Crée un proxy DynamicContentProvider pour les récompenses
     */
    private Object createRewardProvider(Class<?> providerInterface, Class<?> dynamicItemClass, Class<?> builderClass) {
        return java.lang.reflect.Proxy.newProxyInstance(
            providerInterface.getClassLoader(),
            new Class<?>[] { providerInterface },
            (proxy, method, args) -> {
                if ("getContent".equals(method.getName())) {
                    Player player = (Player) args[0];
                    @SuppressWarnings("unchecked")
                    Map<String, String> providerArgs = (Map<String, String>) args[1];
                    return buildRewardItems(player, providerArgs, builderClass);
                }
                if ("getId".equals(method.getName())) {
                    return "kfaction_rewards";
                }
                if ("onClick".equals(method.getName())) {
                    return null;
                }
                if ("toString".equals(method.getName())) return "KfactionRewardProvider";
                if ("hashCode".equals(method.getName())) return 43;
                if ("equals".equals(method.getName())) return proxy == args[0];
                return null;
            }
        );
    }
    
    /**
     * Construit les items de quêtes pour le GUI
     */
    private List<Object> buildQuestItems(Player player, Map<String, String> args, Class<?> builderClass) throws Exception {
        List<Object> items = new ArrayList<>();
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) return items;
        
        List<QuestProgressView> quests =
                plugin.getQuestManager().getQuestViews(faction);
        if (quests.isEmpty()) return items;
        
        for (QuestProgressView quest : quests) {
            Object builder = builderClass.newInstance();

            String statusColor = quest.isCompleted() ? "§a" : "§e";
            String statusIcon = quest.isCompleted() ? "§a✔ " : "";

            Method materialMethod = builderClass.getMethod("material", String.class);
            Method nameMethod = builderClass.getMethod("name", String.class);
            Method dataMethod = builderClass.getMethod("data", int.class);
            Method loreMethod = builderClass.getMethod("lore", String[].class);
            Method glowMethod = builderClass.getMethod("glow", boolean.class);
            Method buildMethod = builderClass.getMethod("build");
            
            materialMethod.invoke(builder, quest.getDefinition().getIconMaterial());
            dataMethod.invoke(builder, quest.getDefinition().getIconData());
            nameMethod.invoke(builder, statusIcon + "§f"
                    + render(quest.getDefinition().getDisplayName(), quest));
            glowMethod.invoke(builder, quest.isCompleted());
            
            List<String> loreLines = new ArrayList<>();
            for (String configured : quest.getDefinition().getLore()) {
                loreLines.add(render(configured, quest));
            }
            if (!loreLines.isEmpty()) loreLines.add("");
            if (quest.isCompleted()) {
                loreLines.add("§a§l✔ Quête obligatoire complétée");
            } else {
                loreLines.add("§7Progression:");
                loreLines.add(progressBar(quest.getPercent(), 20));
                loreLines.add(statusColor + String.valueOf(quest.getProgress())
                        + "§7/§e" + quest.getRequired()
                        + " §7(" + quest.getPercent() + "%)");
            }
            loreMethod.invoke(builder,
                    (Object) loreLines.toArray(new String[loreLines.size()]));
            
            items.add(buildMethod.invoke(builder));
        }
        
        return items;
    }
    
    /**
     * Construit les items de récompenses par niveau pour le GUI
     */
    private List<Object> buildRewardItems(Player player, Map<String, String> args, Class<?> builderClass) throws Exception {
        List<Object> items = new ArrayList<>();
        
        Faction faction = plugin.getFactionManager().getPlayerFaction(player);
        if (faction == null) return items;
        
        int currentLevel = faction.getLevel();
        ProgressionConfig config = plugin.getQuestManager().getActiveConfig();
        if (config == null) return items;
        for (LevelDefinition definition : config.getLevels().values()) {
            int level = definition.getNumber();
            
            Object builder = builderClass.newInstance();
            
            Method materialMethod = builderClass.getMethod("material", String.class);
            Method nameMethod = builderClass.getMethod("name", String.class);
            Method dataMethod = builderClass.getMethod("data", int.class);
            Method loreMethod = builderClass.getMethod("lore", String[].class);
            Method glowMethod = builderClass.getMethod("glow", boolean.class);
            Method buildMethod = builderClass.getMethod("build");
            
            boolean unlocked = currentLevel > level;
            boolean current = currentLevel == level;
            
            // Matériau et couleur selon statut
            if (unlocked) {
                materialMethod.invoke(builder, "STAINED_GLASS_PANE");
                dataMethod.invoke(builder, 5); // Vert lime
                nameMethod.invoke(builder, "§a§l✔ Niveau " + level);
                glowMethod.invoke(builder, true);
            } else if (current) {
                materialMethod.invoke(builder, "STAINED_GLASS_PANE");
                dataMethod.invoke(builder, 1); // Orange (en cours)
                nameMethod.invoke(builder, "§6§l⚡ Niveau " + level + " §7(En cours)");
                glowMethod.invoke(builder, false);
            } else {
                materialMethod.invoke(builder, "STAINED_GLASS_PANE");
                dataMethod.invoke(builder, 14); // Rouge (verrouillé)
                nameMethod.invoke(builder, "§c§l✖ Niveau " + level);
                glowMethod.invoke(builder, false);
            }
            
            // Construire le lore des récompenses
            List<String> loreLines = new ArrayList<>();
            loreLines.add("");
            
            loreLines.add("§7Récompenses à l'entrée:");
            if (definition.getRewardsOnEnter().isEmpty()) {
                loreLines.add("§8▸ §7Aucune récompense définie");
            } else {
                for (RewardDefinition reward : definition.getRewardsOnEnter()) {
                    String statusPrefix = currentLevel >= level ? "§a✔ " : "§c✖ ";
                    loreLines.add("§8▸ " + statusPrefix
                            + reward.getDescription().replace("&", "§"));
                }
            }
            
            loreLines.add("");
            if (unlocked) {
                loreLines.add("§a✔ Débloqué!");
            } else if (current) {
                loreLines.add("§6⚡ Quêtes en cours");
            } else {
                loreLines.add("§c✖ Verrouillé");
            }
            
            loreMethod.invoke(builder, (Object) loreLines.toArray(new String[0]));
            
            items.add(buildMethod.invoke(builder));
        }
        
        return items;
    }

    private String render(String value, QuestProgressView view) {
        return value.replace("&", "§")
                .replace("{progress}", String.valueOf(view.getProgress()))
                .replace("{amount}", String.valueOf(view.getRequired()))
                .replace("{remaining}", String.valueOf(view.getRemaining()))
                .replace("{percent}", String.valueOf(view.getPercent()));
    }

    private String progressBar(int percent, int length) {
        int filled = Math.max(0, Math.min(length, percent * length / 100));
        StringBuilder value = new StringBuilder("§a");
        for (int index = 0; index < length; index++) {
            if (index == filled) value.append("§7");
            value.append("▌");
        }
        return value.toString();
    }
    
    public boolean isRegistered() {
        return registered;
    }
}
