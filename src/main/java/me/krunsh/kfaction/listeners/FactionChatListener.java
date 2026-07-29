package me.krunsh.kfaction.listeners;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.FPlayer.ChatMode;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;

/**
 * Listener du chat faction
 * Intercepte les messages en mode FACTION/ALLY/TRUCE et les gère localement
 * Priorité LOWEST pour intercepter AVANT Kchat
 */
public class FactionChatListener implements Listener {
    
    private final Kfaction plugin;
    
    // Formats cachés
    private String factionFormat;
    private String allyFormat;
    private String truceFormat;
    private String spyFormat;
    
    public FactionChatListener(Kfaction plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Charge les formats depuis la config. À appeler après le chargement de la config.
     */
    public void loadConfig() {
        factionFormat = plugin.getConfigManager().getString("faction-chat.faction.format", 
            "&a[{faction}] {role}{player}: &f{message}");
        allyFormat = plugin.getConfigManager().getString("faction-chat.ally.format", 
            "&5[Ally][{faction}] {player}: &f{message}");
        truceFormat = plugin.getConfigManager().getString("faction-chat.truce.format", 
            "&e[Truce][{faction}] {player}: &f{message}");
        spyFormat = plugin.getConfigManager().getString("faction-chat.spy.format", 
            "&8[SPY] &7{original}");
    }
    
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
        
        // Si pas dans une faction ou mode PUBLIC, laisser passer à Kchat
        if (!fPlayer.hasFaction() || fPlayer.getChatMode() == ChatMode.PUBLIC) {
            return;
        }
        
        // Annuler l'event pour que Kchat ne le gère pas
        event.setCancelled(true);
        
        // Traiter sur le thread principal pour les appels Bukkit
        final String message = event.getMessage();
        final ChatMode chatMode = fPlayer.getChatMode();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            processPrivateChat(player, fPlayer, message, chatMode);
        });
    }
    
    /**
     * Traite un message de chat privé (faction/ally/truce)
     */
    private void processPrivateChat(Player sender, FPlayer fPlayer, String message, ChatMode chatMode) {
        Faction faction = plugin.getFactionManager().getFaction(fPlayer.getFactionId());
        if (faction == null) {
            plugin.getMessageManager().send(sender, "chat.not-in-faction");
            return;
        }
        
        // Récupérer les destinataires selon le mode
        Set<Player> recipients = getRecipients(faction, chatMode);
        
        // Formater le message
        String formattedMessage = formatMessage(sender, fPlayer, faction, message, chatMode);
        
        // Envoyer le message aux destinataires
        for (Player recipient : recipients) {
            recipient.sendMessage(formattedMessage);
        }
        
        // Logger en console
        String consoleFormat = "[Faction-" + chatMode.name() + "] " + faction.getName() + " | " 
                + sender.getName() + ": " + message;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', consoleFormat));
        
        // Envoyer aux admins qui spy cette faction
        sendToSpies(faction, formattedMessage, chatMode);
    }
    
    /**
     * Récupère les destinataires selon le mode de chat
     */
    private Set<Player> getRecipients(Faction faction, ChatMode chatMode) {
        Set<Player> recipients = new HashSet<>();
        
        switch (chatMode) {
            case FACTION:
                // Membres de la faction seulement
                for (FPlayer member : plugin.getFPlayerManager().getPlayersInFaction(faction.getId())) {
                    Player player = member.getPlayer();
                    if (player != null && player.isOnline()) {
                        recipients.add(player);
                    }
                }
                break;
                
            case ALLY:
                // Membres de la faction + alliés confirmés
                recipients.addAll(getFactionOnlinePlayers(faction));
                
                for (String allyId : faction.getAllies()) {
                    Faction allyFaction = plugin.getFactionManager().getFaction(allyId);
                    if (allyFaction != null) {
                        // Vérifier que l'alliance est mutuelle
                        if (allyFaction.getAllies().contains(faction.getId())) {
                            recipients.addAll(getFactionOnlinePlayers(allyFaction));
                        }
                    }
                }
                break;
                
            case TRUCE:
                // Membres de la faction + trêves confirmées
                recipients.addAll(getFactionOnlinePlayers(faction));
                
                for (String truceId : faction.getTruces()) {
                    Faction truceFaction = plugin.getFactionManager().getFaction(truceId);
                    if (truceFaction != null) {
                        // Vérifier que la trêve est mutuelle
                        if (truceFaction.getTruces().contains(faction.getId())) {
                            recipients.addAll(getFactionOnlinePlayers(truceFaction));
                        }
                    }
                }
                break;
                
            default:
                break;
        }
        
        return recipients;
    }
    
    /**
     * Récupère les joueurs en ligne d'une faction
     */
    private Set<Player> getFactionOnlinePlayers(Faction faction) {
        Set<Player> players = new HashSet<>();
        for (FPlayer member : plugin.getFPlayerManager().getPlayersInFaction(faction.getId())) {
            Player player = member.getPlayer();
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }
    
    /**
     * Formate le message selon le mode de chat
     */
    private String formatMessage(Player sender, FPlayer fPlayer, Faction faction, String message, ChatMode chatMode) {
        String format;
        
        switch (chatMode) {
            case FACTION:
                format = factionFormat;
                break;
            case ALLY:
                format = allyFormat;
                break;
            case TRUCE:
                format = truceFormat;
                break;
            default:
                format = "{player}: {message}";
        }
        
        // Remplacer les placeholders
        FactionRole role = fPlayer.getRole();
        String rolePrefix = (role != null) ? role.getPrefix() : "";
        
        format = format.replace("{faction}", faction.getName())
                       .replace("{faction_tag}", faction.getTag() != null ? faction.getTag() : faction.getName())
                       .replace("{role}", rolePrefix)
                       .replace("{role_name}", role != null ? role.getDisplayName() : "")
                       .replace("{player}", sender.getName())
                       .replace("{message}", message);
        
        return ChatColor.translateAlternateColorCodes('&', format);
    }
    
    /**
     * Envoie le message aux admins qui spy cette faction
     */
    private void sendToSpies(Faction faction, String originalMessage, ChatMode chatMode) {
        String spyMessage = ChatColor.translateAlternateColorCodes('&', 
            spyFormat.replace("{original}", ChatColor.stripColor(originalMessage))
                     .replace("{faction}", faction.getName())
                     .replace("{mode}", chatMode.name()));
        
        // Parcourir tous les joueurs en ligne avec permission spy
        for (Player player : Bukkit.getOnlinePlayers()) {
            FPlayer fPlayer = plugin.getFPlayerManager().getFPlayer(player);
            
            // Vérifier si le joueur spy cette faction spécifique
            if (fPlayer.isSpyingFaction(faction.getId())) {
                // Ne pas envoyer aux membres de la faction (ils l'ont déjà reçu)
                if (!faction.getId().equals(fPlayer.getFactionId())) {
                    player.sendMessage(spyMessage);
                }
            }
        }
    }
}
