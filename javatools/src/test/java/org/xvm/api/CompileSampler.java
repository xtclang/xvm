package org.xvm.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A sampling profiler small enough to keep in the harness.
 *
 * <p>Answers "where does compile time go" without instrumenting the compiler, and works the same in
 * the sequential and parallel configurations, which is the point: a phase timer would have to be
 * threaded through the engine, and a JFR recording would need build plumbing to enable and a tool
 * to read. This just walks every live thread's stack on an interval and attributes the sample to
 * the deepest {@code org.xvm} frame - the method actually running, rather than the caller.</p>
 *
 * <p>Sampling bias is the usual: it sees threads that are RUNNABLE at a tick, so it measures CPU,
 * not wall time, and misses anything blocked. That is what we want here - a blocked compile is a
 * scheduling question, a hot method is a bottleneck.</p>
 */
public final class CompileSampler implements AutoCloseable {
    private final Map<String, AtomicLong> f_counts = new ConcurrentHashMap<>();
    private final AtomicBoolean           f_stop   = new AtomicBoolean();
    private final Thread                  f_thread;
    private final AtomicLong              f_samples = new AtomicLong();

    public CompileSampler(long msInterval) {
        f_thread = new Thread(() -> {
            while (!f_stop.get()) {
                for (var entry : Thread.getAllStackTraces().entrySet()) {
                    Thread thread = entry.getKey();
                    if (thread == Thread.currentThread() || thread.getState() != Thread.State.RUNNABLE) {
                        continue;
                    }
                    String frame = deepestXvmFrame(entry.getValue());
                    if (frame != null) {
                        f_samples.incrementAndGet();
                        f_counts.computeIfAbsent(frame, k -> new AtomicLong()).incrementAndGet();
                    }
                }
                try {
                    Thread.sleep(msInterval);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "compile-sampler");
        f_thread.setDaemon(true);
        f_thread.start();
    }

    private static String deepestXvmFrame(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            String cls = element.getClassName();
            if (cls.startsWith("org.xvm.") && !cls.startsWith("org.xvm.api.CompileSampler")) {
                int dot = cls.lastIndexOf('.');
                return cls.substring(dot + 1) + '.' + element.getMethodName();
            }
        }
        return null;
    }

    /**
     * @param top  how many rows to render
     *
     * @return the hottest methods, as a share of samples that were inside xvm code
     */
    public String render(int top) {
        long total = Math.max(f_samples.get(), 1);
        List<Map.Entry<String, AtomicLong>> rows = f_counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, AtomicLong> e) -> e.getValue().get()).reversed())
                .limit(top)
                .toList();

        var sb = new StringBuilder(String.format("%d samples inside xvm code%n", total));
        for (var row : rows) {
            sb.append(String.format("  %5.1f%%  %-48s %d%n",
                    100.0 * row.getValue().get() / total, row.getKey(), row.getValue().get()));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        f_stop.set(true);
        f_thread.interrupt();
    }
}
