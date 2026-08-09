package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Test;

import me.krunsh.kfaction.core.operation.OperationContext;
import me.krunsh.kfaction.data.FLocation;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.permissions.TerritoryAction;
import me.krunsh.kfaction.zones.GlobalZoneType;

/**
 * Freeze exact de la surface publique Kfaction API 2.2.
 *
 * Toute modification volontaire de cette interface doit:
 * 1. être traitée comme une décision de compatibilité;
 * 2. mettre à jour API_VERSION;
 * 3. mettre à jour ce contrat.
 */
public class KfactionApiV2ContractTest {

    @Test
    public void apiVersionIsFrozenAt220() {
        assertEquals(
                "2.2.0",
                KfactionApiV2.API_VERSION
        );

        assertEquals(
                2,
                KfactionApiV2.API_MAJOR
        );
    }

    @Test
    public void exactPublicSurfaceRemainsFrozen() {
        Set<String> actual =
                new TreeSet<String>();

        for (Method method
                : KfactionApiV2.class
                        .getDeclaredMethods()) {
            actual.add(
                    signature(method)
            );
        }

        Set<String> expected =
                new TreeSet<String>(
                        Arrays.asList(
                                "boolean canPvp(org.bukkit.entity.Player,org.bukkit.entity.Player)",
                                "int getApiMajor()",
                                "int getDefaultMaxMembers()",
                                "int getDefaultMaxWarps()",
                                "java.lang.Boolean getRelationPermission(java.lang.String,java.lang.String,java.lang.String)",
                                "java.lang.Boolean getRolePermission(java.lang.String,java.lang.String,java.lang.String)",
                                "java.lang.String getApiVersion()",
                                "java.lang.String getRelation(java.lang.String,java.lang.String)",
                                "java.util.List getFactions()",
                                "java.util.List getGlobalZones()",
                                "java.util.List getProgressionQuests(java.lang.String)",
                                "java.util.List getRewardLevels(java.lang.String)",
                                "me.krunsh.kfaction.api.v2.ApiResult claimFill(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.data.FLocation,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult claimRadius(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.data.FLocation,int,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult claimSingle(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.data.FLocation,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult clearGlobalZoneById(me.krunsh.kfaction.data.FLocation,java.lang.String,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult clearGlobalZone(me.krunsh.kfaction.data.FLocation,me.krunsh.kfaction.zones.GlobalZoneType,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult disbandFaction(java.lang.String,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult joinMember(java.lang.String,java.util.UUID,me.krunsh.kfaction.data.FactionRole,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult reloadProgression(me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult removeMember(java.lang.String,java.util.UUID,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult setGlobalZoneById(me.krunsh.kfaction.data.FLocation,java.lang.String,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult setGlobalZone(me.krunsh.kfaction.data.FLocation,me.krunsh.kfaction.zones.GlobalZoneType,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult setRole(java.lang.String,java.util.UUID,me.krunsh.kfaction.data.FactionRole,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult transferLeadership(java.lang.String,java.util.UUID,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult unclaimAll(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult unclaimRadius(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.data.FLocation,int,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.ApiResult unclaimSingle(org.bukkit.entity.Player,java.lang.String,me.krunsh.kfaction.data.FLocation,me.krunsh.kfaction.core.operation.OperationContext)",
                                "me.krunsh.kfaction.api.v2.FactionView findFaction(java.lang.String)",
                                "me.krunsh.kfaction.api.v2.FactionView getFaction(java.lang.String)",
                                "me.krunsh.kfaction.api.v2.FactionView getPlayerFaction(java.util.UUID)",
                                "me.krunsh.kfaction.api.v2.GraceView getGrace()",
                                "me.krunsh.kfaction.api.v2.ZoneView getGlobalZoneAt(me.krunsh.kfaction.data.FLocation)",
                                "me.krunsh.kfaction.api.v2.PermissionView checkTerritory(org.bukkit.entity.Player,org.bukkit.Location,me.krunsh.kfaction.permissions.TerritoryAction)",
                                "me.krunsh.kfaction.api.v2.PlayerView getPlayer(java.util.UUID)",
                                "me.krunsh.kfaction.api.v2.ProgressionView getProgression(java.lang.String)",
                                "me.krunsh.kfaction.api.v2.TerritoryView getTerritory(me.krunsh.kfaction.data.FLocation,java.util.UUID)"
                        )
                );

        assertEquals(
                expected,
                actual
        );
    }


    @Test
    public void dynamicZoneUpgradeRemainsAdditive() throws Exception {
        assertEquals(
                ZoneView.class,
                KfactionApiV2.class
                        .getMethod(
                                "getGlobalZoneAt",
                                FLocation.class
                        )
                        .getReturnType()
        );

        assertEquals(
                java.util.List.class,
                KfactionApiV2.class
                        .getMethod(
                                "getGlobalZones"
                        )
                        .getReturnType()
        );

        assertEquals(
                ApiResult.class,
                KfactionApiV2.class
                        .getMethod(
                                "setGlobalZoneById",
                                FLocation.class,
                                String.class,
                                OperationContext.class
                        )
                        .getReturnType()
        );

        assertEquals(
                ApiResult.class,
                KfactionApiV2.class
                        .getMethod(
                                "clearGlobalZoneById",
                                FLocation.class,
                                String.class,
                                OperationContext.class
                        )
                        .getReturnType()
        );

        /*
         * Les overloads historiques SAFEZONE/WARZONE restent disponibles.
         */
        assertEquals(
                ApiResult.class,
                KfactionApiV2.class
                        .getMethod(
                                "setGlobalZone",
                                FLocation.class,
                                GlobalZoneType.class,
                                OperationContext.class
                        )
                        .getReturnType()
        );
    }

    @Test
    public void interfaceDoesNotExposeLiveInternalManagersOrDomainObjects() {
        for (Method method
                : KfactionApiV2.class
                        .getDeclaredMethods()) {
            assertSafeType(
                    method.getReturnType()
            );

            for (Class<?> parameter
                    : method.getParameterTypes()) {
                assertSafeType(
                        parameter
                );
            }
        }
    }

    private static String signature(
            Method method
    ) {
        StringBuilder value =
                new StringBuilder();

        value.append(
                method.getReturnType()
                        .getName()
        );

        value.append(' ');
        value.append(
                method.getName()
        );
        value.append('(');

        Class<?>[] parameters =
                method.getParameterTypes();

        for (int index = 0;
                index < parameters.length;
                index++) {
            if (index > 0) {
                value.append(',');
            }

            value.append(
                    parameters[index]
                            .getName()
            );
        }

        value.append(')');

        return value.toString();
    }

    private static void assertSafeType(
            Class<?> type
    ) {
        if (type == null
                || type.isPrimitive()) {
            return;
        }

        String name =
                type.getName();

        assertFalse(
                "Manager exposed by API: "
                        + name,
                name.startsWith(
                        "me.krunsh.kfaction.managers."
                )
        );

        assertFalse(
                "Service exposed by API: "
                        + name,
                name.startsWith(
                        "me.krunsh.kfaction.services."
                )
        );

        assertFalse(
                "Live Faction exposed by API",
                "me.krunsh.kfaction.data.Faction"
                        .equals(name)
        );

        assertFalse(
                "Live FPlayer exposed by API",
                "me.krunsh.kfaction.data.FPlayer"
                        .equals(name)
        );
    }
}
