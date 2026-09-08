package org.xvm.api;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Collectors;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileRepository;
import org.xvm.asm.ErrorList;
import org.xvm.compiler.ast.AstNode;
import org.xvm.asm.ErrorListener;
import java.util.function.Predicate;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.TypeSystemThread;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import java.nio.file.Path;

import java.util.function.Function;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.tool.ModuleInfo;

import org.xvm.runtime.Container;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.MainContainer;
import org.xvm.runtime.template._native.reflect.xRTModuleTemplate;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.text.xString.StringHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Lazy;

import org.xvm.util.Severity;


/**
 * A first-class, resident Java embedding engine for compiling and running Ecstasy code - the
 * surface an LSP server, a watch-mode build daemon, or an in-process test runner should use
 * instead of the CLI-oriented {@link Connector}/{@code Launcher} path.
 *
 * <p>One engine boots the runtime ONCE (a {@link Runtime} plus the native "-1" container plane) and
 * is reused for the whole session, so compiles stay warm and runs share one native plane:</p>
 *
 * <pre>{@code
 * try (var engine = XtcEngine.builder().modulePath(xdkLibDir).build()) {
 *     CompileResult result = engine.compile("Hello", sourceText);   // in-memory, LSP-buffer friendly
 *     if (result.isSuccess()) {
 *         engine.run(result, "Hello").join();                       // nested child, event-driven completion
 *     } else {
 *         result.diagnostics().forEach(...);                        // structured errors/warnings
 *     }
 * }
 * }</pre>
 *
 * <p><b>Deployment model.</b> Runs are requests posted into a long-lived Ecstasy application
 * ({@code runner.xtclang.org}) hosted in container zero, which creates the run's container itself -
 * the model the LSPAPI branch proposes. This engine no longer builds containers from Java.
 * under the shared native plane - the sanctioned one-root-plus-nested-children model (the shape
 * {@code Runner.x} and the platform kernel use), reached from Java without a guest host module. This
 * is deliberately NOT sibling main containers over one plane, which leak, nor a fresh bootstrap per
 * run, which throws away warmth.</p>
 *
 * <p><b>Concurrency.</b> Compiles are independent (each builds its own module graph) and runs are
 * independent nested containers scheduled on the shared runtime executor, so both can be issued in
 * parallel - subject to the runtime's shared-lazy-state being concurrency-safe (the native-injection
 * singletons and pool caches this branch hardened).</p>
 *
 * <p><b>TODO - unified pipeline diagnostics.</b> Today {@link #compile} returns per-request
 * {@link Diagnostic}s and {@link #run} surfaces failures through the completion future's exceptional
 * value. An LSP ultimately wants ONE diagnostic/logging channel spanning the whole compile-to-run
 * pipeline, with request correlation - a single sink that a compile's {@link org.xvm.asm.ErrorListener},
 * a run's unhandled-exception handler, and engine logging all feed, tagged by request id. The intended
 * shape: an engine-level {@code DiagnosticSink} interface passed to {@link Builder}, adapted into an
 * {@code ErrorListener} for compiles and registered as the run container's unhandled-exception/console
 * target, so the host receives a coherent, ordered, source-anchored stream across both. That belongs
 * in a follow-up so the per-request shape here does not calcify into the contract.</p>
 */
public final class XtcEngine
        implements AutoCloseable {
    private final ModuleRepository repoLibrary;
    private final @NotNull ErrorListener diagnosticSink;

    /**
     * The runtime plane: a started {@link Runtime} and the {@link NativeContainer} rooted on it.
     *
     * <p>Booted on first use rather than in the constructor, because <b>compiling needs no
     * runtime</b>. Booting eagerly cost every caller a full native-template initialization they
     * might never use, and made the engine unusable for the case that wants it most: a compile
     * during a build of the XDK itself, where the boot-strap library the container requires
     * ({@code _native.xtclang.org}) is an output of the very build in progress. That failed with
     * "Missing boot-strap library" from the constructor, before any source was read.</p>
     *
     * <p>{@link Lazy.Bound} rather than {@code Lazy.of(this::boot)}: a supplier capturing
     * {@code this} in a field initializer is a constructor this-escape, which this build makes a
     * fatal lint.</p>
     */
    private final Lazy.Bound<XtcEngine, InterpreterConnector> f_connector =
            Lazy.ofBound(XtcEngine::bootConnector);

    /** The Ecstasy application hosted in container zero that owns creating run containers. */
    private static final String RUNNER_MODULE = "runner.xtclang.org";

    private XtcEngine(@NotNull ModuleRepository repoLibrary, @NotNull ErrorListener diagnosticSink) {
        this.repoLibrary     = Objects.requireNonNull(repoLibrary, "repoLibrary");
        this.diagnosticSink  = Objects.requireNonNull(diagnosticSink, "diagnosticSink");
    }

    /**
     * Start the runtime and boot the native container this engine's runs are rooted on.
     *
     * <p>Lazy on purpose: compiling must never require a bootable runtime, because the library a
     * container needs can be an output of the very build that is compiling. Only runs touch it.</p>
     */
    private InterpreterConnector bootConnector() {
        var connector = new InterpreterConnector(repoLibrary, diagnosticSink);
        connector.loadModule(RUNNER_MODULE);
        connector.start(null);
        return connector;
    }

    /**
     * @return the native container every run is rooted on
     */
    private NativeContainer nativeContainer() {
        return f_connector.get(this).getNativeContainer();
    }

    /**
     * Libraries this engine has already linked and NakedRef-injected, so it does not redo either
     * per compile. Keyed by the repository, because a compile may name its own
     * (see {@link #compile(ModuleRepository, ModuleSource...)}).
     *
     * <p>T1 of the parallel-compile plan. Before this, every compile cloned the whole ecstasy
     * module - 60-72ms warm, about a fifth of a warm compile - purely so it had a private copy to
     * link and inject into. Doing that work once against the shared library makes the copy
     * unnecessary.</p>
     */
    /**
     * Keyed WEAKLY on the input repository, because the key decides the lifetime.
     *
     * <p>A caller that compiles against the engine's own library passes the same repository every
     * time; that repository is a field of this engine, so its entry is strongly reachable and the
     * library is prepared exactly once - which is the whole point of T1.
     *
     * <p>A caller that feeds one compile's output into the next has to pass a composite of the
     * library plus what it has built, and the natural way to write that is a new repository per
     * compile. Under a strong map that retained a fully prepared library - about 16 MB - per
     * compile, for the life of the engine: measured at 44 retained libraries for 44 compiles while
     * building the XDK, and it is the first confirmed part of the T15 soak leak. Weak keys let each
     * one go when the caller drops the repository, without the caller having to know that reusing
     * the repository object was load-bearing.
     *
     * <p>Safe to key weakly because the value cannot reach the key: a {@code PreparedLibrary} holds
     * a {@code BuildRepository} of {@code ModuleStructure}s, and a module has no reference back to
     * the repository it was loaded from.
     */
    private final Map<ModuleRepository, PreparedLibrary> f_mapPreparedLibraries =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * A library that has been linked, NakedRef-injected and warmed once, and is now HELD.
     *
     * @param repo          the retained module instances; compiles resolve library modules from
     *                      here, never from the originating repository
     * @param typeNakedRef  the NakedRef type taken from the turtle prototype in that library
     */
    private record PreparedLibrary(@NotNull ModuleRepository repo, @NotNull TypeConstant typeNakedRef) {}

    /**
     * Link, NakedRef-inject and warm a library once, then HOLD the module instances for the life of
     * the engine.
     *
     * <p>Holding them is the whole point, and it is not an optimization. Preparation mutates the
     * library - it links modules and sets the NakedRef type on each pool - and an on-disk
     * repository treats a mutated module as stale: {@code DirRepository.ensureModule} does
     *
     * <pre>if (module == null || module.isModified()) { module = tryLoad(); }</pre>
     *
     * so the next {@code loadModule} DISCARDS the prepared structure and re-reads it from disk.
     * The replacement has a fresh {@code ConstantPool} with no NakedRef type, and the compile that
     * gets it dies much later, in an unrelated phase, with "Mack module (javatools_turtle) is
     * missing". Preparation silently undone by the act of using it.
     *
     * <p>That is also the deeper reason the per-compile clone existed at all: its comment says
     * "create a copy, allowing the compiler to mutate the repos[0] contents". The compiler mutates
     * what it compiles against. Serving ONE library to many compiles therefore requires owning the
     * instances, not asking for them again.
     */
    private @NotNull PreparedLibrary ensureLibraryPrepared(@NotNull ModuleRepository repoLib) {
        PreparedLibrary prepared = f_mapPreparedLibraries.get(repoLib);
        if (prepared != null) {
            return prepared;
        }

        // Prepared outside the map's lock: this links, injects and warms an entire library, and
        // holding a lock across it would serialize every compile in the engine behind the first
        // one. Two callers racing a cold library each prepare one and the last put wins, which
        // costs a duplicate preparation and is correct - preparation is idempotent, and the loser's
        // copy is unreachable the moment it is replaced.
        prelinkSystemLibraries(repoLib);
        var repoHeld = new BuildRepository();
        TypeConstant typeNakedRef = injectNakedRefIntoLibrary(repoLib, repoHeld);
        warmRootObject(repoHeld);

        prepared = new PreparedLibrary(repoHeld, typeNakedRef);
        f_mapPreparedLibraries.put(repoLib, prepared);
        return prepared;
    }

    /**
     * Set the NakedRef type on the library's own modules. The per-compile
     * {@link #injectNakedRefType} still covers the modules being compiled; this covers the ones
     * they compile against, which used to be reached only because a clone of each had been dropped
     * into the compile's build repository.
     */
    private static TypeConstant injectNakedRefIntoLibrary(
            ModuleRepository repoLib, BuildRepository repoHeld) {
        ModuleStructure moduleTurtle = repoLib.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle == null) {
            // Not survivable, and not something to discover later. Returning null here used to
            // leave every pool without a NakedRef type, and the failure surfaced deep in a
            // validation phase as "Mack module is missing" - describing the symptom, in the wrong
            // place, with nothing pointing back to preparation.
            throw new IllegalStateException("cannot prepare a library without the turtle module ("
                    + Constants.TURTLE_MODULE + "); module path=" + repoLib);
        }

        TypeConstant typeNakedRef =
                ((ClassStructure) moduleTurtle.getChild("NakedRef")).getFormalType();
        for (var sModule : repoLib.getModuleNames()) {
            ModuleStructure module = repoLib.loadModule(sModule);
            if (module != null) {
                module.getConstantPool().setNakedRefType(typeNakedRef);
                // Retain THIS instance. See ensureLibraryPrepared: preparation modifies the module,
                // and an on-disk repository re-reads a modified module from scratch.
                repoHeld.storeModule(module);
            }
        }
        return typeNakedRef;
    }

    /**
     * Build the root Object's TypeInfo once, here, while preparation is still single-threaded.
     *
     * <p>Building it is a one-time DESTRUCTIVE step: on success {@code ensureObjectTypeInfo}
     * discards EVERY other TypeInfo in the pool, because anything built while Object itself was
     * incomplete may have been flattened against a partial Object. That is correct and harmless
     * when one thread owns the pool. It is ruinous when several share one, which is exactly what
     * serving a single prepared library to concurrent compiles creates: the sweep clears types
     * other threads are in the middle of using, and a type flattened across that moment can be
     * marked Complete while missing the members it should have inherited. It surfaces far away, as
     * "Could not find a matching method or function" against a perfectly good library type.
     *
     * <p>Doing it during preparation means the sweep happens exactly once, with nobody else in the
     * pool. This is NOT the blanket "warm every type" that was tried and rejected earlier - that
     * one swallowed failures and cached half-built TypeInfos. This warms one type, the one whose
     * construction is documented as special, and lets any failure propagate.
     */
    private static void warmRootObject(ModuleRepository repoLib) {
        ModuleStructure moduleEcstasy = repoLib.loadModule(Constants.ECSTASY_MODULE);
        if (moduleEcstasy != null) {
            // typeInfo(), not ensureTypeInfo(PROBE): this is the COMPUTE half - a question, not
            // an assertion - and the types it validates are owned by no particular request. The
            // no-listener overload is where that idiom is spelled, so this says it by choosing the
            // method rather than by handing in a listener that means "do not listen".
            moduleEcstasy.getConstantPool().typeObject().typeInfo();
        }
    }

    /**
     * A snapshot of every cache that a resident host can grow without bound, plus the heap.
     *
     * <p>Sharing one prepared library instead of cloning it per compile means the library's caches
     * now live for the lifetime of the ENGINE rather than the request. Any of them that is keyed by
     * something a request brought with it will therefore accumulate, and the first symptom is not a
     * wrong answer - it is an {@code OutOfMemoryError} several hundred compiles later, by which
     * point the cause is invisible. This exists so growth is a number you can watch instead of a
     * crash you discover.
     *
     * <p>Read it as a TREND, not a value: what matters is whether a column moves between requests
     * while the workload is constant. A pool count that climbs while the caches stay flat means
     * per-request structures are being retained by something; caches that climb mean the library
     * itself is accumulating.
     *
     * <p>Requests a garbage collection before sampling the heap, so the figure reflects retained
     * memory rather than allocation churn. That makes this a diagnostic to call between requests,
     * never inside one.
     *
     * @return one line per shared library module, plus a summary line
     */
    public @NotNull String cacheReport() {
        var sb = new StringBuilder(512);
        // java.lang.Runtime spelled out: "Runtime" in this file is org.xvm.runtime.Runtime, which
        // is imported, so this is the one case where the qualified name is the only option.
        java.lang.Runtime jvm = java.lang.Runtime.getRuntime();

        // Collect first. Sampling totalMemory-freeMemory without it measures "allocated since the
        // last collection", which for a compiler - an allocation-heavy, mostly-garbage workload -
        // says nothing about what is RETAINED, and retention is the only thing this report is for.
        // A hint, not a guarantee, so the figure is still approximate; it is a trend line.
        jvm.gc();
        long cHeap = jvm.totalMemory() - jvm.freeMemory();
        int  cInfoTotal = 0;
        int  cRelTotal  = 0;

        // Report the HELD instances. Asking repoLibrary again would re-read any module a compile
        // had modified, so every figure would describe a structure nothing is using - which is
        // exactly how this report once showed a library module with zero cached TypeInfos while a
        // compile was actively building against it.
        ModuleRepository repoReport = f_mapPreparedLibraries.values().stream().findFirst()
                .map(PreparedLibrary::repo).orElse(repoLibrary);
        // The library's own pools, so "outside the library" can be distinguished from the normal
        // cross-module references inside it.
        var setLibraryPools = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<ConstantPool, Boolean>());
        for (var sModule : repoReport.getModuleNames()) {
            ModuleStructure moduleLib = repoReport.loadModule(sModule);
            if (moduleLib != null) {
                setLibraryPools.add(moduleLib.getConstantPool());
            }
        }
        Predicate<ConstantPool> isLibrary = setLibraryPools::contains;

        for (var sModule : repoReport.getModuleNames()) {
            ModuleStructure module = repoReport.loadModule(sModule);
            if (module == null) {
                continue;
            }
            ConstantPool pool       = module.getConstantPool();
            int          cRelations = pool.types().mapToInt(TypeConstant::getRelationCacheSize).sum();
            int          cForeign   = pool.types().mapToInt(TypeConstant::countForeignRelationKeys).sum();
            int          cRefOut    = pool.countRefTypeKeysOutside(isLibrary);
            int          cOutside   = pool.types()
                                          .mapToInt(type -> type.countTypeInfoMembersOutside(isLibrary))
                                          .filter(n -> n > 0).sum();
            int          cInfos     = pool.getCachedTypeInfoCount();
            cInfoTotal += cInfos;
            cRelTotal  += cRelations;
            sb.append(String.format(
                    "  %-28s constants=%-7d typeInfos=%-6d relations=%-6d"
                            + " foreignRel=%-5d outsideLib=%-6d refsOut=%-5d sweeps=%d%n",
                    sModule, pool.size(), cInfos, cRelations, cForeign, cOutside, cRefOut,
                    pool.getObjectSweepCount()));
        }

        // Report the CEILING alongside the usage. Without it "heap=505MB" is unreadable: it is
        // healthy against a 8GB ceiling and terminal against Gradle's 512m default for test
        // workers, and the difference between those two readings was several hours.
        long cMax = jvm.maxMemory();
        sb.append(String.format("  TOTAL typeInfos=%d relations=%d | poolsCreated=%d "
                        + "| heap=%dMB of %dMB (%d%%)%n",
                cInfoTotal, cRelTotal, ConstantPool.getPoolsCreated(),
                cHeap / (1024 * 1024), cMax / (1024 * 1024), cHeap * 100 / cMax));
        return sb.toString();
    }

    /**
     * Drop every prepared library, so the next compile links, injects and warms afresh.
     *
     * <p>For a host that has observed the XDK on disk change. The prepared library is deliberately
     * held STRONGLY and never invalidated on its own, and it is worth being explicit about why,
     * because the obvious alternatives are both wrong:
     *
     * <ul>
     * <li><b>Weak or soft references.</b> Collecting a prepared module does not produce a cache
     *     miss - it produces a DIFFERENT, unprepared module, because re-reading the file cannot
     *     restore the NakedRef injection, the linked modules or the built TypeInfos. That is the
     *     defect T10 fixed, and holding it weakly would reintroduce it as something that only
     *     happens under memory pressure.</li>
     * <li><b>Re-reading when the structure is dirty.</b> That is what
     *     {@code DirRepository.ensureModule} does, and it is correct THERE: a repository is a view
     *     of what is on disk, so a modified structure is stale by definition. It is exactly wrong
     *     for a working set, where "modified" is the point.</li>
     * </ul>
     *
     * <p>So staleness is the caller's to detect - it is the one watching the filesystem - and this
     * is coarse on purpose: the unit is the whole library, because a partially re-prepared library
     * is the state that produced the original bug. It is safe to call between requests and not
     * during one.
     */
    public void discardPreparedLibraries() {
        f_mapPreparedLibraries.clear();
    }

    private NativeContainer containerNative() {
        return nativeContainer();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    // ----- compile -------------------------------------------------------------------------------

    /**
     * Compile a single in-memory module source (an LSP buffer).
     *
     * @param sModuleName  the module's name (for diagnostics/labels)
     * @param sSource      the module source text
     *
     * @return the compile result: the compiled modules (empty on failure) plus all diagnostics
     */
    public @NotNull CompileResult compile(@NotNull String sModuleName, @NotNull String sSource) {
        return compile(new SourceUnit(sModuleName, sSource));
    }

    /**
     * Compile one or more in-memory module sources together, resolving cross-module references among
     * them. Each {@link SourceUnit} is one module (name plus source text); pass any number.
     *
     * <p>The result's {@link CompileResult#buildRepository() build repository} is an in-memory
     * {@code ModuleRepository} holding the compiled modules: run them straight from it, or flush it -
     * fully or partially - into any sink repository (a {@code DirRepository} is the disk sink). There is
     * no disk-specific path, because an {@code .xtc} is just a serialized byte stream whose destination
     * is the caller's business.</p>
     *
     * <p>Inputs are immutable value records passed as varargs, never a mutable collection: nothing handed
     * in can be aliased or mutated behind the engine's back.</p>
     *
     * <p>TODO - source trees: an LSP workspace also has on-disk module DIRECTORY TREES (a module as a
     * directory of {@code .x} files). Supporting those means building the module node tree with
     * {@link org.xvm.tool.ModuleInfo} (which already walks a source directory into a parsed
     * {@code TypeCompositionStatement} node tree) and feeding the module statements into the same
     * pipeline below - a {@code compile(Path...)} overload on top of ModuleInfo, not a shell-out to the
     * Launcher. A {@code Path} is one convenient source, not the model: the content-based form here is
     * the core, so the API assumes no local compilation environment.</p>
     *
     * <p>TODO - granularity: this compiles a whole module, but the shape must NOT preclude method-level /
     * incremental recompilation later. That is driven EXTERNALLY - the build's incremental compiler
     * decides what to recompile and re-invokes at whatever granularity; the compiler gaining a stable
     * per-method recompilation entry point is a separate project. What this API owes, at any granularity
     * and however often it is called in parallel or sequence, is that a compile must NOT mutate or corrupt
     * shared/global runtime state - exactly why the pool-publication marker and synthesis windows in this
     * runtime exist. Keep that invariant and every granularity, ordering, and concurrency stays possible.</p>
     *
     * @param units  the module sources to compile (name + source), as immutable value records
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull SourceUnit @NotNull... units) {
        return compile(ErrorListener.BLACKHOLE, units);
    }

    /**
     * Compile one or more in-memory module sources, streaming every diagnostic to the CALLER's own
     * {@link ErrorListener} as it is produced, in addition to collecting them into the returned
     * {@link CompileResult}.
     *
     * <p>This is the {@code ToolConnector}-shaped entry point (cpurdy/LSPAPI passes an
     * {@code ErrorListener} to every compile/run): a long-running host - an LSP server, a build
     * daemon, a test runner - already owns a diagnostic sink and wants messages as they happen,
     * correlated with its own request id, rather than only as a batch at the end. The returned
     * result still carries the full {@link Diagnostic} list, so a caller that wants the batch form
     * (or wants both) is unaffected.</p>
     *
     * <p>The listener is never null: pass {@link ErrorListener#BLACKHOLE} (what the no-listener
     * overload uses) to discard the stream. Making it total removes the null-check-at-every-use
     * pattern that the glued-on {@code ErrorListener} plumbing spread through the older code.</p>
     *
     * @param errsCaller  the caller's diagnostic sink; {@link ErrorListener#BLACKHOLE} to discard
     * @param units       the module sources to compile
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull ErrorListener errsCaller,
                                          @NotNull SourceUnit @NotNull... units) {
        Objects.requireNonNull(errsCaller, "errsCaller (use ErrorListener.BLACKHOLE to discard)");
        Objects.requireNonNull(units, "units");

        var event     = new CompileEvent();
        event.modules = Arrays.stream(units).map(SourceUnit::moduleName).collect(Collectors.joining(","));
        event.begin();
        try {
            return compileInternal(errsCaller, event, errs -> parseSources(errs, List.of(units)));
        } finally {
            event.commit();
        }
    }

    /**
     * Compile modules that live on disk, as source files or source directory trees.
     *
     * <p>This is the entry point a build tool wants: it hands over the same {@code .x} paths the CLI
     * would be given, and the module node tree is built through {@link ModuleInfo} - the same walk the
     * CLI compiler performs - instead of shelling out to a new JVM. Everything after parsing is the
     * pipeline the in-memory {@link #compile(SourceUnit...)} form uses, so a path-compiled module and a
     * buffer-compiled one are produced identically.</p>
     *
     * @param paths  the module source files or directories to compile
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull Path @NotNull... paths) {
        return compile(ErrorListener.BLACKHOLE, paths);
    }

    /**
     * Compile on-disk modules, each with its own resource root.
     *
     * <p>A module's resources are not a section bolted onto the artifact - the compiler inlines them
     * while compiling, because Ecstasy source can name a file or directory literal and that content
     * is baked into the module as constants. So a module with resources cannot be compiled correctly
     * without knowing where to resolve those literals, which is what
     * {@link ModuleSource#resourceDirs} supplies.</p>
     *
     * @param sources  the modules to compile, each pairing a source path with its resource root
     *
     * @return the compile result
     */
    /**
     * Compile against a caller-supplied library, rather than the one this engine was built with.
     *
     * <p>The upstream {@code LspSupport.compile} takes its input repository per call for the same
     * reason: a resident host serves many requests, and which library a request compiles against
     * is a property of the request, not of the host. Binding it to the engine forces one engine
     * per module path - which is exactly why the Gradle plugin keeps a
     * {@code Map<List<File>, XtcEngine>}.</p>
     *
     * <p>Concurrency is unaffected either way: a read-through {@link LinkedRepository} clones each
     * library module it serves into that compile's own {@link BuildRepository}, so the structures
     * the compiler mutates are per-compile whichever repository they came from.</p>
     *
     * @param repoInput  the library to resolve dependencies against
     * @param sources    the modules to compile
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull ModuleRepository repoInput,
                                          @NotNull ModuleSource @NotNull... sources) {
        return compile(ErrorListener.BLACKHOLE, repoInput, sources);
    }

    /**
     * Compile against a caller-supplied library, reporting diagnostics as they are logged.
     *
     * <p>The combination matters: a caller that needs its own input repository - anything compiling
     * a dependency graph, where each module resolves against what the ones before it produced - had
     * no way to supply a listener, and therefore no way to see diagnostics as they happen and no
     * way to <b>stop</b>. Cancellation in this compiler is cooperative and runs through
     * {@link ErrorListener#isAbortDesired()}, which the stages consult (see {@code runPhase}), so a
     * caller with no listener cannot abort a compile it has started.</p>
     *
     * @param errsCaller  the caller's diagnostic sink, and its means of aborting
     * @param repoInput   the library to resolve dependencies against
     * @param sources     the modules to compile
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull ErrorListener errsCaller, @NotNull ModuleRepository repoInput,
                                          @NotNull ModuleSource @NotNull... sources) {
        Objects.requireNonNull(errsCaller, "errsCaller (use ErrorListener.BLACKHOLE to discard)");
        Objects.requireNonNull(repoInput, "repoInput");
        Objects.requireNonNull(sources, "sources");

        var event     = new CompileEvent();
        event.modules = Arrays.stream(sources)
                .map(source -> source.source().toString()).collect(Collectors.joining(","));
        event.begin();
        try {
            return compileInternal(errsCaller, event,
                    errs -> parseModuleSources(errs, List.of(sources)), repoInput);
        } finally {
            event.commit();
        }
    }

    public @NotNull CompileResult compile(@NotNull ModuleSource @NotNull... sources) {
        return compile(ErrorListener.BLACKHOLE, sources);
    }

    /**
     * Compile on-disk modules with resource roots, reporting diagnostics to the caller's listener.
     *
     * @param errsCaller  the caller's diagnostic sink (use {@link ErrorListener#BLACKHOLE} to discard)
     * @param sources     the modules to compile, each pairing a source path with its resource root
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull ErrorListener errsCaller,
                                          @NotNull ModuleSource @NotNull... sources) {
        Objects.requireNonNull(errsCaller, "errsCaller (use ErrorListener.BLACKHOLE to discard)");
        Objects.requireNonNull(sources, "sources");

        var event     = new CompileEvent();
        event.modules = Arrays.stream(sources)
                .map(source -> source.source().toString()).collect(Collectors.joining(","));
        event.begin();
        try {
            return compileInternal(errsCaller, event, errs -> parseModuleSources(errs, List.of(sources)));
        } finally {
            event.commit();
        }
    }

    /**
     * Compile on-disk modules, reporting diagnostics to the caller's listener as they are logged.
     *
     * @param errsCaller  the caller's diagnostic sink (use {@link ErrorListener#BLACKHOLE} to discard)
     * @param paths       the module source files or directories to compile
     *
     * @return the compile result
     */
    public @NotNull CompileResult compile(@NotNull ErrorListener errsCaller,
                                          @NotNull Path @NotNull... paths) {
        Objects.requireNonNull(errsCaller, "errsCaller (use ErrorListener.BLACKHOLE to discard)");
        Objects.requireNonNull(paths, "paths");

        var event     = new CompileEvent();
        event.modules = Arrays.stream(paths).map(Path::toString).collect(Collectors.joining(","));
        event.begin();
        try {
            return compileInternal(errsCaller, event, errs -> parseSourceTrees(errs, List.of(paths)));
        } finally {
            event.commit();
        }
    }

    /**
     * Parse in-memory sources into module node trees.
     */
    private static List<TypeCompositionStatement> parseSources(ErrorListener errs, List<SourceUnit> units) {
        return units.stream().map(unit -> parseModule(unit.source(), errs)).filter(Objects::nonNull).toList();
    }

    /**
     * Parse on-disk sources into module node trees, through the same {@link ModuleInfo} walk the CLI
     * compiler uses, so a directory-tree module is handled exactly as the CLI handles it.
     */
    private static List<TypeCompositionStatement> parseSourceTrees(ErrorListener errs, List<Path> paths) {
        return parseModuleSources(errs, paths.stream().map(ModuleSource::of).toList());
    }

    /**
     * Parse on-disk sources into module node trees, through the same {@link ModuleInfo} walk the CLI
     * compiler uses, resolving each module's file/directory literals against its resource root.
     */
    private static List<TypeCompositionStatement> parseModuleSources(ErrorListener errs, List<ModuleSource> sources) {
        var listModules = new ArrayList<TypeCompositionStatement>(sources.size());
        for (ModuleSource source : sources) {
            List<File> listResource = source.resourceDirs().stream().map(Path::toFile).toList();
            // deduce=false, deliberately. The engine is handed exact paths by its caller - a build
            // tool knows precisely which sources and resource roots it means - so inferring
            // locations from filesystem convention can only turn a known input into a guessed one.
            var        info         = new ModuleInfo(source.source().toFile(), false,
                                          listResource.isEmpty() ? null : listResource, null);
            ModuleInfo.Node node = info.getSourceTree(errs);
            if (node != null) {
                listModules.add(node.type());
            }
        }
        return listModules;
    }

    private @NotNull CompileResult compileInternal(
            @NotNull ErrorListener errsCaller,
            @NotNull CompileEvent event,
            @NotNull Function<ErrorListener, List<TypeCompositionStatement>> fnParse) {
        return compileInternal(errsCaller, event, fnParse, repoLibrary);
    }

    private @NotNull CompileResult compileInternal(
            @NotNull ErrorListener errsCaller,
            @NotNull CompileEvent event,
            @NotNull Function<ErrorListener, List<TypeCompositionStatement>> fnParse,
            @NotNull ModuleRepository repoInput) {
        var errsCollect = ErrorList.unlimited();
        // Always tee - never a null listener to test for. The ErrorList stays the PRIMARY so its
        // abort/serious-error semantics keep driving the compiler stages; the caller's sink (possibly
        // BLACKHOLE) just observes.
        ErrorListener errs = new TeeErrorListener(errsCollect, errsCaller);
        var repoBuild = new BuildRepository();

        // A read-through library repo with the build repo at the front: exactly the shape the CLI
        // Launcher configures. "Read-through" means every library module loaded through it is cached
        // into repoBuild - which is how the turtle prototype ends up co-resident with the modules being
        // compiled so its NakedRef type can be injected across them all (see below). The caller never
        // has to name the turtle/native-bridge modules per request the way the CLI does with -L flags;
        // the engine already resolved them when it booted its native container from this same path.
        // The library is linked and injected once per engine, so a compile no longer needs a
        // private clone of it: read-through is off, and repoBuild holds only what is being
        // compiled. See ensureLibraryPrepared.
        PreparedLibrary library = ensureLibraryPrepared(repoInput);
        TypeConstant typeNakedRef = library.typeNakedRef();
        var repoCompile = new LinkedRepository(false, repoBuild, library.repo());
        var compilers   = new ArrayList<Compiler>();


        // stage 1: parse each source and create its initial module structure in the build repo
        for (var stmtModule : fnParse.apply(errs)) {
            var           compiler = new Compiler(stmtModule, errs);
            FileStructure struct   = compiler.generateInitialFileStructure();
            if (struct == null) {
                continue;
            }
            try {
                repoBuild.storeModule(struct.getModule());
            } catch (Exception e) {
                throw new IllegalStateException("storing module " + stmtModule.getName(), e);
            }
            compilers.add(compiler);
        }

        // stages 2-5: link, resolve names, inject turtle, validate, generate code - IN THIS ORDER.
        // The ordering matches org.xvm.tool.Compiler exactly and is load-bearing. NakedRef must be
        // injected AFTER link+resolveNames because each compiled module AND each library dependency it
        // links against (ecstasy, cached into repoBuild by the read-through load) needs the type set on
        // its own pool before validation builds Ref TypeInfo - otherwise it fails "Mack module is
        // missing". On-disk modules carry this baked in at build time; in-memory ones inject it here.
        // Between phases, stop if the previous one produced a serious error - the CLI's
        // flushAndCheckErrors. Without this the engine ran every phase unconditionally, so an
        // ordinary user error could leave a compiler short of the stage the NEXT phase asserts:
        // "Cannot find a module" left it at Resolving, and generateCode's ensureReached(Validated)
        // then threw IllegalStateException. A missing dependency has to be a diagnostic, not a
        // crash.
        compilers.forEach(compiler -> compiler.linkModules(repoCompile));
        if (!errsCollect.hasSeriousErrors()) {
            runPhase(compilers, Compiler::resolveNames);
        }
        if (!errsCollect.hasSeriousErrors()) {
            injectNakedRefType(repoBuild, typeNakedRef);
            runPhase(compilers, Compiler::validateExpressions);
        }
        if (!errsCollect.hasSeriousErrors()) {
            runPhase(compilers, Compiler::generateCode);
        }

        // On success, ASSEMBLE each compiled module (round-trip it through serialization) into a fresh
        // result repository. A freshly-compiled in-memory FileStructure still holds ops whose arguments
        // are unresolved AST references; assembly is what rewrites them into constant-pool indices and
        // finalizes the pool - exactly what the CLI gets for free by writing the module to a .xtc file.
        // Without this, a run fails at the first op with an AssertionError on the constant id. Running
        // the assembled (deserialized) module is then identical to loading a module off disk.
        var modules    = new ArrayList<ModuleConstant>();
        var repoResult = new BuildRepository();
        if (!errsCollect.hasSeriousErrors()) {
            for (var compiler : compilers) {
                FileStructure struct = compiler.getFileStructure();
                if (struct != null) {
                    ModuleStructure assembled = assemble(struct);
                    repoResult.storeModule(assembled);
                    modules.add(assembled.getIdentityConstant());
                }
            }
        }
        // A request must leave the thread as it found it. This thread is going straight back into a
        // pool to serve the NEXT request, and anything left behind - a type still marked as being
        // built, work still owed - belongs to a compile that has finished, referring to a pool that
        // is now somebody else's. Draining it later is how one request ends up building a TypeInfo
        // for another request's type and interning into a pool that request is assembling (T12).
        //
        // Reported rather than thrown: the compile itself succeeded or failed on its own merits and
        // the caller should get that answer, but the leak is a defect and must not be silent.
        String sLeak = TypeSystemThread.current().describeLeak();
        if (sLeak != null) {
            errs.warn("TRACE-TI",
                    "compile finished leaving thread-local type-system work behind: " + sLeak);
        }

        var result = new CompileResult(List.copyOf(modules), diagnostics(errsCollect), repoResult);
        event.compiled    = result.modules().size();
        event.diagnostics = result.diagnostics().size();
        event.success     = result.isSuccess();
        return result;
    }

    /**
     * Assemble a freshly-compiled FileStructure into a runnable module by round-tripping it through
     * serialization: {@code writeTo} reregisters constants and finalizes op arguments into pool
     * indices, and reading the bytes back yields a module in the same shape the runtime loads from a
     * {@code .xtc} on disk. This is the in-memory equivalent of the CLI's emit-to-disk step.
     */
    private static ModuleStructure assemble(FileStructure struct) {
        try {
            var bytes = new ByteArrayOutputStream();
            struct.writeTo(bytes);
            return new FileStructure(new ByteArrayInputStream(bytes.toByteArray())).getModule();
        } catch (IOException e) {
            throw new IllegalStateException("assembling module " + struct.getModuleId(), e);
        }
    }

    private static @Nullable TypeCompositionStatement parseModule(String sSource, ErrorListener errs) {
        Statement stmt;
        try {
            stmt = new Parser(new Source(sSource), errs).parseSource();
        } catch (CompilerException e) {
            // an unrecoverable parse (half-typed LSP buffers hit this constantly) - the parser logged
            // what it could to errs; do not let it escape as a hard exception
            return null;
        }
        if (stmt == null) {
            return null;
        }
        // a source file parses to a StatementBlock whose final statement is the module
        Statement stmtModule = stmt instanceof StatementBlock block && !block.getStatements().isEmpty()
                ? block.getStatements().getLast()
                : stmt;
        return stmtModule instanceof TypeCompositionStatement tcs ? tcs : null;
    }

    /**
     * Force-load and link the system libraries (ecstasy + turtle prototype) through the read-through
     * compile repo, so they are cached co-resident in the build repo before anything compiles against
     * them. This is the in-process equivalent of the Launcher's {@code prelinkSystemLibraries}, and it
     * is what lets the engine supply turtle/native from its own configured module path instead of
     * making every caller name those modules explicitly.
     */
    private void prelinkSystemLibraries(ModuleRepository repo) {
        for (var sModule : List.of(Constants.ECSTASY_MODULE, Constants.TURTLE_MODULE)) {
            ModuleStructure module = repo.loadModule(sModule);
            if (module == null) {
                diagnosticSink.error(Compiler.MODULE_MISSING, sModule);
                continue;
            }

            FileStructure struct = module.getFileStructure();
            if (struct == null) {
                continue;
            }

            // The library's pool reports to RUNTIME and is left that way. This used to redirect it
            // at the engine's own sink, which was wrong in two directions: the engine does not own
            // this pool - it comes out of the caller's repository, so two engines over one
            // repository would fight over it, last writer winning and each then hearing the other's
            // library diagnostics - and it did not do what it looked like it did, because a
            // compile's own diagnostics never resolve through here. Constants are adopted by the
            // pool that registers them (ConstantPool.register calls adoptedBy), so a compile
            // referencing a library type asks ITS pool, holding ITS listener.
            //
            // What the redirect DID cover is this: linkModules reports by RETURN VALUE, naming the
            // module it could not find, and that value was discarded. A module path missing a
            // dependency therefore prepared "successfully" and failed later, during a compile,
            // as an unresolved name in library code that the user did not write. The engine's own
            // sink is the right place for that, and passing it the outcome of an operation the
            // engine invoked is the shape this framework wants - who asks, hears - rather than
            // reaching into a structure the engine does not own.
            ModuleConstant idMissing = struct.linkModules(repo, false);
            if (idMissing != null) {
                diagnosticSink.error(Compiler.MODULE_MISSING, idMissing.getName());
            }
        }
    }

    /**
     * Set the NakedRef type from the turtle prototype on the pool of every module in the build repo -
     * the compiled modules AND the library dependencies the read-through load cached alongside them.
     * This mirrors {@code org.xvm.tool.Compiler.injectNativeTurtle} exactly: the compiler needs the
     * NakedRef type available to each ConstantPool that participates in building Ref TypeInfo.
     */
    private static void injectNakedRefType(BuildRepository repoBuild, TypeConstant typeNakedRef) {
        if (typeNakedRef == null) {
            return;
        }
        for (var sModule : repoBuild.getModuleNames()) {
            repoBuild.loadModule(sModule).getConstantPool().setNakedRefType(typeNakedRef);
        }
    }

    /** The bounded fixed-point retry loop the compiler stages need (mirrors the CLI driver). */
    private static void runPhase(List<Compiler> compilers, CompilerPhase phase) {
        for (int cTriesLeft = 0x3F; cTriesLeft > 0; cTriesLeft--) {
            boolean fDone = true;
            for (var compiler : compilers) {
                fDone &= phase.run(compiler, cTriesLeft == 1);
                if (compiler.isAbortDesired()) {
                    return;
                }
            }
            if (fDone) {
                return;
            }
        }
        compilers.forEach(Compiler::logRemainingDeferredAsErrors);
    }

    @FunctionalInterface
    private interface CompilerPhase {
        boolean run(Compiler compiler, boolean fLastAttempt);
    }

    /**
     * The engine's native container - the single root of its container plane.
     *
     * Exposed for host-side diagnostics: a host that wants to assert container ownership after a
     * run needs a handle on the plane the run happened in, and every nested run container is
     * reachable from this one. It is not an execution entry point.
     *
     * @return the native ("-1") container this engine booted
     */
    public @NotNull Container diagnosticContainer() {
        return containerNative();
    }

    // ----- run -----------------------------------------------------------------------------------

    /**
     * Run a compiled module's {@code run(...)} entry point as a nested container under the shared native
     * plane, returning the EVENT-DRIVEN completion future. The future completes normally when the run
     * finishes and exceptionally (carrying the XTC exception) if it threw - the caller awaits it with
     * its own deadline, no polling.
     *
     * @param result       a successful {@link #compile} result containing the module
     * @param sModuleName  the module to run
     *
     * @return the run-completion future
     */
    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull CompileResult result, @NotNull String sModuleName) {
        boolean fFound = result.modules().stream().anyMatch(id -> id.getName().equals(sModuleName));
        if (!fFound) {
            throw new IllegalArgumentException("module not in compile result: " + sModuleName);
        }

        return run(result, sModuleName, new Injection[0]);
    }

    /**
     * Run a just-compiled module with injections granted to that run alone.
     *
     * @param result        a successful {@link #compile} result containing the module
     * @param sModuleName   the module to run
     * @param injections    values granted to THIS run only; see
     *                      {@link #run(String, String, Map)}
     *
     * @return the run-completion future
     */
    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull CompileResult result,
                                                        @NotNull String sModuleName,
                                                        @NotNull Injection @NotNull... injections) {
        return runFrom(new LinkedRepository(result.buildRepository(), repoLibrary), sModuleName,
                "run", Objects.requireNonNull(injections, "injections"));
    }

    /**
     * Run a module that is already built and sitting on this engine's module path, as a nested
     * container under the shared native plane.
     *
     * <p>This is the entry point for a host that did not compile the module through this engine -
     * a build plugin running an artifact it produced in an earlier task, for instance. The
     * {@link #run(CompileResult, String)} overload exists for the compile-then-run case, where the
     * module is still only in the build repository; both reach the same nested-container path.</p>
     *
     * @param sModuleName  the module to run, resolved against the engine's module path
     * @param sMethodName  the module method to invoke (the CLI default is {@code "run"})
     *
     * @return the run-completion future
     */
    /**
     * Run a module with injections granted to that run alone.
     *
     * @param sModuleName   the module to run, resolved against the engine's module path
     * @param sMethodName   the module method to invoke (the CLI default is {@code "run"})
     * @param injections    values granted to THIS run only, each naming one {@code String}
     *                      injection
     *
     * @return the run-completion future
     */
    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull String sModuleName,
                                                        @NotNull String sMethodName,
                                                        @NotNull Injection @NotNull... injections) {
        return runFrom(repoLibrary, sModuleName, sMethodName,
                Objects.requireNonNull(injections, "injections"));
    }

    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull String sModuleName,
                                                        @NotNull String sMethodName) {
        return runFrom(repoLibrary, sModuleName, sMethodName);
    }

    /**
     * The one nested-container run path, reached by both {@code run} overloads.
     *
     * The native container assembles the run-time FileStructure: it merges its own boot-resolved
     * turtle prototype (whose pool already carries the NakedRef type) into a fresh combined pool, so
     * an assembled app module runs here exactly as a module loaded from disk would - no hand-patching
     * of the run-time pool is needed (that is the whole point of assembling the module at compile).
     */
    private @NotNull CompletableFuture<ObjectHandle> runFrom(@NotNull ModuleRepository repoRun,
                                                             @NotNull String sModuleName,
                                                             @NotNull String sMethodName,
                                                             @NotNull Injection @NotNull... injections) {
        ModuleStructure moduleApp = repoRun.loadModule(sModuleName);
        if (moduleApp == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "module not found on the module path: " + sModuleName));
        }

        // Per-run injections are supplied to the runner, which fabricates them in the run's own
        // provider rather than resolving them through pass-through against container zero - that
        // is what stops two runs sharing one value. Only single-valued String injections are
        // expressible today; a multi-valued name is a String[] injection, which runTask has no
        // way to carry (see H19 in the LSPAPI analysis).
        var listNames  = new ArrayList<String>(injections.length);
        var listValues = new ArrayList<String>(injections.length);
        for (Injection injection : injections) {
            listNames.add(injection.name());
            listValues.add(injection.value());
        }
        if (!"run".equals(sMethodName)) {
            // runTask invokes run(); an arbitrary entry point has no equivalent in the runner
            // surface, and pretending otherwise would run the wrong method.
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "the runner app invokes run(); no entry point named " + sMethodName));
        }

        InterpreterConnector connector = f_connector.get(this);
        MainContainer        main      = connector.getMainContainer();

        FileStructure struct;
        try {
            struct = prepareForRun(connector, moduleApp, repoRun);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }

        // Hand the module and ITS repository to the runner app, which creates the container.
        ObjectHandle hModule     = xRTModuleTemplate.makeHandle(main, struct.getModule());
        ObjectHandle hRepository = connector.getNativeContainer().nativeTemplates()
                                        .coreRepository().makeHandle(repoRun);

        // No console for now: the runner's TaskResourceProvider takes a console id, and this
        // branch's redirectable console (E38) is the sink it should be given once wired.
        ObjectHandle hNames  = xString.makeArrayHandle(main, listNames.toArray(String[]::new));
        ObjectHandle hValues = xString.makeArrayHandle(main, listValues.toArray(String[]::new));

        // registerTransientTask, not registerTask: the runner deletes the run's file-system root
        // once the run completes.
        //
        // Precisely: the runner assigns `completion` - which is what resolves the future returned
        // here - and only THEN asks for the deletion, with `^`. So the deletion is prompt and
        // guaranteed, being a scheduled service call rather than anything GC-triggered, but it is
        // not ordered against this future. A caller cannot assume the root is gone the instant the
        // run resolves; TransientTaskDirectoryTest waits for it for that reason.
        //
        // This used to be justified against the Cleaner that PR #545 registered on Control - not
        // deterministic, not run at JVM exit, able to call back into container zero after this
        // engine closed its connector. That Cleaner is gone: upstream now makes Control
        // AutoCloseable and deletes the root in close(). So the old justification is spent, and the
        // choice stands for a different and better reason.
        //
        // Upstream's is right for ITS caller and wrong for ours, and the distinction is who holds
        // the files. A Control is handed to a caller who may want to inspect what the run produced,
        // so deleting at completion would be too early - that is exactly why close() and not
        // whenComplete. This engine hands out no Control and exposes no task directory: run()
        // answers a result, and nothing can look at the root afterwards because nothing is given a
        // way to name it. A root that no one can reach is one nobody has to remember to close.
        return main.invokeAsync("registerTransientTask", hModule, hRepository,
                                xNullable.makeHandle(main), hNames, hValues)
                .thenCompose(hTaskId -> main.invokeAsync("startTask", hTaskId))
                .thenApply(XtcEngine::resultOf);
    }

    /**
     * Unpack the {@code (Int result, String failure)} tuple that {@code startTask} answers with.
     *
     * <p>One future for the whole run: the runner completes it once, after it has recorded the
     * outcome, so there is nothing to poll and no window in which the result is not yet readable.
     *
     * @param hTuple  the completion tuple
     *
     * @return the run's result handle
     *
     * @throws IllegalStateException if the run completed exceptionally
     */
    private static @NotNull ObjectHandle resultOf(@NotNull ObjectHandle hTuple) {
        ObjectHandle[] ahOutcome = ((TupleHandle) hTuple).m_ahValue;
        String         sFailure  = ((StringHandle) ahOutcome[1]).getStringValue();
        if (!sFailure.isEmpty()) {
            throw new IllegalStateException(sFailure);
        }
        return ahOutcome[0];
    }

    /**
     * Copy the module through serialization so the run cannot share structures with the compiler.
     *
     * <p>Lifted from {@code InterpreterControl.prepareModule}. Writing the module out and reading it
     * back gives the run a FileStructure with its own ConstantPool, structurally disconnected from
     * whatever produced it - the isolation the CLI gets for free by writing a {@code .xtc} and
     * loading it again.
     */
    private static FileStructure prepareForRun(InterpreterConnector connector,
                                               ModuleStructure moduleApp,
                                               ModuleRepository repoRun) {
        FileStructure struct;
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            moduleApp.getFileStructure().writeTo(bytes);
            ModuleStructure moduleCopy = new FileStructure(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()), true, false).getModule();
            struct = connector.getNativeContainer().createFileStructure(moduleCopy);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("unable to prepare " + moduleApp.getName(), e);
        }

        ModuleConstant idMissing = struct.linkModules(repoRun, true);
        if (idMissing != null) {
            throw new IllegalStateException("missing dependency: " + idMissing.getName());
        }
        return struct;
    }

    /**
     * Run a compiled module and return a {@link RunControl} - the same shape the upstream
     * {@code ToolConnector.Control} defines - instead of a bare future.
     *
     * <p>A completion future answers "is it done, and what did it return"; a long-running host also
     * needs to ASK about a run it started: is it still going, when did it start and stop, and can I
     * stop it. An LSP cancelling a stale run, or a test runner enforcing a timeout, needs exactly
     * that. The future is still available from {@link RunControl#completion()}, so nothing is lost.</p>
     *
     * @param result       a successful {@link #compile} result containing the module
     * @param sModuleName  the module to run
     *
     * @return a control handle for the running module
     */
    public @NotNull RunControl start(@NotNull CompileResult result, @NotNull String sModuleName) {
        var event = new RunEvent();
        event.module = sModuleName;
        event.begin();

        Instant                         whenStarted = Instant.now();
        CompletableFuture<ObjectHandle> future      = run(result, sModuleName);
        var                             control     = new RunControlImpl(whenStarted, future);

        future.whenComplete((handle, error) -> {
            control.stop();
            event.succeeded = error == null;
            event.commit();
        });
        return control;
    }

    /**
     * Management and monitoring for a running module - the engine's form of the upstream
     * {@code ToolConnector.Control}.
     */
    public interface RunControl {
        /** @return true iff the module is still running */
        boolean running();

        /** @return when the run started */
        @NotNull Instant whenStarted();

        /** @return when the run stopped, or empty while it is still running */
        @NotNull Optional<Instant> whenStopped();

        /**
         * @return the module's {@code run()} result once it has completed normally, else empty. An
         *         Ecstasy {@code Int} exit code arrives as a {@code Long}, matching the upstream
         *         {@code Control.result()} contract - but as an {@link Optional}, so "still running",
         *         "failed" and "returned null" cannot be confused with each other.
         */
        @NotNull Optional<Long> result();

        /** @return the failure if the run completed exceptionally, else empty */
        @NotNull Optional<Throwable> error();

        /**
         * Stop the run as promptly as the runtime allows.
         *
         * <p>Honest limitation: this cancels the completion future so the CALLER stops waiting, and
         * the container is released for collection. It does not yet forcibly unwind a fiber that is
         * mid-execution - that needs the runtime's own termination path
         * ({@code Container.terminate(ServiceContext)}) wired to a cooperative cancellation check,
         * which is deliberately not faked here.</p>
         */
        void kill();

        /** @return the event-driven completion future, for callers that prefer to await it */
        @NotNull CompletableFuture<ObjectHandle> completion();
    }

    private static final class RunControlImpl
            implements RunControl {
        private final Instant                         whenStarted;
        private final CompletableFuture<ObjectHandle> future;
        private volatile Instant                      whenStopped;

        RunControlImpl(@NotNull Instant whenStarted, @NotNull CompletableFuture<ObjectHandle> future) {
            this.whenStarted = whenStarted;
            this.future      = future;
        }

        void stop() {
            whenStopped = Instant.now();
        }

        @Override
        public boolean running() {
            return !future.isDone();
        }

        @Override
        public Instant whenStarted() {
            return whenStarted;
        }

        @Override
        public @NotNull Optional<Instant> whenStopped() {
            return Optional.ofNullable(whenStopped);
        }

        @Override
        public @NotNull Optional<Long> result() {
            if (!future.isDone() || future.isCompletedExceptionally()) {
                return Optional.empty();
            }
            // getNow cannot block here: the future is already completed normally
            return future.getNow(null) instanceof ObjectHandle.JavaLong hLong
                    ? Optional.of(hLong.getValue())
                    : Optional.empty();
        }

        @Override
        public @NotNull Optional<Throwable> error() {
            return future.isCompletedExceptionally() && !future.isCancelled()
                    ? Optional.of(future.exceptionNow())
                    : Optional.empty();
        }

        @Override
        public void kill() {
            future.cancel(true);
            stop();
        }

        @Override
        public CompletableFuture<ObjectHandle> completion() {
            return future;
        }
    }

    // ----- lifecycle -----------------------------------------------------------------------------

    @Override
    public void close() {
        // nothing to shut down if no run ever needed a runtime: compiling never boots one, which
        // is why the connector is lazy rather than built in the constructor
        if (f_connector.isComputed()) {
            f_connector.get(this).shutdown();
        }
    }

    // ----- diagnostics ---------------------------------------------------------------------------

    private static List<Diagnostic> diagnostics(ErrorList errs) {
        var list = new ArrayList<Diagnostic>(errs.getErrors().size());
        for (ErrorInfo err : errs.getErrors()) {
            Source source = err.getSource();
            list.add(new Diagnostic(err.getSeverity(), err.getCode(), err.getMessageText(),
                    source == null ? null : source.getFileName(), err.getLine(), err.getOrigin()));
        }
        return List.copyOf(list);
    }

    /**
     * Forwards every diagnostic to a second listener while keeping the first as the authority for
     * abort/serious-error decisions. Used to stream compile diagnostics to a caller's sink without
     * giving up the engine's own collection of them.
     */
    private record TeeErrorListener(ErrorList primary, ErrorListener secondary)
            implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            primary.log(err);
            secondary.log(err);
        }

        @Override
        public boolean isAbortDesired() {
            // BOTH, not just the engine's own list. The caller's listener is how a host
            // PARTICIPATES rather than merely observes - wrapping a real ErrorList and passing it
            // to compile() is exactly what the documentation tells a host to do when it wants a say
            // in when compilation gives up. Consulting only `primary` silently made that advice
            // false through this path: a host could pass ErrorList.firstError() and still watch the
            // compiler run to completion. A host that only observes is unaffected, because a
            // stateless listener answers false.
            return primary.isAbortDesired() || secondary.isAbortDesired();
        }

        @Override
        public @NotNull ErrorListener branch(AstNode node) {
            // MUST override. The interface default branches with a budget of ONE serious error
            // (`new BranchedErrorListener(this, 1, node)`), while an ErrorList branches with its
            // own (`f_cMaxErrors`). A method body is validated through such a branch in
            // StatementBlock.compileMethod, and BranchedErrorListener.isAbortDesired() is true as
            // soon as the budget is spent - so inheriting the default made the engine abandon
            // validation of a method after its first error, before a loop's type narrowing could
            // reach a fixed point. Branching off the tee (rather than off `primary`) keeps both
            // sinks fed when the branch merges.
            return new ErrorList.BranchedErrorListener(this, primary.getSeriousErrorMax(), node);
        }

        @Override
        public boolean hasSeriousErrors() {
            return primary.hasSeriousErrors();
        }

        @Override
        public boolean isSilent() {
            return primary.isSilent();
        }
    }

    // ----- JFR telemetry -------------------------------------------------------------------------

    /**
     * A compile, as a JFR event: the first concrete piece of the unified-telemetry plan
     * (docs/reentrancy/plans/unified-logging-jfr-telemetry.md). A long-running host - LSP server,
     * build daemon, test runner - can then profile compile cost and diagnostic volume with standard
     * JDK tooling instead of bespoke timing. Events cost nothing when JFR is not recording.
     */
    @Name("org.xvm.Compile")
    @Label("XTC Compile")
    @Category({"Ecstasy", "Engine"})
    static final class CompileEvent extends Event {
        // DELIBERATELY non-final: JFR populates an event by field assignment between begin() and
        // commit(), and reads the fields reflectively at commit time - they cannot be final. This is
        // the only mutable state in this class; everything else here is final or an immutable record.
        @Label("Modules")     private String  modules;
        @Label("Compiled")    private int     compiled;
        @Label("Diagnostics") private int     diagnostics;
        @Label("Success")     private boolean success;
    }

    /**
     * A nested-container run, as a JFR event. Duration spans the run's whole lifetime (the event is
     * committed when the completion future settles), so a host sees run cost without instrumenting
     * the runtime itself.
     */
    @Name("org.xvm.Run")
    @Label("XTC Run")
    @Category({"Ecstasy", "Engine"})
    static final class RunEvent extends Event {
        // non-final for the same JFR reason as CompileEvent above
        @Label("Module")    private String  module;
        @Label("Succeeded") private boolean succeeded;
    }

    /**
     * One in-memory module source to compile: the module's name plus its source text. An immutable
     * value record - the API never takes a mutable collection of sources.
     *
     * @param moduleName  the module's name (for diagnostics/labels)
     * @param source      the module source text
     */
    public record SourceUnit(@NotNull String moduleName, @NotNull String source) {
    }

    /**
     * An on-disk module to compile: its source file or directory, and the resource root against which
     * file and directory literals in that source are resolved.
     *
     * @param source        the module's source file or source directory
     * @param resourceDirs  the module's resource roots, in aggregate; empty when it has none.
     *                       {@link Path}, like {@code source}: {@link ModuleInfo} wants
     *                       {@link File}, but that is a property of the consumer and is converted
     *                       at the call, not a reason for one field of this record to be modern and
     *                       the other legacy.
     */
    public record ModuleSource(@NotNull Path source, @NotNull List<Path> resourceDirs) {
        public ModuleSource {
            Objects.requireNonNull(source, "source");
            // copied, so the record is immutable whatever the caller does with its list afterwards
            resourceDirs = List.copyOf(resourceDirs);
        }

        /**
         * A module with no resources.
         *
         * @param source  the module's source file or source directory
         */
        public static @NotNull ModuleSource of(@NotNull Path source) {
            return new ModuleSource(source, List.of());
        }

        /**
         * A module whose file and directory literals resolve against the given resource roots, in
         * aggregate - the same way the CLI accepts more than one resource location.
         *
         * @param source        the module's source file or source directory
         * @param resourceDirs  the resource roots
         */
        public static @NotNull ModuleSource of(@NotNull Path source, @NotNull Path @NotNull... resourceDirs) {
            return new ModuleSource(source, List.of(resourceDirs));
        }
    }

    /**
     * A structured compile/run diagnostic (the LSP-facing shape).
     *
     * @param severity  the severity
     * @param code      the message code
     * @param message   the rendered message text
     * @param source    the source name/uri, or null
     * @param line      the 1-based line, or 0 if unknown
     */
    /**
     * One injection granted to a single run: a name and the value it resolves to.
     *
     * <p>Replaces a {@code Map<String, List<String>>}. That shape was wrong in two ways at once. It
     * was a mutable collection in a public signature, which this project does not want; and more
     * importantly its TYPE advertised something the runner cannot do - a {@code List} per name says
     * multi-valued {@code String[]} injections are expressible, and they are not, so every call had
     * to be checked at run time and rejected with an {@code UnsupportedOperationException}. A single
     * value per name cannot express the unsupported case, so there is nothing left to check.
     *
     * <p>If {@code String[]} injections ever become carryable (see H19), they get their own factory
     * rather than being smuggled back in as a collection nobody can validate at the call site.
     *
     * @param name   the injection name, as the module requests it with {@code @Inject}
     * @param value  the value granted to THIS run
     */
    public record Injection(@NotNull String name, @NotNull String value) {
        public Injection {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    public record Diagnostic(@NotNull Severity severity, @NotNull String code, @NotNull String message,
                            @Nullable String source, int line, @NotNull ErrorListener.Origin origin) {
        /**
         * @return a short description of where this diagnostic came from - the thread that raised it
         *         and, at run time, the fiber
         */
        public @NotNull String originText() {
            return origin.toString();
        }
    }

    /**
     * The outcome of a {@link #compile}: the compiled module ids (empty on failure), every diagnostic
     * produced, and the in-memory build repository holding the compiled modules.
     *
     * <p>Compilation is ALWAYS in-memory - the natural fit for an LSP, which recompiles tentatively on
     * every edit and never wants to touch disk for a buffer that may change again in milliseconds. The
     * modules held here are already assembled (runnable), so {@link #run} executes them directly with no
     * disk round-trip. When a caller DOES want binaries on disk - to hand off to another tool, to run in
     * a separate process, or simply to publish a finished build - it calls {@link #writeTo} to sync the
     * compiled modules out as {@code .xtc} files. "Compile directly to disk" is therefore just
     * {@code engine.compile(...).writeTo(dir)}: compile in memory, then persist.</p>
     */
    public record CompileResult(@NotNull List<ModuleConstant> modules, @NotNull List<Diagnostic> diagnostics,
                                @NotNull BuildRepository buildRepository) {
        public boolean isSuccess() {
            return !modules.isEmpty()
                && diagnostics.stream().noneMatch(d -> d.severity().ordinal() >= Severity.ERROR.ordinal());
        }

        /**
         * Persist the compiled modules to disk as {@code .xtc} binaries (one file per module, named by
         * the module's unqualified name), the "sync to disk" half of the in-memory-first model. The
         * files land in the standard XDK on-disk layout, so they reload through an ordinary
         * {@link DirRepository} exactly like any other compiled module.
         *
         * @param dir  the output directory (created if absent)
         *
         * @return the files written, one per module
         */
        public @NotNull List<File> writeTo(@NotNull File dir) throws IOException {
            return writeTo(dir, null);
        }

        /**
         * Persist the compiled modules, optionally stamping a version onto each.
         *
         * @param dir      the destination directory
         * @param version  the version to stamp, or null to leave the modules unversioned
         *
         * @return the files written
         */
        public @NotNull List<File> writeTo(@NotNull File dir, @Nullable Version version)
                throws IOException {
            if (!isSuccess()) {
                throw new IllegalStateException("cannot persist a failed compilation: " + diagnostics);
            }
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("not a writable directory: " + dir);
            }
            var repoDir = new DirRepository(dir, false);
            var written = new ArrayList<File>(modules.size());
            for (var id : modules) {
                ModuleStructure module = buildRepository.loadModule(id.getName());
                if (version != null) {
                    module.setVersion(version);
                }
                repoDir.storeModule(module);
                written.add(new File(dir, id.getUnqualifiedName() + ".xtc"));
            }
            return List.copyOf(written);
        }
    }

    // ----- builder -------------------------------------------------------------------------------

    public static final class Builder {
        private final List<File> modulePath = new ArrayList<>();
        private @NotNull ErrorListener diagnosticSink = ErrorListener.RUNTIME;

        /**
         * Supply a sink for the runtime container's diagnostics - work that no single compile owns.
         *
         * <p>This is NOT the same thing as the listener passed to {@link #compile}. That one
         * receives the diagnostics of one compile, and two compiles running at once each keep their
         * own. This one is engine-scoped and reaches the {@code InterpreterConnector}'s container.
         *
         * <p>It also receives <b>library preparation</b> failures: a system library that cannot be
         * loaded, or that links against a module the configured path does not contain. Those are
         * reported because the engine invoked the operation and read its result, not because
         * anything redirected a pool.
         *
         * <p><b>It does not cover diagnostics raised inside the library pools themselves</b>, and
         * cannot without reintroducing shared mutable state. This used to redirect each library pool
         * here, by calling a setter on a {@code ConstantPool} the engine does not own - the library
         * {@code FileStructure} comes out of the CALLER's repository, so two engines over one
         * repository fought over it, last writer winning, and each then heard the other's library
         * diagnostics. The pool's listener is now fixed at construction, and nothing constructs a
         * library structure on the engine's behalf: {@code injectNakedRefIntoLibrary} deliberately
         * retains the caller's instances.
         *
         * <p>What that costs is small and bounded. A compile's own diagnostics are unaffected -
         * {@code ConstantPool.register} adopts foreign constants, so a compile referencing a library
         * type asks its own pool, holding its own listener. What remains is work done entirely
         * inside the library, which means a broken or corrupt library: a system fault, reported
         * through {@link ErrorListener#RUNTIME}. Capturing those too would need the engine to own
         * library COPIES constructed with this sink - a change to preparation, not to listeners.
         *
         * @param diagnosticSink  the engine-lifetime sink; defaults to {@link ErrorListener#RUNTIME}
         */
        public @NotNull Builder diagnosticSink(@NotNull ErrorListener diagnosticSink) {
            this.diagnosticSink = Objects.requireNonNull(diagnosticSink, "diagnosticSink");
            return this;
        }

        public @NotNull Builder modulePath(@NotNull File @NotNull... dirs) {
            for (File dir : dirs) {
                modulePath.add(Objects.requireNonNull(dir, "module path dir"));
            }
            return this;
        }

        public @NotNull XtcEngine build() {
            // A module path is directories AND individual .xtc module files, which is what the CLI
            // accepts (see Launcher.makeRepo) and what the Gradle plugin resolves - its path is
            // mostly files. Taking only directories made the engine unusable for a plugin compile:
            // every file entry was dropped silently, and a path of nothing but files threw.
            var repositories = new ArrayList<ModuleRepository>();
            for (File file : modulePath) {
                if (file.isDirectory()) {
                    repositories.add(new DirRepository(file, true));
                } else if (file.isFile()) {
                    repositories.add(new FileRepository(file, true));
                }
            }
            if (repositories.isEmpty()) {
                throw new IllegalStateException(
                        "no readable module-path entries were provided: " + modulePath);
            }
            ModuleRepository repo = repositories.size() == 1
                    ? repositories.get(0)
                    : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
            return new XtcEngine(repo, diagnosticSink);
        }
    }
}
