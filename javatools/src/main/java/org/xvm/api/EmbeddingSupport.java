package org.xvm.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.InstantRepository;

import static java.util.Objects.requireNonNull;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.at;

/**
 * A class used to support embedding Ecstasy tools. This implementation uses the Connector API to
 * run a long-running Ecstasy application (in "Container Zero") that is responsible for spinning up
 * any number of child containers to "run()" modules. EmbeddingSupport is a singleton, but it does
 * require configuration; specifically, it requires a Module Repository from which to load the core
 * Ecstasy classes. Without configuration, EmbeddingSupport will attempt to locate the core Ecstasy
 * classes using the "XDK_HOME" OS property.
 *
 * <p>The methods on EmbeddingSupport itself can be assumed to be thread-safe and concurrent.
 *
 * <p>EmbeddingSupport is a singleton because running a module requires the interpreter, and the
 * interpreter keeps JVM-wide state. When the Connector creates its NativeContainer, the container
 * constructs every native class template, and the template constructors assign static fields such
 * as {@code xObject.INSTANCE} and {@code xObject.CLASS}; there are about 150 such assignments in
 * the {@code org.xvm.runtime} classes. A second interpreter in the same JVM would overwrite them
 * underneath the first, so there can be only one interpreter, and therefore only one
 * EmbeddingSupport, whose core repository can be configured only once.
 *
 * <p>Compiling does not use the interpreter, so it does not need the singleton: a
 * {@link ModuleCompiler} can be created for any core repository, any number of times. The compile
 * methods here are a convenience that compile against the configured core repository.
 *
 * <p>The restriction could be removed by making the static runtime state belong to the runtime
 * that creates it: the templates and class compositions that are now in static fields would be
 * held by the NativeContainer, which already maps types to their templates, and reached through
 * the container that each template already refers to. With no JVM-wide state left, a Connector,
 * and with it EmbeddingSupport, could be an ordinary object created for a given core repository,
 * and {@link #configure} would become a constructor.
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

    private volatile boolean configured;
    private ModuleRepository cfgRepo;
    private String           cfgInjector;
    private Connector        connector;

    /**
     * @return true if configured
     * @throws IllegalStateException if not configured
     */
    private boolean verifyConfigured() {
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

    /**
     * Diagnostics for a host that keeps the runtime across runs: the external consoles that runs
     * registered and that are still registered. A run releases its console when it ends, so this is
     * zero between runs; JIT runs register none.
     *
     * @return the number of registered external consoles
     */
    public int registeredConsoles() {
        return ensureConnector() instanceof InterpreterConnector interpreter
                ? xExternalConsole.registeredCount(interpreter.getNativeContainer())
                : 0;
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
     * @return the ConstantPool of the core Ecstasy libraries used by the runtime Connector instance
     *         that is instantiated by EmbeddingSupport
     */
    public ConstantPool getConstantPool() {
        verifyConfigured();
        return ensureConnector().getConstantPool();
    }

    // ----- compiler support ----------------------------------------------------------------------

    /**
     * Compile a module that is in a String, against the configured core repository.
     *
     * @param source  the source code for an entire module to compile
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     *
     * @see ModuleCompiler#compile(String, ModuleRepository, ErrorListener)
     */
    public ModuleStructure compile(String source, ModuleRepository input, @NotNull ErrorListener errs) {
        verifyConfigured();
        requireNonNull(errs, "errs");
        return new ModuleCompiler(cfgRepo).compile(source, input, errs);
    }

    /**
     * Compile a module that is in a file, against the configured core repository.
     *
     * @param file    the module source file
     * @param input   (optional) the module repository to read any required modules from
     * @param output  (optional) the module repository to write any compiled modules to
     * @param errs    the ErrorListener to log any compiler messages to
     *
     * @return true if the compilation succeeded and the result was placed into the output
     *
     * @see ModuleCompiler#compile(File, ModuleRepository, ModuleRepository, ErrorListener)
     */
    public boolean compile(File file, ModuleRepository input, ModuleRepository output,
                           @NotNull ErrorListener errs) {
        verifyConfigured();
        requireNonNull(errs, "errs");
        return new ModuleCompiler(cfgRepo).compile(file, input, output, errs);
    }

    /**
     * The host's input for an embedded application's console.
     */
    @FunctionalInterface
    public interface ConsoleInput {
        /**
         * Input for a console that has none: reading a line is unsupported.
         */
        ConsoleInput NONE = _ -> {
            throw new UnsupportedOperationException("The console has no input");
        };

        /**
         * Read the next line. The runtime calls this on one of its IO threads, so it may block
         * until the host has a line.
         *
         * @param suppressEcho  true when the application asks that the line not be shown as it is
         *                      typed, as for a password; how to honor that is up to the host
         *
         * @return the line, or null at the end of the input
         *
         * @throws UnsupportedOperationException if this console has no input, as for {@link #NONE}
         */
        String readLine(boolean suppressEcho) throws IOException;

        /**
         * @return input that reads lines from the specified reader and ignores "suppressEcho"
         */
        static ConsoleInput of(BufferedReader reader) {
            Objects.requireNonNull(reader, "reader");
            return _ -> reader.readLine();
        }
    }

    /**
     * The method to invoke as a module's entry point, and the arguments to pass it.
     *
     * @param name  the name of the entry method, conventionally "run"
     * @param args  the arguments for the entry method; never shell-parsed
     */
    public record EntryPoint(String name, List<String> args) {
        /**
         * The conventional entry point: "run", with no arguments.
         */
        public static final EntryPoint RUN = new EntryPoint("run");

        public EntryPoint {
            Objects.requireNonNull(name, "name");
            args = List.copyOf(args);
        }

        /**
         * Create an entry point for the specified method and arguments.
         */
        public EntryPoint(String name, String... args) {
            this(name, List.of(args));
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
         * Closing again, also from another thread while the first close is in progress, returns
         * once the first close is done.
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

        ModuleRepository repository = new LinkedRepository(input, cfgRepo);
        ModuleStructure module = version == null
                ? repository.loadModule(moduleName)
                : repository.loadModule(moduleName, version, true);
        if (module == null) {
            errs.error(version == null ? ERR_NO_APP_MODULE : ERR_NO_APP_MODULE_VER, NOWHERE, moduleName, version);
            return null;
        }

        if (cfgInjector != null) {
            throw new UnsupportedOperationException("Custom injectors are not implemented yet");
        }

        try {
            Connector connector = ensureConnector();
            return useJit()
                    ? JitControl.create(connector, module, repository, console, rootDir, errs)
                    : InterpreterControl.create(connector, module, repository, entry, console,
                            consoleIn, rootDir, errs);
        } catch (RuntimeException | AssertionError e) {
            // an AssertionError is an Error, so the RuntimeException guard alone let a tripped
            // assertion in the structure code past this report and out to the host. Errors are
            // not caught wholesale: a VirtualMachineError says the JVM is in trouble, not that
            // this module failed to start, and handling one is not something to rely on
            errs.error(ERR_CREATE_APP_CONTAINER, at(module), e, "Unable to start " + moduleName);
            return null;
        }
    }

    /**
     * Find the entry method of a module, as the command-line runner does: exactly one method of
     * that name must exist on the module type, including the methods its annotations contribute.
     * Functions are not entry points. The module must be linked: {@link FileStructure#isLinked()}
     * must be true for {@code module.getFileStructure()}, since the methods of its type depend on
     * its dependencies.
     *
     * @param module  the linked module to run
     * @param entry   the entry method's name and the supplied arguments
     *
     * @return the entry method; {@link #passesArguments} tells whether it is passed the arguments
     *
     * @throws IllegalStateException     if the module is not linked
     * @throws IllegalArgumentException  if the method is missing or ambiguous, or if it cannot be
     *                                   called with the arguments
     */
    static MethodStructure entryMethod(ModuleStructure module, EntryPoint entry) {
        if (!module.getFileStructure().isLinked()) {
            throw new IllegalStateException("Module " + module.getName() + " is not linked");
        }

        String       methodName = entry.name();
        ConstantPool pool       = module.getConstantPool();
        try (var _ = ConstantPool.withPool(pool)) {
            Set<MethodStructure> methods = Connector.findMethods(module.getIdentityConstant(), methodName);
            if (methods.size() != 1) {
                throw new IllegalArgumentException((methods.isEmpty() ? "Missing" : "Ambiguous")
                        + " method " + quoted(methodName) + " in module " + module.getName());
            }

            MethodStructure method = methods.iterator().next();
            if (method.getRequiredParamCount() > 1) {
                throw new IllegalArgumentException("Unsupported arguments for method " + quoted(methodName)
                        + ": " + method.getIdentityConstant().getSignature().getValueString());
            }

            // as for the command-line runner, the parameter may be of any type a String[] can be
            // passed as, such as Object or List<String>
            if (passesArguments(method, entry)) {
                TypeConstant typeArg = method.getParam(0).getType();
                if (!pool.ensureArrayType(pool.typeString()).isA(typeArg)) {
                    throw new IllegalArgumentException("Unsupported argument type "
                            + typeArg.getValueString() + " for method " + quoted(methodName));
                }
            }
            return method;
        }
    }

    /**
     * Decide whether an entry method is passed the supplied arguments, as the command-line runner
     * does. The arguments are a single value, an array for the first parameter, so any further
     * parameters must have default values. The array is passed when the method requires it, or
     * when it is not empty and the method has a parameter. Otherwise nothing is passed, and a
     * default value of the parameter applies. Arguments for a method without parameters are
     * dropped, and {@link #runMethod} reports {@link #WARN_ARGUMENTS_IGNORED}, as the command-line
     * runner warns of them.
     *
     * @param method  the entry method, as found by {@link #entryMethod}
     * @param entry   the entry method's name and the supplied arguments
     *
     * @return true to pass the arguments as an array; false to pass nothing
     */
    static boolean passesArguments(MethodStructure method, EntryPoint entry) {
        return method.getRequiredParamCount() == 1
                || !entry.args().isEmpty() && method.getParamCount() > 0;
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
    /**
     * "%1" - name of the entry method
     */
    public static final String WARN_ARGUMENTS_IGNORED   = "EMB-6";
    /**
     * "%1" - why the module's entry method cannot be run, as the command-line runner reports it
     */
    public static final String ERR_ENTRY_METHOD         = "EMB-7";
}
