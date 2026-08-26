package me.krunsh.kfaction.placeholders;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.MemberView;

public final class KfactionExpansionTest {

    @Test
    public void resolvesOnlineSlotsByRoleThenName() {
        FactionView faction = faction(Arrays.asList(
                member("zoe", "MEMBER", true),
                member("alpha", "LEADER", true),
                member("beta", "MEMBER", true),
                member("offline", "COLEADER", false)
        ));

        Assert.assertEquals(
                "&cLeader &8» &falpha",
                resolve(faction, "online_member_1")
        );
        Assert.assertEquals(
                "beta",
                resolve(faction, "online_member_2_name")
        );
        Assert.assertEquals(
                "Membre",
                resolve(faction, "online_member_3_role")
        );
        Assert.assertEquals("", resolve(faction, "online_member_4"));
    }

    @Test
    public void distinguishesUnsupportedAndInvalidPlaceholders() {
        FactionView faction = faction(Arrays.asList(
                member("alpha", "LEADER", true)
        ));

        Assert.assertNull(resolve(faction, "faction_name"));
        Assert.assertEquals("", resolve(faction, "online_member_0"));
        Assert.assertEquals("", resolve(faction, "online_member_nope"));
        Assert.assertEquals("", resolve(null, "online_member_1"));
    }

    private static String resolve(FactionView faction, String key) {
        return KfactionExpansion.resolveOnlineMemberPlaceholder(faction, key);
    }

    private static MemberView member(
            String name,
            String role,
            boolean online
    ) {
        return new MemberView(
                UUID.nameUUIDFromBytes(name.getBytes()),
                name,
                role,
                online
        );
    }

    private static FactionView faction(List<MemberView> members) {
        return new FactionView(
                "volkaria",
                "Volkaria",
                "VOLK",
                "Test",
                members.get(0).getUuid(),
                members,
                0,
                0,
                0,
                0.0D,
                0.0D,
                0L,
                0,
                false,
                false,
                0L,
                0L
        );
    }
}
