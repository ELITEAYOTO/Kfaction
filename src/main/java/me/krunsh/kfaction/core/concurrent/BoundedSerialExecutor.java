package me.krunsh.kfaction.core.concurrent;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exécuteur série à queue bornée.
 *
 * Contrairement à Executors.newSingleThreadExecutor(), la queue n'est jamais
 * illimitée et aucune RejectedExecutionException ne fuite vers l'appelant.
 *
 * IMPORTANT:
 * aucune policy CallerRuns n'est utilisée : un rejet ne doit jamais transformer
 * une écriture disque en I/O sur le thread Bukkit principal.
 */
public final class BoundedSerialExecutor {

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;

    private final AtomicLong acceptedTasks =
            new AtomicLong();

    private final AtomicLong rejectedTasks =
            new AtomicLong();

    private final AtomicLong completedTasks =
            new AtomicLong();

    private final AtomicLong failedTasks =
            new AtomicLong();

    public BoundedSerialExecutor(
            String threadName,
            int queueCapacity,
            boolean daemon
    ) {
        if (threadName == null
                || threadName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "threadName cannot be empty"
            );
        }

        if (queueCapacity < 1) {
            throw new IllegalArgumentException(
                    "queueCapacity must be >= 1"
            );
        }

        this.queueCapacity =
                queueCapacity;

        ThreadFactory factory =
                new NamedThreadFactory(
                        threadName.trim(),
                        daemon
                );

        this.executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(
                                queueCapacity
                        ),
                        factory,
                        new ThreadPoolExecutor.AbortPolicy()
                );

        this.executor.prestartCoreThread();
    }

    /**
     * Soumet sans jamais exécuter la tâche sur le thread appelant.
     *
     * @return false si shutdown ou queue saturée.
     */
    public boolean tryExecute(
            Runnable task
    ) {
        if (task == null
                || executor.isShutdown()) {
            rejectedTasks.incrementAndGet();
            return false;
        }

        try {
            executor.execute(
                    wrap(task)
            );

            acceptedTasks.incrementAndGet();
            return true;

        } catch (RejectedExecutionException exception) {
            rejectedTasks.incrementAndGet();
            return false;
        }
    }

    /**
     * Variante Callable.
     *
     * @return Future accepté ou null si saturation/shutdown.
     */
    public <T> Future<T> trySubmit(
            final Callable<T> callable
    ) {
        if (callable == null
                || executor.isShutdown()) {
            rejectedTasks.incrementAndGet();
            return null;
        }

        FutureTask<T> future =
                trackedFuture(
                        callable
                );

        try {
            executor.execute(future);
            acceptedTasks.incrementAndGet();
            return future;

        } catch (RejectedExecutionException exception) {
            rejectedTasks.incrementAndGet();
            return null;
        }
    }

    /**
     * Soumission bornée avec attente de place dans la queue.
     *
     * Réservée aux points déjà synchrones/durables (shutdown par exemple).
     * La tâche reste exécutée par le worker, jamais par l'appelant.
     */
    public <T> Future<T> submitWithTimeout(
            final Callable<T> callable,
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        if (callable == null
                || unit == null
                || timeout < 0L
                || executor.isShutdown()) {
            rejectedTasks.incrementAndGet();
            return null;
        }

        FutureTask<T> future =
                trackedFuture(
                        callable
                );

        boolean accepted =
                executor.getQueue()
                        .offer(
                                future,
                                timeout,
                                unit
                        );

        if (!accepted) {
            rejectedTasks.incrementAndGet();
            return null;
        }

        acceptedTasks.incrementAndGet();
        return future;
    }

    private <T> FutureTask<T> trackedFuture(
            final Callable<T> callable
    ) {
        return new FutureTask<T>(
                new Callable<T>() {
                    @Override
                    public T call()
                            throws Exception {
                        try {
                            T result =
                                    callable.call();

                            completedTasks
                                    .incrementAndGet();

                            return result;

                        } catch (Throwable throwable) {
                            failedTasks.incrementAndGet();

                            if (throwable
                                    instanceof Exception) {
                                throw (Exception) throwable;
                            }

                            throw new RuntimeException(
                                    throwable
                            );
                        }
                    }
                }
        );
    }

    private Runnable wrap(
            final Runnable task
    ) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                    completedTasks.incrementAndGet();

                } catch (RuntimeException exception) {
                    failedTasks.incrementAndGet();
                    throw exception;

                } catch (Error error) {
                    failedTasks.incrementAndGet();
                    throw error;
                }
            }
        };
    }

    public void shutdown() {
        executor.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    public boolean awaitTermination(
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        return executor.awaitTermination(
                timeout,
                unit
        );
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public int getQueueSize() {
        return executor.getQueue()
                .size();
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getAcceptedTasks() {
        return acceptedTasks.get();
    }

    public long getRejectedTasks() {
        return rejectedTasks.get();
    }

    public long getCompletedTasks() {
        return completedTasks.get();
    }

    public long getFailedTasks() {
        return failedTasks.get();
    }

    private static final class NamedThreadFactory
            implements ThreadFactory {

        private final String threadName;
        private final boolean daemon;

        private NamedThreadFactory(
                String threadName,
                boolean daemon
        ) {
            this.threadName = threadName;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(
                Runnable runnable
        ) {
            Thread thread =
                    new Thread(
                            runnable,
                            threadName
                    );

            thread.setDaemon(daemon);
            thread.setPriority(
                    Thread.NORM_PRIORITY
            );

            return thread;
        }
    }
}
