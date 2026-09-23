package org.xvm.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.locks.ReentrantLock;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
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

import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.javajit.JitConnector;

import org.xvm.tool.Console;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.Deadline;

import static org.xvm.runtime.Runtime.DEFAULT_SHUTDOWN_TIMEOUT;

import static org.xvm.util.Severity.ERROR;

/**
 * A class used to support embedding Ecstasy tools. Interpreter requests use a long-running Ecstasy
 * application (in "Container Zero") to create child application containers. JIT requests use a
 * shared Java-targeting XVM with a fresh container per request. Both runtimes start lazily. Use
 * {@link #create} for an owned session or {@link #instance} for the legacy singleton. Configuration
 * supplies the Module Repository from which to load the core Ecstasy classes. Without configuration,
 * the singleton attempts to locate them using the "XDK_HOME" environment variable.
 *
 * <p>Host-side compilation and request preparation are serialized. Applications run asynchronously
 * in separate containers. Close each control to release its request and close the session to stop
 * its runtime.
 */
public class EmbeddingSupport
        implements AutoCloseable {
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
    private JitConnector     jitConnector;
    private Path             jitBridge;
    private volatile boolean closed;
    private Throwable        cleanupFailure;

    private final Set<OwnedControl> controls = new HashSet<>();
    private final ReentrantLock closeLock = new ReentrantLock();

    // Native templates contain mutable static state; an implementation loader can host only one
    // live embedding runtime. Release ownership only after native and host cleanup have completed.
    private static EmbeddingSupport runtimeOwner;

    /**
     * @return true if configured
     * @throws IllegalStateException if not configured
     */
    private boolean verifyConfigured() {
        if (closed) {
            throw new IllegalStateException("Embedding session is closed");
        }
        if (cleanupFailure != null) {
            throw new IllegalStateException("Embedding session failed to release a request", cleanupFailure);
        }
        if (!configured) {
            // attempt to auto-configure
            String home = System.getenv("XDK_HOME");
            if (home != null) {
                File dir = new File(new File(home), "lib");
                if (dir.isDirectory()) {
                    configure(new DirRepository(dir, true), null);
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
     * @return the Connector instance
     */
    public Connector ensureConnector() {
        return ensureConnector(RunRequest.Backend.INTERPRETER);
    }

    /**
     * Obtain the session's lazily created runtime for the selected backend.
     */
    public Connector ensureConnector(RunRequest.Backend backend) {
        synchronized (LOCK) {
            verifyConfigured();
            if (Objects.requireNonNull(backend) == RunRequest.Backend.JIT) {
                if (jitConnector == null) {
                    jitConnector = new JitConnector(cfgRepo, jitBridge);
                }
                return jitConnector;
            }
            if (connector == null) {
                if (runtimeOwner != null && runtimeOwner != this) {
                    throw new IllegalStateException(
                            "Another embedding session owns the runtime in this classloader");
                }
                this.connector = InterpreterControl.createConnector(cfgRepo);
                runtimeOwner = this;
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
     * Create an owned embedding session without starting its execution runtime.
     *
     * <p>Close the session when the host is finished. Only one session per implementation
     * classloader may own a live interpreter runtime; a closed session cannot be reopened.
     *
     * @param coreRepo  the repository containing the XDK libraries
     *
     * @return a configured session
     */
    public static EmbeddingSupport create(ModuleRepository coreRepo) {
        return new EmbeddingSupport().configure(Objects.requireNonNull(coreRepo), null);
    }

    /**
     * Create a session with an explicit JIT template JAR or class directory. The templates are read
     * and augmented by the JIT and must not be placed on the application's Java classpath.
     *
     * @param coreRepo   the repository containing the XDK libraries
     * @param jitBridge  the JIT template JAR or class directory
     *
     * @return a configured session; neither execution backend is started yet
     */
    public static EmbeddingSupport create(ModuleRepository coreRepo, Path jitBridge) {
        EmbeddingSupport session = create(coreRepo);
        session.jitBridge = Objects.requireNonNull(jitBridge);
        return session;
    }

    /**
     * Close outstanding controls and terminate the session's runtime. Caller-owned consoles,
     * repositories, and file-system roots remain owned by the caller.
     */
    @Override
    public void close() {
        close(DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Close the session using one budget for all controls and runtime termination.
     *
     * @param timeout  the nonnegative shutdown budget
     */
    public void close(Duration timeout) {
        Deadline deadline = Deadline.after(timeout);
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    if (!closeLock.tryLock(deadline.remainingNanos(), TimeUnit.NANOSECONDS)) {
                        throw new IllegalStateException("Embedding session close is still in progress");
                    }
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            try {
                closeSession(deadline);
            } finally {
                closeLock.unlock();
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Retain incomplete controls and runtimes so a later close can finish after a deadline expires.
     * A failed native cleanup remains a failure; executor termination alone cannot release ownership.
     */
    private void closeSession(Deadline deadline) {
        List<OwnedControl> pending;
        Connector runtime;
        JitConnector jitRuntime;
        synchronized (LOCK) {
            closed = true;
            pending = List.copyOf(controls);
            runtime = connector;
            jitRuntime = jitConnector;
        }

        Throwable failure = null;
        var controlFailures = new LinkedHashMap<OwnedControl, Throwable>();
        for (OwnedControl control : pending) {
            try {
                control.close(deadline.remaining());
            } catch (RuntimeException | Error e) {
                controlFailures.put(control, e);
            }
        }
        boolean stopped = runtime == null;
        boolean jitStopped = jitRuntime == null;
        // A failed bounded close can leave a JIT worker running. Keep its templates until it stops.
        if (jitRuntime != null && controlFailures.isEmpty()) {
            try {
                jitRuntime.xvm.close();
                jitStopped = true;
            } catch (IOException | RuntimeException | Error e) {
                failure = collectFailure(failure, e);
            }
        }
        try {
            if (runtime instanceof InterpreterConnector interpreter) {
                try {
                    interpreter.close(deadline.remaining());
                } finally {
                    stopped = interpreter.isClosed();
                }
            }
        } catch (RuntimeException | Error e) {
            failure = collectFailure(failure, e);
        }
        if (stopped) {
            for (OwnedControl control : pending) {
                if (control.delegate instanceof InterpreterControl interpreterControl) {
                    try {
                        interpreterControl.releaseAfterRuntimeClose(deadline.remaining());
                        controlFailures.remove(control);
                        synchronized (LOCK) {
                            controls.remove(control);
                        }
                    } catch (RuntimeException | Error e) {
                        controlFailures.put(control, collectFailure(controlFailures.get(control), e));
                    }
                }
            }
        }
        for (Throwable controlFailure : controlFailures.values()) {
            failure = collectFailure(failure, controlFailure);
        }
        synchronized (LOCK) {
            if (stopped) {
                connector = null;
            }
            if (jitStopped) {
                jitConnector = null;
            }
            if (stopped && controls.isEmpty() && runtimeOwner == this) {
                runtimeOwner = null;
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("Embedding session did not close", failure);
        }
    }

    private static Throwable collectFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
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
            if (closed) {
                throw new IllegalStateException("Embedding session is closed");
            }
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
     * @return the ConstantPool of the core Ecstasy libraries used by the runtime Connector instance
     *         that is instantiated by EmbeddingSupport
     */
    public ConstantPool getConstantPool() {
        return ensureConnector().getConstantPool();
    }

    // ----- compiler support ----------------------------------------------------------------------

    /**
     * Compile a module that is in a String.
     *
     * @param source  the source code for an entire module to compile
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    (optional) the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     */
    public ModuleStructure compile(String source, ModuleRepository input, ErrorListener errs) {
        synchronized (LOCK) {
            try (var ignore = ConstantPool.withPool(null)) {
                verifyConfigured();
                try {
                    EmbeddingCompiler compiler = new EmbeddingCompiler(source, input, cfgRepo, errs);
                    return compiler.process() == 0
                            ? compiler.getModule()
                            : null;
                } catch (RuntimeException | AssertionError e) {
                    // as in run(): the compiler runs over caller-supplied source, so a failure in it is
                    // reported here rather than thrown at the caller, who was promised a null instead
                    if (errs != null) {
                        errs.log(ERROR, ERR_INTERNAL,
                                new Object[] {e, "Compilation failed"}, null);
                    }
                    return null;
                }
            }
        }
    }

    /**
     * Compile source trees and resources using the standard compiler options and output rules.
     * Each call uses fresh compilation state without starting the execution runtime.
     *
     * @return zero on success, nonzero if compilation fails
     */
    public int compile(CompilerOptions options, Console console, ErrorListener errs) {
        synchronized (LOCK) {
            try (var ignore = ConstantPool.withPool(null)) {
                verifyConfigured();
                try {
                    return new FileCompiler(options, console == null ? SILENT_CONSOLE : console,
                            errs, cfgRepo, null, null).compile();
                } catch (LauncherException e) {
                    return 1;
                } catch (RuntimeException | AssertionError e) {
                    if (errs != null) {
                        errs.log(ERROR, ERR_INTERNAL, new Object[] {e, "Compilation failed"}, null);
                    }
                    return 1;
                }
            }
        }
    }

    /**
     * Compile a module that is in a file or directory, including nested sources and resources.
     *
     * @param file    the location of the module source code on disk, either the module source file
     *                or the directory containing a single .x file and nested contents thereof
     * @param input   (optional) the module repository to read any required modules from
     * @param output  (optional) the module repository to write any compiled modules to
     * @param errs    (optional) the ErrorListener to log any compiler messages to
     *
     * @return true if the compilation succeeded and the result was placed into the output
     */
    public boolean compile(File file, ModuleRepository input, ModuleRepository output,
                           ErrorListener errs) {
        synchronized (LOCK) {
            try (var ignore = ConstantPool.withPool(null)) {
                verifyConfigured();
                var options = CompilerOptions.builder().addInputFile(file).forceRebuild(true).build();
                try {
                    return new FileCompiler(options, SILENT_CONSOLE, errs, cfgRepo, input,
                            output == null ? new BuildRepository() : output).compile() == 0;
                } catch (RuntimeException | AssertionError e) {
                    if (errs != null) {
                        errs.log(ERROR, ERR_INTERNAL, new Object[] {e, "Compilation failed"}, null);
                    }
                    return false;
                }
            }
        }
    }

    /**
     * Adapter that supplies the source and repositories to the standard compiler pipeline and
     * captures its single compiled module instead of writing it to disk.
     */
    private static class EmbeddingCompiler
            extends org.xvm.tool.Compiler {
        private final String           source;
        private final ModuleRepository inRepo;
        private final ModuleRepository coreRepo;
        private       ModuleStructure  module;

        protected EmbeddingCompiler(String source, ModuleRepository input, ModuleRepository core,
                                    ErrorListener errs) {
            super(CompilerOptions.builder().build(), SILENT_CONSOLE, errs);

            this.source   = source;
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
                block = new Parser(new Source(source), this).parseSource();
            } catch (CompilerException e) {
                return 1;
            }
            if (checkErrors("source parsing") != 0) {
                return 1;
            }

            Statement stmt = block.getStatements().getLast();
            if (!(stmt instanceof TypeCompositionStatement stmtModule) ||
                    stmtModule.getCategory().getId() != Id.MODULE) {
                log(ERROR, "In-memory source does not contain a module");
                return checkErrors("source parsing");
            }

            Compiler      compiler = new Compiler(stmtModule, this);
            FileStructure struct   = compiler.generateInitialFileStructure();
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
                // Publish assembled code, just as file compilation does. Compiler-owned ops still
                // refer to the compilation pool and cannot safely be cloned into runtime pools.
                try {
                    var bytes = new ByteArrayOutputStream();
                    struct.writeTo(bytes);
                    this.module = new FileStructure(new ByteArrayInputStream(bytes.toByteArray())).getModule();
                } catch (IOException e) {
                    log(ERROR, e, "I/O exception assembling module: {}", struct.getModule().getName());
                    return 1;
                }
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
        default void close() {
            close(DEFAULT_SHUTDOWN_TIMEOUT);
        }

        /**
         * Stop the application and release its resources within the supplied budget.
         *
         * @param timeout  the nonnegative shutdown budget
         */
        void close(Duration timeout);
    }

    // ----- run support ---------------------------------------------------------------------------

    /**
     * Create a runtime container and execute the provided module.
     *
     * <p>A limited set of injections are made available to the module, including the console, clock,
     * and other "safe" injectable types. The FileSystem is provided as detailed by the "rootDir"
     * parameter.
     *
     * @param module      the module to execute
     * @param console     (optional) the PrintWriter for the executing application
     * @param rootDir     (optional) the root directory for the application's file system; supplied
     *                    directories are caller-owned and are not deleted; null selects a
     *                    unique temporary directory that is deleted when the
     *                    returned Control is closed
     * @param injections  (optional) additional "String" and "String[]" injections
     * @param errs        (optional) a means for the container to report uncaught exceptions and
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
     * <p>The "customerInjector" option allows the caller to indicate an Ecstasy Injector class that
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
     *                        selects a unique temporary directory that is deleted
     *                        when the returned Control is closed
     * @param injections      (optional) additional "String" and "String[]" injections
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources; when used, the "rootDir" value is
     *                        ignored
     * @param errs            (optional) a means for the container to report uncaught exceptions and
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
            ErrorListener             errs) {
        return run(input, moduleName, version, console, rootDir, injections, customInjector,
                "run", List.of(), false, RunRequest.Backend.INTERPRETER, errs);
    }

    /**
     * Execute a module with an explicit entry point and host resource context.
     */
    public Control run(RunRequest request, ErrorListener errs) {
        return run(request.repository(), request.moduleName(), null, request.console(),
                request.directory(), request.injections(), null, request.method(), request.arguments(),
                request.hostFileSystem(), request.backend(), errs);
    }

    private Control run(ModuleRepository input, String moduleName, Version version, PrintWriter console,
                        File rootDir, Map<String, List<String>> injections, String customInjector,
                        String method, List<String> arguments, boolean hostFileSystem,
                        RunRequest.Backend backend, ErrorListener errs) {
        synchronized (LOCK) {
            try (var ignore = ConstantPool.withPool(null)) {
                verifyConfigured();

                ModuleRepository repository = input == null || input == cfgRepo
                        ? new LinkedRepository(true, new BuildRepository(), cfgRepo)
                        : new LinkedRepository(true, new BuildRepository(), input, cfgRepo);
                ModuleStructure module = version == null
                        ? repository.loadModule(moduleName)
                        : repository.loadModule(moduleName, version, true);
                if (module == null) {
                    if (errs != null) {
                        errs.log(ERROR, version == null ? ERR_NO_APP_MODULE : ERR_NO_APP_MODULE_VER,
                                new Object[] {moduleName, version}, null);
                    }
                    return null;
                }

                if (customInjector != null || cfgInjector != null) {
                    throw new UnsupportedOperationException(
                            "Custom injectors are not implemented yet");
                }

                try {
                    Connector connector = ensureConnector(backend);
                    Control delegate = backend == RunRequest.Backend.JIT
                            ? JitControl.create((JitConnector) connector, module, repository, console,
                                    method, arguments, injections == null ? Map.of() : injections, errs)
                            : InterpreterControl.create(connector, module, repository, console, rootDir,
                                    method, arguments, hostFileSystem,
                                    injections == null ? Map.of() : injections, errs);
                    var control = new OwnedControl(delegate);
                    controls.add(control);
                    return control;
                } catch (RuntimeException | AssertionError e) {
                    // an AssertionError is an Error, so the RuntimeException guard alone let a tripped
                    // assertion in the structure code past this report and out to the host. Errors are
                    // not caught wholesale: a VirtualMachineError says the JVM is in trouble, not that
                    // this module failed to start, and handling one is not something to rely on
                    if (errs != null) {
                        errs.log(ERROR, ERR_CREATE_APP_CONTAINER,
                                new Object[] {e, "Unable to start " + moduleName}, module);
                    }
                    return null;
                }
            }
        }
    }

    /**
     * Keep each control owned by its session until its resources have been released.
     */
    private class OwnedControl
            implements Control {
        OwnedControl(Control delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean running() {
            return delegate.running();
        }

        @Override
        public void join() {
            delegate.join();
        }

        @Override
        public Instant whenStarted() {
            return delegate.whenStarted();
        }

        @Override
        public Instant whenStopped() {
            return delegate.whenStopped();
        }

        @Override
        public Long result() {
            return delegate.result();
        }

        @Override
        public void close(Duration timeout) {
            try {
                delegate.close(timeout);
            } catch (RuntimeException | Error e) {
                synchronized (LOCK) {
                    cleanupFailure = e;
                }
                throw e;
            }
            synchronized (LOCK) {
                controls.remove(this);
            }
        }

        private final Control delegate;
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
