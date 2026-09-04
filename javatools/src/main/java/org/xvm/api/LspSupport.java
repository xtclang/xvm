package org.xvm.api;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import org.xvm.tool.Console;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

import static org.xvm.util.Handy.readFileChars;

import static org.xvm.util.Severity.ERROR;

/**
 * A class used to support LSP and other tool uses. This implementation uses the Connector API to
 * run a long-running Ecstasy application (in "Container Zero") that is responsible for spinning up
 * any number of child containers to "run()" modules. The LspSupport is a singleton, but it does
 * require configuration; specifically, it requires a Module Repository from which to load the core
 * Ecstasy classes. Without configuration, the LspSupport will attempt to locate the core Ecstasy
 * classes using the "XDK_HOME" OS property.
 *
 * The methods on the LspSupport itself can be assumed to be thread-safe and concurrent.
 */
public class LspSupport {
    // ----- internal (construction etc.) ----------------------------------------------------------

    /**
     * Internal constructor.
     */
    private LspSupport() {}

    /**
     * Internal singleton implementation.
     */
    private static class Singleton {
        static final LspSupport instance = new LspSupport();
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

    private boolean          configured;
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
                throw new IllegalStateException("ToolConnect has not been configured, and the"
                        + " \"XDK_HOME\" environment variable is missing or invalid");
            }
        }
        return true;
    }

    /**
     * @return true when the JIT implementation is complete and can be used by the ToolConnector
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
                Connector connector = useJit()
                        ? JitControl.createConnector(cfgRepo)
                        : InterpreterControl.createConnector(cfgRepo);
                this.connector = connector;
            }
            return connector;
        }
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * @return the singleton ToolConnector instance
     */
    public static LspSupport instance() {
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
    public LspSupport configure(ModuleRepository coreRepo, String customInjector) {
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
     * @return true iff the TooolConnector has been configured
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * @return the configured repository, or null if the ToolConnector has not been configured
     */
    public ModuleRepository getConfiguredRepository() {
        return cfgRepo;
    }

    /**
     * @return the "modulename:classname" of the default injector to use for all "run()" containers,
     *         or null to use the ToolConnector's built-in default injector
     */
    public String getConfiguredInjector() {
        return cfgInjector;
    }

    /**
     * @return the ConstantPool of the core Ecstasy libraries used by the runtime Connector instance
     *         that is instantiated by this ToolConnector
     */
    public ConstantPool getConstantPool() {
        verifyConfigured();
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
        verifyConfigured();
        try {
            LspCompiler compiler = new LspCompiler(source, input, cfgRepo, errs);
            return compiler.process() == 0
                    ? compiler.getModule()
                    : null;
        } catch (LauncherException e) {
            if (errs != null) {
                errs.log(ERROR, ERR_INTERNAL,
                        new Object[] {e, "Compilation failed"}, null);
            }
            return null;
        }
    }

    /**
     * Compile a module that is in a file or directory.
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
        ModuleStructure module;
        try {
            module = compile(new String(readFileChars(file)), input, errs);
        } catch (IOException e) {
            if (errs != null) {
                errs.log(ERROR, ERR_INTERNAL,
                        new Object[] {e, "Unable to read module " + file}, null);
            }
            return false;
        }

        if (module == null) {
            assert errs == null || errs.hasSeriousErrors();
            return false;
        }

        if (output != null) {
            try {
                output.storeModule(module);
            } catch (IOException e) {
                if (errs != null) {
                    errs.log(ERROR, ERR_INTERNAL,
                            new Object[] {e, "Unable to store module " + module.getName()}, module);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Adapter that supplies the source and repositories to the standard compiler pipeline and
     * captures its single compiled module instead of writing it to disk.
     */
    private static class LspCompiler
            extends org.xvm.tool.Compiler {
        private final String           source;
        private final ModuleRepository inRepo;
        private final ModuleRepository coreRepo;
        private       ModuleStructure  module;

        protected LspCompiler(String source, ModuleRepository input, ModuleRepository core,
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
    public interface Control {
        /**
         * @return true if the module is still running
         */
        boolean running();

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
         * Stop the app as quickly as possible, and release all of its resources where possible.
         */
        void kill();

        /**
         * @return the Ecstasy Int exit code from the module's run() method, provided as a Java
         *         "Long"; null otherwise
         */
        Long result();

        /**
         * @return the File containing the output that the application printed to the Console
         */
        File console();
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
     * @param console     (optional) the PrintStream for the executing application
     * @param rootDir     (optional) the root directory for the application's file system; null
     *                    indicates a temporary (e.g. in-memory) file system only
     * @param injections  (optional) additional "String" and "String[]" injections
     * @param errs        (optional) a means for the container to report uncaught exceptions and
     *                    other errors
     *
     * @return a Control object for the running module
     */
    public Control run(
            ModuleStructure           module,
            PrintStream               console,
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
     * @param console         (optional) the PrintStream for the executing application
     * @param rootDir         (optional) the root directory for the application's file system; null
     *                        indicates a temporary (e.g. in-memory) file system only
     * @param injections      (optional) additional "String" and "String[]" injections
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources; when used, the "rootDir" value is
     *                        ignored
     * @param errs            (optional) a means for the container to report uncaught exceptions and
     *                        other errors
     *
     * @return a Control object for the running module
     */
    public Control run(
            ModuleRepository          input,
            String                    moduleName,
            Version                   version,
            PrintStream               console,
            File                      rootDir,
            Map<String, List<String>> injections,
            String                    customInjector,
            ErrorListener             errs) {
        verifyConfigured();

        ModuleRepository repository = input == null || input == cfgRepo
                ? cfgRepo
                : new LinkedRepository(input, cfgRepo);
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

        if (rootDir != null || injections != null && !injections.isEmpty()
                || customInjector != null || cfgInjector != null) {
            throw new UnsupportedOperationException(
                    "Custom file systems and injectors are not implemented yet");
        }

        try {
            Connector connector = ensureConnector();
            return useJit()
                    ? JitControl.create(connector, module, repository, console, errs)
                    : InterpreterControl.create(connector, module, repository, console, errs);
        } catch (RuntimeException e) {
            if (errs != null) {
                errs.log(ERROR, ERR_CREATE_APP_CONTAINER,
                        new Object[] {e, "Unable to start " + moduleName}, module);
            }
            return null;
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Informational only: App starting.
     */
    public static final String INFO_STARTED               = "TC-01";
    /**
     * Informational only: App stopped.
     */
    public static final String INFO_STOPPED               = "TC-02";
    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE          = "TC-10";
    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE_VER      = "TC-11";
    /**
     * "%1" - name of app module
     * "%2" - exception (may be null)
     * "%3" - additional description (may be null)
     */
    public static final String ERR_BAD_APP_MODULE         = "TC-12";
    /**
     * "%1" - name of missing module
     */
    public static final String ERR_MISSING_MODULE         = "TC-13";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_CREATE_APP_CONTAINER   = "TC-14";
    /**
     * "%1" - name of injector module
     * "%2" - name of injector class
     * "%3" - exception (may be null)
     * "%4" - additional description (may be null)
     */
    public static final String ERR_CREATE_CUSTOM_INJECTOR = "TC-15";
    /**
     * "%1" - exception
     */
    public static final String ERR_UNHANDLED_EXCEPTION    = "TC-16";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_INTERNAL               = "TC-99";
}
