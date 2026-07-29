package me.krunsh.kfaction.storage;

import java.util.function.Consumer;

import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Interface de stockage abstrait
 * Permet d'implémenter différents backends (FlatFile, MySQL, etc.)
 */
public interface Storage {
    
    /**
     * Initialise le stockage (connexions, création de fichiers/tables, etc.)
     */
    void initialize();
    
    /**
     * Ferme le stockage proprement
     */
    void shutdown();
    
    // === Factions ===
    
    /**
     * Charge toutes les factions et les passe au consumer
     * @param consumer Consumer qui reçoit chaque faction chargée
     */
    void loadFactions(Consumer<Faction> consumer);
    
    /**
     * Charge une faction spécifique
     * @param factionId ID de la faction
     * @return La faction ou null si non trouvée
     */
    Faction loadFaction(String factionId);
    
    /**
     * Sauvegarde une faction
     * @param faction La faction à sauvegarder
     */
    void saveFaction(Faction faction);

    /**
     * Sauvegarde une faction et confirme que la nouvelle version est durable.
     *
     * Les anciens backends restent compatibles, mais un backend utilisé par une
     * transition transactionnelle doit surcharger cette méthode et ne retourner
     * true qu'après le remplacement durable de l'enregistrement.
     */
    default boolean saveFactionChecked(Faction faction) {
        saveFaction(faction);
        return true;
    }
    
    /**
     * Supprime une faction
     * @param factionId ID de la faction
     */
    void deleteFaction(String factionId);
    
    // === FPlayers ===
    
    /**
     * Charge tous les FPlayers et les passe au consumer
     * @param consumer Consumer qui reçoit chaque FPlayer chargé
     */
    void loadFPlayers(Consumer<FPlayer> consumer);
    
    /**
     * Charge un FPlayer spécifique
     * @param uuid UUID du joueur (en string)
     * @return Le FPlayer ou null si non trouvé
     */
    FPlayer loadFPlayer(String uuid);
    
    /**
     * Sauvegarde un FPlayer
     * @param fPlayer Le FPlayer à sauvegarder
     */
    void saveFPlayer(FPlayer fPlayer);
    
    /**
     * Supprime un FPlayer
     * @param uuid UUID du joueur (en string)
     */
    void deleteFPlayer(String uuid);
    
    // === Utilitaires ===
    
    /**
     * @return Le nom du type de stockage
     */
    String getType();
    
    /**
     * Vérifie si le stockage est fonctionnel
     * @return true si opérationnel
     */
    boolean isConnected();
}
