package me.krunsh.kfaction.api.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ApiResultHardeningTest {

    @Test
    public void noChangeRemainsSuccessfulButNotSuccess() {
        ApiResult<Void> result =
                new ApiResult<Void>(
                        ApiResult.Status.NO_CHANGE,
                        null,
                        "already-done",
                        null
                );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.isSuccessful()
        );
    }

    @Test
    public void failureRejectsSuccessfulStatuses() {
        assertInvalid(
                ApiResult.Status.SUCCESS
        );

        assertInvalid(
                ApiResult.Status.NO_CHANGE
        );
    }

    private static void assertInvalid(
            ApiResult.Status status
    ) {
        try {
            ApiResult.failure(
                    status,
                    "invalid",
                    null
            );

            fail(
                    "Expected IllegalArgumentException for "
                            + status
            );

        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
