package me.krunsh.kfaction.storage;

import java.util.Collection;
import java.util.function.Consumer;

import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;

/**
 * Backend de persistance Kfaction.
 *
 * V2 :
 * - lecture -> reconstruit le domaine ;
 * - écriture -> reçoit exclusivement des StorageSnapshot immuables ;
 * - writeSnapshots permet aux backends transactionnels (SQLite) de persister
 *   un batch complet en une seule transaction.
 */
public interface Storage {

    void initialize();

    void shutdown();

    // ============================================================
    // Lecture domaine
    // ============================================================

    void loadFactions(Consumer<Faction> consumer);

    Faction loadFaction(String factionId);

    void loadFPlayers(Consumer<FPlayer> consumer);

    FPlayer loadFPlayer(String uuid);

    /**
     * Payload singleton du moteur Global Zones V2.
     * null = aucune zone persistée.
     */
    String loadGlobalZonesPayload();

    /**
     * Payload singleton de la Grace Period V2.
     * null = aucun état persisté.
     */
    String loadGraceStatePayload();

    // ============================================================
    // Écriture V2 immuable
    // ============================================================

    /**
     * Écrit durablement un snapshot déjà sérialisé.
     */
    boolean writeSnapshot(StorageSnapshot snapshot);

    /**
     * Écrit un batch de snapshots.
     *
     * Le backend FlatFile utilise par défaut plusieurs écritures atomiques.
     * SQLite surcharge cette méthode afin de faire une transaction unique.
     */
    default boolean writeSnapshots(
            Collection<StorageSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return true;
        }

        boolean success = true;

        for (StorageSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }

            if (!writeSnapshot(snapshot)) {
                success = false;
            }
        }

        return success;
    }

    // ============================================================
    // Compatibilité V1
    // ============================================================

    /**
     * @deprecated préférer StorageManager + StorageSnapshot.
     */
    @Deprecated
    void saveFaction(Faction faction);

    /**
     * @deprecated préférer StorageManager.saveFactionNow.
     */
    @Deprecated
    default boolean saveFactionChecked(Faction faction) {
        saveFaction(faction);
        return true;
    }

    void deleteFaction(String factionId);

    /**
     * @deprecated préférer StorageManager + StorageSnapshot.
     */
    @Deprecated
    void saveFPlayer(FPlayer fPlayer);

    void deleteFPlayer(String uuid);

    String getType();

    boolean isConnected();
}
