package me.krunsh.kfaction.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.grace.GraceState;

/**
 * Event de cycle de vie de la Grace Period V2.
 *
 * PRE est annulable pour START/STOP/EXTEND.
 * EXPIRE est une conséquence temporelle et n'émet qu'un POST.
 */
public final class GracePeriodEvent
        extends Event
        implements Cancellable {

    public enum Phase {
        PRE,
        POST
    }

    public enum Action {
        START,
        STOP,
        EXTEND,
        EXPIRE
    }

    private static final HandlerList HANDLERS =
            new HandlerList();

    private final Phase phase;
    private final Action action;
    private final GraceState previousState;
    private final GraceState nextState;
    private final OperationContext context;

    private boolean cancelled;

    public GracePeriodEvent(
            Phase phase,
            Action action,
            GraceState previousState,
            GraceState nextState,
            OperationContext context
    ) {
        if (phase == null
                || action == null
                || previousState == null
                || nextState == null
                || context == null) {
            throw new IllegalArgumentException(
                    "GracePeriodEvent parameters cannot be null"
            );
        }

        this.phase = phase;
        this.action = action;
        this.previousState = previousState;
        this.nextState = nextState;
        this.context = context;
        this.cancelled = false;
    }

    public Phase getPhase() {
        return phase;
    }

    public Action getAction() {
        return action;
    }

    public GraceState getPreviousState() {
        return previousState;
    }

    public GraceState getNextState() {
        return nextState;
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
    public void setCancelled(boolean cancelled) {
        if (phase == Phase.PRE
                && action != Action.EXPIRE) {
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
