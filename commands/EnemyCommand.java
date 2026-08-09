package me.krunsh.kfaction.commands;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kfaction.permissions.FactionCapability;

public final class EnemyCommand extends AbstractRelationCommand {

    public EnemyCommand(Kfaction plugin) {
        super(
                plugin,
                "enemy",
                Relation.ENEMY,
                FactionCapability.RELATION_ENEMY
        );
    }

    @Override public String getName() { return "enemy"; }
    @Override public String getDescription() { return "Déclarer une faction ennemie"; }
    @Override public String getUsage() { return "<faction>"; }
}
