package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.hooks.VaultHook;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Commande /f show [faction] - Afficher les infos d'une faction
 */
public class ShowCommand extends SubCommand {

    private static final int MAX_MEMBER_DETAILS_CACHE = 2048;

    private final ConcurrentMap<UUID, CachedMemberDetails> memberDetailsCache =
            new ConcurrentHashMap<UUID, CachedMemberDetails>();
    private final AtomicBoolean economyFailureLogged = new AtomicBoolean();
    
    public ShowCommand(Kfaction plugin) {
        super(plugin);
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Faction faction;
        
        if (args.length > 0) {
            // Faction spécifiée
            faction = plugin.getFactionManager().getFactionByName(args[0]);
            if (faction == null) {
                sendMessage(sender, "show.faction-not-found", "{name}", args[0]);
                return;
            }
        } else {
            // Faction du joueur
            if (!(sender instanceof Player)) {
                sendMessage(sender, "show.specify-faction");
                return;
            }
            faction = plugin.getFactionManager().getPlayerFaction((Player) sender);
            if (faction == null) {
                sendMessage(sender, "show.not-in-faction");
                return;
            }
        }
        
        displayFactionInfo(sender, faction);
    }
    
    private void displayFactionInfo(CommandSender sender, Faction faction) {
        Faction viewerFaction = null;
        if (sender instanceof Player) {
            viewerFaction = plugin.getFactionManager().getPlayerFaction((Player) sender);
        }
        
        String relationColor = "&f";
        if (viewerFaction != null) {
            Relation relation = viewerFaction.getRelationTo(faction);
            relationColor = relation.getColorPrefix();
        }
        
        List<FactionMemberView> members = collectMembers(faction);
        List<FactionMemberView> online =
                FactionMemberView.selectAndSort(members, true);
        List<FactionMemberView> offline =
                FactionMemberView.selectAndSort(members, false);

        sendLine(sender, display(
                "separator",
                "&8&m----------------------------------------"
        ));
        sendLine(sender, display(
                "title",
                "{relation_color}&l{faction}",
                "{relation_color}", relationColor,
                "{faction}", faction.getName()
        ));

        if (!faction.getDescription().isEmpty()) {
            sendLine(sender, display(
                    "description",
                    "&7{description}",
                    "{description}", faction.getDescription()
            ));
        }

        sendLine(sender, display(
                "separator",
                "&8&m----------------------------------------"
        ));
        
        // Leader
        String leaderName = memberName(members, faction.getLeader(), "Aucun");
        sendLine(sender, display(
                "leader",
                "&6Chef: &f{leader}",
                "{leader}", leaderName
        ));
        
        // Membres
        int baseMemberLimit =
                plugin.getConfigManager()
                        .getInt(
                                "factions.members.max-per-faction",
                                50
                        );

        int effectiveMemberLimit =
                Math.max(
                        1,
                        baseMemberLimit
                                + Math.max(
                                        0,
                                        faction.getExtraMembers()
                                )
                );

        sendLine(sender, display(
                "members-summary",
                "&6Membres: &a{online}&7/&f{total} &8• &7Places: &f{total}&7/&f{max}",
                "{online}", String.valueOf(online.size()),
                "{offline}", String.valueOf(offline.size()),
                "{total}", String.valueOf(members.size()),
                "{max}", String.valueOf(effectiveMemberLimit)
        ));

        sendMemberSection(sender, online, true);
        sendMemberSection(sender, offline, false);
        
        // Power
        double power = plugin.getPowerManager().getFactionPower(faction);
        double maxPower = plugin.getPowerManager().getFactionMaxPower(faction);
        String powerColor = power < faction.getClaimCount() ? "&c" : "&a";
        sendLine(sender, display(
                "power",
                "&6Power: {power_color}{power}&7/{max_power}",
                "{power_color}", powerColor,
                "{power}", String.format(Locale.US, "%.1f", power),
                "{max_power}", String.format(Locale.US, "%.1f", maxPower)
        ));
        
        // Claims
        sendLine(sender, display(
                "claims",
                "&6Territoire: &f{claims} chunks",
                "{claims}", String.valueOf(faction.getClaimCount())
        ));
        
        // Banque
        if (plugin.getHookManager().hasVault()) {
            sendLine(sender, display(
                    "bank",
                    "&6Banque: &f{money}",
                    "{money}", plugin.getHookManager()
                            .getVaultHook()
                            .format(faction.getBank())
            ));
        }
        
        // Relations
        String allies = relationNames(faction.getAllies());
        if (!allies.isEmpty()) {
            sendLine(sender, display(
                    "allies",
                    "&6Alliés: &d{factions}",
                    "{factions}", allies
            ));
        }
        
        String enemies = relationNames(faction.getEnemies());
        if (!enemies.isEmpty()) {
            sendLine(sender, display(
                    "enemies",
                    "&6Ennemis: &c{factions}",
                    "{factions}", enemies
            ));
        }

        sendLine(sender, display(
                "separator",
                "&8&m----------------------------------------"
        ));
    }

    private List<FactionMemberView> collectMembers(Faction faction) {
        List<FactionMemberView> result =
                new ArrayList<FactionMemberView>(faction.getMemberCount());

        for (UUID uuid : faction.getMembers()) {
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            boolean online = onlinePlayer != null && onlinePlayer.isOnline();

            FPlayer profile = plugin.getFPlayerManager().findLoaded(uuid);
            String name = online
                    ? onlinePlayer.getName()
                    : profile != null
                            ? profile.getLastKnownName()
                            : null;

            if (name == null || name.trim().isEmpty()) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                name = offlinePlayer != null ? offlinePlayer.getName() : null;
            }

            FactionRole role = faction.getRole(uuid);

            result.add(new FactionMemberView(
                    uuid,
                    name,
                    role,
                    online
            ));
        }

        return result;
    }

    private void sendMemberSection(
            CommandSender sender,
            List<FactionMemberView> members,
            boolean online
    ) {
        String label = display(
                online ? "online-label" : "offline-label",
                online
                        ? "&aConnectés &7({count})&8: "
                        : "&cDéconnectés &7({count})&8: ",
                "{count}", String.valueOf(members.size())
        );

        if (members.isEmpty()) {
            sendLine(sender, label + display("none", "&7Aucun"));
            return;
        }

        int namesPerLine = Math.max(
                1,
                Math.min(
                        15,
                        plugin.getConfigManager().getInt(
                                "faction-show.names-per-line",
                                8
                        )
                )
        );

        if (!(sender instanceof Player)) {
            for (int start = 0; start < members.size(); start += namesPerLine) {
                int end = Math.min(start + namesPerLine, members.size());
                String prefix = start == 0 ? label : "  ";
                sendLine(sender, prefix + plainNames(members, start, end, online));
            }
            return;
        }

        Player player = (Player) sender;

        for (int start = 0; start < members.size(); start += namesPerLine) {
            int end = Math.min(start + namesPerLine, members.size());
            List<BaseComponent> components = new ArrayList<BaseComponent>();

            appendLegacy(
                    components,
                    start == 0 ? label : "&8  ↳ ",
                    null
            );

            for (int index = start; index < end; index++) {
                FactionMemberView member = members.get(index);
                if (index > start) {
                    appendLegacy(components, "&7, ", null);
                }

                appendLegacy(
                        components,
                        (online ? "&a" : "&c") + member.getName(),
                        memberHover(member)
                );
            }

            player.spigot().sendMessage(
                    components.toArray(new BaseComponent[components.size()])
            );
        }
    }

    private String memberHover(FactionMemberView member) {
        CachedMemberDetails details = memberDetails(member);
        String serverRankLine = details.serverRank != null
                ? display(
                        "server-rank-line",
                        "&7Rang serveur: &f{server_rank}\n",
                        "{server_rank}", details.serverRank
                )
                : "";

        return display(
                "member-hover",
                "&e{name}\n&7Rang faction: {role_color}{role}\n{server_rank_line}&7Argent: &6{money}",
                "{name}", member.getName(),
                "{role_color}", FactionMemberView.roleColor(member.getRole()),
                "{role}", member.getRole().getDisplayName(),
                "{server_rank_line}", serverRankLine,
                "{money}", details.money
        );
    }

    private CachedMemberDetails memberDetails(FactionMemberView member) {
        UUID uuid = member.getUuid();
        long now = System.nanoTime();

        if (uuid != null) {
            CachedMemberDetails cached = memberDetailsCache.get(uuid);
            if (cached != null && cached.expiresAtNanos > now) {
                return cached;
            }
        }

        String unavailable = display("economy-unavailable", "&7Indisponible");
        String money = unavailable;
        String serverRank = null;

        if (plugin.getHookManager().hasVault() && uuid != null) {
            try {
                VaultHook vault = plugin.getHookManager().getVaultHook();
                OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                money = vault.format(vault.getBalance(target));

                if (vault.hasChat()
                        && plugin.getConfigManager().getBoolean(
                                "faction-show.server-rank.enabled",
                                true
                        )) {
                    String resolved = vault.getPrimaryGroup(target);
                    if (resolved != null && !resolved.trim().isEmpty()) {
                        serverRank = resolved;
                    }
                }
            } catch (RuntimeException exception) {
                if (economyFailureLogged.compareAndSet(false, true)) {
                    plugin.getLogger().warning(
                            "Impossible de lire un profil Vault pour /f show: "
                                    + exception.getClass().getSimpleName()
                    );
                }
            }
        }

        long ttlSeconds = Math.max(
                1L,
                Math.min(
                        300L,
                        plugin.getConfigManager().getLong(
                                "faction-show.member-details-cache-seconds",
                                15L
                        )
                )
        );
        CachedMemberDetails fresh = new CachedMemberDetails(
                money,
                serverRank,
                now + TimeUnit.SECONDS.toNanos(ttlSeconds)
        );

        if (uuid != null) {
            pruneMemberDetailsCache(now);
            memberDetailsCache.put(uuid, fresh);
        }
        return fresh;
    }

    private void pruneMemberDetailsCache(long now) {
        if (memberDetailsCache.size() < MAX_MEMBER_DETAILS_CACHE) {
            return;
        }

        for (java.util.Map.Entry<UUID, CachedMemberDetails> entry
                : memberDetailsCache.entrySet()) {
            CachedMemberDetails value = entry.getValue();
            if (value == null || value.expiresAtNanos <= now) {
                memberDetailsCache.remove(entry.getKey(), value);
            }
        }

        if (memberDetailsCache.size() >= MAX_MEMBER_DETAILS_CACHE) {
            memberDetailsCache.clear();
        }
    }

    private static String memberName(
            List<FactionMemberView> members,
            UUID memberId,
            String fallback
    ) {
        if (memberId == null) {
            return fallback;
        }

        for (FactionMemberView member : members) {
            if (memberId.equals(member.getUuid())) {
                return member.getName();
            }
        }
        return "Inconnu";
    }

    private static final class CachedMemberDetails {
        private final String money;
        private final String serverRank;
        private final long expiresAtNanos;

        private CachedMemberDetails(
                String money,
                String serverRank,
                long expiresAtNanos
        ) {
            this.money = money;
            this.serverRank = serverRank;
            this.expiresAtNanos = expiresAtNanos;
        }
    }

    private static String plainNames(
            List<FactionMemberView> members,
            int start,
            int end,
            boolean online
    ) {
        StringBuilder result = new StringBuilder();

        for (int index = start; index < end; index++) {
            if (index > start) {
                result.append("&7, ");
            }

            result.append(online ? "&a" : "&c")
                    .append(members.get(index).getName());
        }

        return result.toString();
    }

    private String relationNames(Set<String> factionIds) {
        if (factionIds == null || factionIds.isEmpty()) {
            return "";
        }

        List<String> names = new ArrayList<String>();
        for (String factionId : factionIds) {
            Faction related = plugin.getFactionManager().getFaction(factionId);
            if (related != null) {
                names.add(related.getName());
            }
        }

        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return join(names);
    }

    private String display(
            String key,
            String fallback,
            Object... replacements
    ) {
        String raw = plugin.getMessageManager().getRaw("show.display." + key);
        String value = raw != null && !raw.isEmpty() ? raw : fallback;

        for (int index = 0; index + 1 < replacements.length; index += 2) {
            value = value.replace(
                    String.valueOf(replacements[index]),
                    String.valueOf(replacements[index + 1])
            );
        }

        return plugin.getMessageManager().colorize(value);
    }

    private static void appendLegacy(
            List<BaseComponent> destination,
            String legacy,
            String hover
    ) {
        BaseComponent[] components = TextComponent.fromLegacyText(
                ChatColor.translateAlternateColorCodes(
                        '&',
                        legacy != null ? legacy : ""
                )
        );

        HoverEvent hoverEvent = hover != null && !hover.isEmpty()
                ? new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        TextComponent.fromLegacyText(hover)
                )
                : null;

        for (BaseComponent component : components) {
            if (hoverEvent != null) {
                component.setHoverEvent(hoverEvent);
            }
            destination.add(component);
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private static void sendLine(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes(
                '&',
                message != null ? message : ""
        ));
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Faction faction : plugin.getFactionManager().getPlayerFactions()) {
                if (faction.getName().toLowerCase().startsWith(partial)) {
                    completions.add(faction.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getName() {
        return "show";
    }
    
    @Override
    public String getDescription() {
        return "Affiche les informations d'une faction";
    }
    
    @Override
    public String getUsage() {
        return "[faction]";
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}
