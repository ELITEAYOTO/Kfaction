package me.krunsh.kfaction.tasks;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Tâche de vérification de l'inactivité des leaders
 * Transfère automatiquement le leadership si le leader est inactif
 * Exécutée une fois par heure
 */
public class InactivityTask extends BukkitRunnable {
    
    private final Kfaction plugin;
    private final long leaderInactivityThreshold;  // en millisecondes
    private final long memberInactivityThreshold;  // en millisecondes
    private final boolean disbandIfNoReplacement;
    
    public InactivityTask(Kfaction plugin) {
        this.plugin = plugin;
        
        // Convertir les jours en millisecondes
        int leaderDays = plugin.getConfigManager().getInt("inactivity.leader-days", 14);
        int memberDays = plugin.getConfigManager().getInt("inactivity.member-days", 30);
        this.disbandIfNoReplacement = plugin.getConfigManager().getBoolean("inactivity.disband-if-no-replacement", false);
        
        this.leaderInactivityThreshold = leaderDays * 24L * 60L * 60L * 1000L;
        this.memberInactivityThreshold = memberDays * 24L * 60L * 60L * 1000L;
        
        plugin.getLogger().info("InactivityTask initialisée - Leader: " + leaderDays + " jours, Membre: " + memberDays + " jours");
    }
    
    @Override
    public void run() {
        if (plugin.getConfigManager().getBoolean("debug", false)) {
            plugin.getLogger().info("[InactivityTask] Vérification de l'inactivité des leaders...");
        }
        
        long now = System.currentTimeMillis();
        int transferCount = 0;
        int disbandCount = 0;
        
        for (Faction faction : plugin.getFactionManager().getAllFactions()) {
            // Ignorer les factions système et permanentes
            if (faction.isSystemFaction() || faction.isPermanent()) {
                continue;
            }
            
            UUID leaderId = faction.getLeader();
            if (leaderId == null) {
                continue;
            }
            
            // Vérifier la dernière connexion du leader
            OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderId);
            long lastPlayed = leader.getLastPlayed();
            
            // Si le leader n'a jamais joué ou est inactif
            if (lastPlayed == 0 || (now - lastPlayed) > leaderInactivityThreshold) {
                // Chercher un remplaçant actif
                UUID newLeader = findActiveReplacement(faction, now);
                
                if (newLeader != null) {
                    // Transférer le leadership
                    String oldLeaderName = leader.getName() != null ? leader.getName() : leaderId.toString();
                    OfflinePlayer newLeaderPlayer = Bukkit.getOfflinePlayer(newLeader);
                    String newLeaderName = newLeaderPlayer.getName() != null ? newLeaderPlayer.getName() : newLeader.toString();
                    
                    faction.setLeader(newLeader);
                    plugin.getStorageManager().markDirty(faction);
                    
                    plugin.getLogger().info("[Inactivité] Leadership de " + faction.getName() + 
                        " transféré de " + oldLeaderName + " à " + newLeaderName);
                    transferCount++;
                    
                    // Notifier le nouveau leader s'il est en ligne
                    if (newLeaderPlayer.isOnline() && newLeaderPlayer.getPlayer() != null) {
                        plugin.getMessageManager().send(newLeaderPlayer.getPlayer(), 
                            "inactivity.leadership-transferred",
                            "{faction}", faction.getName());
                    }
                } else if (disbandIfNoReplacement) {
                    // Aucun remplaçant actif, dissoudre la faction
                    plugin.getLogger().info("[Inactivité] Faction " + faction.getName() + 
                        " dissoute pour inactivité totale");
                    plugin.getFactionManager().disbandFaction(faction);
                    disbandCount++;
                }
            }
        }
        
        if (plugin.getConfigManager().getBoolean("debug", false) && (transferCount > 0 || disbandCount > 0)) {
            plugin.getLogger().info("[InactivityTask] Terminé - " + transferCount + " transferts, " + disbandCount + " dissolutions");
        }
    }
    
    /**
     * Trouve un remplaçant actif pour le leadership
     * Priorité: Co-leader > Modérateur > Membre (par activité récente)
     */
    private UUID findActiveReplacement(Faction faction, long now) {
        UUID bestCandidate = null;
        int bestPriority = -1;
        long bestLastPlayed = 0;
        
        for (UUID memberId : faction.getMembers()) {
            // Ne pas considérer le leader actuel
            if (memberId.equals(faction.getLeader())) {
                continue;
            }
            
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            long lastPlayed = member.getLastPlayed();
            
            // Vérifier si le membre est actif (pas au-delà du seuil membre)
            if (lastPlayed == 0 || (now - lastPlayed) > memberInactivityThreshold) {
                continue;  // Membre inactif, ne pas considérer
            }
            
            // Calculer la priorité basée sur le rôle
            FactionRole role = faction.getRole(memberId);
            int priority = role != null ? role.getPriority() : 0;
            
            // Prendre le membre avec la plus haute priorité et la connexion la plus récente
            if (priority > bestPriority || (priority == bestPriority && lastPlayed > bestLastPlayed)) {
                bestCandidate = memberId;
                bestPriority = priority;
                bestLastPlayed = lastPlayed;
            }
        }
        
        return bestCandidate;
    }
    
    /**
     * Démarre la tâche planifiée
     * Exécutée une fois par heure (72000 ticks)
     */
    public void start() {
        // Attendre 5 minutes après le démarrage, puis exécuter toutes les heures
        this.runTaskTimerAsynchronously(plugin, 6000L, 72000L);
        plugin.getLogger().info("InactivityTask démarrée (vérification toutes les heures)");
    }
}
