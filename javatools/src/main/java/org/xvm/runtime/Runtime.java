package org.xvm.runtime;

import java.time.Duration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ConstantPool;

import org.xvm.util.Deadline;

import org.xvm.util.concurrent.ConcurrentLinkedBlockingQueue;

/**
 * The runtime.
 */
public class Runtime
        implements AutoCloseable {
    public Runtime() {
        int parallelism = Integer.parseInt(System.getProperty("xvm.parallelism", "0"));
        if (parallelism <= 0) {
            parallelism = java.lang.Runtime.getRuntime().availableProcessors();
        }

        ThreadFactory factoryXVM = r -> {
            Thread thread = new Thread(serviceThreads, r);
            thread.setDaemon(true);
            thread.setName("XvmWorker@" + thread.hashCode());
            return thread;
        };

        // TODO: replace with a fair scheduling based ExecutorService
        f_executorXVM = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.SECONDS,
                new ConcurrentLinkedBlockingQueue<>(), factoryXVM);

        ThreadFactory factoryIO = Thread.ofVirtual()
                .name("IOWorker@", 0)
                .factory();
        f_executorIO = Executors.newThreadPerTaskExecutor(factoryIO);
        timer = new Timer("ecstasy:LocalClock", true);
    }

    public void start() {
    }

    /**
     * Register a newly constructed container for discovery and debugging. This registration is
     * weak: inactive containers without external references or cleanup obligations may be collected.
     * Resource acquisition must additionally call {@link #retainResourceOwner} before allocation.
     *
     * @param container  the newly constructed container
     */
    public void registerContainer(Container container) {
        synchronized (f_containers) {
            if (closing) {
                throw new IllegalStateException("Runtime is closing");
            }
            f_containers.putIfAbsent(container, null);
        }
    }

    /**
     * @return a snapshot of live containers
     */
    public Set<Container> containers() {
        synchronized (f_containers) {
            return new HashSet<>(f_containers.keySet());
        }
    }

    /**
     * Retain a container before reserving a native resource. The caller must hold the container
     * monitor, add its resource registration before releasing that monitor, and eventually call
     * {@link #releaseContainer} when its last cleanup obligation finishes successfully.
     *
     * <p>The registry monitor serializes reservation with runtime shutdown. A reservation either
     * precedes shutdown, whose container snapshot will include this owner, or fails before the
     * resource factory is called. This method never calls back into the container while locked.
     * Retention affects reachability only; it does not count idle resources as application activity.
     *
     * @param container  the resource owner
     *
     * @throws IllegalStateException if runtime shutdown has begun
     */
    void retainResourceOwner(Container container) {
        synchronized (f_containers) {
            if (closing) {
                throw new IllegalStateException("Runtime is closing");
            }
            retainedContainers.add(container);
        }
    }

    /**
     * Retain a container until its termination completes successfully, including asynchronous
     * cleanup of descendants. Unlike acquisition, termination is allowed during runtime shutdown.
     * Call under the container monitor before publishing its termination future; release only
     * after successful completion of all shutdown work. Failure preserves retention for reporting.
     *
     * @param container  the terminating container
     */
    void retainTerminatingContainer(Container container) {
        synchronized (f_containers) {
            retainedContainers.add(container);
        }
    }

    /**
     * Release strong retention after the container's last obligation succeeds. Call under its
     * monitor, only when no resources, pending shutdown work or cleanup failure remain. The weak
     * discovery registration stays intact, and ordinary references may still keep the container
     * alive. Never release merely because application execution has become idle.
     *
     * @param container  the container whose cleanup obligations have ended
     */
    void releaseContainer(Container container) {
        synchronized (f_containers) {
            retainedContainers.remove(container);
        }
    }

    /**
     * Obtain a snapshot of containers with cleanup obligations, for lifecycle diagnostics.
     * Callers should discard the snapshot after inspection: it also strongly references the owners.
     *
     * @return the containers retained for acquisition, resources, termination or cleanup failures
     */
    Set<Container> retainedContainers() {
        synchronized (f_containers) {
            return Set.copyOf(retainedContainers);
        }
    }

    /**
     * @return a container that uses the specified ConstantPool; null if not found
     */
    public Container findContainer(ConstantPool pool) {
        for (Container container : containers()) {
            if (container.getConstantPool() == pool) {
                return container;
            }
        }
        return null;
    }

    /**
     * Submit ServiceContext work for eventual processing by the runtime.
     *
     * @param task the task to process
     */
    protected void submitService(Runnable task) {
        f_executorXVM.submit(task);
        m_lastXvmSubmitNanos = System.nanoTime();
    }

    /**
     * Submit IO work for eventual processing by the runtime.
     *
     * @param task the task to process
     */
    protected void submitIO(Runnable task) {
        f_executorIO.submit(task);
    }

    /**
     * Schedule an alarm on this runtime's timer.
     *
     * @param task         the alarm to schedule
     * @param delayMillis  the delay in milliseconds
     */
    void scheduleTimer(TimerTask task, long delayMillis) {
        timer.schedule(task, delayMillis);
    }

    /**
     * Remove cancelled tasks after an owner's cancellation hooks finish. Purge once per owner
     * termination instead of scanning the shared queue for each cancelled alarm.
     *
     * @return the number of removed tasks
     */
    int purgeCancelledTimers() {
        return timer.purge();
    }

    /**
     * @return a unique id
     */
    public long makeUniqueId() {
        return f_idProducer.getAndIncrement();
    }

    public void shutdownXVM() {
        f_executorIO .shutdown();
        f_executorXVM.shutdown();
    }

    /**
     * Stop the runtime's containers, timers, and executors. Shutdown is bounded; a failure to
     * terminate is reported to the owner instead of leaving an apparently reusable runtime.
     */
    @Override
    public void close() {
        close(DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Stop the runtime within one shared time budget.
     *
     * @param timeout  the nonnegative shutdown budget
     */
    public synchronized void close(Duration timeout) {
        Deadline deadline = Deadline.after(timeout);
        synchronized (f_containers) {
            closing = true;
        }
        timer.cancel();

        if (termination == null) {
            termination = CompletableFuture.allOf(containers().stream()
                    .map(Container::terminateServices)
                    .toArray(CompletableFuture[]::new));
            // Services need their queued shutdown work even after a caller's budget expires.
            // Dropping that work would leave their termination futures permanently incomplete.
            termination.whenComplete((_, failure) -> f_executorXVM.shutdown());
        }

        boolean interrupted = Thread.interrupted();
        RuntimeException failure = null;
        try {
            termination.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
            failure = new IllegalStateException("Interrupted while stopping containers", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            failure = new IllegalStateException("Unable to stop containers", e);
        } finally {
            f_executorIO.shutdownNow();
            serviceThreads.interrupt();
            try {
                boolean ioStopped = f_executorIO.awaitTermination(
                        deadline.remainingNanos(), TimeUnit.NANOSECONDS);
                boolean xvmStopped = f_executorXVM.awaitTermination(
                        deadline.remainingNanos(), TimeUnit.NANOSECONDS);
                if (!ioStopped || !xvmStopped) {
                    throw new IllegalStateException("Runtime executors did not terminate");
                }
            } catch (InterruptedException e) {
                interrupted = true;
                if (failure == null) {
                    failure = new IllegalStateException("Interrupted while stopping executors", e);
                } else {
                    failure.addSuppressed(e);
                }
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * @return true once container cleanup succeeded and both executors have stopped
     */
    public boolean isTerminated() {
        var pending = termination;
        return pending != null && pending.isDone() && !pending.isCompletedExceptionally()
                && f_executorXVM.isTerminated() && f_executorIO.isTerminated();
    }

    public boolean isIdle() {
        // TODO: very naive; replace
        return m_lastXvmSubmitNanos < System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(10)
            && f_executorXVM.getActiveCount() == 0;
    }

    public boolean isDebuggerActive() {
        return m_fDebugger;
    }

    public void setDebuggerActive(boolean fActive) {
        m_fDebugger = fActive;
    }

    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Default shutdown budget for hosts that do not provide one explicitly.
     */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * The executor for XVM services.
     */
    public final ThreadPoolExecutor f_executorXVM;

    /**
     * The executor for IO services.
     */
    public final ExecutorService f_executorIO;

    /**
     * The timer belongs to this runtime, so closing an embedding session cancels its alarms.
     */
    private final Timer timer;

    /**
     * Interrupt active service work without discarding queued shutdown processing.
     */
    private final ThreadGroup serviceThreads = new ThreadGroup("XVM");

    /**
     * Weak discovery index for debugging and descendant traversal. All access uses its monitor;
     * registry operations must not call container methods while holding that monitor.
     */
    private final Map<Container, Object> f_containers = new WeakHashMap<>();

    /**
     * Strong roots for outstanding cleanup obligations, guarded by {@link #f_containers}. Each
     * retained child also keeps its ancestors reachable through {@link Container#f_parent}.
     */
    private final Set<Container> retainedContainers = new HashSet<>();

    /**
     * Guarded by {@link #f_containers}; prevents container creation during shutdown.
     */
    private boolean closing;

    /**
     * Retain the first shutdown's cleanup result across retries, including cleanup failures.
     */
    private volatile CompletableFuture<Void> termination;

    /**
     * A unique id producer.
     */
    protected final AtomicLong f_idProducer = new AtomicLong();

    /**
     * The time at which the last service task was submitted.
     */
    private volatile long m_lastXvmSubmitNanos;

    /**
     * The "debugger is active" flag.
     */
    private boolean m_fDebugger;
}
