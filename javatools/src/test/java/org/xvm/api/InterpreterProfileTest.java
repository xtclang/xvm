package org.xvm.api;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * THROWAWAY profiling harness: sweeps the manualTests suite through ONE warm {@link XtcEngine} and
 * records JFR around each phase separately, so compile time and interpreter time can be told apart.
 *
 * <p>Not an assertion test. It is tagged "heavy" and driven by hand:</p>
 *
 * <pre>{@code
 * ./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.triage" \
 *     -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=/tmp/prof --rerun-tasks --no-build-cache
 * ./gradlew :javatools:test --tests "org.xvm.api.InterpreterProfileTest.profile" \
 *     -PincludeHeavyTests -PtestMaxHeap=8g -Dxvm.profile.out=/tmp/prof --rerun-tasks --no-build-cache
 * }</pre>
 *
 * <p>Two invocations, not two phases of one, because triage deliberately provokes runs that hang or
 * blow up; doing that in the same JVM as the measured passes would leave stray container work
 * running underneath the recording.</p>
 */
public class InterpreterProfileTest {
    private static final String OUT_PROPERTY  = "xvm.profile.out";
    private static final int    RUN_TIMEOUT_S = 25;

    @Test
    @Tag("heavy")
    public void triage() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");
        Path out = outDir();

        var lines    = new ArrayList<String>();
        var runnable = new ArrayList<String>();
        Path progress = out.resolve("triage-progress.txt");
        Files.writeString(progress, "");

        // ONE engine per module, deliberately. `TaskRegistry` in runner.x is a singleton service
        // whose `startTask(Int id) = taskFor(id).start()` is a BLOCKING service call - so a module
        // that never finishes (a server, a module waiting on a network name service, the
        // interactive debugger) wedges the registry's fiber and every later run on that engine
        // times out too. Triage without this reports the first hang plus a cascade of false ones.
        XtcEngine engine = engine();
        try {
            for (File src : sources()) {
                String name = moduleNameOf(src);
                if (name == null) {
                    record(lines, progress, src.getName() + "\tNO-MODULE-DECL");
                    continue;
                }
                if (usesDebugger(src)) {
                    // `assert:debug` opens DebugConsole, which blocks on stdin AND sets a global
                    // Runtime debuggerActive flag - that flag changes the interpreter's inner loop
                    // (ServiceContext.execute consults isDebuggerActive per op-budget check and per
                    // return) for every module that runs afterwards. Measuring past one of these
                    // would be measuring a different interpreter.
                    record(lines, progress, name + "\tSKIPPED\tsource contains assert:debug");
                    continue;
                }
                note(progress, "-> " + name);
                XtcEngine.CompileResult result;
                long                    tCompile = System.nanoTime();
                try {
                    result = engine.compile(compileSource(src));
                } catch (Throwable t) {
                    record(lines, progress, name + "\tCOMPILE-THREW\t" + describe(t));
                    continue;
                }
                long msCompile = (System.nanoTime() - tCompile) / 1_000_000;
                if (!result.isSuccess()) {
                    record(lines, progress, name + "\tCOMPILE-FAILED\t" + msCompile + "ms\t"
                            + result.diagnostics().stream().findFirst().map(Object::toString).orElse("?"));
                    continue;
                }
                long tRun = System.nanoTime();
                try {
                    engine.run(result, name).get(RUN_TIMEOUT_S, TimeUnit.SECONDS);
                    long msRun = (System.nanoTime() - tRun) / 1_000_000;
                    record(lines, progress, name + "\tOK\tcompile=" + msCompile + "ms\trun="
                            + msRun + "ms");
                    runnable.add(name);
                } catch (Throwable t) {
                    long msRun = (System.nanoTime() - tRun) / 1_000_000;
                    record(lines, progress, name + "\tRUN-FAILED\tcompile=" + msCompile + "ms\trun="
                            + msRun + "ms\t" + describe(t));
                    // the plane may be wedged; start a clean one for the next module
                    engine.close();
                    engine = engine();
                }
            }
        } finally {
            engine.close();
        }

        Files.write(out.resolve("triage.txt"), lines);
        Files.write(out.resolve("runnable.txt"), runnable);
        lines.forEach(System.out::println);
        System.out.println("TRIAGE runnable=" + runnable.size() + " of " + sources().size());
    }

    @Test
    @Tag("heavy")
    public void profile() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");
        Path out = outDir();
        Path list = out.resolve("runnable.txt");
        assumeTrue(Files.isRegularFile(list), "run the triage method first to produce " + list);

        List<String> names   = Files.readAllLines(list).stream().filter(s -> !s.isBlank()).toList();
        List<File>   sources = sources().stream()
                .filter(f -> names.contains(moduleNameOf(f)))
                .toList();
        System.out.println("PROFILE modules=" + sources.size());

        Path progress = out.resolve("profile-progress.txt");
        Files.writeString(progress, "PROFILE modules=" + sources.size() + System.lineSeparator());
        var log  = new ArrayList<String>();
        var warm = new ArrayList<XtcEngine.CompileResult>();
        try (var engine = engine()) {
            // pass 1: cold compile - classloading, TypeInfo construction, pool warmup all land here
            phase(out, progress, log, "compile-cold", () -> compileAll(engine, sources, log));

            // pass 2: warm compile - the same work with everything already loaded; these are the
            //         results the run passes execute
            phase(out, progress, log, "compile-warm",
                    () -> warm.addAll(compileAll(engine, sources, log)));

            // pass 3: cold run - first time any of this executes; the interpreter plus every
            //         one-time per-shape cost (TypeInfo, ClassComposition, native template init)
            phase(out, progress, log, "run-cold", () -> runAll(engine, warm, log));

            // passes 4-6: warm runs, repeated for variance
            for (int i = 1; i <= 3; i++) {
                String name = "run-warm" + i;
                phase(out, progress, log, name, () -> runAll(engine, warm, log));
            }
        }

        Files.write(out.resolve("phases.txt"), log);
        log.forEach(System.out::println);
    }

    /**
     * Steady-state interpreter profile, isolated from per-run container setup.
     *
     * <p>The manualTests modules mostly run for well under a second each, so a sweep of them mixes
     * interpretation with container creation, linking and TypeInfo construction in unknown
     * proportions. These three modules each spend tens of seconds inside one fiber doing one kind of
     * work, so what the recording contains IS the inner loop:</p>
     *
     * <ul>
     *   <li>{@code HotArith} - loop and arithmetic ops only, no calls</li>
     *   <li>{@code HotCall} - the same loop with a non-virtual call per iteration</li>
     *   <li>{@code HotVirtual} - the same loop with a virtual call through an interface</li>
     * </ul>
     */
    @Test
    @Tag("heavy")
    public void profileSteadyState() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");
        Path out      = outDir();
        Path progress = out.resolve("steady-progress.txt");
        Files.writeString(progress, "steady state" + System.lineSeparator());

        var log = new ArrayList<String>();
        try (var engine = engine()) {
            // What one run costs before a single op of the module's own code executes: the runner's
            // registerTransientTask/startTask round trip, container creation, linking, the file
            // store. Everything the sweep measures sits on top of this.
            var nop = engine.compile("Nop", "module Nop { void run() {} }");
            for (int i = 0; i < 5; i++) {
                engine.run(nop, "Nop").get(120, TimeUnit.SECONDS);
            }
            long tNop = System.nanoTime();
            int  cNop = 20;
            for (int i = 0; i < cNop; i++) {
                engine.run(nop, "Nop").get(120, TimeUnit.SECONDS);
            }
            String nopLine = "EMPTY-MODULE RUN: " + cNop + " runs in "
                    + (System.nanoTime() - tNop) / 1_000_000 + "ms";
            log.add(nopLine);
            note(progress, nopLine);
            phase(out, progress, log, "steady-Nop", () -> {
                for (int i = 0; i < cNop; i++) {
                    engine.run(nop, "Nop").get(120, TimeUnit.SECONDS);
                }
            });

            for (var unit : List.of(
                    new XtcEngine.SourceUnit("HotArith", """
                            module HotArith {
                                void run() {
                                    Int total = 0;
                                    for (Int i : 0 ..< 40_000_000) {
                                        total += i * 3 + (i % 7);
                                    }
                                    assert total != -1;
                                }
                            }
                            """),
                    new XtcEngine.SourceUnit("HotCall", """
                            module HotCall {
                                void run() {
                                    Int total = 0;
                                    for (Int i : 0 ..< 10_000_000) {
                                        total += step(i);
                                    }
                                    assert total != -1;
                                }
                                Int step(Int i) = i * 3 + (i % 7);
                            }
                            """),
                    new XtcEngine.SourceUnit("HotVirtual", """
                            module HotVirtual {
                                interface Step {
                                    Int apply(Int i);
                                }
                                const Triple implements Step {
                                    @Override Int apply(Int i) = i * 3 + (i % 7);
                                }
                                const Double implements Step {
                                    @Override Int apply(Int i) = i * 2 + (i % 5);
                                }
                                void run() {
                                    Step[] steps = [new Triple(), new Double()];
                                    Int total = 0;
                                    for (Int i : 0 ..< 10_000_000) {
                                        total += steps[i % 2].apply(i);
                                    }
                                    assert total != -1;
                                }
                            }
                            """))) {
                var compiled = engine.compile(unit);
                if (!compiled.isSuccess()) {
                    note(progress, unit.moduleName() + " COMPILE-FAILED " + compiled.diagnostics());
                    continue;
                }
                // one warmup run so the JIT has compiled the interpreter before we measure it
                long tWarm = System.nanoTime();
                engine.run(compiled, unit.moduleName()).get(600, TimeUnit.SECONDS);
                note(progress, unit.moduleName() + " warmup=" + (System.nanoTime() - tWarm) / 1_000_000 + "ms");

                phase(out, progress, log, "steady-" + unit.moduleName(), () ->
                        engine.run(compiled, unit.moduleName()).get(600, TimeUnit.SECONDS));
            }
        }
        Files.write(out.resolve("steady.txt"), log);
        log.forEach(System.out::println);
    }

    // ----- phases ---------------------------------------------------------------------------------

    @FunctionalInterface
    private interface Phase {
        void run() throws Exception;
    }

    private static void phase(Path out, Path progress, List<String> log, String name, Phase body)
            throws Exception {
        System.gc();
        note(progress, "=== PHASE " + name + " starting ===");
        Recording recording = startRecording(name);
        long      t0        = System.nanoTime();
        try (var jfr = new JfrProfile(name)) {
            body.run();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            note(progress, "PHASE " + name + " wall=" + ms + "ms");
            log.add("PHASE " + name + " wall=" + ms + "ms");
            log.add("--- JfrProfile summary: " + name + " ---");
            log.add(jfr.stopAndRender(25));
        } finally {
            recording.stop();
            recording.dump(out.resolve(name + ".jfr"));
            recording.close();
        }
    }

    /**
     * A finer-grained recording than the stock "profile" configuration: the interpreter's inner
     * loop turns over far faster than a 10-20 ms sampling period can resolve.
     */
    private static Recording startRecording(String name) throws Exception {
        var recording = new Recording(Configuration.getConfiguration("profile"));
        recording.setName(name + "-fine");
        recording.setToDisk(true);
        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
        recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(1));
        recording.enable("jdk.ObjectAllocationSample").with("throttle", "3000/s");
        recording.start();
        return recording;
    }

    private static List<XtcEngine.CompileResult> compileAll(XtcEngine engine, List<File> sources,
                                                            List<String> log) {
        var results = new ArrayList<XtcEngine.CompileResult>(sources.size());
        for (File src : sources) {
            long t = System.nanoTime();
            var  r = engine.compile(compileSource(src));
            log.add(String.format("  compile %-28s %6d ms %s", moduleNameOf(src),
                    (System.nanoTime() - t) / 1_000_000, r.isSuccess() ? "OK" : "FAIL"));
            if (r.isSuccess()) {
                results.add(r);
            }
        }
        return results;
    }

    private static List<String> runAll(XtcEngine engine, List<XtcEngine.CompileResult> compiled,
                                       List<String> log) {
        var done = new ArrayList<String>(compiled.size());
        for (XtcEngine.CompileResult result : compiled) {
            String name = result.modules().getFirst().getName();
            long   t    = System.nanoTime();
            String outcome;
            try {
                engine.run(result, name).get(RUN_TIMEOUT_S, TimeUnit.SECONDS);
                outcome = "OK";
                done.add(name);
            } catch (Throwable e) {
                outcome = describe(e);
            }
            log.add(String.format("  run     %-28s %6d ms %s", name,
                    (System.nanoTime() - t) / 1_000_000, outcome));
        }
        return done;
    }

    // ----- support --------------------------------------------------------------------------------

    private static XtcEngine engine() {
        Path root = XdkOutputs.root();
        return XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build();
    }

    private static XtcEngine.ModuleSource compileSource(File src) {
        Path resources = XdkOutputs.root().resolve("manualTests/src/main/resources");
        return Files.isDirectory(resources)
                ? XtcEngine.ModuleSource.of(src.toPath(), resources)
                : XtcEngine.ModuleSource.of(src.toPath());
    }

    private static List<File> sources() {
        File dir = XdkOutputs.root().resolve("manualTests/src/main/x").toFile();
        var  all = new ArrayList<>(Arrays.asList(dir.listFiles(f -> f.getName().endsWith(".x"))));
        all.sort(Comparator.comparing(File::getName));
        return all;
    }

    /** @return true iff the module opens the interactive debugger, which poisons every later run */
    private static boolean usesDebugger(File src) {
        try {
            return Files.readString(src.toPath()).contains("assert:debug");
        } catch (Exception e) {
            return false;
        }
    }

    private static String moduleNameOf(File src) {
        try {
            for (String line : Files.readAllLines(src.toPath())) {
                String trimmed = line.trim();
                int    at      = trimmed.indexOf("module ");
                if (at == 0 || (at > 0 && trimmed.startsWith("@") && at < 40)) {
                    String rest = trimmed.substring(at + "module ".length()).trim();
                    int    end  = 0;
                    while (end < rest.length()
                            && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_'
                                || rest.charAt(end) == '.')) {
                        end++;
                    }
                    if (end > 0) {
                        return rest.substring(0, end);
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static void record(List<String> lines, Path progress, String line) {
        lines.add(line);
        note(progress, line);
    }

    /** Append a line to the progress file so a long sweep can be watched from outside. */
    private static void note(Path progress, String line) {
        try {
            Files.writeString(progress, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new IllegalStateException("cannot write " + progress, e);
        }
        System.out.println(line);
        System.out.flush();
    }

    private static String describe(Throwable t) {
        Throwable cause = t.getCause() == null ? t : t.getCause();
        String    msg   = String.valueOf(cause.getMessage()).replace('\n', '|');
        return cause.getClass().getSimpleName() + ": "
                + (msg.length() > 200 ? msg.substring(0, 200) : msg);
    }

    private static Path outDir() throws Exception {
        String dir = System.getProperty(OUT_PROPERTY);
        assumeTrue(dir != null && !dir.isBlank(), "-D" + OUT_PROPERTY + "=<dir> is required");
        Path path = Path.of(dir);
        Files.createDirectories(path);
        return path;
    }
}
