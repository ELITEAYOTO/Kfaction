package me.krunsh.kfaction.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.utils.KfactionLogger;

/**
 * Gestionnaire canonique des relations entre factions.
 *
 * Lot25D:
 * - config V2 réellement utilisée;
 * - limites ALLY/TRUCE vérifiées sur les deux factions;
 * - demandes typées (ALLY != TRUCE);
 * - expiration configurable et persistée proprement;
 * - relation ALLY/TRUCE toujours symétrique, même sans validation mutuelle;
 * - ENEMY volontairement unilatéral;
 * - notifications alignées sur messages.yml.
 */
public class RelationManager {

    private final Kfaction plugin;

    private int maxAllies;
    private int maxEnemies;
    private int maxTruces;

    private boolean allyEnabled;
    private boolean enemyEnabled;
    private boolean truceEnabled;

    private boolean requireMutualAlliance;
    private boolean requireMutualTruce;

    private long requestExpirationMs;

    public RelationManager(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfig();

        KfactionLogger.debug(
                plugin,
                "RelationManager: allies="
                        + maxAllies
                        + ", truces="
                        + maxTruces
                        + ", enemies="
                        + maxEnemies
                        + ", requestExpirationMs="
                        + requestExpirationMs
        );
    }

    public void loadConfig() {
        allyEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "relations.ally.enabled",
                                true
                        );

        maxAllies =
                plugin.getConfigManager()
                        .getInt(
                                "relations.ally.max-per-faction",
                                3
                        );

        requireMutualAlliance =
                plugin.getConfigManager()
                        .getBoolean(
                                "relations.ally.require-mutual",
                                true
                        );

        truceEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "relations.truce.enabled",
                                true
                        );

        maxTruces =
                plugin.getConfigManager()
                        .getInt(
                                "relations.truce.max-per-faction",
                                5
                        );

        requireMutualTruce =
                plugin.getConfigManager()
                        .getBoolean(
                                "relations.truce.require-mutual",
                                true
                        );

        enemyEnabled =
                plugin.getConfigManager()
                        .getBoolean(
                                "relations.enemy.enabled",
                                true
                        );

        maxEnemies =
                plugin.getConfigManager()
                        .getInt(
                                "relations.enemy.max-per-faction",
                                10
                        );

        requestExpirationMs =
                Math.max(
                        1L,
                        plugin.getConfigManager()
                                .getLong(
                                        "relations.request-expiration-seconds",
                                        300L
                                )
                ) * 1000L;
    }

    // ============================================================
    // Results
    // ============================================================

    public enum RelationResult {
        SUCCESS("Relation établie"),
        REQUEST_SENT("Demande envoyée"),
        REQUEST_PENDING("Une demande est déjà en attente entre ces factions"),
        ALREADY_SET("Cette relation existe déjà"),
        LIMIT_REACHED("Limite de relations atteinte"),
        DISABLED("Ce type de relation est désactivé"),
        CANNOT_SELF("Vous ne pouvez pas établir de relation avec votre propre faction"),
        NO_PERMISSION("Vous n'avez pas la permission"),
        NOT_FOUND("Faction non trouvée"),
        UNAVAILABLE("Cette mutation doit être exécutée sur le thread principal");

        private final String message;

        RelationResult(
                String message
        ) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return this == SUCCESS
                    || this == REQUEST_SENT;
        }
    }

    // ============================================================
    // Public mutations
    // ============================================================

    public RelationResult requestAlly(
            Faction requesting,
            Faction target
    ) {
        return requestRelation(
                requesting,
                target,
                Relation.ALLY
        );
    }

    public RelationResult requestTruce(
            Faction requesting,
            Faction target
    ) {
        return requestRelation(
                requesting,
                target,
                Relation.TRUCE
        );
    }

    /**
     * ENEMY reste volontairement unilatéral.
     *
     * PermissionService.resolveEffectiveRelation() traite ENEMY comme effectif
     * si l'un OU l'autre côté l'a déclaré.
     */
    public RelationResult declareEnemy(
            Faction requesting,
            Faction target
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return RelationResult.UNAVAILABLE;
        }

        RelationResult basic =
                validatePair(
                        requesting,
                        target
                );

        if (basic != null) {
            return basic;
        }

        if (!enemyEnabled) {
            return RelationResult.DISABLED;
        }

        if (requesting.getRelationTo(
                target
        ) == Relation.ENEMY) {
            return RelationResult.ALREADY_SET;
        }

        if (atLimit(
                requesting,
                Relation.ENEMY
        )) {
            return RelationResult.LIMIT_REACHED;
        }

        clearPendingBetween(
                requesting,
                target
        );

        requesting.setRelation(
                target.getId(),
                Relation.ENEMY
        );

        plugin.getStorageManager()
                .markDirty(
                        requesting
                );

        notifyEnemy(
                requesting,
                target
        );

        return RelationResult.SUCCESS;
    }

    /**
     * Revenir à NEUTRAL retire la relation dans les DEUX sens.
     *
     * C'est nécessaire même si ENEMY était auparavant unilatéral.
     */
    public RelationResult setNeutral(
            Faction requesting,
            Faction target
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return RelationResult.UNAVAILABLE;
        }

        RelationResult basic =
                validatePair(
                        requesting,
                        target
                );

        if (basic != null) {
            return basic;
        }

        Relation requestingView =
                requesting.getRelationTo(
                        target
                );

        Relation targetView =
                target.getRelationTo(
                        requesting
                );

        if (requestingView == Relation.NEUTRAL
                && targetView == Relation.NEUTRAL
                && !hasPendingBetween(
                        requesting,
                        target
                )) {
            return RelationResult.ALREADY_SET;
        }

        clearPendingBetween(
                requesting,
                target
        );

        requesting.setRelation(
                target.getId(),
                Relation.NEUTRAL
        );

        target.setRelation(
                requesting.getId(),
                Relation.NEUTRAL
        );

        plugin.getStorageManager()
                .markDirty(
                        requesting
                );

        plugin.getStorageManager()
                .markDirty(
                        target
                );

        notifyNeutral(
                requesting,
                target
        );

        return RelationResult.SUCCESS;
    }

    // ============================================================
    // Mutual relations
    // ============================================================

    private RelationResult requestRelation(
            Faction requesting,
            Faction target,
            Relation relation
    ) {
        if (!Bukkit.isPrimaryThread()) {
            return RelationResult.UNAVAILABLE;
        }

        RelationResult basic =
                validatePair(
                        requesting,
                        target
                );

        if (basic != null) {
            return basic;
        }

        if (!isEnabled(
                relation
        )) {
            return RelationResult.DISABLED;
        }

        pruneExpired(
                requesting
        );

        pruneExpired(
                target
        );

        if (requesting.getRelationTo(target)
                == relation
                && target.getRelationTo(requesting)
                        == relation) {
            return RelationResult.ALREADY_SET;
        }

        boolean requireMutual =
                relation == Relation.ALLY
                        ? requireMutualAlliance
                        : requireMutualTruce;

        /*
         * Une relation mutuelle consomme une place chez les DEUX factions.
         * Le contrôle est répété au moment de l'acceptation.
         */
        if (atLimit(
                requesting,
                relation
        )
                || atLimit(
                        target,
                        relation
                )) {
            return RelationResult.LIMIT_REACHED;
        }

        if (!requireMutual) {
            clearPendingBetween(
                    requesting,
                    target
            );

            establishMutual(
                    requesting,
                    target,
                    relation
            );

            notifyEstablished(
                    requesting,
                    target,
                    relation
            );

            return RelationResult.SUCCESS;
        }

        /*
         * Le target est l'ancien demandeur si sa demande typée correspond
         * exactement à la relation demandée maintenant.
         */
        if (target.hasRelationRequest(
                requesting.getId(),
                relation
        )) {
            clearPendingBetween(
                    requesting,
                    target
            );

            establishMutual(
                    requesting,
                    target,
                    relation
            );

            notifyAccepted(
                    target,
                    requesting,
                    relation
            );

            return RelationResult.SUCCESS;
        }

        /*
         * Une autre proposition entre les mêmes factions doit être résolue
         * avant d'en créer une nouvelle. Cela évite les états ambigus.
         */
        if (requesting.hasAnyRelationRequestForFaction(
                target.getId()
        )
                || target.hasAnyRelationRequestForFaction(
                        requesting.getId()
                )) {
            return RelationResult.REQUEST_PENDING;
        }

        requesting.addRelationRequest(
                target.getId(),
                relation
        );

        plugin.getStorageManager()
                .markDirty(
                        requesting
                );

        notifyRequestReceived(
                requesting,
                target,
                relation
        );

        return RelationResult.REQUEST_SENT;
    }

    private void establishMutual(
            Faction first,
            Faction second,
            Relation relation
    ) {
        first.setRelation(
                second.getId(),
                relation
        );

        second.setRelation(
                first.getId(),
                relation
        );

        plugin.getStorageManager()
                .markDirty(
                        first
                );

        plugin.getStorageManager()
                .markDirty(
                        second
                );
    }

    // ============================================================
    // Pending requests
    // ============================================================

    private void pruneExpired(
            Faction faction
    ) {
        if (faction != null
                && faction.pruneExpiredRelationRequests(
                        requestExpirationMs
                )) {
            plugin.getStorageManager()
                    .markDirty(
                            faction
                    );
        }
    }

    private boolean hasPendingBetween(
            Faction first,
            Faction second
    ) {
        return first != null
                && second != null
                && (first.hasAnyRelationRequestForFaction(
                        second.getId()
                )
                || second.hasAnyRelationRequestForFaction(
                        first.getId()
                ));
    }

    private void clearPendingBetween(
            Faction first,
            Faction second
    ) {
        if (first == null
                || second == null) {
            return;
        }

        if (first.removeRelationRequestsForFaction(
                second.getId()
        )) {
            plugin.getStorageManager()
                    .markDirty(
                            first
                    );
        }

        if (second.removeRelationRequestsForFaction(
                first.getId()
        )) {
            plugin.getStorageManager()
                    .markDirty(
                            second
                    );
        }
    }

    // ============================================================
    // Limits / config
    // ============================================================

    private RelationResult validatePair(
            Faction first,
            Faction second
    ) {
        if (first == null
                || second == null
                || first.isSystemFaction()
                || second.isSystemFaction()) {
            return RelationResult.NOT_FOUND;
        }

        if (first.getId()
                .equals(
                        second.getId()
                )) {
            return RelationResult.CANNOT_SELF;
        }

        return null;
    }

    private boolean isEnabled(
            Relation relation
    ) {
        if (relation == Relation.ALLY) {
            return allyEnabled;
        }

        if (relation == Relation.TRUCE) {
            return truceEnabled;
        }

        if (relation == Relation.ENEMY) {
            return enemyEnabled;
        }

        return true;
    }

    private boolean atLimit(
            Faction faction,
            Relation relation
    ) {
        int maximum =
                maxFor(
                        relation
                );

        if (maximum < 0) {
            return false;
        }

        int current;

        if (relation == Relation.ALLY) {
            current =
                    faction.getAllies()
                            .size();

        } else if (relation == Relation.TRUCE) {
            current =
                    faction.getTruces()
                            .size();

        } else if (relation == Relation.ENEMY) {
            current =
                    faction.getEnemies()
                            .size();

        } else {
            return false;
        }

        return current >= maximum;
    }

    private int maxFor(
            Relation relation
    ) {
        if (relation == Relation.ALLY) {
            return maxAllies;
        }

        if (relation == Relation.TRUCE) {
            return maxTruces;
        }

        if (relation == Relation.ENEMY) {
            return maxEnemies;
        }

        return -1;
    }

    // ============================================================
    // Notifications
    // ============================================================

    /**
     * Lors d'une demande, l'acteur reçoit déjà la confirmation de commande.
     * Seule la faction cible doit donc recevoir le broadcast métier.
     */
    private void notifyRequestReceived(
            Faction requesting,
            Faction target,
            Relation relation
    ) {
        String key =
                relation == Relation.ALLY
                        ? "relation.ally-request-received"
                        : "relation.truce-request-received";

        target.broadcast(
                plugin.getMessageManager()
                        .get(
                                key,
                                "{faction}",
                                requesting.getName()
                        )
        );
    }

    private void notifyAccepted(
            Faction originalRequester,
            Faction accepter,
            Relation relation
    ) {
        String acceptedKey =
                relation == Relation.ALLY
                        ? "relation.ally-accepted"
                        : "relation.truce-accepted";

        String establishedKey =
                relation == Relation.ALLY
                        ? "relation.ally-established"
                        : "relation.truce-established";

        originalRequester.broadcast(
                plugin.getMessageManager()
                        .get(
                                acceptedKey,
                                "{faction}",
                                accepter.getName()
                        )
        );

        accepter.broadcast(
                plugin.getMessageManager()
                        .get(
                                establishedKey,
                                "{faction}",
                                originalRequester.getName()
                        )
        );
    }

    private void notifyEstablished(
            Faction first,
            Faction second,
            Relation relation
    ) {
        String key =
                relation == Relation.ALLY
                        ? "relation.ally-established"
                        : "relation.truce-established";

        first.broadcast(
                plugin.getMessageManager()
                        .get(
                                key,
                                "{faction}",
                                second.getName()
                        )
        );

        second.broadcast(
                plugin.getMessageManager()
                        .get(
                                key,
                                "{faction}",
                                first.getName()
                        )
        );
    }

    private void notifyEnemy(
            Faction requesting,
            Faction target
    ) {
        requesting.broadcast(
                plugin.getMessageManager()
                        .get(
                                "relation.enemy-declared",
                                "{faction}",
                                target.getName()
                        )
        );

        target.broadcast(
                plugin.getMessageManager()
                        .get(
                                "relation.enemy-received",
                                "{faction}",
                                requesting.getName()
                        )
        );
    }

    private void notifyNeutral(
            Faction requesting,
            Faction target
    ) {
        requesting.broadcast(
                plugin.getMessageManager()
                        .get(
                                "relation.neutral-set",
                                "{faction}",
                                target.getName()
                        )
        );

        target.broadcast(
                plugin.getMessageManager()
                        .get(
                                "relation.neutral-received",
                                "{faction}",
                                requesting.getName()
                        )
        );
    }

    // ============================================================
    // Read helpers
    // ============================================================

    public Relation getRelation(
            Player player1,
            Player player2
    ) {
        Faction faction1 =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player1
                        );

        Faction faction2 =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player2
                        );

        if (faction1 == null
                || faction2 == null) {
            return Relation.NEUTRAL;
        }

        Relation first =
                faction1.getRelationTo(
                        faction2
                );

        Relation second =
                faction2.getRelationTo(
                        faction1
                );

        if (first == Relation.ENEMY
                || second == Relation.ENEMY) {
            return Relation.ENEMY;
        }

        if (first != Relation.NEUTRAL) {
            return first;
        }

        return second != null
                ? second
                : Relation.NEUTRAL;
    }

    public boolean areAllies(
            Player player1,
            Player player2
    ) {
        Relation relation =
                getRelation(
                        player1,
                        player2
                );

        return relation == Relation.ALLY
                || relation == Relation.MEMBER;
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
        Faction faction1 =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player1
                        );

        Faction faction2 =
                plugin.getFactionManager()
                        .getPlayerFaction(
                                player2
                        );

        return faction1 != null
                && faction2 != null
                && faction1.getId()
                        .equals(
                                faction2.getId()
                        );
    }

    public int getMaxAllies() {
        return maxAllies;
    }

    public int getMaxEnemies() {
        return maxEnemies;
    }

    public int getMaxTruces() {
        return maxTruces;
    }

    public long getRequestExpirationMs() {
        return requestExpirationMs;
    }

    /**
     * Compatibilité binaire V1.
     *
     * Le code V2 ne doit plus utiliser cette mutation brute.
     */
    @Deprecated
    public void setEnemy(
            Faction faction1,
            Faction faction2
    ) {
        if (!Bukkit.isPrimaryThread()
                || faction1 == null
                || faction2 == null
                || faction1.getId()
                        .equals(
                                faction2.getId()
                        )) {
            return;
        }

        faction1.setRelation(
                faction2.getId(),
                Relation.ENEMY
        );

        faction2.setRelation(
                faction1.getId(),
                Relation.ENEMY
        );

        plugin.getStorageManager()
                .markDirty(
                        faction1
                );

        plugin.getStorageManager()
                .markDirty(
                        faction2
                );

        KfactionLogger.debug(
                plugin,
                "RelationManager#setEnemy legacy utilisé pour "
                        + faction1.getId()
                        + " <-> "
                        + faction2.getId()
        );
    }
}
