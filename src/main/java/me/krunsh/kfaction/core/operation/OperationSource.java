package me.krunsh.kfaction.core.operation;

/**
 * Origine d'une opération métier Kfaction.
 *
 * Le core ne dépend volontairement pas de Bukkit ici : cette valeur peut donc
 * être utilisée par les commandes, l'API, Kgui, les tâches internes et plus
 * tard par le système d'audit.
 */
public enum OperationSource {

    /**
     * Action déclenchée depuis une commande joueur.
     */
    COMMAND,

    /**
     * Action déclenchée depuis une interface graphique.
     */
    GUI,

    /**
     * Action déclenchée par l'API publique Kfaction.
     */
    API,

    /**
     * Action administrative / staff.
     */
    ADMIN,

    /**
     * Action provenant d'un hook ou d'une intégration externe.
     */
    INTEGRATION,

    /**
     * Action interne automatique de Kfaction.
     */
    SYSTEM,

    /**
     * Action déclenchée par une tâche planifiée.
     */
    TASK
}
