package me.krunsh.kfaction.commands;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import me.krunsh.kfaction.data.FactionRole;

public final class FactionMemberViewTest {

    @Test
    public void filtersStatusAndSortsRoleThenAlphabetically() {
        List<FactionMemberView> members = Arrays.asList(
                member("zoe", FactionRole.MEMBER, true),
                member("alpha", FactionRole.LEADER, true),
                member("beta", FactionRole.MEMBER, true),
                member("offline", FactionRole.COLEADER, false)
        );

        List<FactionMemberView> online =
                FactionMemberView.selectAndSort(members, true);

        Assert.assertEquals(3, online.size());
        Assert.assertEquals("alpha", online.get(0).getName());
        Assert.assertEquals("beta", online.get(1).getName());
        Assert.assertEquals("zoe", online.get(2).getName());

        List<FactionMemberView> offline =
                FactionMemberView.selectAndSort(members, false);

        Assert.assertEquals(1, offline.size());
        Assert.assertEquals("offline", offline.get(0).getName());
    }

    @Test
    public void exposesStableRoleColors() {
        Assert.assertEquals(
                "&c",
                FactionMemberView.roleColor(FactionRole.LEADER)
        );
        Assert.assertEquals(
                "&7",
                FactionMemberView.roleColor(FactionRole.MEMBER)
        );
        Assert.assertEquals(
                "&8",
                FactionMemberView.roleColor(null)
        );
    }

    private static FactionMemberView member(
            String name,
            FactionRole role,
            boolean online
    ) {
        return new FactionMemberView(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                role,
                online
        );
    }
}
