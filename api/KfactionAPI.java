package me.krunsh.kfaction.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.KfactionApiProvider;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FPlayer;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;

/**
 * API historique Kfaction.
 *
 * Compatibilité binaire conservée pour les plugins V1.
 *
 * Nouveau code:
 * utiliser KfactionApiV2 via ServicesManager ou v2().
 */
public class KfactionAPI {

    private final Kfaction plugin;
    private final KfactionApiV2 v2;

    public KfactionAPI(
            Kfaction plugin
    ) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin cannot be null"
            );
        }

        this.plugin = plugin;

        this.v2 =
                new KfactionApiProvider(
                        plugin
                );

        registerServices();
    }

    /**
     * Accès direct depuis l'ancienne API.
     */
    public KfactionApiV2 v2() {
        return v2;
    }

    /**
     * Alias explicite pour les intégrations JavaBeans/Kotlin.
     */
    public KfactionApiV2 getV2() {
        return v2;
    }

    private void registerServices() {
        ServicesManager services =
                Bukkit.getServicesManager();

        /*
         * L'ancienne documentation annonçait déjà une registration
         * ServicesManager, mais le baseline ne l'effectuait pas réellement.
         */
        services.register(
                KfactionAPI.class,
                this,
                plugin,
                ServicePriority.Normal
        );

        services.register(
                KfactionApiV2.class,
                v2,
                plugin,
                ServicePriority.Normal
        );

        plugin.getLogger().info(
                "Kfaction API V2 enregistrée: "
                        + KfactionApiV2.API_VERSION
        );
    }

    // ============================================================
    // LEGACY FACTIONS
    // ============================================================

    /**
     * @deprecated Retourne le domaine mutable live.
     * Utiliser KfactionApiV2#getFaction.
     */
    @Deprecated
    public Faction getFaction(
            String factionId
    ) {
        return plugin.getFactionManager()
                .getFaction(
                        factionId
                );
    }

    /**
     * @deprecated Retourne le domaine mutable live.
     */
    @Deprecated
    public Faction getFactionByTag(
            String tag
    ) {
        Faction faction =
                plugin.getFactionManager()
                        .getFactionByTag(tag);

        if (faction == null) {
            faction =
                    plugin.getFactionManager()
                            .getFactionByName(tag);
        }

        return faction;
    }

    /**
     * @deprecated Retourne le domaine mutable live.
     */
    @Deprecated
    public Faction getPlayerFaction(
            Player player
    ) {
        return player != null
                ? plugin.getFactionManager()
                        .getPlayerFaction(player)
                : null;
    }

    /**
     * @deprecated Retourne le domaine mutable live.
     */
    @Deprecated
    public Faction getPlayerFaction(
            UUID uuid
    ) {
        return uuid != null
                ? plugin.getFactionManager()
                        .getPlayerFaction(uuid)
                : null;
    }

    public boolean hasFaction(
            Player player
    ) {
        return player != null
                && plugin.getFactionManager()
                        .getPlayerFaction(player)
                        != null;
    }

    /**
     * @deprecated Liste d'objets Faction mutables.
     */
    @Deprecated
    public List<Faction> getAllFactions() {
        return new ArrayList<Faction>(
                plugin.getFactionManager()
                        .getPlayerFactions()
        );
    }

    // ============================================================
    // LEGACY FPLAYER
    // ============================================================

    /**
     * Lecture V1 corrigée:
     * ne crée plus de profil implicitement.
     *
     * @deprecated Utiliser KfactionApiV2#getPlayer.
     */
    @Deprecated
    public FPlayer getFPlayer(
            Player player
    ) {
        return player != null
                ? plugin.getFPlayerManager()
                        .find(
                                player.getUniqueId()
                        )
                : null;
    }

    /**
     * @deprecated Utiliser KfactionApiV2#getPlayer.
     */
    @Deprecated
    public FPlayer getFPlayer(
            UUID uuid
    ) {
        return uuid != null
                ? plugin.getFPlayerManager()
                        .find(uuid)
                : null;
    }

    // ============================================================
    // LEGACY POWER
    // ============================================================

    public double getPlayerPower(
            Player player
    ) {
        FPlayer fPlayer =
                getFPlayer(player);

        return fPlayer != null
                ? fPlayer.getPower()
                : 0.0D;
    }

    public double getPlayerMaxPower(
            Player player
    ) {
        if (player == null) {
            return 0.0D;
        }

        return plugin.getPowerManager()
                .getPlayerMaxPower(
                        player.getUniqueId()
                );
    }

    public double getFactionPower(
            Faction faction
    ) {
        return faction != null
                ? faction.getPower()
                : 0.0D;
    }

    /**
     * @deprecated Mutation directe legacy.
     * Conservée uniquement pour compatibilité.
     */
    @Deprecated
    public void setPlayerPower(
            UUID uuid,
            double power
    ) {
        if (uuid == null) {
            return;
        }

        plugin.getPowerManager()
                .setPlayerPower(
                        uuid,
                        power
                );
    }

    // ============================================================
    // LEGACY CLAIMS
    // ============================================================

    /**
     * @deprecated Retourne le domaine mutable live.
     */
    @Deprecated
    public Faction getFactionAt(
            Location location
    ) {
        if (location == null) {
            return null;
        }

        return plugin.getClaimManager()
                .getFactionAt(
                        new FLocation(
                                location
                        )
                );
    }

    public boolean isClaimed(
            Location location
    ) {
        Faction faction =
                getFactionAt(location);

        return faction != null
                && !faction.isWilderness();
    }

    public boolean isSafezone(
            Location location
    ) {
        Faction faction =
                getFactionAt(location);

        return faction != null
                && faction.isSafezone();
    }

    public boolean isWarzone(
            Location location
    ) {
        Faction faction =
                getFactionAt(location);

        return faction != null
                && faction.isWarzone();
    }

    public int getFactionClaimCount(
            Faction faction
    ) {
        return faction != null
                ? faction.getClaimCount()
                : 0;
    }

    // ============================================================
    // LEGACY RELATIONS
    // ============================================================

    public Relation getRelation(
            Player player1,
            Player player2
    ) {
        if (player1 == null
                || player2 == null) {
            return Relation.NEUTRAL;
        }

        Faction first =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player1
                        );

        Faction second =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player2
                        );

        if (first == null
                || second == null) {
            return Relation.NEUTRAL;
        }

        return first.getRelationTo(
                second
        );
    }

    public Relation getRelation(
            Faction faction1,
            Faction faction2
    ) {
        if (faction1 == null
                || faction2 == null) {
            return Relation.NEUTRAL;
        }

        return faction1.getRelationTo(
                faction2
        );
    }

    public boolean areAllies(
            Player player1,
            Player player2
    ) {
        return getRelation(
                player1,
                player2
        ) == Relation.ALLY;
    }

    public boolean areEnemies(
            Player player1,
            Player player2
    ) {
        return getRelation(
                player1,
                player2
        ) == Relation.ENEMY;
    }

    public boolean areSameFaction(
            Player player1,
            Player player2
    ) {
        return getRelation(
                player1,
                player2
        ) == Relation.MEMBER;
    }

    // ============================================================
    // LEGACY TERRITORY
    // ============================================================

    public boolean canBuild(
            Player player,
            Location location
    ) {
        return plugin.getTerritoryManager()
                .canPerformAction(
                        player,
                        location,
                        me.krunsh.kfaction.data.PermissionAction.BUILD
                );
    }

    public boolean canBreak(
            Player player,
            Location location
    ) {
        return plugin.getTerritoryManager()
                .canPerformAction(
                        player,
                        location,
                        me.krunsh.kfaction.data.PermissionAction.DESTROY
                );
    }

    public boolean canInteract(
            Player player,
            Location location
    ) {
        return plugin.getTerritoryManager()
                .canPerformAction(
                        player,
                        location,
                        me.krunsh.kfaction.data.PermissionAction.SWITCH
                );
    }

    // ============================================================
    // LEGACY RAID
    // ============================================================

    public boolean isRaidable(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return false;
        }

        return plugin.getClaimManager()
                .isRaidable(faction);
    }

    public int getSurclaimableChunks(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0;
        }

        return plugin.getClaimManager()
                .getOverclaimableCount(
                        faction
                );
    }

    // ============================================================
    // LEGACY UTILS
    // ============================================================

    public boolean isBypassing(
            Player player
    ) {
        FPlayer fPlayer =
                getFPlayer(player);

        return fPlayer != null
                && fPlayer.isBypassing();
    }
}
