package org.xvm.api;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import java.util.function.Function;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.CompilerException;
import org.xvm.compiler.InstantRepository;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.IncompleteStatement;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.tool.Console;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo;
import org.xvm.tool.ModuleInfo.Node;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.at;
import static org.xvm.util.Severity.ERROR;

/**
 * A class used to support embedding Ecstasy tools. This implementation uses the Connector API to
 * run a long-running Ecstasy application (in "Container Zero") that is responsible for spinning up
 * any number of child containers to "run()" modules. EmbeddingSupport is a singleton, but it does
 * require configuration; specifically, it requires a Module Repository from which to load the core
 * Ecstasy classes. Without configuration, EmbeddingSupport will attempt to locate the core Ecstasy
 * classes using the "XDK_HOME" OS property.
 *
 * Hosts must serialize compilations that share this instance's configured repository. Configuration
 * must complete before compilation or execution begins.
 */
public class EmbeddingSupport {
    // ----- internal (construction etc.) ----------------------------------------------------------

    /**
     * Internal constructor.
     */
    EmbeddingSupport() {}

    /**
     * Internal singleton implementation.
     */
    private static class Singleton {
        static final EmbeddingSupport instance = new EmbeddingSupport();
    }

    private static final Object LOCK = new Object();

    private static final Console SILENT_CONSOLE = new Console() {
        @Override
        public String out(Object value) {
            return String.valueOf(value);
        }

        @Override
        public String err(Object value) {
            return String.valueOf(value);
        }
    };

    private volatile boolean configured;
    private ModuleRepository cfgRepo;
    private String           cfgInjector;
    private Connector        connector;

    /**
     * Build a read-only repository over whichever of the given directories exist.
     *
     * @param dirs  the directories, in search order
     *
     * @return the repository, or null if none of the directories exist
     */
    private static ModuleRepository repoOver(File... dirs) {
        List<ModuleRepository> list = new ArrayList<>();
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                list.add(new DirRepository(dir, true));
            }
        }
        return switch (list.size()) {
            case 0  -> null;
            case 1  -> list.getFirst();
            default -> new LinkedRepository(list.toArray(ModuleRepository.NO_REPOS));
        };
    }

    /**
     * A snapshot of what the compiler is holding on to.
     *
     * An embedding host that keeps a compiler alive across many compilations - a language server
     * is the obvious one - needs some way to see whether it is accumulating. These are the cheap
     * numbers: taking them costs a field read and a repository listing, so a host can log one per
     * compilation without measuring itself instead of the compiler.
     *
     * @param modules        how many modules the configured repository offers
     * @param constants      how many constants are interned in the current pool
     * @param invalidations  how many times cached type information has been invalidated
     * @param heapBytes      used heap, which is the JVM's figure and not the compiler's alone
     */
    public record Footprint(int modules, int constants, int invalidations, long heapBytes) {
        @Override
        public String toString() {
            return "modules=" + modules + ", constants=" + constants
                    + ", invalidations=" + invalidations
                    + ", heap=" + (heapBytes / (1024 * 1024)) + "MB";
        }
    }

    /**
     * Take a {@link Footprint} without selecting a compilation's pool. This does not initialize
     * the runtime; pool counts are zero. Use {@link #footprint(Compilation)} for compiler counts.
     *
     * @return the snapshot; the counts are zero where nothing has been configured or built yet
     */
    public Footprint footprint() {
        return footprint(null);
    }

    /**
     * Take a passive snapshot of a compilation's pool and the configured repository.
     *
     * @param compilation  the compilation to measure, or null for repository and heap counts only
     *
     * @return the snapshot; pool counts are zero if no file structure was built
     */
    public Footprint footprint(Compilation compilation) {
        ConstantPool pool    = compilation == null ? null : compilation.pool();
        Runtime      runtime = Runtime.getRuntime();
        return new Footprint(cfgRepo == null ? 0 : cfgRepo.getModuleNames().size(), pool == null ? 0 : pool.size(),
                pool == null ? 0 : pool.getInvalidationCount(),
                runtime.totalMemory() - runtime.freeMemory());
    }

    /**
     * @return true if configured
     * @throws IllegalStateException if not configured
     */
    private boolean verifyConfigured() {
        if (!configured) {
            // attempt to auto-configure
            String home = System.getenv("XDK_HOME");
            if (home != null) {
                // an XDK keeps its libraries in lib/ and the two modules the compiler bootstraps
                // against - the turtle, mack.xtclang.org, and the native bridge - in javatools/.
                // Configuring only lib/ produced a repository that could never compile anything:
                // every compile failed in prelinkSystemLibraries with "Unable to load module:
                // mack.xtclang.org", reported as an internal error with no location
                File             dirHome = new File(home);
                ModuleRepository repo    = repoOver(new File(dirHome, "lib"), new File(dirHome, "javatools"));
                if (repo != null) {
                    configure(repo, null);
                }
            }

            if (!configured) {
                throw new IllegalStateException("EmbeddingSupport has not been configured, and the"
                        + " \"XDK_HOME\" environment variable is missing or invalid");
            }
        }
        return true;
    }

    /**
     * @return true when the JIT implementation is complete and can be used by EmbeddingSupport
     */
    private boolean useJit() {
        return false;
    }

    /**
     * @return the Connector instance
     */
    public Connector ensureConnector() {
        synchronized (LOCK) {
            if (connector == null) {
                this.connector = useJit()
                        ? JitControl.createConnector(cfgRepo)
                        : InterpreterControl.createConnector(cfgRepo);
            }
            return connector;
        }
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * @return the singleton EmbeddingSupport instance
     */
    public static EmbeddingSupport instance() {
        return Singleton.instance;
    }

    /**
     * Provide configuration necessary for the underlying Ecstasy tools and libraries. Must be
     * called exactly one time before any other method.
     *
     * @param coreRepo        the ModuleRepository to load the core libraries from
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources in lieu of the default injector
     *                        for this implementation
     */
    public EmbeddingSupport configure(ModuleRepository coreRepo, String customInjector) {
        synchronized (LOCK) {
            if (configured) {
                if (!(Objects.equals(coreRepo, cfgRepo) && Objects.equals(customInjector, cfgInjector))) {
                    throw new IllegalStateException("configuration has been performed, and cannot be modified");
                }
            } else {
                cfgRepo     = coreRepo;
                cfgInjector = customInjector;
                configured  = true;
            }
        }
        return this;
    }

    /**
     * @return true iff EmbeddingSupport has been configured
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * @return the configured repository, or null if EmbeddingSupport has not been configured
     */
    public ModuleRepository getConfiguredRepository() {
        return configured ? cfgRepo : null;
    }

    /**
     * @return the "modulename:classname" of the default injector to use for all "run()" containers,
     *         or null to use EmbeddingSupport's built-in default injector
     */
    public String getConfiguredInjector() {
        return configured ? cfgInjector : null;
    }

    /**
     * Obtain the constant pool of the runtime, starting one if it is not running yet.
     *
     * This is not the pool a compilation used - that belongs to the {@link Compilation} it
     * produced. Asking for this one boots an interpreter: a connector builds a NativeContainer,
     * which loads a native template for every core module, so it needs the whole library and not
     * just the part the compiler bootstraps against. The old name said "get" and read like an
     * accessor.
     *
     * @return the runtime's constant pool
     */
    public ConstantPool ensureRuntimePool() {
        verifyConfigured();
        return ensureConnector().getConstantPool();
    }

    /**
     * Obtain the runtime's constant pool, starting the runtime if necessary.
     *
     * @return the runtime's constant pool
     * @deprecated use {@link #ensureRuntimePool()} to make runtime initialization explicit;
     *             use {@link Compilation#pool()} for a compilation's pool
     */
    @Deprecated
    public ConstantPool getConstantPool() {
        return ensureRuntimePool();
    }

    // ----- compiler support ----------------------------------------------------------------------

    /**
     * Compile a module that is in a String.
     *
     * @param source  the source code for an entire module to compile
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     */
    public ModuleStructure compile(String source, ModuleRepository input, @NotNull ErrorListener errs) {
        return compile(new Source(source), input, errs);
    }

    /**
     * Compile a module held in memory, as a named document.
     *
     * A diagnostic's identity includes the name of the source it came from, so a host holding
     * several documents that are not on disk - an editor's unsaved buffers - has to be able to
     * tell them apart. Without a name, two documents with a problem at the same offset produce
     * the same identity and a listener that deduplicates discards the second. The name belongs to
     * the document rather than to the act of compiling it, which is why this takes a
     * {@link Source}: {@code new Source(text, uri)} says it once, where a second String parameter
     * would sit next to the first and be silently swappable with it.
     *
     * @param source  the source to compile, carrying whatever name it was created with
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     */
    public ModuleStructure compile(Source source, ModuleRepository input, @NotNull ErrorListener errs) {
        return compileModule(source, input, errs).module();
    }

    /**
     * The outcome of compiling a source or module source tree.
     *
     * A failed compilation used to answer with nothing but null, which threw away everything the
     * attempt had built. That is most of what a host wants when it fails: the structures a
     * verification error was raised against still exist, and the pool they were interned in is
     * the only way to reach them - to ask a type what building its TypeInfo had to say, for
     * instance.
     *
     * Recovered source trees have not entered compiler passes. Traverse their children using
     * each root's Source; child parent pointers may not yet be installed.
     *
     * @param module       the compiled module, or null if the compilation did not get that far
     * @param file         the file structure that was built, or null if it did not get that far
     * @param ast          the assembled source tree, or null if loading/parsing failed; linked
     *                     member trees retain their original Sources and positions
     * @param sourceTrees  available per-source syntax, including recovered trees on parse failure;
     *                     these are structural facts, not a promise of semantic validity
     */
    public record Compilation(ModuleStructure module, FileStructure file, StatementBlock ast,
                              List<StatementBlock> sourceTrees) {
        public Compilation {
            sourceTrees = List.copyOf(sourceTrees);
        }

        /** Retain the original construction API for hosts supplying one assembled tree. */
        public Compilation(ModuleStructure module, FileStructure file, StatementBlock ast) {
            this(module, file, ast, ast == null ? List.of() : List.of(ast));
        }

        /**
         * Represent file structures without a successful module or retained source tree.
         *
         * @param file  the file structure produced so far
         *
         * @return a partial compilation
         */
        public static Compilation forFile(FileStructure file) {
            return new Compilation(null, requireNonNull(file, "file"), null);
        }

        /**
         * @return true iff a module came out of it
         */
        public boolean succeeded() {
            return module != null;
        }

        /**
         * @return the pool this compilation interned into, or null if there was no compilation
         */
        public ConstantPool pool() {
            return file == null ? null : file.getConstantPool();
        }

        /**
         * Walk the assembled source after successful loading/parsing.
         *
         * A host that wants to say where something is has to come through here: a
         * {@link org.xvm.asm.Component} knows its name, its kind and its children, and nothing
         * about the text it was written in. Only the AST carries positions.
         *
         * @return the assembled source root, or null after loading/parsing errors; use
         *         {@link #sourceTrees()} for available structural syntax in that case
         */
        public StatementBlock parsed() {
            return ast;
        }
    }

    /**
     * Compile a module held in memory, and answer with everything the attempt produced.
     *
     * @param source  the source to compile, carrying whatever name it was created with
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return the outcome; never null, though its parts may be
     */
    public Compilation compileModule(Source source, ModuleRepository input, @NotNull ErrorListener errs) {
        requireNonNull(source, "source");
        return compileModule(listener -> {
            StatementBlock tree = new Parser(source, listener).parseSource();
            return new ParsedSources(listener.hasSeriousErrors() ? null : tree, List.of(tree));
        }, input, errs);
    }

    /**
     * Compile a module's source tree using the same discovery, resource association and parse-tree
     * assembly as the CLI. The returned AST includes the member files with their original Sources.
     * Hosts may override {@link ModuleInfo#readSource(File)} and
     * {@link ModuleInfo#sourceEntries(File)} to supply a consistent snapshot of unsaved text and
     * source membership. The default provider discovers and reads files from disk.
     *
     * @param sources  a fresh ModuleInfo for this attempt; do not reuse a previously parsed tree
     * @param input    the optional repository of compiled dependencies
     * @param errs     the listener for this compilation
     *
     * @return the outcome, including the linked AST when source loading succeeded
     */
    public Compilation compileModule(ModuleInfo sources, ModuleRepository input, @NotNull ErrorListener errs) {
        requireNonNull(sources, "sources");
        return compileModule(listener -> {
            Node root = sources.getSourceTree(listener);
            return new ParsedSources(root == null ? null : (StatementBlock) root.ast(),
                    sources.getParsedSources());
        }, input, errs);
    }

    /**
     * Facts retained by an explicit incomplete-source analysis, never a compiled module.
     *
     * The available source syntax and sites may be unvalidated. A child expression supplies a
     * semantic fact only if validation succeeded (isValidated and a fitting TypeFit) and its
     * resolved target/type is available. Failed validation can leave placeholder types. An absent
     * pool means semantic analysis did not start. No overload, missing argument, or result type
     * is inferred for the incomplete operation. Consumers must copy facts while exclusively
     * owning the attempt, as with Compilation; ASTs and pools are not concurrent query objects.
     */
    public record PartialAnalysis(List<StatementBlock> sourceTrees, List<IncompleteStatement> sites,
                                  Optional<ConstantPool> pool) {
        public PartialAnalysis {
            sourceTrees = List.copyOf(sourceTrees);
            sites       = List.copyOf(sites);
            requireNonNull(pool, "pool");
        }
    }

    /**
     * Analyze the intact prefix of one trailing incomplete expression statement in a single
     * source module. Supports a member-access dot or unfinished call at EOF, with complete
     * preceding declarations/statements and arguments. Other syntax errors prevent semantic
     * analysis; returns, assignments, incomplete nested arguments and module member files are
     * outside this bounded contract. Complete input has no incomplete site and is not compiled.
     *
     * Parsing reports immediately to the host and respects its budget/cancellation. Only the
     * recognized EOF boundary may enter compiler passes. The incomplete statement validates its
     * intact children in the real method context, then fails validation before method emission.
     * Its repeated EOF diagnostic stops the compiler internally without being delivered twice
     * to the host. Other diagnostics and unexpected failures remain visible.
     *
     * @param source  a fresh named source for this attempt
     * @param input   the optional repository of compiled dependencies
     * @param errs    the host's diagnostic listener
     *
     * @return partial facts with no success/module artifact contract
     */
    public PartialAnalysis analyzeIncomplete(Source source, ModuleRepository input,
                                            @NotNull ErrorListener errs) {
        verifyConfigured();
        requireNonNull(source, "source");
        requireNonNull(errs, "errs");
        Set<String> delivered = new HashSet<>();
        ErrorListener host = ErrorListener.cancellable(ErrorListener.collecting(error -> {
            if (delivered.add(error.genUID())) {
                errs.log(error);
            }
        }), errs::isAbortDesired);
        ErrorList syntaxErrors = new ErrorList(ErrorList.UNLIMITED);
        StatementBlock tree;
        try {
            if (host.isAbortDesired()) {
                return new PartialAnalysis(List.of(), List.of(), Optional.empty());
            }
            tree = Parser.forPartialAnalysis(source, ErrorListener.tee(syntaxErrors, host)).parseSource();
        } catch (CompilerException e) {
            return new PartialAnalysis(List.of(), List.of(), Optional.empty());
        } catch (RuntimeException | AssertionError e) {
            host.error(ERR_INTERNAL, NOWHERE, e, "Incomplete-source parsing failed");
            return new PartialAnalysis(List.of(), List.of(), Optional.empty());
        }

        List<AstNode> nodes = new ArrayList<>(List.of(tree));
        for (int i = 0; i < nodes.size(); ++i) {
            nodes.get(i).children().forEachRemaining(nodes::add);
        }
        List<IncompleteStatement> sites = nodes.stream()
                .filter(IncompleteStatement.class::isInstance)
                .map(IncompleteStatement.class::cast).toList();
        if (sites.size() != 1 || syntaxErrors.getErrors().stream().anyMatch(error ->
                error.getSeverity().isAtLeast(ERROR) && !error.getCode().equals(Parser.UNEXPECTED_EOF))) {
            return new PartialAnalysis(List.of(tree), List.of(), Optional.empty());
        }

        Compilation attempt = compileModule(listener -> new ParsedSources(tree, List.of(tree)), input, host);
        return new PartialAnalysis(List.of(tree), sites, Optional.ofNullable(attempt.pool()));
    }

    /** An assembled tree is available only when parsing/loading succeeded. */
    private record ParsedSources(StatementBlock root, List<StatementBlock> sources) {}

    private Compilation compileModule(Function<ErrorListener, ParsedSources> parse,
                                      ModuleRepository input, ErrorListener errs) {
        verifyConfigured();
        requireNonNull(errs, "errs");
        EmbeddingCompiler compiler = new EmbeddingCompiler(parse, input, cfgRepo, errs);
        try {
            if (!errs.isAbortDesired()) {
                compiler.process();
            }
        } catch (LauncherException e) {
            // Expected aborts already have diagnostics or a cancellation request.
            if (!errs.hasSeriousErrors() && !errs.isAbortDesired()) {
                errs.error(ERR_INTERNAL, NOWHERE, e, "Compilation failed");
            }
        } catch (RuntimeException | AssertionError e) {
            // An earlier source error must not hide an unexpected compiler failure.
            errs.error(ERR_INTERNAL, NOWHERE, e, "Compilation failed");
        }
        return compiler.result();
    }

    /**
     * Compile a module that is in a file or directory.
     *
     * @param file    the location of the module source code on disk, either the module source file
     *                or the directory containing a single .x file and nested contents thereof
     * @param input   (optional) the module repository to read any required modules from
     * @param output  (optional) the module repository to write any compiled modules to
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return true if the compilation succeeded and the result was placed into the output
     */
    public boolean compile(File file, ModuleRepository input, ModuleRepository output, @NotNull ErrorListener errs) {
        requireNonNull(errs, "errs");
        ModuleStructure module;
        try {
            module = compileModule(new ModuleInfo(file, false), input, errs).module();
        } catch (IllegalArgumentException e) {
            errs.error(ERR_INTERNAL, NOWHERE, e, "Unable to locate module " + file);
            return false;
        }

        if (module == null) {
            assert errs.hasSeriousErrors() || errs.isAbortDesired();
            return false;
        }

        if (output != null) {
            try {
                output.storeModule(module);
            } catch (IOException e) {
                errs.error(ERR_INTERNAL, at(module), e, "Unable to store module " + module.getName());
                return false;
            }
        }
        return true;
    }

    /**
     * Adapter that supplies the source and repositories to the standard compiler pipeline and
     * captures its single compiled module instead of writing it to disk.
     */
    private static class EmbeddingCompiler
            extends org.xvm.tool.Compiler {
        private final Function<ErrorListener, ParsedSources> parse;

        private final ModuleRepository     inRepo;
        private final ModuleRepository     coreRepo;
        private       ModuleStructure      module;
        private       FileStructure        file;
        private       StatementBlock       ast;
        private       List<StatementBlock> sourceTrees = List.of();

        /**
         * Everything this attempt produced.
         *
         * Each part is present when the attempt got that far and null when it did not, which the
         * fields already say: the module is only ever assigned on success, and the file structure
         * and the AST only once they exist. So there is nothing for the caller to decide, and no
         * success flag to pass in and get wrong.
         *
         * @return the outcome; never null, though its parts may be
         */
        Compilation result() {
            return new Compilation(module, file, ast, sourceTrees);
        }

        /**
         * @return the file structure this compilation built, which exists whether or not the
         *         compilation went on to succeed
         */
        FileStructure getFileStructure() {
            return file;
        }

        @Override
        public boolean isAbortDesired() {
            // Cancellation must also stop between phases; stopping only an individual compiler
            // loop would let the following phase run against an unfinished predecessor.
            return super.isAbortDesired() || f_errs.isAbortDesired();
        }

        protected EmbeddingCompiler(Function<ErrorListener, ParsedSources> parse,
                                    ModuleRepository input, ModuleRepository core, ErrorListener errs) {
            super(CompilerOptions.builder().build(), SILENT_CONSOLE, errs);

            this.parse    = parse;
            this.inRepo   = input;
            this.coreRepo = core;
        }

        @Override
        protected int process() {
            ModuleRepository repoLib = ensureLibraryRepo();
            checkErrors("repository setup");

            prelinkSystemLibraries(repoLib);
            checkErrors("system library linking");

            StatementBlock block;
            try {
                // The launcher stops the next compilation stage on any error. Parsing can still
                // recover within its own stage, subject to the host's budget and cancellation.
                ErrorListener errs = ErrorListener.cancellable(
                        ErrorListener.collecting(this::log), f_errs::isAbortDesired);
                ParsedSources parsed = parse.apply(errs);
                block = this.ast = parsed.root();
                this.sourceTrees = parsed.sources();
            } catch (CompilerException e) {
                return 1;
            }
            if (checkErrors("source parsing") != 0) {
                return 1;
            }

            if (block == null) {
                log(ERROR, "Unable to load module sources");
                return 1;
            }

            if (block.getStatements().isEmpty()) {
                log(ERROR, "In-memory source does not contain a module");
                return checkErrors("source parsing");
            }
            Statement stmt = block.getStatements().getLast();
            if (!(stmt instanceof TypeCompositionStatement stmtModule) ||
                    stmtModule.getCategory().getId() != Id.MODULE) {
                log(ERROR, "In-memory source does not contain a module");
                return checkErrors("source parsing");
            }

            Compiler      compiler = new Compiler(stmtModule, this);
            FileStructure struct   = compiler.generateInitialFileStructure();
            this.file = struct;
            if (struct == null || checkErrors("module creation") != 0) {
                return 1;
            }

            try {
                repoLib.storeModule(struct.getModule());
            } catch (IOException e) {
                log(ERROR, e, "I/O exception storing module: {}", struct.getModule().getName());
                return 1;
            }

            int result = super.compile(List.of(compiler), repoLib);
            if (result == 0) {
                this.module = struct.getModule();
            }
            return result;
        }

        @Override
        protected int compile(List<Compiler> compilers, ModuleRepository repoLib) {
            throw new IllegalStateException("This method must not be called");
        }

        @Override
        protected ModuleRepository configureLibraryRepo(List<File> ignore) {
            BuildRepository build = new BuildRepository();
            return inRepo == null || inRepo == coreRepo
                    ? new LinkedRepository(true, build, coreRepo)
                    : new LinkedRepository(true, build, inRepo, coreRepo);
        }

        @Override
        protected int emitModules(List<Node> allNodes, ModuleRepository ignore) {
            throw new IllegalStateException("This method must not be called");
        }

        /**
         * @return the result of the compilation
         */
        protected ModuleStructure getModule() {
            return module;
        }
    }

    /**
     * Represents management and monitoring information about a running Ecstasy module.
     */
    public interface Control
            extends AutoCloseable {
        /**
         * @return true if the module is still running
         */
        boolean running();

        /**
         * Wait for the module to finish running.
         */
        void join();

        /**
         * @return the Java Instant when the app was started up
         */
        Instant whenStarted();

        /**
         * @return if running() is false, this is the Java Instant when the app stopped running,
         *         otherwise null
         */
        Instant whenStopped();

        /**
         * @return the Ecstasy Int exit code from the module's run() method, provided as a Java
         *         "Long"; null otherwise
         */
        Long result();

        /**
         * Stop the app if necessary, wait for it to terminate, and release all of its resources.
         */
        @Override
        void close();
    }

    // ----- run support ---------------------------------------------------------------------------

    /**
     * Create a runtime container and execute the provided module.
     *
     * A limited set of injections are made available to the module, including the console, clock,
     * and other "safe" injectable types. The FileSystem is provided as detailed by the "rootDir"
     * parameter.
     *
     * @param module      the module to execute
     * @param console     (optional) the PrintWriter for the executing application
     * @param rootDir     (optional) the root directory for the application's file system; supplied
     *                    directories are caller-owned and are not deleted; null selects a
     *                    task-specific directory under "./.runner" that is deleted when the
     *                    returned Control is closed
     * @param injections  (optional) additional "String" and "String[]" injections
     * @param errs        a means for the container to report uncaught exceptions and
     *                    other errors
     *
     * @return a Control object for the running module
     */
    public Control run(
            ModuleStructure           module,
            PrintWriter               console,
            File                      rootDir,
            Map<String, List<String>> injections,
            ErrorListener             errs) {
        return run(new InstantRepository(module), module.getName(), module.getVersion(),
                console, rootDir, injections, null, errs);
    }

    /**
     * Create a runtime container and execute the specified module.
     *
     * The "customerInjector" option allows the caller to indicate an Ecstasy Injector class that
     * will be loaded into its own container, and provided with the full set of injectable resources
     * that Ecstasy supports, also including any provided String injections; in turn, that
     * implementation provides the injections that will be available to the specified module within
     * its own container.
     *
     * @param input           the ModuleRepository providing any necessary modules
     * @param moduleName      the module name to execute; must be loadable from "input"
     * @param version         (optional) the version of the module to load
     * @param console         (optional) the PrintWriter for the executing application
     * @param rootDir         (optional) the root directory for the application's file system;
     *                        supplied directories are caller-owned and are not deleted; null
     *                        selects a task-specific directory under "./.runner" that is deleted
     *                        when the returned Control is closed
     * @param injections      (optional) additional "String" and "String[]" injections
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources; when used, the "rootDir" value is
     *                        ignored
     * @param errs            a means for the container to report uncaught exceptions and
     *                        other errors
     *
     * @return a Control object for the running module, or null if it could not be started, in
     *         which case the reason is reported to "errs"
     */
    public Control run(
            ModuleRepository          input,
            String                    moduleName,
            Version                   version,
            PrintWriter               console,
            File                      rootDir,
            Map<String, List<String>> injections,
            String                    customInjector,
            @NotNull ErrorListener    errs) {
        verifyConfigured();
        requireNonNull(errs, "errs");

        ModuleRepository repository = input == null || input == cfgRepo
                ? cfgRepo
                : new LinkedRepository(input, cfgRepo);
        ModuleStructure module = version == null
                ? repository.loadModule(moduleName)
                : repository.loadModule(moduleName, version, true);
        if (module == null) {
            errs.error(version == null ? ERR_NO_APP_MODULE : ERR_NO_APP_MODULE_VER, NOWHERE, moduleName, version);
            return null;
        }

        if (injections != null && !injections.isEmpty()
                || customInjector != null || cfgInjector != null) {
            throw new UnsupportedOperationException(
                    "Custom injectors are not implemented yet");
        }

        try {
            Connector connector = ensureConnector();
            return useJit()
                    ? JitControl.create(connector, module, repository, console, rootDir, errs)
                    : InterpreterControl.create(connector, module, repository, console, rootDir, errs);
        } catch (RuntimeException | AssertionError e) {
            // an AssertionError is an Error, so the RuntimeException guard alone let a tripped
            // assertion in the structure code past this report and out to the host. Errors are
            // not caught wholesale: a VirtualMachineError says the JVM is in trouble, not that
            // this module failed to start, and handling one is not something to rely on
            errs.error(ERR_CREATE_APP_CONTAINER, at(module), e, "Unable to start " + moduleName);
            return null;
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE        = "EMB-1";
    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE_VER    = "EMB-2";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_CREATE_APP_CONTAINER = "EMB-3";
    /**
     * "%1" - exception
     */
    public static final String ERR_UNHANDLED_EXCEPTION  = "EMB-4";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_INTERNAL             = "EMB-5";
}
