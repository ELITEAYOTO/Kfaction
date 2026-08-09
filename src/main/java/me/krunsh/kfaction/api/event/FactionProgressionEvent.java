package me.krunsh.kfaction.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.Faction;

/**
 * Event de jalon Progression V2.
 *
 * Les actions fréquentes (chaque bloc/mob) ne produisent PAS d'event Bukkit.
 * Seuls les jalons importants passent ici.
 */
public final class FactionProgressionEvent
        extends Event
        implements Cancellable {

    public enum Phase {
        PRE,
        POST
    }

    public enum Action {
        QUEST_COMPLETED,
        LEVEL_UP,
        REWARD_APPLIED,
        REWARD_PENDING,
        STATE_BLOCKED,
        CONFIG_RELOAD
    }

    private static final HandlerList HANDLERS =
            new HandlerList();

    private final Phase phase;
    private final Action action;
    private final Faction faction;

    private final int oldLevel;
    private final int newLevel;

    private final String identifier;
    private final String detail;

    private final OperationContext context;

    private boolean cancelled;

    public FactionProgressionEvent(
            Phase phase,
            Action action,
            Faction faction,
            int oldLevel,
            int newLevel,
            String identifier,
            String detail,
            OperationContext context
    ) {
        if (phase == null
                || action == null
                || context == null) {
            throw new IllegalArgumentException(
                    "phase/action/context cannot be null"
            );
        }

        this.phase = phase;
        this.action = action;
        this.faction = faction;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.identifier = identifier;
        this.detail = detail;
        this.context = context;
        this.cancelled = false;
    }

    public Phase getPhase() {
        return phase;
    }

    public Action getAction() {
        return action;
    }

    public Faction getFaction() {
        return faction;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDetail() {
        return detail;
    }

    public OperationContext getContext() {
        return context;
    }

    public boolean isPre() {
        return phase == Phase.PRE;
    }

    public boolean isPost() {
        return phase == Phase.POST;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(
            boolean cancelled
    ) {
        /*
         * Seuls les PRE peuvent bloquer une transition.
         */
        if (phase == Phase.PRE) {
            this.cancelled = cancelled;
        }
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
