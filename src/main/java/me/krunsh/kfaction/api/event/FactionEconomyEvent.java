package me.krunsh.kfaction.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.economy.MoneyAmount;

/**
 * Event Economy V2.
 *
 * PRE est cancellable.
 * POST représente une transaction réellement commitée.
 */
public final class FactionEconomyEvent
        extends Event
        implements Cancellable {

    public enum Phase {
        PRE,
        POST
    }

    public enum Type {
        DEPOSIT_TO_FACTION,
        WITHDRAW_FROM_FACTION,
        FACTION_TRANSFER
    }

    private static final HandlerList HANDLERS =
            new HandlerList();

    private final Phase phase;
    private final Type type;
    private final Player player;
    private final Faction faction;
    private final Faction otherFaction;
    private final MoneyAmount amount;
    private final long factionBalanceBeforeMinor;
    private final long factionBalanceAfterMinor;
    private final OperationContext context;

    private boolean cancelled;

    public FactionEconomyEvent(
            Phase phase,
            Type type,
            Player player,
            Faction faction,
            Faction otherFaction,
            MoneyAmount amount,
            long factionBalanceBeforeMinor,
            long factionBalanceAfterMinor,
            OperationContext context
    ) {
        if (phase == null
                || type == null
                || faction == null
                || amount == null
                || context == null) {
            throw new IllegalArgumentException(
                    "FactionEconomyEvent required fields cannot be null"
            );
        }

        this.phase = phase;
        this.type = type;
        this.player = player;
        this.faction = faction;
        this.otherFaction = otherFaction;
        this.amount = amount;
        this.factionBalanceBeforeMinor =
                factionBalanceBeforeMinor;
        this.factionBalanceAfterMinor =
                factionBalanceAfterMinor;
        this.context = context;
        this.cancelled = false;
    }

    public Phase getPhase() {
        return phase;
    }

    public Type getType() {
        return type;
    }

    public Player getPlayer() {
        return player;
    }

    public Faction getFaction() {
        return faction;
    }

    public Faction getOtherFaction() {
        return otherFaction;
    }

    public MoneyAmount getAmount() {
        return amount;
    }

    public long getFactionBalanceBeforeMinor() {
        return factionBalanceBeforeMinor;
    }

    public long getFactionBalanceAfterMinor() {
        return factionBalanceAfterMinor;
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
