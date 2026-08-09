package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;

public final class TruceCommand extends AbstractRelationCommand {

    public TruceCommand(Kfaction plugin) {
        super(
                plugin,
                "truce",
                Relation.TRUCE,
                FactionCapability.RELATION_TRUCE
        );
    }

    @Override public String getName() { return "truce"; }
    @Override public String getDescription() { return "Demander une trêve"; }
    @Override public String getUsage() { return "<faction>"; }
}
