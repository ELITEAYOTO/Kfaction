package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;

public final class NeutralCommand extends AbstractRelationCommand {

    public NeutralCommand(Kfaction plugin) {
        super(
                plugin,
                "neutral",
                Relation.NEUTRAL,
                FactionCapability.RELATION_NEUTRAL
        );
    }

    @Override public String getName() { return "neutral"; }
    @Override public String getDescription() { return "Devenir neutre avec une faction"; }
    @Override public String getUsage() { return "<faction>"; }
}
