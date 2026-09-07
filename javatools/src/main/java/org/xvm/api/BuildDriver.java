package org.xvm.api;

import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.ConcurrentBuildRepository;

import org.xvm.util.Severity;

/**
 * Compiles a set of modules in dependency order on one warm {@link XtcEngine}.
 *
 * <p>Scheduling is driven by the dependency graph rather than by levels: a module compiles the moment
 * every module it imports has compiled, not when its whole "level" has. That is expressed once, as a
 * {@link CompletableFuture} per module chained on its dependencies' futures, and <b>the executor decides the
 * concurrency</b> - {@link java.util.concurrent.Executors#newSingleThreadExecutor} runs the identical graph
 * sequentially, {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor} runs it as wide as the
 * graph allows. Sequential and parallel are therefore the same code, and a difference in their results is a
 * finding rather than a difference in how they were written.</p>
 *
 * <p><b>Do not pass a work-stealing pool.</b> The compiler keeps per-call state in thread-locals, which is
 * correct only while one task owns one thread. A {@code ForkJoinPool} runs several tasks on one carrier thread
 * and would bleed that state between them; a virtual thread per task keeps the guarantee, because each virtual
 * thread is a distinct {@link Thread} with its own thread-locals.</p>
 *
 * <h2>Isolation between compiles</h2>
 *
 * <p>Each compile gets its <b>own empty front repository</b>, with the shared accumulated output and the
 * library behind it. That is load-bearing, not tidiness: {@link LinkedRepository} clones a module only when it
 * finds it in a repository after the first, and caches the clone in the front one. Sharing a front repository
 * means the first compile caches a private clone there and every later compile finds that same instance,
 * uncloned, and mutates it - which is exactly what the clone exists to prevent.</p>
 */
public final class BuildDriver {
    private BuildDriver() {}

    /**
     * One module to build.
     *
     * @param name          the qualified module name, e.g. {@code json.xtclang.org}
     * @param label         a short name for reporting, e.g. {@code lib_json}
     * @param source        the module's root source file or source directory
     * @param resourceDirs  the module's resource roots; these are BUILD configuration and cannot be inferred
     *                      from the source tree, so a caller has to supply them the way Gradle supplies
     *                      {@code xcc -r}
     * @param deps          the qualified names of the modules this one imports, restricted to modules in the
     *                      same build; anything already on the module path is not a dependency here
     */
    public record Module(@NotNull String name, @NotNull String label, @NotNull Path source,
                         @NotNull List<Path> resourceDirs, @NotNull Set<String> deps) {
        public Module {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(source, "source");
            resourceDirs = List.copyOf(resourceDirs);
            deps         = Set.copyOf(deps);
        }
    }

    /** What happened to one module. */
    public enum Status { BUILT, FAILED, SKIPPED, CANCELLED }

    /**
     * @param label        the module's short name
     * @param status       what happened
     * @param errors       error-or-worse diagnostics
     * @param warnings     warning diagnostics
     * @param elapsed      wall time for this module alone
     * @param firstError   the first error's text, or null
     */
    public record Outcome(@NotNull String label, @NotNull Status status, int errors, int warnings,
                          @NotNull Duration elapsed, @Nullable String firstError) {}

    /**
     * @param outcomes  one per module, in completion order
     * @param wall      wall time for the whole build
     */
    public record Result(@NotNull List<Outcome> outcomes, @NotNull Duration wall) {
        public Result {
            outcomes = List.copyOf(outcomes);
        }

        public long built()     { return count(Status.BUILT); }
        public long failed()    { return count(Status.FAILED); }
        public long skipped()   { return count(Status.SKIPPED); }
        public long cancelled() { return count(Status.CANCELLED); }

        public boolean isSuccess() {
            return outcomes.stream().allMatch(o -> o.status() == Status.BUILT);
        }

        private long count(Status status) {
            return outcomes.stream().filter(o -> o.status() == status).count();
        }

        public @NotNull String render() {
            var sb = new StringBuilder(String.format("%d built, %d failed, %d skipped, %d cancelled of %d in %s%n",
                    built(), failed(), skipped(), cancelled(), outcomes.size(), wall()));
            for (Outcome o : outcomes) {
                sb.append(String.format("  %-9s %-24s %6d ms  %d err %d warn%s%n", o.status(), o.label(),
                        o.elapsed().toMillis(), o.errors(), o.warnings(),
                        o.firstError() == null ? "" : "  | " + o.firstError()));
            }
            return sb.toString();
        }
    }

    /**
     * Build every module, in dependency order, on the supplied executor.
     *
     * @param engine     the engine to compile on
     * @param library    the prebuilt library, for anything not built here
     * @param modules    the modules to build, in an order where each appears after its dependencies
     * @param executor   single-threaded for a sequential build, virtual-thread-per-task for a parallel one
     * @param failFast   true to stop the build at the first failure, including compiles already in flight;
     *                   cancellation is cooperative, through {@link ErrorListener#isAbortDesired()}, so an
     *                   in-flight compile stops at its next stage boundary rather than instantly
     *
     * @return the result
     */
    public static @NotNull Result build(@NotNull XtcEngine engine, @NotNull ModuleRepository library,
                                        @NotNull List<Module> modules, @NotNull ExecutorService executor,
                                        boolean failFast) {
        var shared   = new ConcurrentBuildRepository();
        var outcomes = Collections.synchronizedList(new ArrayList<Outcome>());
        var futures  = new LinkedHashMap<String, CompletableFuture<Boolean>>();
        var abort    = new AtomicBoolean();
        var start    = Instant.now();

        for (Module module : modules) {
            CompletableFuture<?>[] after = module.deps().stream()
                    .map(futures::get).filter(Objects::nonNull).toArray(CompletableFuture[]::new);

            futures.put(module.name(), CompletableFuture.allOf(after)
                    // a dependency's failure is not this module's failure to report
                    .handle((ignored, error) -> depsBuilt(module, futures))
                    .thenApplyAsync(depsBuilt -> {
                        if (abort.get()) {
                            return record(outcomes, module, Status.CANCELLED, 0, 0, Duration.ZERO,
                                    "the build was stopped by an earlier failure");
                        }
                        if (!depsBuilt) {
                            return record(outcomes, module, Status.SKIPPED, 0, 0, Duration.ZERO,
                                    "a dependency did not build");
                        }
                        boolean ok = compileOne(engine, library, shared, module, outcomes, abort);
                        if (!ok && failFast) {
                            abort.set(true);
                        }
                        return ok;
                    }, executor));
        }

        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
        return new Result(outcomes, Duration.between(start, Instant.now()));
    }

    private static boolean depsBuilt(Module module, java.util.Map<String, CompletableFuture<Boolean>> futures) {
        return module.deps().stream().map(futures::get)
                .allMatch(f -> f == null || (!f.isCompletedExceptionally() && f.join()));
    }

    private static boolean compileOne(XtcEngine engine, ModuleRepository library, ConcurrentBuildRepository shared,
                                      Module module, List<Outcome> outcomes, AtomicBoolean abort) {
        var start = Instant.now();

        // An empty front repository per compile, so every module this compile touches is cloned FOR it.
        var input = new LinkedRepository(true, new BuildRepository(), shared, library);
        var errs  = new AbortableListener(abort);

        XtcEngine.CompileResult result;
        try {
            result = engine.compile(errs, input, new XtcEngine.ModuleSource(module.source(), module.resourceDirs()));
        } catch (RuntimeException e) {
            return record(outcomes, module, Status.FAILED, 1, 0, Duration.between(start, Instant.now()),
                    e.toString());
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

        record(outcomes, module, result.isSuccess() ? Status.BUILT : Status.FAILED, errors, warnings, elapsed,
                first);
        return result.isSuccess();
    }

    private static boolean record(List<Outcome> outcomes, Module module, Status status, int errors, int warnings,
                                  Duration elapsed, String firstError) {
        outcomes.add(new Outcome(module.label(), status, errors, warnings, elapsed, firstError));
        return status == Status.BUILT;
    }

    /**
     * A listener that collects nothing and exists only to answer "should this compile stop".
     *
     * <p>This is how a build cancels work already in flight. Cancellation in the compiler is cooperative:
     * the stages consult {@link ErrorListener#isAbortDesired()} between phases, so a compile that has been
     * told to stop does so at the next boundary rather than being interrupted mid-phase. Interruption is not
     * an option here - the compiler mutates the structures it is compiling, and stopping it at an arbitrary
     * point would leave them half-built.</p>
     */
    private record AbortableListener(AtomicBoolean abort) implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            // diagnostics come back on the CompileResult; this listener exists for isAbortDesired
        }

        @Override
        public boolean isAbortDesired() {
            return abort.get();
        }
    }
}
