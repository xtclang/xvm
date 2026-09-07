package org.xvm.api;

import java.io.File;
import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.ConcurrentBuildRepository;

import org.xvm.api.XtcEngine.CompileResult;
import org.xvm.api.XtcEngine.ModuleSource;

import org.xvm.util.Severity;

/**
 * Builds the XDK's own libraries in dependency order on one warm {@link XtcEngine}.
 *
 * <p>The scheduling is dependency-driven rather than level-driven: a module compiles as soon as
 * every module it imports has compiled. That is expressed once, as a {@link CompletableFuture} per
 * module chained on its dependencies' futures, and the <b>executor decides the concurrency</b> -
 * a single-threaded executor runs the identical graph sequentially, a virtual-thread-per-task
 * executor runs it as wide as the graph allows. So the sequential harness and the parallel one are
 * the same code, and a difference between their results is a real finding rather than a difference
 * in how they were written.</p>
 *
 * <p><b>Do not substitute a work-stealing pool.</b> The compiler keeps per-call state in
 * {@code TypeSystemThread} thread-locals, which is correct only while one task owns one thread;
 * a ForkJoinPool runs several tasks on one carrier thread and would bleed that state between them.
 * See the execution-model section of {@code plans/parallel-compiler-plan.md}.</p>
 */
public final class XdkBuildHarness {
    private XdkBuildHarness() {}

    /**
     * The modules the most recent {@link #build} produced, for a caller that wants to check them
     * rather than trust that "compiled without errors" means "compiled correctly".
     */
    public static ConcurrentBuildRepository lastOutput() {
        return s_lastOutput;
    }

    private static volatile ConcurrentBuildRepository s_lastOutput;

    /**
     * One module to build.
     *
     * @param moduleName  the qualified module name, e.g. {@code json.xtclang.org}
     * @param label       a short name for reporting, e.g. {@code lib_json}
     * @param source        the module's root source file
     * @param resourceDirs  the module's resource roots; {@code lib_ecstasy} needs its, because
     *                      {@code TypeSystem.x} reads {@code $/implicit.x} as a resource literal
     * @param deps          the qualified names of the modules it imports
     */
    public record Node(String moduleName, String label, Path source, List<File> resourceDirs,
                       Set<String> deps) {
        public Node {
            resourceDirs = List.copyOf(resourceDirs);
            deps         = Set.copyOf(deps);
        }
    }

    /**
     * What happened to one module.
     *
     * @param label       the module's short name
     * @param status      built, failed, or skipped because a dependency failed
     * @param errors      error-or-worse diagnostics
     * @param warnings    warning diagnostics
     * @param elapsed     wall time for this module's compile alone
     * @param firstError  the first error's text, or null
     */
    public record Outcome(String label, Status status, int errors, int warnings,
                          Duration elapsed, String firstError) {}

    public enum Status { BUILT, FAILED, SKIPPED }

    /**
     * The result of one whole build.
     *
     * @param outcomes  one per module, in completion order
     * @param wall      wall time for the build
     * @param heapUsed  heap in use after the build, having asked for a collection first
     */
    public record Report(List<Outcome> outcomes, Duration wall, long heapUsed) {
        public long built()   { return outcomes.stream().filter(o -> o.status() == Status.BUILT).count(); }
        public long failed()  { return outcomes.stream().filter(o -> o.status() == Status.FAILED).count(); }
        public long skipped() { return outcomes.stream().filter(o -> o.status() == Status.SKIPPED).count(); }

        public String render() {
            var sb = new StringBuilder(String.format(
                    "%d built, %d failed, %d skipped of %d in %s (heap after: %d MB)%n",
                    built(), failed(), skipped(), outcomes.size(), wall, heapUsed >> 20));
            for (Outcome o : outcomes) {
                sb.append(String.format("  %-8s %-22s %6d ms  %d err %d warn%s%n",
                        o.status(), o.label(), o.elapsed().toMillis(), o.errors(), o.warnings(),
                        o.firstError() == null ? "" : "  | " + o.firstError()));
            }
            return sb.toString();
        }
    }

    /**
     * Build every node, in dependency order, on the supplied executor.
     *
     * <p>Modules already present in {@code repoLibrary} are still rebuilt: the point is to compile
     * the XDK's sources, not to resolve against the prebuilt copies. Each module resolves its
     * dependencies against the library plus everything this build has produced so far, so a module
     * genuinely consumes the output of the compile that preceded it.</p>
     *
     * @param engine      the engine to compile on
     * @param repoLibrary the prebuilt library, for anything not built here
     * @param nodes       the modules to build
     * @param executor    single-threaded for a sequential build, virtual-thread-per-task for a
     *                    parallel one; the graph is identical either way
     *
     * @return the report
     */
    public static Report build(XtcEngine engine, ModuleRepository repoLibrary,
                               List<Node> nodes, ExecutorService executor) {
        var shared = new ConcurrentBuildRepository();
        var outcomes = new ArrayList<Outcome>();       // guarded by itself
        var futures  = new LinkedHashMap<String, CompletableFuture<Boolean>>();
        var start    = Instant.now();

        for (Node node : nodes) {
            CompletableFuture<?>[] after = node.deps().stream()
                    .map(futures::get)
                    .filter(Objects::nonNull)
                    .toArray(CompletableFuture[]::new);

            futures.put(node.moduleName(),
                    CompletableFuture.allOf(after)
                            // a dependency's failure is not this module's failure to report
                            .handle((ignored, error) -> depsOk(node, futures))
                            .thenApplyAsync(depsOk -> depsOk
                                    ? compileOne(engine, repoLibrary, shared, node, outcomes)
                                    : skip(node, outcomes), executor));
        }

        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
        s_lastOutput = shared;
        var wall = Duration.between(start, Instant.now());

        System.gc();
        var runtime = Runtime.getRuntime();
        return new Report(List.copyOf(outcomes), wall, runtime.totalMemory() - runtime.freeMemory());
    }

    private static boolean depsOk(Node node, Map<String, CompletableFuture<Boolean>> futures) {
        return node.deps().stream()
                .map(futures::get)
                .allMatch(f -> f == null || (!f.isCompletedExceptionally() && f.join()));
    }

    private static boolean compileOne(XtcEngine engine, ModuleRepository repoLibrary,
                                      ConcurrentBuildRepository shared, Node node,
                                      List<Outcome> outcomes) {
        var start = Instant.now();

        CompileResult result;
        try {
            // A snapshot of what is built so far, taken under the lock: the compile itself must not
            // hold it, or a parallel run would serialize on the whole compile rather than the copy.
            // Each compile gets its OWN empty front repository, with the shared accumulated
            // output and the library BEHIND it. That is not a style choice - it is what keeps
            // compiles isolated. LinkedRepository clones a module only when it finds it in a
            // repository after the first (`i > 0 && readThrough`), and caches the clone in the
            // front one. With a SHARED front repository, the first compile gets a private clone
            // and every later compile finds that same instance at position 0 and mutates it,
            // which is precisely what the clone existed to prevent. An empty front per compile
            // means every module this compile touches is cloned for this compile.
            //
            // The engine's prepared-library cache is weak-keyed, so a fresh repository per compile
            // no longer retains anything.
            ModuleRepository input =
                    new LinkedRepository(true, new BuildRepository(), shared, repoLibrary);
            // diagnostics come back on the CompileResult, so no separate listener is needed
            result = engine.compile(input,
                    new ModuleSource(node.source(), node.resourceDirs()));
        } catch (RuntimeException e) {
            outcomes.add(new Outcome(node.label(), Status.FAILED, 1, 0,
                    Duration.between(start, Instant.now()), e.toString()));
            return false;
        }

        var elapsed  = Duration.between(start, Instant.now());
        int errors   = (int) result.diagnostics().stream()
                .filter(d -> d.severity().ordinal() >= Severity.ERROR.ordinal()).count();
        int warnings = result.diagnostics().size() - errors;

        if (result.isSuccess()) {
            for (String name : result.buildRepository().getModuleNames()) {
                ModuleStructure compiled = result.buildRepository().loadModule(name);
                if (compiled != null) {
                    shared.storeModule(compiled);
                }
            }
        }

        String first = result.diagnostics().stream()
                .filter(d -> d.severity().ordinal() >= Severity.ERROR.ordinal())
                .map(Object::toString).findFirst().orElse(null);

        synchronized (outcomes) {
            outcomes.add(new Outcome(node.label(),
                    result.isSuccess() ? Status.BUILT : Status.FAILED,
                    errors, warnings, elapsed, first));
        }
        return result.isSuccess();
    }

    private static boolean skip(Node node, List<Outcome> outcomes) {
        synchronized (outcomes) {
            outcomes.add(new Outcome(node.label(), Status.SKIPPED, 0, 0, Duration.ZERO,
                    "a dependency did not build"));
        }
        return false;
    }
}
