package org.xvm.api;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;

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
import org.xvm.asm.ConstantPool;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
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
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
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
 * <p><b>Deployment model.</b> Runs execute as {@link NestedContainer#createForHost nested containers}
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
    private final Lazy.Bound<XtcEngine, RuntimePlane> f_plane =
            Lazy.ofBound(XtcEngine::bootPlane);

    /** A started runtime and the native container rooted on it. */
    private record RuntimePlane(Runtime runtime, NativeContainer containerNative) {}

    private XtcEngine(@NotNull ModuleRepository repoLibrary, @NotNull ErrorListener diagnosticSink) {
        this.repoLibrary     = Objects.requireNonNull(repoLibrary, "repoLibrary");
        this.diagnosticSink  = Objects.requireNonNull(diagnosticSink, "diagnosticSink");
    }

    /**
     * Start the runtime and boot the native container this engine's runs are rooted on.
     *
     * <p>The native container is the root of every run, and a NestedContainer with none of its own
     * inherits from its parent - so a host sink set here reaches every run this engine starts.</p>
     */
    private RuntimePlane bootPlane() {
        Runtime runtimeNew = new Runtime();
        runtimeNew.start();
        return new RuntimePlane(runtimeNew,
                NativeContainer.create(runtimeNew, repoLibrary, diagnosticSink));
    }

    private NativeContainer containerNative() {
        return f_plane.get(this).containerNative();
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
            return compileInternal(errsCaller, event, errs -> parseSources(errs, units));
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
            return compileInternal(errsCaller, event, errs -> parseModuleSources(errs, sources));
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
            return compileInternal(errsCaller, event, errs -> parseSourceTrees(errs, paths));
        } finally {
            event.commit();
        }
    }

    /**
     * Parse in-memory sources into module node trees.
     */
    private static List<TypeCompositionStatement> parseSources(ErrorListener errs, SourceUnit[] units) {
        var listModules = new ArrayList<TypeCompositionStatement>(units.length);
        for (var unit : units) {
            TypeCompositionStatement stmtModule = parseModule(unit.source(), errs);
            if (stmtModule != null) {
                listModules.add(stmtModule);
            }
        }
        return listModules;
    }

    /**
     * Parse on-disk sources into module node trees, through the same {@link ModuleInfo} walk the CLI
     * compiler uses, so a directory-tree module is handled exactly as the CLI handles it.
     */
    private static List<TypeCompositionStatement> parseSourceTrees(ErrorListener errs, Path[] paths) {
        var aSource = new ModuleSource[paths.length];
        for (int i = 0; i < paths.length; i++) {
            aSource[i] = ModuleSource.of(paths[i]);
        }
        return parseModuleSources(errs, aSource);
    }

    /**
     * Parse on-disk sources into module node trees, through the same {@link ModuleInfo} walk the CLI
     * compiler uses, resolving each module's file/directory literals against its resource root.
     */
    private static List<TypeCompositionStatement> parseModuleSources(ErrorListener errs,
                                                                     ModuleSource[] sources) {
        var listModules = new ArrayList<TypeCompositionStatement>(sources.length);
        for (ModuleSource source : sources) {
            List<File> listResource = source.resourceDirs();
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
        var repoCompile = new LinkedRepository(true, repoBuild, repoLibrary);
        var compilers   = new ArrayList<Compiler>();

        // pre-load and link the system libraries (ecstasy + turtle prototype) so they are cached into
        // repoBuild before anything is compiled against them - the Launcher's prelinkSystemLibraries step
        prelinkSystemLibraries(repoCompile);

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
            injectNakedRefType(repoBuild);
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
            if (module != null) {
                FileStructure struct = module.getFileStructure();
                if (struct != null) {
                    // A library module owns its own ConstantPool, so resolving a library type
                    // reports to THAT pool - not to whichever compile happened to ask. Point it at
                    // the engine's sink so a host still hears it. Per-compile diagnostics are
                    // unaffected: they are owned by the pool of the module being compiled.
                    struct.getConstantPool().setErrorListener(diagnosticSink);
                    struct.linkModules(repo, false);
                }
            }
        }
    }

    /**
     * Set the NakedRef type from the turtle prototype on the pool of every module in the build repo -
     * the compiled modules AND the library dependencies the read-through load cached alongside them.
     * This mirrors {@code org.xvm.tool.Compiler.injectNativeTurtle} exactly: the compiler needs the
     * NakedRef type available to each ConstantPool that participates in building Ref TypeInfo.
     */
    private static void injectNakedRefType(BuildRepository repoBuild) {
        ModuleStructure moduleTurtle = repoBuild.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle == null) {
            return;
        }
        TypeConstant typeNakedRef = ((ClassStructure) moduleTurtle.getChild("NakedRef")).getFormalType();
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

        return run(result, sModuleName, Map.of());
    }

    /**
     * Run a just-compiled module with its own {@code String}/{@code String[]} injections.
     *
     * @param result        a successful {@link #compile} result containing the module
     * @param sModuleName   the module to run
     * @param mapInjections values granted to THIS run only; see
     *                      {@link #run(String, String, Map)}
     *
     * @return the run-completion future
     */
    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull CompileResult result,
                                                        @NotNull String sModuleName,
                                                        @NotNull Map<String, List<String>> mapInjections) {
        return runFrom(new LinkedRepository(result.buildRepository(), repoLibrary), sModuleName,
                "run", Objects.requireNonNull(mapInjections, "mapInjections"));
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
     * Run a module with its own {@code String}/{@code String[]} injections.
     *
     * @param sModuleName   the module to run, resolved against the engine's module path
     * @param sMethodName   the module method to invoke (the CLI default is {@code "run"})
     * @param mapInjections values granted to THIS run only, keyed by injection name; a single
     *                      value satisfies a {@code String} injection and the whole list satisfies
     *                      a {@code String[]} one
     *
     * @return the run-completion future
     */
    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull String sModuleName,
                                                        @NotNull String sMethodName,
                                                        @NotNull Map<String, List<String>> mapInjections) {
        return runFrom(repoLibrary, sModuleName, sMethodName,
                Objects.requireNonNull(mapInjections, "mapInjections"));
    }

    public @NotNull CompletableFuture<ObjectHandle> run(@NotNull String sModuleName,
                                                        @NotNull String sMethodName) {
        return runFrom(repoLibrary, sModuleName, sMethodName, Map.of());
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
                                                             @NotNull Map<String, List<String>> mapInjections) {
        ModuleStructure moduleApp = repoRun.loadModule(sModuleName);
        if (moduleApp == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "module not found on the module path: " + sModuleName));
        }

        NativeContainer containerNative = containerNative();
        FileStructure struct = containerNative.createFileStructure(moduleApp);

        ModuleConstant idMissing = struct.linkModules(repoRun, true);
        if (idMissing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("missing dependency: " + idMissing.getName()));
        }

        NestedContainer containerRun =
                NestedContainer.createForHost(containerNative, struct.getModuleId(), List.of());
        registerInjections(containerRun, mapInjections);
        return containerRun.runModule(sMethodName);
    }

    /**
     * Grant this run its own {@code String} and {@code String[]} injections.
     *
     * <p>Registered on the run's own container through
     * {@link NestedContainer#registerHostResource}, so they take precedence over the native plane
     * and die with the container - a run cannot see another run's injections, and nothing is left
     * behind on the shared plane afterwards. That is the difference from configuring a process-wide
     * host once: two runs of the same module can be given different values.</p>
     *
     * <p>Both shapes are registered for every name because an injection is keyed by name AND type:
     * the module decides which it declares, and asking for the one that was not registered is
     * indistinguishable from asking for a name nobody supplied.</p>
     */
    private void registerInjections(@NotNull NestedContainer containerRun,
                                    @NotNull Map<String, List<String>> mapInjections) {
        if (mapInjections.isEmpty()) {
            return;
        }

        ConstantPool pool        = containerRun.getConstantPool();
        TypeConstant typeString  = pool.typeString();
        TypeConstant typeStrings = pool.ensureArrayType(typeString);

        mapInjections.forEach((sName, listValues) -> {
            if (listValues == null || listValues.isEmpty()) {
                return;
            }
            containerRun.registerHostResource(new InjectionKey(sName, typeString),
                    (frame, hOpts) -> xString.makeHandle(frame, listValues.getLast()));
            containerRun.registerHostResource(new InjectionKey(sName, typeStrings),
                    (frame, hOpts) -> xString.makeArrayHandle(
                            containerRun, listValues.toArray(String[]::new)));
        });
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
        // nothing to shut down if no run ever needed a runtime
        if (f_plane.isComputed()) {
            f_plane.get(this).runtime().shutdownXVM();
        }
    }

    // ----- diagnostics ---------------------------------------------------------------------------

    private static List<Diagnostic> diagnostics(ErrorList errs) {
        var list = new ArrayList<Diagnostic>(errs.getErrors().size());
        for (ErrorInfo err : errs.getErrors()) {
            Source source = err.getSource();
            list.add(new Diagnostic(err.getSeverity(), err.getCode(), err.getMessageText(),
                    source == null ? null : source.getFileName(), err.getLine()));
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
     * @param resourceDirs  the module's resource roots, in aggregate; empty when it has none
     */
    public record ModuleSource(@NotNull Path source, @NotNull List<File> resourceDirs) {
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
        public static @NotNull ModuleSource of(@NotNull Path source, @NotNull File @NotNull... resourceDirs) {
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
    public record Diagnostic(@NotNull Severity severity, @NotNull String code, @NotNull String message,
                            @Nullable String source, int line) {
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
         * Supply a sink for diagnostics that no single compile owns - library-type resolution and
         * runtime metadata.
         *
         * <p>This is NOT the same thing as the listener passed to {@link #compile}. That one
         * receives the diagnostics of one compile, and two compiles running at once each keep their
         * own. This one is engine-scoped and receives the work that belongs to the shared library
         * pools and the runtime container, which no per-compile listener can be given without
         * reintroducing shared mutable state.
         *
         * <p>A host that wants to observe everything sets both.
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
