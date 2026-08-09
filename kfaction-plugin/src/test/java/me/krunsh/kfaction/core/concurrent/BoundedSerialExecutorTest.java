package me.krunsh.kfaction.core.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class BoundedSerialExecutorTest {

    @Test
    public void rejectsWhenWorkerAndQueueAreFull()
            throws Exception {
        BoundedSerialExecutor executor =
                new BoundedSerialExecutor(
                        "test-bounded-writer",
                        1,
                        true
                );

        CountDownLatch firstStarted =
                new CountDownLatch(1);

        CountDownLatch releaseFirst =
                new CountDownLatch(1);

        AtomicBoolean secondRan =
                new AtomicBoolean(false);

        AtomicBoolean rejectedRan =
                new AtomicBoolean(false);

        try {
            assertTrue(
                    executor.tryExecute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    firstStarted.countDown();

                                    try {
                                        releaseFirst.await(
                                                5,
                                                TimeUnit.SECONDS
                                        );
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread()
                                                .interrupt();
                                    }
                                }
                            }
                    )
            );

            assertTrue(
                    firstStarted.await(
                            2,
                            TimeUnit.SECONDS
                    )
            );

            /*
             * Worker occupé -> cette tâche prend l'unique case de queue.
             */
            assertTrue(
                    executor.tryExecute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    secondRan.set(true);
                                }
                            }
                    )
            );

            /*
             * Worker + queue pleins.
             *
             * La tâche doit être REFUSÉE, jamais exécutée en CallerRuns.
             */
            assertFalse(
                    executor.tryExecute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    rejectedRan.set(true);
                                }
                            }
                    )
            );

            assertEquals(
                    1L,
                    executor.getRejectedTasks()
            );

            releaseFirst.countDown();

            executor.shutdown();

            assertTrue(
                    executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    )
            );

            assertTrue(
                    secondRan.get()
            );

            assertFalse(
                    rejectedRan.get()
            );

        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void exposesBoundedQueueCapacity() {
        BoundedSerialExecutor executor =
                new BoundedSerialExecutor(
                        "test-capacity",
                        7,
                        true
                );

        try {
            assertEquals(
                    7,
                    executor.getQueueCapacity()
            );

            assertEquals(
                    0,
                    executor.getQueueSize()
            );

        } finally {
            executor.shutdownNow();
        }
    }
}
