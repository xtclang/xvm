package org.xvm.testing;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds a hung test by the work being done, not by the clock: a wait fails when nothing has used CPU for an
 * {@linkplain #idle() idle window} while what it waits for has not happened, or at once when threads of this JVM
 * deadlock. The work is all of this JVM's threads, its garbage collection and compilation included, and all the
 * processes it started and theirs. A slower or busier machine still uses CPU while it gets there, so a result never
 * depends on its speed. The one limit of time is the {@linkplain #livelock() livelock guard}, for a wait that stays
 * busy and never ends.
 * <p>
 * A wait's failure says what it waited for and where, and holds a dump of every thread of this JVM and of each JVM it
 * started. {@link Await} is the shared watchdog's waits; a test of the watchdog makes its own with short windows.
 */
public final class Watchdog implements AutoCloseable {
    /**
     * How long nothing may use CPU, while a wait's condition does not hold, before the wait fails as hung. A test that
     * makes progress uses CPU, its own or a process's, long before this.
     */
    public static final Duration IDLE = Duration.ofSeconds(60);

    /**
     * The one absolute limit, in seconds: a single wait that stays unmet this long, with CPU busy all along, fails as
     * a livelock. The idle window ends a hang, and a deadlock fails at once, but code that spins without end still
     * uses CPU, so without this a livelock would never end. It is ten minutes because no single wait of a correct test
     * comes near that however slow the machine, while a livelock still fails within a CI job's time. It is a constant
     * in seconds for annotations such as JUnit's {@code @Timeout}.
     */
    public static final long LIVELOCK_SECONDS = 600;

    /** {@link #LIVELOCK_SECONDS} as a duration. */
    public static final Duration LIVELOCK = Duration.ofSeconds(LIVELOCK_SECONDS);

    /**
     * The CPU that still counts as nothing, as a share of one core over the idle window: an idle JVM's own upkeep, its
     * timers and the sampling, stays well below it, and work that makes progress goes well above it.
     */
    static final double EPSILON = 0.02;

    /** How often the CPU is sampled and the threads checked for a deadlock. */
    private static final Duration SAMPLE = Duration.ofMillis(500);

    /** How long a blocking wait blocks before it asks the watchdog again. */
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    /** How often a condition is polled. */
    private static final long POLL_MILLIS = 10;

    /** How long a dump of a started JVM may take; it is only for the failure's message. */
    private static final Duration DUMP_LIMIT = Duration.ofSeconds(30);

    /** What a wait's attempt answers while what it waits for has not happened. */
    private static final Object PENDING = new Object();

    private final Duration idle;
    private final Duration livelock;
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean system = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private final Thread sampler;

    /** The CPU used up to each sample, oldest first, covering at least the idle window. */
    private final Deque<Sample> samples = new ArrayDeque<>();

    /** The CPU of each started process at the last sample, by pid. */
    private Map<Long, Long> children = Map.of();
    private long processCpu;
    private long samplerCpu;
    private long used;
    private volatile long[] deadlocked;
    private volatile boolean closed;

    /**
     * A watchdog with its own idle window and livelock guard, whose sampling starts now.
     *
     * @param idle      how long nothing may use CPU before a wait fails as hung
     * @param livelock  how long a single busy wait may stay unmet
     */
    public Watchdog(Duration idle, Duration livelock) {
        this.idle     = idle;
        this.livelock = livelock;
        this.sampler  = Thread.ofPlatform().daemon().name("hang-watchdog").unstarted(this::sample);
        processCpu    = system.getProcessCpuTime();
        samples.add(new Sample(System.nanoTime(), 0));
        sampler.start();
    }

    /**
     * @return the watchdog of this JVM's tests, with the {@link #IDLE} window and the {@link #LIVELOCK} guard
     */
    public static Watchdog shared() {
        return Shared.INSTANCE;
    }

    public Duration idle() {
        return idle;
    }

    public Duration livelock() {
        return livelock;
    }

    @Override
    public void close() {
        closed = true;
        sampler.interrupt();
    }

    // ----- waits ---------------------------------------------------------------------------------

    /**
     * @return the future's value, as {@link Future#get()} has it, once it completes
     */
    public <T> T get(Future<T> future, Object what) throws InterruptedException, ExecutionException {
        return waitFor(what == null ? future : what, nanos -> {
            try {
                return future.get(nanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                return PENDING;
            }
        });
    }

    /**
     * @return true, once the latch counts down to zero
     */
    public boolean await(CountDownLatch latch, Object what) throws InterruptedException {
        return waitFor(what == null ? latch : what, nanos -> latch.await(nanos, TimeUnit.NANOSECONDS) ? Boolean.TRUE : PENDING);
    }

    /**
     * @return the arrival index, as {@link CyclicBarrier#await()} has it, once every party arrives
     */
    public int await(CyclicBarrier barrier, Object what) throws InterruptedException, BrokenBarrierException {
        // a barrier's await breaks the barrier when it times out, so every party waits without a limit, and this one
        // waits for its own arrival on another thread
        return call(what == null ? barrier : what, barrier::await);
    }

    /**
     * @return the head of the queue, once it has one
     */
    public <T> T poll(BlockingQueue<T> queue, Object what) throws InterruptedException {
        return waitFor(what == null ? queue : what, nanos -> {
            T head = queue.poll(nanos, TimeUnit.NANOSECONDS);
            return head == null ? PENDING : head;
        });
    }

    /**
     * @return true, once the semaphore gives the permits
     */
    public boolean tryAcquire(Semaphore semaphore, int permits, Object what) throws InterruptedException {
        return waitFor(what == null ? semaphore : what,
                nanos -> semaphore.tryAcquire(permits, nanos, TimeUnit.NANOSECONDS) ? Boolean.TRUE : PENDING);
    }

    /**
     * @return true, once the process ends
     */
    public boolean waitFor(Process process, Object what) throws InterruptedException {
        return waitFor(what == null ? process : what,
                nanos -> process.waitFor(nanos, TimeUnit.NANOSECONDS) ? Boolean.TRUE : PENDING);
    }

    /**
     * @return true, once the executor has shut down and its tasks have ended
     */
    public boolean awaitTermination(ExecutorService executor, Object what) throws InterruptedException {
        return waitFor(what == null ? executor : what,
                nanos -> executor.awaitTermination(nanos, TimeUnit.NANOSECONDS) ? Boolean.TRUE : PENDING);
    }

    /**
     * Wait for the thread to end.
     */
    public void join(Thread thread, Object what) throws InterruptedException {
        waitFor(what == null ? thread : what, nanos -> thread.join(Duration.ofNanos(nanos)) ? Boolean.TRUE : PENDING);
    }

    /**
     * Poll the condition until it holds.
     */
    public void until(BooleanSupplier condition, Object what) {
        try {
            waitFor(what == null ? condition : what, nanos -> {
                if (condition.getAsBoolean()) {
                    return Boolean.TRUE;
                }
                Thread.sleep(POLL_MILLIS);
                return PENDING;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + describe(what == null ? condition : what), e);
        }
    }

    /**
     * Run the assertion until it passes; a hang fails with its last failure as the cause.
     */
    public void untilAsserted(Action assertion, Object what) {
        AssertionError[] last = {null};
        try {
            waitFor(what == null ? assertion : what, nanos -> {
                try {
                    assertion.run();
                    return Boolean.TRUE;
                } catch (AssertionError e) {
                    last[0] = e;
                } catch (Throwable e) {
                    throw Watchdog.<RuntimeException>sneaky(e);
                }
                Thread.sleep(POLL_MILLIS);
                return PENDING;
            });
        } catch (AssertionError hung) {
            if (last[0] != null && hung.getCause() == null) {
                hung.initCause(last[0]);
            }
            throw hung;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + describe(what == null ? assertion : what), e);
        }
    }

    /**
     * Run the block on a thread of its own, as JUnit's {@code assertTimeoutPreemptively} does, and wait for it: a hang
     * interrupts the thread and fails. What the block throws, it throws here.
     *
     * @return the block's value
     */
    public <T> T call(Object what, Block<T> block) {
        FutureTask<T> task = new FutureTask<>(() -> {
            try {
                return block.call();
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new ExecutionException(e);
            }
        });
        Thread thread = Thread.ofPlatform().daemon().name("awaited: " + describe(what)).start(task);
        try {
            return get(task, what);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof ExecutionException wrapped ? wrapped.getCause() : e.getCause();
            throw Watchdog.<RuntimeException>sneaky(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for " + describe(what), e);
        } finally {
            thread.interrupt();
        }
    }

    /**
     * Run the action on a thread of its own and wait for it, as {@link #call} does.
     */
    public void run(Object what, Action action) {
        call(what, () -> {
            action.run();
            return null;
        });
    }

    /** A block of code that answers a value and may throw anything. */
    @FunctionalInterface
    public interface Block<T> {
        T call() throws Throwable;
    }

    /** A block of code that may throw anything. */
    @FunctionalInterface
    public interface Action {
        void run() throws Throwable;
    }

    /** One attempt at a wait, for at most the nanoseconds given: its value, or {@link #PENDING}. */
    @FunctionalInterface
    private interface Attempt {
        Object attempt(long nanos) throws Exception;
    }

    /**
     * Attempt until the attempt answers, and fail when the watchdog finds a hang, a deadlock or a livelock first.
     */
    @SuppressWarnings("unchecked")
    private <T> T waitFor(Object what, Attempt attempt) throws InterruptedException {
        long start = System.nanoTime();
        while (true) {
            Object value;
            try {
                value = attempt.attempt(SLICE_NANOS);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                // what the wait's own call throws, such as a future's ExecutionException, which its caller declares
                throw Watchdog.<RuntimeException>sneaky(e);
            }
            if (value != PENDING) {
                return (T) value;
            }
            Optional<String> hung = verdict(start);
            if (hung.isPresent()) {
                throw new AssertionError("hung waiting for " + describe(what) + " at " + caller() + ": " + hung.get()
                        + "\n\n" + dumps());
            }
        }
    }

    // ----- the verdict ---------------------------------------------------------------------------

    /**
     * @return why a wait that began at {@code start} is hung, if it is
     */
    private Optional<String> verdict(long start) {
        long[] locked = deadlocked;
        if (locked != null) {
            return Optional.of("threads " + Arrays.stream(threads.getThreadInfo(locked))
                    .filter(info -> info != null)
                    .map(info -> "\"" + info.getThreadName() + "\"")
                    .collect(Collectors.joining(", ")) + " are deadlocked");
        }
        long now    = System.nanoTime();
        long waited = now - start;
        long window = idle.toNanos();
        if (waited >= livelock.toNanos()) {
            return Optional.of("still busy after the livelock guard of " + livelock.toMinutes() + " minutes");
        }
        if (waited < window) {
            return Optional.empty();
        }
        synchronized (samples) {
            Sample latest = samples.peekLast();
            // the latest sample at or before the window's start, if the samples reach back that far
            Sample first = null;
            for (Sample sample : samples) {
                if (sample.at() > now - window) {
                    break;
                }
                first = sample;
            }
            if (first == null || latest == null || latest.at() - first.at() < window) {
                return Optional.empty();
            }
            long cpu = latest.used() - first.used();
            return cpu < EPSILON * (latest.at() - first.at())
                    ? Optional.of("nothing used CPU for " + idle.toSeconds() + " s (" + TimeUnit.NANOSECONDS.toMillis(cpu)
                            + " ms in this JVM and the processes it started)")
                    : Optional.empty();
        }
    }

    /**
     * The sampler's loop: the CPU this JVM and its processes used since the last sample, without the sampler's own,
     * and whether any threads deadlocked.
     */
    private void sample() {
        long self = Thread.currentThread().threadId();
        while (!closed) {
            try {
                Thread.sleep(SAMPLE);
            } catch (InterruptedException e) {
                return;
            }
            long                cpu     = system.getProcessCpuTime();
            Map<Long, Long>     now     = new HashMap<>();
            List<ProcessHandle> unknown = new ArrayList<>();
            long                spent   = 0;
            ProcessHandle.current().descendants().forEach(child -> child.info().totalCpuDuration().ifPresentOrElse(
                    total -> now.put(child.pid(), total.toNanos()), () -> unknown.add(child)));
            if (!unknown.isEmpty() && !ps(unknown, now)) {
                // a process whose CPU cannot be known counts as working, so that it never fails a wait as hung
                spent += idle.toNanos();
            }
            for (Map.Entry<Long, Long> child : now.entrySet()) {
                spent += Math.max(0, child.getValue() - children.getOrDefault(child.getKey(), 0L));
            }
            // a process that ended since used CPU this sample cannot see: its end counts as work
            if (!now.keySet().containsAll(children.keySet())) {
                spent += idle.toNanos();
            }
            long sampler = threads.getThreadCpuTime(self);
            spent += Math.max(0, cpu - processCpu - Math.max(0, sampler - samplerCpu));
            long[] locked = threads.findDeadlockedThreads();
            synchronized (samples) {
                processCpu = cpu;
                samplerCpu = sampler;
                children   = now;
                used      += spent;
                long at = System.nanoTime();
                samples.addLast(new Sample(at, used));
                while (samples.size() > 2 && samples.peekFirst().at() < at - idle.toNanos() - SAMPLE.toNanos() * 4) {
                    samples.removeFirst();
                }
            }
            deadlocked = locked;
        }
    }

    /**
     * Add the CPU of each of the processes to {@code cpu}, by pid, from {@code ps}: macOS tells a process nothing of
     * another's CPU, its own children's included, but ps reads it.
     *
     * @return whether ps gave the CPU of every one of the processes that is still alive
     */
    private static boolean ps(List<ProcessHandle> processes, Map<Long, Long> cpu) {
        String pids = processes.stream().map(child -> String.valueOf(child.pid())).collect(Collectors.joining(","));
        try {
            Process ps = new ProcessBuilder("ps", "-o", "pid=,time=", "-p", pids).redirectErrorStream(true).start();
            String out = new String(ps.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!ps.waitFor(DUMP_LIMIT.toSeconds(), TimeUnit.SECONDS)) {
                ps.destroyForcibly();
                return false;
            }
            Map<Long, Long> read = out.lines()
                    .map(line -> line.strip().split("\\s+"))
                    .filter(fields -> fields.length == 2 && fields[0].chars().allMatch(Character::isDigit))
                    .collect(Collectors.toMap(fields -> Long.parseLong(fields[0]), fields -> cpuTime(fields[1])));
            cpu.putAll(read);
            // a process that ended before ps looked is not there, and the next sample sees that it ended
            return read.size() >= processes.stream().filter(ProcessHandle::isAlive).count();
        } catch (IOException | NumberFormatException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * @return ps's CPU time, {@code [[dd-]hh:]mm:ss[.ss]}, in nanoseconds
     */
    static long cpuTime(String time) {
        int    dash    = time.indexOf('-');
        long   days    = dash < 0 ? 0 : Long.parseLong(time.substring(0, dash));
        double seconds = 0;
        for (String part : time.substring(dash + 1).split(":")) {
            seconds = seconds * 60 + Double.parseDouble(part);
        }
        return TimeUnit.DAYS.toNanos(days) + (long) (seconds * 1e9);
    }

    /** The CPU used, in nanoseconds, up to a time, from {@link System#nanoTime()}. */
    private record Sample(long at, long used) {
    }

    // ----- the message ---------------------------------------------------------------------------

    /**
     * @return what a wait waits for, as its caller named it, or the object it waits on
     */
    private static String describe(Object what) {
        // a lambda's name says nothing; the caller's line says what it runs
        return what != null && what.getClass().isHidden() ? "the code it runs" : String.valueOf(what);
    }

    /**
     * @return the frame that called the wait: the first outside the watchdog, {@link Await} and the Kotlin files of
     *         waits, {@code Awaiting.kt}, that modules have
     */
    private static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !helper(frame.getClassName()))
                .findFirst()
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + "(" + frame.getFileName() + ":"
                        + frame.getLineNumber() + ")")
                .orElse("an unknown frame"));
    }

    /**
     * @return whether the class is one of the waits' own: the watchdog's, Await's or a module's Awaiting.kt
     */
    private static boolean helper(String name) {
        return Stream.of(Watchdog.class.getName(), Await.class.getName())
                .anyMatch(own -> name.equals(own) || name.startsWith(own + "$")) || name.endsWith("AwaitingKt");
    }

    /**
     * @return every thread of this JVM, with its whole stack and its locks, then each started JVM's threads
     */
    private String dumps() {
        StringBuilder out = new StringBuilder("Threads of this JVM:\n");
        Arrays.stream(threads.dumpAllThreads(true, true)).forEach(info -> dump(info, out));
        List<ProcessHandle> started = ProcessHandle.current().descendants().toList();
        for (ProcessHandle child : started) {
            String command = child.info().commandLine().orElse(child.info().command().orElse("?"));
            out.append("\nProcess ").append(child.pid()).append(": ").append(command).append('\n');
            if (child.info().command().map(c -> c.endsWith("/java") || c.endsWith("\\java.exe")).orElse(false)) {
                out.append(threadsOf(child)).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * One thread's state, locks and whole stack, as a JVM's thread dump shows them, which {@link ThreadInfo#toString()}
     * cuts at eight frames.
     */
    private static void dump(ThreadInfo info, StringBuilder out) {
        out.append('"').append(info.getThreadName()).append("\" #").append(info.getThreadId())
                .append(info.isDaemon() ? " daemon " : " ").append(info.getThreadState());
        if (info.getLockName() != null) {
            out.append(" on ").append(info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            out.append(" owned by \"").append(info.getLockOwnerName()).append("\" #").append(info.getLockOwnerId());
        }
        out.append('\n');
        StackTraceElement[] stack    = info.getStackTrace();
        MonitorInfo[]       monitors = info.getLockedMonitors();
        for (int depth = 0; depth < stack.length; depth++) {
            out.append("\tat ").append(stack[depth]).append('\n');
            for (MonitorInfo monitor : monitors) {
                if (monitor.getLockedStackDepth() == depth) {
                    out.append("\t- locked ").append(monitor).append('\n');
                }
            }
        }
        LockInfo[] synchronizers = info.getLockedSynchronizers();
        if (synchronizers.length > 0) {
            out.append("\tLocked synchronizers:\n");
            for (LockInfo synchronizer : synchronizers) {
                out.append("\t- ").append(synchronizer).append('\n');
            }
        }
        out.append('\n');
    }

    /**
     * @return a started JVM's threads, from jcmd's Thread.print, or jstack's where there is no jcmd
     */
    private static String threadsOf(ProcessHandle child) {
        Path bin  = Path.of(System.getProperty("java.home"), "bin");
        Path jcmd = bin.resolve("jcmd");
        List<String> command = Files.isExecutable(jcmd)
                ? List.of(jcmd.toString(), String.valueOf(child.pid()), "Thread.print", "-l")
                : List.of(bin.resolve("jstack").toString(), "-l", String.valueOf(child.pid()));
        try {
            Process dump = new ProcessBuilder(command).redirectErrorStream(true).start();
            // a dump of a JVM that does not answer must not hang the failure that reports the hang
            if (!dump.waitFor(DUMP_LIMIT.toSeconds(), TimeUnit.SECONDS)) {
                dump.destroyForcibly();
                return "(no thread dump within " + DUMP_LIMIT.toSeconds() + " s)";
            }
            return new String(dump.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(no thread dump: " + e + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(no thread dump: interrupted)";
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable e) throws E {
        throw (E) e;
    }

    /** The shared watchdog, made when a test first waits. */
    private static final class Shared {
        static final Watchdog INSTANCE = new Watchdog(IDLE, LIVELOCK);
    }
}
