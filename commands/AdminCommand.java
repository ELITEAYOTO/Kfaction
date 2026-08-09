package me.krunsh.kfaction.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionQuest;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.QuestCategory;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.policy.ZoneUnclaimSelection;
import me.krunsh.kfaction.progression.QuestProgressView;
import me.krunsh.kfaction.progression.ValidationIssue;

/**
 * Commande admin complète pour la gestion staff de Kfaction
 * Usage: /f admin <subcommand> [args...]
 */
public class AdminCommand extends SubCommand {

    private static final long ZONE_CONFIRM_TIMEOUT_MS = 30_000L;
    private static final int MAX_ZONE_RADIUS = 20;
    private final Map<UUID, PendingZoneUnclaim> pendingZoneUnclaims = new ConcurrentHashMap<>();
    
    private static final String[] SUB_COMMANDS = {
        "bypass", "reload", "setpower", "disband", "forcejoin", "forceleave",
        "forceleader", "setrole", "inspect", "rename", "settag", "setbalance",
        "lock", "unlock", "teleport", "claim", "unclaim", "debug", "help",
        "validateprogression", "questinfo"
    };
    
    public AdminCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            showAdminHelp(sender);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        
        switch (subCommand) {
            case "bypass":
                handleBypass(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "setpower":
                handleSetPower(sender, subArgs);
                break;
            case "disband":
                handleForceDisband(sender, subArgs);
                break;
            case "forcejoin":
                handleForceJoin(sender, subArgs);
                break;
            case "forceleave":
                handleForceLeave(sender, subArgs);
                break;
            case "forceleader":
                handleForceLeader(sender, subArgs);
                break;
            case "setrole":
                handleSetRole(sender, subArgs);
                break;
            case "inspect":
                handleInspect(sender, subArgs);
                break;
            case "rename":
                handleRename(sender, subArgs);
                break;
            case "settag":
                handleSetTag(sender, subArgs);
                break;
            case "setbalance":
                handleSetBalance(sender, subArgs);
                break;
            case "lock":
                handleLock(sender, subArgs, true);
                break;
            case "unlock":
                handleLock(sender, subArgs, false);
                break;
            case "teleport":
            case "tp":
                handleTeleport(sender, subArgs);
                break;
            case "claim":
                handleAdminClaim(sender, subArgs);
                break;
            case "unclaim":
                handleAdminUnclaim(sender, subArgs);
                break;
            case "debug":
                handleDebug(sender, subArgs);
                break;
            case "setlevel":
            case "setxp":
            case "addxp":
            case "resetlevel":
            case "selectcategory":
            case "resetquests":
            case "completequest":
                handleRemovedProgressionCommand(sender);
                break;
            case "validateprogression":
                handleValidateProgression(sender);
                break;
            case "questinfo":
                handleQuestInfo(sender, subArgs);
                break;
            case "help":
            case "?":
                showAdminHelp(sender);
                break;
            default:
                sendMessage(sender, "admin.unknown-command");
                break;
        }
    }
    
    // ============ SOUS-COMMANDES ============
    
    private void handleBypass(CommandSender sender) {
        if (!checkPermission(sender, "kfaction.admin.bypass")) return;
        if (!(sender instanceof Player)) {
            sendMessage(sender, "general.player-only");
            return;
        }
        Player player = (Player) sender;
        plugin.toggleBypass(player.getUniqueId());
        boolean bypass = plugin.isBypassing(player.getUniqueId());
        sendMessage(sender, bypass ? "admin.bypass-enabled" : "admin.bypass-disabled");
        notifyStaff("admin.notify.bypass", player.getName(), bypass ? "activé" : "désactivé");
    }
    
    private void handleReload(CommandSender sender) {
        if (!checkPermission(sender, "kfaction.admin.reload")) return;
        long start = System.currentTimeMillis();
        plugin.reload();
        long duration = System.currentTimeMillis() - start;
        sendMessage(sender, "admin.reload-success", "{time}", String.valueOf(duration));
    }
    
    private void handleSetPower(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setpower")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin setpower <joueur> <valeur>");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || !target.hasPlayedBefore()) {
            sendMessage(sender, "admin.player-not-found");
            return;
        }
        
        double power;
        try {
            power = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, "admin.invalid-number");
            return;
        }
        
        plugin.getPowerManager().setPlayerPower(target.getUniqueId(), power);
        sendMessage(sender, "admin.setpower-success", 
            "{player}", target.getName(), 
            "{power}", String.format("%.1f", power));
        notifyStaff("admin.notify.setpower", getSenderName(sender), target.getName(), String.format("%.1f", power));
    }
    
    private void handleForceDisband(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.disband")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin disband <faction>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        if (faction.isSystemFaction()) {
            sendMessage(sender, "admin.cannot-disband-system");
            return;
        }
        
        String name = faction.getName();
        int memberCount = faction.getMemberCount();
        int claimCount = faction.getClaimCount();
        
        plugin.getFactionManager().disbandFaction(faction);
        sendMessage(sender, "admin.disband-success", 
            "{faction}", name, 
            "{members}", String.valueOf(memberCount),
            "{claims}", String.valueOf(claimCount));
        notifyStaff("admin.notify.disband", getSenderName(sender), name);
    }
    
    private void handleForceJoin(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.forcejoin")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin forcejoin <joueur> <faction>");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null) {
            sendMessage(sender, "admin.player-not-found");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[1]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(target.getUniqueId());
        if (fPlayer.hasFaction()) {
            // Retirer de l'ancienne faction
            Faction oldFaction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
            if (oldFaction != null) {
                oldFaction.removeMember(target.getUniqueId());
            }
            plugin.getFPlayerManager().notifyFactionChange(target.getUniqueId(), fPlayer.getFactionId(), null);
        }
        
        faction.addMember(target.getUniqueId(), FactionRole.RECRUIT);
        fPlayer.joinFaction(faction.getId(), FactionRole.RECRUIT);
        plugin.getFPlayerManager().notifyFactionChange(target.getUniqueId(), null, faction.getId());
        plugin.getStorageManager().markDirty(faction);
        plugin.getStorageManager().markDirty(fPlayer);
        
        sendMessage(sender, "admin.forcejoin-success", "{player}", target.getName(), "{faction}", faction.getName());
        notifyStaff("admin.notify.forcejoin", getSenderName(sender), target.getName(), faction.getName());
    }
    
    private void handleForceLeave(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.forceleave")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin forceleave <joueur>");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(target.getUniqueId());
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "admin.player-no-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        String factionName = faction != null ? faction.getName() : "?";
        
        if (faction != null) {
            faction.removeMember(target.getUniqueId());
            plugin.getStorageManager().markDirty(faction);
        }
        String oldFactionId = fPlayer.getFactionId();
        fPlayer.leaveFaction();
        plugin.getFPlayerManager().notifyFactionChange(target.getUniqueId(), oldFactionId, null);
        plugin.getStorageManager().markDirty(fPlayer);
        
        sendMessage(sender, "admin.forceleave-success", "{player}", target.getName(), "{faction}", factionName);
        notifyStaff("admin.notify.forceleave", getSenderName(sender), target.getName(), factionName);
    }
    
    private void handleForceLeader(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.forceleader")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin forceleader <faction> <joueur>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!faction.isMember(target.getUniqueId())) {
            sendMessage(sender, "admin.player-not-member");
            return;
        }
        
        UUID oldLeader = faction.getLeader();
        faction.setLeader(target.getUniqueId());
        plugin.getStorageManager().markDirty(faction);
        
        sendMessage(sender, "admin.forceleader-success", "{player}", target.getName(), "{faction}", faction.getName());
        notifyStaff("admin.notify.forceleader", getSenderName(sender), target.getName(), faction.getName());
    }
    
    private void handleSetRole(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setrole")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin setrole <joueur> <role>");
            sender.sendMessage("§6Rôles: RECRUIT, MEMBER, MODERATOR, COLEADER, LEADER");
            return;
        }
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(target.getUniqueId());
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "admin.player-no-faction");
            return;
        }
        
        FactionRole role;
        try {
            role = FactionRole.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cRôle invalide. Rôles valides: RECRUIT, MEMBER, MODERATOR, COLEADER, LEADER");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction != null) {
            faction.setRole(target.getUniqueId(), role);
            plugin.getStorageManager().markDirty(faction);
        }
        fPlayer.setRole(role);
        plugin.getStorageManager().markDirty(fPlayer);
        
        sendMessage(sender, "admin.setrole-success", "{player}", target.getName(), "{role}", role.getDisplayName());
    }
    
    private void handleInspect(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.inspect")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin inspect <faction|joueur>");
            return;
        }
        
        // Essayer de trouver une faction
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        
        if (faction != null) {
            inspectFaction(sender, faction);
        } else {
            // Essayer de trouver un joueur
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(target.getUniqueId());
            inspectPlayer(sender, target, fPlayer);
        }
    }
    
    private void inspectFaction(CommandSender sender, Faction faction) {
        sender.sendMessage("§6=== Inspection Faction: §e" + faction.getName() + " §6===");
        sender.sendMessage("§7ID: §f" + faction.getId());
        sender.sendMessage("§7Tag: §f" + faction.getTag());
        sender.sendMessage("§7Leader: §f" + (faction.getLeader() != null ? Bukkit.getOfflinePlayer(faction.getLeader()).getName() : "null"));
        sender.sendMessage("§7Membres: §f" + faction.getMemberCount());
        sender.sendMessage("§7Claims: §f" + faction.getClaimCount());
        sender.sendMessage("§7Power: §f" + String.format("%.1f", plugin.getPowerManager().getFactionPower(faction)));
        sender.sendMessage("§7Power Max: §f" + String.format("%.1f", plugin.getPowerManager().getFactionMaxPower(faction)));
        sender.sendMessage("§7Banque: §f" + faction.getBank() + "$");
        sender.sendMessage("§7Home: §f" + (faction.hasHome() ? locToString(faction.getHome()) : "non défini"));
        sender.sendMessage("§7Warps: §f" + faction.getWarpCount());
        sender.sendMessage("§7Ouverte: §f" + (faction.isOpen() ? "oui" : "non"));
        sender.sendMessage("§7Permanente: §f" + (faction.isPermanent() ? "oui" : "non"));
        sender.sendMessage("§7Créée: §f" + formatTime(faction.getCreatedAt()));
        sender.sendMessage("§7Dernière activité: §f" + formatTime(faction.getLastActivity()));
    }
    
    private void inspectPlayer(CommandSender sender, OfflinePlayer target, FPlayer fPlayer) {
        sender.sendMessage("§6=== Inspection Joueur: §e" + target.getName() + " §6===");
        sender.sendMessage("§7UUID: §f" + target.getUniqueId());
        sender.sendMessage("§7En ligne: §f" + (target.isOnline() ? "oui" : "non"));
        sender.sendMessage("§7Faction: §f" + (fPlayer.hasFaction() ? fPlayer.getFactionId() : "aucune"));
        sender.sendMessage("§7Rôle: §f" + (fPlayer.getRole() != null ? fPlayer.getRole().getDisplayName() : "aucun"));
        sender.sendMessage("§7Power: §f" + String.format("%.1f / %.1f", fPlayer.getPower(), fPlayer.getMaxPower()));
        sender.sendMessage("§7Kills: §f" + fPlayer.getKills());
        sender.sendMessage("§7Deaths: §f" + fPlayer.getDeaths());
        sender.sendMessage("§7Mode bypass: §f" + (fPlayer.isBypassing() ? "oui" : "non"));
        sender.sendMessage("§7Mode spy: §f" + (fPlayer.isSpying() ? "oui" : "non"));
        sender.sendMessage("§7Dernière connexion: §f" + formatTime(fPlayer.getLastSeen()));
    }
    
    private void handleRename(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.rename")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin rename <faction> <nouveau_nom>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        String newName = args[1];
        if (!plugin.getFactionManager().isValidName(newName)) {
            sendMessage(sender, "admin.invalid-name");
            return;
        }
        
        if (!plugin.getFactionManager().isNameAvailable(newName)) {
            sendMessage(sender, "admin.invalid-name");
            return;
        }

        String oldName = faction.getName();
        if (!plugin.getFactionManager().renameFaction(faction, newName)) {
            sendMessage(sender, "admin.invalid-name");
            return;
        }
        
        sendMessage(sender, "admin.rename-success", "{old}", oldName, "{new}", newName);
        notifyStaff("admin.notify.rename", getSenderName(sender), oldName, newName);
    }
    
    private void handleSetTag(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.settag")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin settag <faction> <tag>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        String newTag = args[1];
        plugin.getFactionManager().updateFactionTag(faction, newTag);
        
        sendMessage(sender, "admin.settag-success", "{faction}", faction.getName(), "{tag}", newTag);
    }
    
    private void handleSetBalance(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setbalance")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin setbalance <faction> <montant>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, "admin.invalid-number");
            return;
        }
        
        faction.setBank(amount);
        plugin.getStorageManager().markDirty(faction);
        
        sendMessage(sender, "admin.setbalance-success", "{faction}", faction.getName(), "{amount}", String.format("%.2f", amount));
    }
    
    private void handleLock(CommandSender sender, String[] args, boolean lock) {
        String perm = lock ? "kfaction.admin.lock" : "kfaction.admin.unlock";
        if (!checkPermission(sender, perm)) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin " + (lock ? "lock" : "unlock") + " <faction>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        faction.setPermanent(lock);
        plugin.getStorageManager().markDirty(faction);
        
        String key = lock ? "admin.lock-success" : "admin.unlock-success";
        sendMessage(sender, key, "{faction}", faction.getName());
    }
    
    private void handleTeleport(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.teleport")) return;
        if (!(sender instanceof Player)) {
            sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage("§c/f admin tp <faction> [home|warp <nom>]");
            return;
        }
        
        Player player = (Player) sender;
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        if (args.length >= 2 && args[1].equalsIgnoreCase("warp") && args.length >= 3) {
            org.bukkit.Location warp = faction.getWarp(args[2]);
            if (warp == null) {
                sendMessage(sender, "admin.warp-not-found");
                return;
            }
            player.teleport(warp);
            sendMessage(sender, "admin.tp-warp-success", "{faction}", faction.getName(), "{warp}", args[2]);
        } else {
            if (!faction.hasHome()) {
                sendMessage(sender, "admin.no-home");
                return;
            }
            player.teleport(faction.getHome());
            sendMessage(sender, "admin.tp-home-success", "{faction}", faction.getName());
        }
    }
    
    private void handleAdminClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "general.player-only");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage("§c/kfaction claim <warzone|safezone> [rayon|auto]");
            return;
        }

        Player player = (Player) sender;
        String type = args[0].toLowerCase();

        if (!type.equals("warzone") && !type.equals("safezone")) {
            sender.sendMessage("§c/kfaction claim <warzone|safezone> [rayon|auto]");
            return;
        }

        String perm = type.equals("warzone") ? "kfaction.admin.claim.warzone" : "kfaction.admin.claim.safezone";
        if (!checkPermission(sender, perm)) return;

        if (args.length >= 2) {
            String second = args[1].toLowerCase();

            // Mode auto : toggle
            if (second.equals("auto")) {
                boolean active = plugin.getClaimManager().toggleAdminAutoClaim(player.getUniqueId(), type);
                if (active) {
                    String color = type.equals("warzone") ? "§4" : "§a";
                    player.sendMessage(color + "[Admin-Claim] §7Auto-claim §e" + type
                        + "§7 activé. Marchez pour claim automatiquement.");
                    player.sendMessage("§7Retapez la commande pour désactiver.");
                } else {
                    player.sendMessage("§c[Admin-Claim] §7Auto-claim désactivé.");
                }
                return;
            }

            // Mode rayon
            int radius;
            try {
                radius = Integer.parseInt(second);
                if (radius < 0) radius = 0;
                if (radius > 20) {
                    sender.sendMessage("§cRayon maximum : 20.");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c/kfaction claim <warzone|safezone> [rayon|auto]");
                return;
            }

            FLocation center = new FLocation(player.getLocation());
            int count = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    FLocation loc = new FLocation(center.getWorldName(),
                        center.getX() + dx, center.getZ() + dz);
                    if (type.equals("warzone")) {
                        plugin.getClaimManager().claimWarzone(loc);
                    } else {
                        plugin.getClaimManager().claimSafezone(loc);
                    }
                    count++;
                }
            }
            int side = 2 * radius + 1;
            String color = type.equals("warzone") ? "§4" : "§a";
            sender.sendMessage(color + "[Admin-Claim] §a" + count + " chunks claimés en §e" + type
                + "§a (" + side + "×" + side + " autour de vous).");
            return;
        }

        // Chunk unique
        FLocation loc = new FLocation(player.getLocation());
        if (type.equals("warzone")) {
            plugin.getClaimManager().claimWarzone(loc);
            sendMessage(sender, "admin.claim-warzone-success");
        } else {
            plugin.getClaimManager().claimSafezone(loc);
            sendMessage(sender, "admin.claim-safezone-success");
        }
    }
    
    private void handleAdminUnclaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendMessage(sender, "general.player-only");
            return;
        }
        if (!checkPermission(sender, "kfaction.admin.unclaim.all")) return;

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            executePendingZoneUnclaim(player);
            return;
        }

        // Compatibilité : sans argument, retirer uniquement le claim courant.
        if (args.length == 0) {
            FLocation loc = new FLocation(player.getLocation());
            if (plugin.getClaimManager().unclaim(loc)) {
                sendMessage(sender, "admin.unclaim-success", "{location}", loc.toString());
            } else {
                player.sendMessage("§cCe chunk n'est pas claim.");
            }
            return;
        }

        if (args.length < 2 || !ZoneUnclaimSelection.isZoneType(args[0])) {
            sendZoneUnclaimUsage(player);
            return;
        }

        String type = args[0].toLowerCase();
        String mode = args[1].toLowerCase();
        if (mode.equals("auto")) {
            boolean active = plugin.getClaimManager().toggleAdminAutoUnclaim(player.getUniqueId(), type);
            player.sendMessage(active
                ? "§a[Admin-Unclaim] Mode auto §e" + type + " §aactivé. Marchez dans les chunks à retirer."
                : "§c[Admin-Unclaim] Mode auto désactivé.");
            plugin.getLogger().info("[AUDIT] " + player.getName() + " a "
                + (active ? "activé" : "désactivé") + " l'auto-unclaim " + type);
            return;
        }

        List<FLocation> candidates;
        String description;
        if (mode.equals("radius")) {
            int radius = plugin.getConfigManager().getInt("admin.zone-unclaim.default-radius", 5);
            if (args.length >= 3) {
                try {
                    radius = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    player.sendMessage("§cLe rayon doit être un nombre.");
                    return;
                }
            }
            if (radius < 0 || radius > MAX_ZONE_RADIUS) {
                player.sendMessage("§cLe rayon doit être compris entre 0 et " + MAX_ZONE_RADIUS + ".");
                return;
            }
            candidates = ZoneUnclaimSelection.radius(plugin.getClaimManager().getZoneClaims(type),
                new FLocation(player.getLocation()), radius);
            description = "rayon " + radius;
        } else if (mode.equals("all")) {
            candidates = plugin.getClaimManager().getZoneClaims(type);
            description = "toutes les zones";
        } else {
            sendZoneUnclaimUsage(player);
            return;
        }

        if (candidates.isEmpty()) {
            player.sendMessage("§cAucun chunk " + type + " ne correspond à cette sélection.");
            return;
        }

        pendingZoneUnclaims.put(player.getUniqueId(), new PendingZoneUnclaim(
            type, new ArrayList<>(candidates), description, System.currentTimeMillis()));
        player.sendMessage("§c[CONFIRMATION] §e" + candidates.size() + " chunks " + type
            + " §cseront libérés (" + description + ").");
        player.sendMessage("§7Tapez §e/kfaction unclaim confirm §7dans les 30 secondes.");
    }

    private void handleRemovedProgressionCommand(CommandSender sender) {
        sender.sendMessage("§cCette commande appartenait au modèle aléatoire/XP "
                + "et a été neutralisée pour protéger la progression v2.");
        sender.sendMessage("§7Utilisez §e/kfaction validateprogression §7ou "
                + "§e/kfaction questinfo <faction>§7.");
    }

    private void handleValidateProgression(CommandSender sender) {
        if (!checkPermission(sender, "kfaction.admin.reload")) return;
        List<ValidationIssue> issues =
                plugin.getQuestManager().validateCandidate();
        int errors = 0;
        int warnings = 0;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) errors++;
            else warnings++;
            sender.sendMessage((issue.getSeverity() == ValidationIssue.Severity.ERROR
                    ? "§c[ERREUR] " : "§e[AVERTISSEMENT] ")
                    + "§f" + issue.getPath() + "§7: " + issue.getMessage());
        }
        if (issues.isEmpty()) {
            sender.sendMessage("§aProgression valide: aucune anomalie détectée.");
        } else {
            sender.sendMessage("§6Validation terminée: §c" + errors
                    + " erreur(s)§7, §e" + warnings + " avertissement(s)§7. "
                    + "Le snapshot actif n'a pas été modifié.");
        }
    }

    private void executePendingZoneUnclaim(Player player) {
        PendingZoneUnclaim pending = pendingZoneUnclaims.remove(player.getUniqueId());
        if (pending == null || System.currentTimeMillis() - pending.createdAt > ZONE_CONFIRM_TIMEOUT_MS) {
            player.sendMessage("§cAucune suppression de zone en attente, ou confirmation expirée.");
            return;
        }

        int removed = plugin.getClaimManager().unclaimZone(pending.type, pending.locations);
        String factionId = pending.type.equals("warzone") ? Faction.WARZONE_ID : Faction.SAFEZONE_ID;
        plugin.getLogManager().log(factionId, LogType.TERRITORY_UNCLAIM, player,
            "ADMIN " + pending.description + ": " + removed + " chunks");
        plugin.getLogger().warning("[AUDIT] " + player.getName() + " a supprimé " + removed
            + " chunks " + pending.type + " (" + pending.description + ")");
        player.sendMessage("§a[Admin-Unclaim] §e" + removed + " chunks " + pending.type
            + " §aont été libérés.");
    }

    private void sendZoneUnclaimUsage(Player player) {
        player.sendMessage("§cUsage: /kfaction unclaim <warzone|safezone> <auto|radius|all> [rayon]");
        player.sendMessage("§7Compatibilité: /kfaction unclaim retire le chunk courant.");
    }

    private static final class PendingZoneUnclaim {
        private final String type;
        private final List<FLocation> locations;
        private final String description;
        private final long createdAt;

        private PendingZoneUnclaim(String type, List<FLocation> locations,
                                   String description, long createdAt) {
            this.type = type;
            this.locations = locations;
            this.description = description;
            this.createdAt = createdAt;
        }
    }
    
    private void handleDebug(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.debug")) return;
        if (args.length < 1) {
            sender.sendMessage("§6=== Debug Info ===");
            sender.sendMessage("§7Factions chargées: §f" + plugin.getFactionManager().getAllFactions().size());
            sender.sendMessage("§7Joueurs en cache: §f" + plugin.getFPlayerManager().getAllPlayers().size());
            sender.sendMessage("§7Claims indexés: §f" + plugin.getClaimManager().getClaimCount());
            sender.sendMessage("§7Dirty factions: §f" + plugin.getStorageManager().getDirtyFactionCount());
            sender.sendMessage("§7Dirty players: §f" + plugin.getStorageManager().getDirtyPlayerCount());
            return;
        }
        
        if (args[0].equalsIgnoreCase("save")) {
            long start = System.currentTimeMillis();
            plugin.getStorageManager().saveAllSync();
            sender.sendMessage("§aSauvegarde forcée en " + (System.currentTimeMillis() - start) + "ms");
        } else if (args[0].equalsIgnoreCase("gc")) {
            System.gc();
            sender.sendMessage("§aGC déclenché");
        }
    }
    
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== Kfaction Admin ===");
        sender.sendMessage("§e/f admin bypass §7- Toggle bypass protection");
        sender.sendMessage("§e/f admin reload §7- Recharger la config");
        sender.sendMessage("§e/f admin setpower <joueur> <valeur> §7- Modifier power");
        sender.sendMessage("§e/f admin disband <faction> §7- Dissoudre faction");
        sender.sendMessage("§e/f admin forcejoin <joueur> <faction> §7- Forcer join");
        sender.sendMessage("§e/f admin forceleave <joueur> §7- Forcer leave");
        sender.sendMessage("§e/f admin forceleader <faction> <joueur> §7- Forcer leader");
        sender.sendMessage("§e/f admin setrole <joueur> <role> §7- Modifier rôle");
        sender.sendMessage("§e/f admin inspect <faction|joueur> §7- Inspecter");
        sender.sendMessage("§e/f admin rename <faction> <nouveau> §7- Renommer");
        sender.sendMessage("§e/f admin setbalance <faction> <montant> §7- Set banque");
        sender.sendMessage("§e/f admin tp <faction> [home|warp <nom>] §7- TP");
        sender.sendMessage("§e/f admin claim <warzone|safezone> §7- Claim 1 chunk");
        sender.sendMessage("§e/f admin claim <warzone|safezone> <rayon> §7- Carré (2×rayon+1)²");
        sender.sendMessage("§e/f admin claim <warzone|safezone> auto §7- Auto-claim en marchant");
        sender.sendMessage("§e/f admin debug [save|gc] §7- Debug info");
        sender.sendMessage("§6=== Progression v2 ===");
        sender.sendMessage("§e/f admin validateprogression §7- Valider sans activer");
        sender.sendMessage("§e/f admin questinfo <faction> §7- État brut et transition");
        sender.sendMessage("§7Les anciennes commandes XP/catégorie/reroll sont neutralisées.");
    }
    
    // ============ HELPERS ============
    
    private boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sendMessage(sender, "general.no-permission");
            return false;
        }
        return true;
    }
    
    private String getSenderName(CommandSender sender) {
        return sender instanceof Player ? ((Player) sender).getName() : "Console";
    }
    
    private void notifyStaff(String key, Object... replacements) {
        String message = plugin.getMessageManager().get(key, replacements);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("kfaction.notify.admin")) {
                p.sendMessage(message);
            }
        }
    }
    
    private String locToString(org.bukkit.Location loc) {
        return String.format("%s: %d, %d, %d", loc.getWorld().getName(), 
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    private String formatTime(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }
    
    // ============ LEVEL SYSTEM ADMIN COMMANDS ============
    
    private void handleSetLevel(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin setlevel <faction> <level>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, "admin.invalid-number");
            return;
        }
        
        if (level < 0) {
            sender.sendMessage("§cLe niveau ne peut pas être négatif!");
            return;
        }
        
        faction.setLevel(level);
        plugin.getStorageManager().markDirty(faction);
        sender.sendMessage("§a[Kfaction] §fNiveau de §e" + faction.getName() + "§f mis à §b" + level);
        notifyStaff("admin.notify.setpower", getSenderName(sender), faction.getName(), "level=" + level);
    }
    
    private void handleSetXp(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin setxp <faction> <xp>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        int xp;
        try {
            xp = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, "admin.invalid-number");
            return;
        }
        
        faction.setCurrentXp(Math.max(0, xp));
        plugin.getStorageManager().markDirty(faction);
        sender.sendMessage("§a[Kfaction] §fXP de §e" + faction.getName() + "§f mis à §b" + faction.getCurrentXp() 
            + "§f (niveau " + faction.getLevel() + ")");
    }
    
    private void handleAddXp(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin addxp <faction> <amount>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendMessage(sender, "admin.invalid-number");
            return;
        }
        
        int levelsGained = plugin.getLevelManager().addXp(faction, amount);
        sender.sendMessage("§a[Kfaction] §fAjouté §b" + amount + " XP§f à §e" + faction.getName() 
            + "§f → Niveau §b" + faction.getLevel() + "§f, XP: §b" + faction.getCurrentXp()
            + (levelsGained > 0 ? " §a(+" + levelsGained + " niveaux!)" : ""));
    }
    
    private void handleResetLevel(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin resetlevel <faction>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        faction.setLevel(0);
        faction.setCurrentXp(0);
        faction.clearActiveQuests();
        faction.setActiveCategory(null);
        plugin.getStorageManager().markDirty(faction);
        sender.sendMessage("§a[Kfaction] §fReset complet de §e" + faction.getName() + "§f: niveau 0, XP 0, quêtes effacées");
    }
    
    private void handleSelectCategory(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin selectcategory <faction> <mineur|farmer|chasseur>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        QuestCategory category = QuestCategory.fromConfigKey(args[1]);
        if (category == null) {
            sender.sendMessage("§cCatégorie invalide! Utilisez: mineur, farmer, chasseur");
            return;
        }
        
        plugin.getQuestManager().selectCategory(faction, category);
        plugin.getStorageManager().markDirty(faction);
        sender.sendMessage("§a[Kfaction] §fCatégorie §e" + category.getDisplayName() + "§f sélectionnée pour §e" 
            + faction.getName() + "§f. " + faction.getActiveQuests().size() + " quêtes assignées.");
    }
    
    private void handleResetQuests(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin resetquests <faction>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        QuestCategory category = faction.getActiveCategory();
        if (category == null) {
            sender.sendMessage("§cCette faction n'a pas de catégorie sélectionnée! Utilisez /f admin selectcategory d'abord.");
            return;
        }
        
        faction.clearActiveQuests();
        plugin.getQuestManager().assignRandomQuests(faction, category);
        plugin.getStorageManager().markDirty(faction);
        sender.sendMessage("§a[Kfaction] §f" + faction.getActiveQuests().size() + " nouvelles quêtes assignées à §e" 
            + faction.getName() + "§f (catégorie: " + category.getDisplayName() + ")");
    }
    
    private void handleCompleteQuest(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.setlevel")) return;
        if (args.length < 2) {
            sender.sendMessage("§c/f admin completequest <faction> <questId|all>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        List<FactionQuest> quests = faction.getActiveQuests();
        if (quests.isEmpty()) {
            sender.sendMessage("§cCette faction n'a pas de quêtes actives!");
            return;
        }
        
        if (args[1].equalsIgnoreCase("all")) {
            int completed = 0;
            int xpTotal = 0;
            for (FactionQuest quest : quests) {
                if (!quest.isCompleted()) {
                    quest.setProgress(quest.getRequired());
                    quest.setCompleted(true);
                    xpTotal += quest.getXpReward();
                    completed++;
                }
            }
            if (xpTotal > 0) {
                plugin.getLevelManager().addXp(faction, xpTotal);
            }
            plugin.getStorageManager().markDirty(faction);
            sender.sendMessage("§a[Kfaction] §f" + completed + " quêtes complétées pour §e" + faction.getName() 
                + "§f (+" + xpTotal + " XP). Niveau: §b" + faction.getLevel());
        } else {
            String questId = args[1].toLowerCase();
            FactionQuest target = null;
            for (FactionQuest quest : quests) {
                if (quest.getId().equalsIgnoreCase(questId)) {
                    target = quest;
                    break;
                }
            }
            
            if (target == null) {
                sender.sendMessage("§cQuête '" + questId + "' non trouvée! Quêtes actives:");
                for (FactionQuest q : quests) {
                    sender.sendMessage("  §7- §e" + q.getId() + " §7(" + q.getProgress() + "/" + q.getRequired() 
                        + (q.isCompleted() ? " §a✓" : "") + "§7)");
                }
                return;
            }
            
            if (target.isCompleted()) {
                sender.sendMessage("§cCette quête est déjà complétée!");
                return;
            }
            
            target.setProgress(target.getRequired());
            target.setCompleted(true);
            plugin.getLevelManager().addXp(faction, target.getXpReward());
            plugin.getStorageManager().markDirty(faction);
            sender.sendMessage("§a[Kfaction] §fQuête §e" + target.getDisplayName() + "§f complétée (+" 
                + target.getXpReward() + " XP). Niveau: §b" + faction.getLevel());
        }
    }
    
    private void handleQuestInfo(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "kfaction.admin.inspect")) return;
        if (args.length < 1) {
            sender.sendMessage("§c/f admin questinfo <faction>");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFactionByName(args[0]);
        if (faction == null) {
            sendMessage(sender, "admin.faction-not-found");
            return;
        }
        
        List<QuestProgressView> quests =
                plugin.getQuestManager().getQuestViews(faction);
        sender.sendMessage("§6=== " + faction.getName() + " - Progression v2 ===");
        sender.sendMessage("§eNiveau: §b" + faction.getLevel()
                + " §7| §eTranche verrouillée: §b"
                + faction.getProgressionState().getLockedTierId()
                + " §7(rang " + faction.getProgressionState().getLockedTierRank() + ")");
        sender.sendMessage("§eNiveau d'état: §b"
                + faction.getProgressionState().getLevelStarted()
                + " §7| §eTransition: §f"
                + String.valueOf(faction.getProgressionState().getPendingTransition()));
        sender.sendMessage("§eRécompenses ambiguës: §f"
                + faction.getProgressionState().getPendingRewards());
        sender.sendMessage("§eQuêtes obligatoires (" + quests.size() + "):");
        for (QuestProgressView quest : quests) {
            sender.sendMessage("  §7- §e" + quest.getDefinition().getId()
                    + " §7[" + quest.getDefinition().getType() + "] §f"
                    + quest.getProgress() + "/" + quest.getRequired()
                    + (quest.isCompleted() ? " §a✓" : ""));
        }
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String cmd : SUB_COMMANDS) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            
            switch (subCmd) {
                case "disband":
                case "inspect":
                case "rename":
                case "settag":
                case "setbalance":
                case "lock":
                case "unlock":
                case "teleport":
                case "tp":
                case "setlevel":
                case "setxp":
                case "addxp":
                case "resetlevel":
                case "selectcategory":
                case "resetquests":
                case "completequest":
                case "questinfo":
                    // Compléter avec les noms de faction
                    for (Faction f : plugin.getFactionManager().getPlayerFactions()) {
                        if (f.getName().toLowerCase().startsWith(input)) {
                            completions.add(f.getName());
                        }
                    }
                    break;
                case "setpower":
                case "forceleave":
                case "setrole":
                    // Compléter avec les joueurs en ligne
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(input)) {
                            completions.add(p.getName());
                        }
                    }
                    break;
                case "claim":
                    if ("warzone".startsWith(input)) completions.add("warzone");
                    if ("safezone".startsWith(input)) completions.add("safezone");
                    break;
                case "unclaim":
                    if ("warzone".startsWith(input)) completions.add("warzone");
                    if ("safezone".startsWith(input)) completions.add("safezone");
                    if ("confirm".startsWith(input)) completions.add("confirm");
                    break;
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            String input = args[2].toLowerCase();
            
            switch (subCmd) {
                case "forcejoin":
                case "forceleader":
                    // Compléter avec les noms de faction
                    for (Faction f : plugin.getFactionManager().getPlayerFactions()) {
                        if (f.getName().toLowerCase().startsWith(input)) {
                            completions.add(f.getName());
                        }
                    }
                    break;
                case "setrole":
                    for (FactionRole r : FactionRole.values()) {
                        if (r.name().toLowerCase().startsWith(input)) {
                            completions.add(r.name());
                        }
                    }
                    break;
                case "selectcategory":
                    for (QuestCategory cat : QuestCategory.values()) {
                        if (cat.getConfigKey().startsWith(input)) {
                            completions.add(cat.getConfigKey());
                        }
                    }
                    break;
                case "setlevel":
                    for (int i = 0; i <= 10; i++) {
                        String s = String.valueOf(i);
                        if (s.startsWith(input)) completions.add(s);
                    }
                    break;
                case "completequest":
                    if ("all".startsWith(input)) completions.add("all");
                    // Essayer de trouver la faction et lister ses quêtes
                    if (args.length >= 2) {
                        Faction questFaction = plugin.getFactionManager().getFactionByName(args[1]);
                        if (questFaction != null) {
                            for (FactionQuest q : questFaction.getActiveQuests()) {
                                if (q.getId().toLowerCase().startsWith(input) && !q.isCompleted()) {
                                    completions.add(q.getId());
                                }
                            }
                        }
                    }
                    break;
                case "unclaim":
                    if ("auto".startsWith(input)) completions.add("auto");
                    if ("radius".startsWith(input)) completions.add("radius");
                    if ("all".startsWith(input)) completions.add("all");
                    break;
            }
        }
        
        return completions;
    }
    
    @Override
    public String getPermission() {
        return "kfaction.admin";
    }
    
    @Override
    public boolean isPlayerOnly() {
        return false;
    }
    
    @Override public String getName() { return "admin"; }
    @Override public String getDescription() { return "Commandes administrateur"; }
    @Override public String getUsage() { return "<subcommand>"; }
}
