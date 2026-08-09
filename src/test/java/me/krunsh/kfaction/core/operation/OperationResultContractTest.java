package me.krunsh.kfaction.core.operation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import me.krunsh.kfaction.core.operation.OperationResult.Status;

public class OperationResultContractTest {

    @Test
    public void noChangeIsSuccessfulButNotSuccess() {
        OperationResult<Void> result =
                OperationResult.noChange(
                        "already-done"
                );

        assertFalse(
                result.isSuccess()
        );

        assertTrue(
                result.isSuccessful()
        );

        assertNull(
                result.getValue()
        );
    }

    @Test
    public void failureRejectsSuccessfulStatuses() {
        assertInvalidFailureStatus(
                Status.SUCCESS
        );

        assertInvalidFailureStatus(
                Status.NO_CHANGE
        );
    }

    private static void assertInvalidFailureStatus(
            Status status
    ) {
        try {
            OperationResult.failure(
                    status,
                    "invalid"
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
