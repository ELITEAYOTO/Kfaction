package me.krunsh.kfaction.commands;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FactionLog.LogType;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeCommand extends SubCommand {
    
    private static final int TELEPORT_DELAY = 5; // seconds
    private final Map<UUID, BukkitRunnable> pendingTeleports = new HashMap<>();
    
    public HomeCommand(Kfaction plugin) { super(plugin); }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = getPlayer(sender);
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        if (!fPlayer.hasFaction()) {
            sendMessage(sender, "home.not-in-faction");
            return;
        }
        
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            sendMessage(sender, "general.error");
            return;
        }
        
        Location home = faction.getHome();
        if (home == null) {
            sendMessage(sender, "home.not-set");
            return;
        }
        
        // Cancel existing teleport
        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            pendingTeleports.get(uuid).cancel();
            pendingTeleports.remove(uuid);
        }
        
        // Start warmup teleport
        sendMessage(sender, "home.teleporting", "{seconds}", String.valueOf(TELEPORT_DELAY));
        
        final Location startLocation = player.getLocation().clone();
        
        BukkitRunnable teleportTask = new BukkitRunnable() {
            int countdown = TELEPORT_DELAY;
            
            @Override
            public void run() {
                // Check if player moved
                if (player.getLocation().distanceSquared(startLocation) > 0.5) {
                    sendMessage(sender, "home.cancelled-move");
                    pendingTeleports.remove(uuid);
                    cancel();
                    return;
                }
                
                // Check if player is still online
                if (!player.isOnline()) {
                    pendingTeleports.remove(uuid);
                    cancel();
                    return;
                }
                
                countdown--;
                
                if (countdown <= 0) {
                    // Perform teleport
                    player.teleport(home);
                    plugin.getLogManager().log(faction.getId(), LogType.TP_HOME, player, null,
                        String.format("[%d, %d, %d]", home.getBlockX(), home.getBlockY(), home.getBlockZ()));
                    sendMessage(sender, "home.teleported");
                    pendingTeleports.remove(uuid);
                    cancel();
                }
            }
        };
        
        pendingTeleports.put(uuid, teleportTask);
        teleportTask.runTaskTimer(plugin, 20L, 20L); // Run every second
    }
    
    @Override public String getName() { return "home"; }
    @Override public String getDescription() { return "Se téléporter au home de faction"; }
    @Override public String getUsage() { return ""; }
}
