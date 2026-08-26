package org.xvm.api;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.runtime.Container;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NestedContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;

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
    private final ModuleRepository f_repoLibrary;
    private final Runtime          f_runtime;
    private final NativeContainer  f_containerNative;

    private XtcEngine(ModuleRepository repoLibrary) {
        f_repoLibrary     = repoLibrary;
        f_runtime         = new Runtime();
        f_runtime.start();
        f_containerNative = NativeContainer.create(f_runtime, repoLibrary);
    }

    public static Builder builder() {
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
    public CompileResult compile(String sModuleName, String sSource) {
        return compile(Map.of(sModuleName, sSource));
    }

    /**
     * Compile one or more in-memory module sources together, resolving cross-module references among
     * them. Each entry is one module (name to source text).
     *
     * <p>TODO - source trees: an LSP workspace also has on-disk module DIRECTORY TREES (a module as a
     * directory of {@code .x} files with nested packages/classes). Supporting those means building the
     * module node tree with {@link org.xvm.tool.ModuleInfo} (which already walks a source directory into
     * a parsed {@code TypeCompositionStatement} node tree) and feeding the resulting module statements
     * into the same pipeline below. This method covers the in-memory case; a {@code compile(Path...)}
     * overload should be added on top of ModuleInfo rather than by shelling out to the CLI Launcher.</p>
     *
     * <p>TODO - incremental / method-level recompilation (a separate project; the compiler does NOT
     * support this today). On each keystroke an LSP recompiles the whole module here, which is wasteful:
     * most edits change a single method body. The eventual shape is a compiler that can recompile at
     * method granularity - reuse the module's existing {@code FileStructure} and {@code ConstantPool},
     * reparse only the edited method (or the smallest enclosing declaration), re-run
     * resolveNames/validateExpressions/generateCode for just that method's AST subtree, and splice the
     * regenerated {@code MethodStructure.Code} back in, invalidating only the dependent
     * {@code TypeInfo}s. That requires new compiler capabilities (a stable per-method recompilation
     * entry point, dependency tracking from edited declaration to affected TypeInfos, and safe in-place
     * code replacement on a possibly runtime-published pool - which is exactly why the pool-publication
     * marker and synthesis windows in this runtime matter). It is out of scope for this API; the engine
     * should expose a {@code recompileMethod(module, methodId, newSource)} entry once the compiler can
     * back it, so an LSP gets sub-millisecond edits instead of full-module rebuilds.</p>
     *
     * @param namedSources  module name to source text
     *
     * @return the compile result
     */
    public CompileResult compile(Map<String, String> namedSources) {
        var errs      = new ErrorList(Integer.MAX_VALUE);
        var repoBuild = new BuildRepository();

        // A read-through library repo with the build repo at the front: exactly the shape the CLI
        // Launcher configures. "Read-through" means every library module loaded through it is cached
        // into repoBuild - which is how the turtle prototype ends up co-resident with the modules being
        // compiled so its NakedRef type can be injected across them all (see below). The caller never
        // has to name the turtle/native-bridge modules per request the way the CLI does with -L flags;
        // the engine already resolved them when it booted its native container from this same path.
        var repoCompile = new LinkedRepository(true, repoBuild, f_repoLibrary);
        var compilers   = new ArrayList<Compiler>();

        // pre-load and link the system libraries (ecstasy + turtle prototype) so they are cached into
        // repoBuild before anything is compiled against them - the Launcher's prelinkSystemLibraries step
        prelinkSystemLibraries(repoCompile);

        // stage 1: parse each source and create its initial module structure in the build repo
        for (var entry : namedSources.entrySet()) {
            TypeCompositionStatement stmtModule = parseModule(entry.getValue(), errs);
            if (stmtModule == null) {
                continue; // parse errors already in errs
            }
            var           compiler = new Compiler(stmtModule, errs);
            FileStructure struct   = compiler.generateInitialFileStructure();
            if (struct == null) {
                continue;
            }
            try {
                repoBuild.storeModule(struct.getModule());
            } catch (Exception e) {
                throw new IllegalStateException("storing module " + entry.getKey(), e);
            }
            compilers.add(compiler);
        }

        // stages 2-5: link, resolve names, inject turtle, validate, generate code - IN THIS ORDER.
        // The ordering matches org.xvm.tool.Compiler exactly and is load-bearing. NakedRef must be
        // injected AFTER link+resolveNames because each compiled module AND each library dependency it
        // links against (ecstasy, cached into repoBuild by the read-through load) needs the type set on
        // its own pool before validation builds Ref TypeInfo - otherwise it fails "Mack module is
        // missing". On-disk modules carry this baked in at build time; in-memory ones inject it here.
        compilers.forEach(compiler -> compiler.linkModules(repoCompile));
        runPhase(compilers, Compiler::resolveNames);
        injectNakedRefType(repoBuild);
        runPhase(compilers, Compiler::validateExpressions);
        runPhase(compilers, Compiler::generateCode);

        // On success, ASSEMBLE each compiled module (round-trip it through serialization) into a fresh
        // result repository. A freshly-compiled in-memory FileStructure still holds ops whose arguments
        // are unresolved AST references; assembly is what rewrites them into constant-pool indices and
        // finalizes the pool - exactly what the CLI gets for free by writing the module to a .xtc file.
        // Without this, a run fails at the first op with an AssertionError on the constant id. Running
        // the assembled (deserialized) module is then identical to loading a module off disk.
        var modules    = new ArrayList<ModuleConstant>();
        var repoResult = new BuildRepository();
        if (!errs.hasSeriousErrors()) {
            for (var compiler : compilers) {
                FileStructure struct = compiler.getFileStructure();
                if (struct != null) {
                    ModuleStructure assembled = assemble(struct);
                    repoResult.storeModule(assembled);
                    modules.add(assembled.getIdentityConstant());
                }
            }
        }
        return new CompileResult(List.copyOf(modules), diagnostics(errs), repoResult);
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

    private static TypeCompositionStatement parseModule(String sSource, ErrorList errs) {
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
    private static void prelinkSystemLibraries(ModuleRepository repo) {
        for (var sModule : List.of(Constants.ECSTASY_MODULE, Constants.TURTLE_MODULE)) {
            ModuleStructure module = repo.loadModule(sModule);
            if (module != null) {
                FileStructure struct = module.getFileStructure();
                if (struct != null) {
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
    public CompletableFuture<ObjectHandle> run(CompileResult result, String sModuleName) {
        boolean fFound = result.modules().stream().anyMatch(id -> id.getName().equals(sModuleName));
        if (!fFound) {
            throw new IllegalArgumentException("module not in compile result: " + sModuleName);
        }

        // The native container assembles the run-time FileStructure: it merges its own boot-resolved
        // turtle prototype (whose pool already carries the NakedRef type) into a fresh combined pool, so
        // an assembled app module runs here exactly as a module loaded from disk would - no hand-patching
        // of the run-time pool is needed (that is the whole point of assembling the module at compile).
        var             repoRun   = new LinkedRepository(result.buildRepository(), f_repoLibrary);
        ModuleStructure moduleApp = repoRun.loadModule(sModuleName);
        FileStructure   struct    = f_containerNative.createFileStructure(moduleApp);

        ModuleConstant idMissing = struct.linkModules(repoRun, true);
        if (idMissing != null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("missing dependency: " + idMissing.getName()));
        }

        Container containerRun = NestedContainer.createForHost(f_containerNative, struct.getModuleId(), List.of());
        return containerRun.runModule("run");
    }

    // ----- lifecycle -----------------------------------------------------------------------------

    @Override
    public void close() {
        f_runtime.shutdownXVM();
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
     * A structured compile/run diagnostic (the LSP-facing shape).
     *
     * @param severity  the severity
     * @param code      the message code
     * @param message   the rendered message text
     * @param source    the source name/uri, or null
     * @param line      the 1-based line, or 0 if unknown
     */
    public record Diagnostic(Severity severity, String code, String message, String source, int line) {
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
    public record CompileResult(List<ModuleConstant> modules, List<Diagnostic> diagnostics,
                                BuildRepository buildRepository) {
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
        public List<File> writeTo(File dir) throws IOException {
            if (!isSuccess()) {
                throw new IllegalStateException("cannot persist a failed compilation: " + diagnostics);
            }
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("not a writable directory: " + dir);
            }
            var repoDir = new DirRepository(dir, false);
            var written = new ArrayList<File>(modules.size());
            for (var id : modules) {
                repoDir.storeModule(buildRepository.loadModule(id.getName()));
                written.add(new File(dir, id.getUnqualifiedName() + ".xtc"));
            }
            return List.copyOf(written);
        }
    }

    // ----- builder -------------------------------------------------------------------------------

    public static final class Builder {
        private final List<File> f_modulePath = new ArrayList<>();

        public Builder modulePath(File... dirs) {
            for (File dir : dirs) {
                f_modulePath.add(Objects.requireNonNull(dir, "module path dir"));
            }
            return this;
        }

        public XtcEngine build() {
            var repositories = new ArrayList<ModuleRepository>();
            for (File dir : f_modulePath) {
                if (dir.isDirectory()) {
                    repositories.add(new DirRepository(dir, true));
                }
            }
            if (repositories.isEmpty()) {
                throw new IllegalStateException("no valid module-path directories were provided");
            }
            ModuleRepository repo = repositories.size() == 1
                    ? repositories.get(0)
                    : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
            return new XtcEngine(repo);
        }
    }
}
