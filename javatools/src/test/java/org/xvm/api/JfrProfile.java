package org.xvm.api;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

/**
 * A JFR recording taken around a block of work, and a summary of what it says.
 *
 * <p>Driven through the {@code jdk.jfr} API rather than a {@code -XX:StartFlightRecording} flag, so
 * it needs no build plumbing and the recording covers exactly the work we care about instead of the
 * whole JVM lifetime.</p>
 *
 * <p>It reports four things a hand-rolled stack sampler cannot: allocation by site (which is what
 * drives GC pressure, and therefore the wall time a compile spends not compiling), total GC pause,
 * monitor contention (the thing that decides whether the parallel configuration actually scales),
 * and method time with full stacks rather than a single frame.</p>
 */
public final class JfrProfile implements AutoCloseable {
    private final Recording f_recording;
    private final Path      f_file;

    public JfrProfile(String name) throws Exception {
        f_file      = Files.createTempFile(name, ".jfr");
        f_recording = new Recording(Configuration.getConfiguration("profile"));
        f_recording.setName(name);
        // deliberately NO setDestination: a recording with a destination is written and closed by
        // stop(), after which dump() fails with "has been closed, no content to write"
        f_recording.setToDisk(true);
        f_recording.start();
    }

    /**
     * Stop the recording and summarise it.
     *
     * @param top  how many rows per section
     *
     * @return the rendered summary
     */
    public String stopAndRender(int top) throws Exception {
        f_recording.stop();
        f_recording.dump(f_file);   // valid only because the recording has no destination

        var    cpu        = new HashMap<String, Long>();
        var    allocBytes = new HashMap<String, Long>();
        long   cpuSamples = 0;
        long   allocTotal = 0;
        long   gcNanos    = 0;
        long   gcCount    = 0;
        var    contention = new HashMap<String, Long>();
        long   blockNanos = 0;

        try (var file = new RecordingFile(f_file)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String        type  = event.getEventType().getName();
                switch (type) {
                    case "jdk.ExecutionSample" -> {
                        String frame = topXvmFrame(event);
                        if (frame != null) {
                            cpuSamples++;
                            cpu.merge(frame, 1L, Long::sum);
                        }
                    }
                    case "jdk.ObjectAllocationSample" -> {
                        long weight = event.hasField("weight") ? event.getLong("weight") : 0;
                        String frame = topXvmFrame(event);
                        allocTotal += weight;
                        if (frame != null) {
                            allocBytes.merge(frame, weight, Long::sum);
                        }
                    }
                    case "jdk.GCPhasePause" -> {
                        gcNanos += event.getDuration().toNanos();
                        gcCount++;
                    }
                    case "jdk.JavaMonitorEnter" -> {
                        blockNanos += event.getDuration().toNanos();
                        String frame = topXvmFrame(event);
                        contention.merge(frame == null ? "(non-xvm)" : frame,
                                event.getDuration().toNanos(), Long::sum);
                    }
                    default -> { }
                }
            }
        }

        var sb = new StringBuilder();
        sb.append(String.format("CPU: %d samples in xvm code%n", cpuSamples));
        render(sb, cpu, cpuSamples, top, "%%");
        sb.append(String.format("%nALLOCATION: %.1f GB sampled%n", allocTotal / 1024.0 / 1024 / 1024));
        render(sb, allocBytes, Math.max(allocTotal, 1), top, "%%");
        sb.append(String.format("%nGC: %d pauses, %.2f s total%n", gcCount, gcNanos / 1e9));
        sb.append(String.format("%nMONITOR CONTENTION: %.3f s blocked%n", blockNanos / 1e9));
        render(sb, contention, Math.max(blockNanos, 1), Math.min(top, 10), "%%");
        return sb.toString();
    }

    private static void render(StringBuilder sb, Map<String, Long> counts, long total, int top,
                               String unit) {
        counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed())
                .limit(top)
                .forEach(e -> sb.append(String.format("  %5.1f%s  %-52s%n",
                        100.0 * e.getValue() / total, unit.substring(0, 1), e.getKey())));
    }

    /**
     * @return the deepest {@code org.xvm} frame, which is the code actually running
     */
    private static String topXvmFrame(RecordedEvent event) {
        if (event.getStackTrace() == null) {
            return null;
        }
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            String cls = frame.getMethod().getType().getName();
            if (cls.startsWith("org.xvm.")) {
                return cls.substring(cls.lastIndexOf('.') + 1) + '.' + frame.getMethod().getName();
            }
        }
        return null;
    }

    @Override
    public void close() {
        f_recording.close();
        try {
            Files.deleteIfExists(f_file);
        } catch (Exception _) {
            // a temp file we could not remove is not worth failing a diagnostic over
        }
    }
}
