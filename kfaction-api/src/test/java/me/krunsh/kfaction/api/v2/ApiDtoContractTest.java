package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Les vues publiques de l'API doivent rester des snapshots immuables.
 */
public class ApiDtoContractTest {

    private static final Class<?>[] DTO_TYPES =
            new Class<?>[] {
                    ChunkView.class,
                    ClaimView.class,
                    ClaimResultView.class,
                    FactionAclView.class,
                    FactionInviteView.class,
                    FactionLogQuery.class,
                    FactionLogView.class,
                    FactionView.class,
                    GraceView.class,
                    MemberView.class,
                    PermissionView.class,
                    PositionView.class,
                    PlayerView.class,
                    ProgressionView.class,
                    QuestView.class,
                    RewardLevelView.class,
                    RelationRequestView.class,
                    RelationView.class,
                    TerritoryView.class,
                    UnclaimResultView.class,
                    WarpView.class,
                    ZoneView.class
            };


    @Test
    public void publicApiEnumsRemainStable() {
        assertArrayEquals(
                new ApiResult.Status[] {
                        ApiResult.Status.SUCCESS,
                        ApiResult.Status.NO_CHANGE,
                        ApiResult.Status.CANCELLED,
                        ApiResult.Status.INVALID_INPUT,
                        ApiResult.Status.NOT_FOUND,
                        ApiResult.Status.FORBIDDEN,
                        ApiResult.Status.CONFLICT,
                        ApiResult.Status.LIMIT_REACHED,
                        ApiResult.Status.UNAVAILABLE,
                        ApiResult.Status.FAILED
                },
                ApiResult.Status.values()
        );

        assertArrayEquals(
                new TerritoryView.Type[] {
                        TerritoryView.Type.WILDERNESS,
                        TerritoryView.Type.FACTION,
                        TerritoryView.Type.SAFEZONE,
                        TerritoryView.Type.WARZONE,
                        TerritoryView.Type.GLOBAL_ZONE
                },
                TerritoryView.Type.values()
        );

        assertArrayEquals(
                new RewardLevelView.State[] {
                        RewardLevelView.State.UNLOCKED,
                        RewardLevelView.State.CURRENT,
                        RewardLevelView.State.LOCKED
                },
                RewardLevelView.State.values()
        );
    }

    @Test
    public void allViewTypesRemainFinal() {
        for (Class<?> type
                : DTO_TYPES) {
            assertTrue(
                    type.getName()
                            + " must remain final",
                    Modifier.isFinal(
                            type.getModifiers()
                    )
            );
        }
    }

    @Test
    public void allInstanceFieldsRemainPrivateFinal() {
        for (Class<?> type
                : DTO_TYPES) {
            for (Field field
                    : type.getDeclaredFields()) {
                if (Modifier.isStatic(
                        field.getModifiers()
                )) {
                    continue;
                }

                assertTrue(
                        type.getName()
                                + "."
                                + field.getName()
                                + " must remain private",
                        Modifier.isPrivate(
                                field.getModifiers()
                        )
                );

                assertTrue(
                        type.getName()
                                + "."
                                + field.getName()
                                + " must remain final",
                        Modifier.isFinal(
                                field.getModifiers()
                        )
                );
            }
        }
    }

    @Test
    public void viewsExposeNoPublicSetter() {
        for (Class<?> type
                : DTO_TYPES) {
            for (Method method
                    : type.getMethods()) {
                assertFalse(
                        type.getName()
                                + " exposes setter "
                                + method.getName(),
                        method.getName()
                                .startsWith("set")
                );
            }
        }
    }
}
