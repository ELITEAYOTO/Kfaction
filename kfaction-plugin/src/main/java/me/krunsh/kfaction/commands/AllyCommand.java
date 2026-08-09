package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;

public final class AllyCommand extends AbstractRelationCommand {

    public AllyCommand(Kfaction plugin) {
        super(
                plugin,
                "ally",
                Relation.ALLY,
                FactionCapability.RELATION_ALLY
        );
    }

    @Override public String getName() { return "ally"; }
    @Override public String getDescription() { return "Demander une alliance"; }
    @Override public String getUsage() { return "<faction>"; }
}
