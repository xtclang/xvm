package org.xvm.tool;

import org.xvm.asm.BuildInfo;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.compiler.BuildRepository;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.DisassemblerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.LauncherOptions.TestRunnerOptions;
import org.xvm.tool.ModuleInfo.Node;
import org.xvm.util.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.xvm.asm.Constants.ECSTASY_MODULE;
import static org.xvm.asm.Constants.TURTLE_MODULE;
import static org.xvm.asm.Constants.VERSION_MAJOR_CUR;
import static org.xvm.asm.Constants.VERSION_MINOR_CUR;
import static org.xvm.util.Handy.quoted;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Handy.toPathString;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.NONE;
import static org.xvm.util.Severity.WARNING;
import static org.xvm.util.Severity.worstOf;


/**
 * The "launcher" commands:
 *
 * <ul><li> {@code xcc} <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> {@code xec} <i>("exec")</i> routes to {@link Runner}
 * </li></ul>
 *
 * @param <T> the Options type for this launcher
 */
public abstract class Launcher<T extends LauncherOptions> implements ErrorListener {

    // ----- command registry ----------------------------------------------------------------------

    /**
     * A command handler that knows how to parse args and launch the appropriate tool.
     */
    @FunctionalInterface
    private interface CommandHandler {
        int launch(String[] args, Console console, ErrorListener errListener);
    }

    /**
     * Registry of available commands. Each entry maps a command name to a handler
     * that parses the args and launches the appropriate tool.
     * <p>
     * Note: We use string literals here instead of calling subclass static methods
     * (e.g., Compiler.getCommandName()) to avoid class loading deadlock - referencing
     * subclass static methods from superclass static initialization can cause deadlock.
     */
    private static final Map<String, CommandHandler> COMMANDS = Map.of(
            "build",  (args, console, err) -> launch(CompilerOptions.parse(args), console, err),
            "run",    (args, console, err) -> launch(RunnerOptions.parse(args), console, err),
            "test",   (args, console, err) -> launch(TestRunnerOptions.parse(args), console, err),
            "disass", (args, console, err) -> launch(DisassemblerOptions.parse(args), console, err)
    );

    /**
     * Get all registered command names.
     */
    private static String commandNames() {
        return String.join(", ", COMMANDS.keySet());
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Command name constants. These are defined here (not in the subclasses) to avoid
     * class loading deadlock when LauncherOptions references them during static initialization.
     */
    public static final String CMD_BUILD  = "build";
    public static final String CMD_RUN    = "run";
    public static final String CMD_TEST   = "test";
    public static final String CMD_DISASS = "disass";

    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    private static final int JDK_VERSION_MIN = 21;

    /**
     * Cached modules.
     */
    protected final Map<File, ModuleInfo> moduleCache;

    /**
     * The parsed options.
     */
    protected T m_options;

    /**
     * A representation of the Console (e.g. terminal) that this tool is running in.
     */
    protected final Console m_console;

    /**
     * Optional ErrorListener that receives ALL errors (tool-level and compilation).
     * When provided (not null), errors are forwarded for external programmatic access.
     * Console displays errors, but m_errors provides structured access.
     */
    protected final ErrorListener m_errors;

    /**
     * The worst severity issue encountered thus far.
     * Tracked in Launcher for control flow decisions (abort/continue).
     */
    protected Severity m_sevWorst = NONE;

    /**
     * The number of times that errors have been suspended without being resumed.
     */
    protected int m_cSuspended;

    /**
     * Constructor for programmatic invocation (uses pre-built Options).
     *
     * @param options the pre-configured Options
     * @param console representation of the terminal within which this command is run (null = default)
     * @param errors optional ErrorListener to receive all errors (null = BLACKHOLE)
     */
    protected Launcher(T options, Console console, ErrorListener errors) {
        m_console = console == null ? DEFAULT_CONSOLE : console;
        m_errors = errors == null ? ErrorListener.BLACKHOLE : errors;
        m_options = options;
        moduleCache = new HashMap<>();
    }

    /**
     * Entry point from the OS. The only thing main should do is turn any return values from processing
     * into a {@code System.exit}, that terminates the process with a system exit code based on this status
     *
     * @param asArg  command line arguments (first arg is command name: xcc, xec, or xtc)
     */
    static void main(String[] asArg) {
        try {
            System.exit(launch(asArg));
        } catch (LauncherException e) {
            System.exit(e.getExitCode());
        }
    }

    /**
     * Helper method for external launchers.
     * Creates a CliConsole for command-line usage.
     *
     * @param asArg  command line arguments (first arg is the command: build, run, or test)
     *
     * @return the result of the corresponding tool "launch" call
     *
     * @throws LauncherException if an unrecoverable exception occurs
     */
    public static int launch(String[] asArg) {
        if (asArg.length < 1) {
            throw new IllegalArgumentException("Command name is missing. Available commands: " + commandNames());
        }

        final var console = new Console() {};
        return launch(
                stripDebugPrefix(asArg[0]),
                Arrays.copyOfRange(asArg, 1, asArg.length),
                console,
                null);
    }

    /**
     * Executes a launcher command and returns an exit code.
     * Use this when calling from a daemon or other long-running process.
     * <p>
     * Supported commands: build, run, test (or --help, --version).
     * Shell scripts call with the command directly (xcc calls with "build", xec with "run").
     *
     * @param cmd command name: build, run, or test
     * @param args command line arguments (options and files)
     * @param console console for output (must not be null)
     * @param errListener optional ErrorListener to receive errors, or null
     * @return exit code (0 for success, non-zero for error)
     */
    public static int launch(String cmd, String[] args, Console console, ErrorListener errListener) {
        try {
            // Check for global options first
            return switch (cmd) {
                case "--version", "-version" -> {
                    showVersion(console);
                    yield 0;
                }
                case "--help", "-h", "-help" -> {
                    showHelp(console);
                    yield 0;
                }
                default -> {
                    final var handler = COMMANDS.get(cmd);
                    if (handler != null) {
                        yield handler.launch(args, console, errListener);
                    }
                    console.log(ERROR, "Unknown command: {}. Available commands: {}", quoted(cmd), commandNames());
                    showHelp(console);
                    yield 1;
                }
            };
        } catch (IllegalArgumentException e) {
            console.log(ERROR, e.getMessage());
            return 1;
        }
    }

    /**
     * Show help for the unified xtc command.
     */
    private static void showHelp(Console console) {
        console.out("""
            Ecstasy command-line tool

            Usage:
                xtc <command> [options] [arguments]

            Commands:
                build    Compile Ecstasy source files (alias: xcc)
                run      Execute an Ecstasy module (alias: xec)
                test     Run tests in an Ecstasy module using xunit
                disass   Disassemble a compiled Ecstasy module

            Options:
                --version   Display the Ecstasy runtime version
                --help      Display this help message

            Use 'xtc <command> --help' for more information about a command.
            """);
    }

    /**
     * Show version information.
     */
    private static void showVersion(Console console) {
        console.out("Ecstasy " + VERSION_MAJOR_CUR + "." + VERSION_MINOR_CUR);
    }

    /**
     * Launch a tool directly with pre-built options.
     * This is the preferred API for programmatic invocation as it avoids the overhead
     * of serializing options to command-line strings and parsing them back.
     *
     * @param options pre-built options (CompilerOptions, RunnerOptions, or DisassemblerOptions)
     * @param console console for output (must not be null)
     * @param errListener optional ErrorListener to receive errors, or null
     * @return exit code (0 for success, non-zero for error)
     */
    public static int launch(LauncherOptions options, Console console, ErrorListener errListener) {
        if (options == null) {
            console.log(ERROR, "Options must not be null");
            return 1;
        }

        final var launcher = switch (options) {
            case final CompilerOptions opts     -> new Compiler(opts, console, errListener);
            case final TestRunnerOptions opts   -> new TestRunner(opts, console, errListener);
            case final RunnerOptions opts       -> new Runner(opts, console, errListener);
            case final DisassemblerOptions opts -> new Disassembler(opts, console, errListener);
            default -> {
                console.log(ERROR, "Unknown options type: {}", options.getClass().getName());
                yield null;
            }
        };

        if (launcher == null) {
            return 1;
        }

        try {
            return launcher.run();
        } catch (LauncherException e) {
            if (e.isError()) {
                console.log(ERROR, e, e.getMessage());
            }
            return e.getExitCode();
        } catch (Throwable e) {
            console.log(ERROR, e, "Unexpected exception or error: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Helper method to insert a command name at the beginning of an argument array.
     * Used by subclass main() methods to delegate to Launcher.main().
     *
     * @param cmd the command name to insert
     * @param args the existing arguments
     * @return new array with command inserted at the beginning
     */
    protected static String[] insertCommand(String cmd, String[] args) {
        return Stream.concat(Stream.of(cmd), Arrays.stream(args)).toArray(String[]::new);
    }

    /**
     * Strips the debug prefix from a command name if present.
     * Supports both "debug_xec" and "debugxec" formats.
     *
     * @param cmd the raw command name potentially with debug prefix
     * @return the command name with debug prefix stripped, or the origensurenal if no prefix found
     */
    private static String stripDebugPrefix(String cmd) {
        String cmdLower = cmd.toLowerCase(DEFAULT_LOCALE);
        return Arrays.stream(DEBUG_PREFIXES)
                .filter(cmdLower::startsWith)
                .findFirst()
                .map(String::length)
                .map(cmd::substring)
                .orElse(cmd);
    }

    /**
     * Execute the Launcher tool.
     *
     * @return the result of the {@link #process} call.
     */
    @SuppressWarnings({"AssertWithSideEffects", "ConstantValue"})
    public int run() {
        final T opts = options();

        final Runtime.Version jdkVersion = Runtime.version();
        if (jdkVersion.version().getFirst() < JDK_VERSION_MIN) {
            log(INFO, "The suggested minimum JVM version is 21; this JVM version ({}) appears to be older", Runtime.version());
        } else {
            log(INFO, "JVM version: {}", Runtime.version());
            // TODO: Use a less warning prone way to determine if -ea is in use.
            boolean fAssertsEnabled = false;
            assert  fAssertsEnabled = true;
            log(INFO, "Java assertions are {}", fAssertsEnabled ? "enabled" : "disabled");
        }

        if (opts.showHelp()) {
            displayHelp();
            return 0;
        }

        if (opts.isVerbose()) {
            out();
            out("Options: " + opts);
            out();
        }

        validateOptions();
        checkErrors();

        final int result = process();
        if (opts.isVerbose()) {
            out();
        }
        return result;
    }

    /**
     * The tool processing entry point.
     *
     * @return the execution status; most commonly 0-success; 1-failure
     */
    protected abstract int process();


    // ----- text output and error handling --------------------------------------------------------

    /**
     * Log a tool-level message with template substitution (SLF4J-style).
     * Use {} placeholders in the template for parameter substitution.
     * <p>
     * Tool-level logs (file not found, invalid options, etc.) are displayed via Console
     * and tracked in Launcher for control flow, but NOT sent to ErrorListener.
     * ErrorListener is only for compilation/runtime errors from compiler/runner.
     * <p>
     * If severity is FATAL, this method throws LauncherException immediately after logging.
     *
     * @param sev       the severity (may indicate an error)
     * @param template  the message template with {} placeholders
     * @param params    parameters to substitute into the template
     */
    protected void log(Severity sev, String template, Object... params) {
        log(sev, null, template, params);
    }

    /**
     * Log an exception with an optional message template.
     * Use {} placeholders in the template for parameter substitution.
     * The exception message will be included in the Console
     * <p>
     * Tool-level exception logs are displayed via Console and tracked in Launcher,
     * but NOT sent to ErrorListener.
     * <p>
     * If severity is FATAL, this method throws LauncherException immediately after logging.
     *
     * @param sev       the severity (may indicate an error)
     * @param cause     the exception that caused this log entry
     * @param template  optional message template with {} placeholders (if null, uses exception message)
     * @param params    parameters to substitute into the template
     */
    protected void log(Severity sev, Throwable cause, String template, Object... params) {
        if (errorsSuspended()) {
            return;
        }
        // 1) Track the worst severity in Launcher
        // 2) Filter and display on Console if bad enough to print
        m_sevWorst = worstOf(m_sevWorst, sev);
        if (isBadEnoughToPrint(sev)) {
            m_console.log(sev, cause, template, params);
        }
        // TODO: Slightly scary - we should probably do our own error handling.
        // FATAL errors abort immediately
        if (sev == FATAL) {
            throw new LauncherException(true, Console.formatTemplate(template, params), cause);
        }
    }

    /**
     * Log a sequence of errors from an ErrorList.
     *
     * @param errs  the ErrorList
     */
    protected void log(ErrorList errs) {
        errs.getErrors().forEach(err -> log(err.getSeverity(), err.toString()));
    }

    /**
     * Print a blank line to the terminal.
     */
    public void out() {
        m_console.out();
    }

    /**
     * Print the String value of some object to the terminal.
     */
    public void out(Object o) {
        m_console.out(o);
    }

    /**
     * Print a blank line to the terminal.
     */
    public void err() {
        m_console.err();
    }

    /**
     * Print the String value of some object to the terminal.
     */
    public void err(Object o) {
        m_console.err(o);
    }

    /**
     * Suspend error detection.
     */
    @SuppressWarnings("unused")
    protected void suspendErrors() {
        ++m_cSuspended;
    }

    /**
     * @return true if errors are currently suspended
     */
    protected boolean errorsSuspended() {
        return m_cSuspended > 0;
    }

    /**
     * Suspend error detection.
     */
    @SuppressWarnings("unused")
    protected void resumeErrors() {
        if (m_cSuspended > 0) {
            --m_cSuspended;
        } else {
            log(FATAL, "Attempt to resume errors when errors have not been suspended");
            // log(FATAL) throws LauncherException immediately
        }
    }

    /**
     * Check if errors warrant aborting. If so, throws LauncherException with context.
     * <p>
     * Checks BOTH Launcher's tracked severity (tool errors) AND ErrorListener delegate (compiler errors).
     *
     * @param context description of what operation was being performed (for error message)
     * @return 1 if errors exist but not severe enough to abort, 0 if no errors
     * @throws LauncherException if errors are severe enough to abort
     */
    protected int checkErrors(String context) {
        if (isAbortDesired()) {
            var message = context == null
                    ? "Aborting due to errors (severity: " + m_sevWorst + ")"
                    : "Aborting during " + context + " (severity: " + m_sevWorst + ")";
            throw new LauncherException(true, message, null);
        }
        return hasSeriousErrors() ? 1 : 0;
    }

    /**
     * Check if errors warrant aborting. If so, throws LauncherException.
     *
     * @return 1 if errors exist but not severe enough to abort, 0 if no errors
     * @throws LauncherException if errors are severe enough to abort
     */
    protected int checkErrors() {
        return checkErrors(null);
    }

    /**
     * Display a help message describing how to use this command-line tool.
     * Delegates to the options class for help text generation.
     */
    protected void displayHelp() {
        out();
        out(desc());
        final var helpText = options().getHelpText();
        if (!helpText.isEmpty()) {
            out();
            out(helpText);
            out();
            return;
        }
        out();
        out("Use --help for detailed options.");
        out();
    }

    /**
     * @return a description of this command-line tool
     */
    public String desc() {
        return this.getClass().getSimpleName();
    }

    // ----- ErrorListener implementation ----------------------------------------------------------

    /**
     * Log a compilation error (ErrorInfo from AST nodes).
     * Displays via Console and forwards to external ErrorListener if provided.
     *
     * @param err the error information
     * @return true if compilation should abort
     */
    @Override
    public boolean log(ErrorInfo err) {
        m_sevWorst = worstOf(m_sevWorst, err.getSeverity());
        log(err.getSeverity(), err.toString());
        m_errors.log(err);
        return isAbortDesired();
    }

    /**
     * Check if compilation should abort based on severity of errors encountered.
     *
     * @return true if errors are bad enough to abort
     */
    @Override
    public boolean isAbortDesired() {
        return isBadEnoughToAbort(m_sevWorst);
    }

    /**
     * Check if any serious errors (ERROR or worse) have been logged.
     *
     * @return true if serious errors encountered
     */
    @Override
    public boolean hasSeriousErrors() {
        return m_sevWorst.isAtLeast(ERROR);
    }

    /**
     * Check if error logging is currently suspended.
     *
     * @return true if errors are suspended
     */
    @Override
    public boolean isSilent() {
        return errorsSuspended();
    }

    /**
     * Validate the options. This is called after options have been parsed and set.
     * Subclasses should implement validation logic that requires access to instance state
     * (like error listeners, logging, etc.).
     *
     * <p>Options classes should remain pure data/configuration holders. All validation
     * logic that requires business logic or instance state belongs here.
     */
     protected abstract void validateOptions();

    /**
     * Determine if a message should be printed based on severity and verbose settings.
     * Subclasses can override for custom filtering (e.g., Compiler strictness levels).
     *
     * @param sev the severity to check
     * @return true if the message should be printed
     */
    protected boolean isBadEnoughToPrint(Severity sev) {
        return options().isVerbose() || sev.isAtLeast(WARNING);
    }

    /**
     * Determine if a message or error of a particular severity should cause the program to exit.
     * Subclasses can override this to implement custom abort behavior.
     *
     * @param sev the severity to evaluate
     * @return true if an error of that severity should exit the program
     */
    protected boolean isBadEnoughToAbort(Severity sev) {
        return sev.isAtLeast(ERROR);
    }

    /**
     * Get the parsed options for this launcher.
     *
     * @return the options instance
     */
    protected T options() {
        return m_options;
    }

    protected final Console console() {
        return m_console;
    }

    // ----- repository management -----------------------------------------------------------------

    /**
     * Configure the library repository that is used to load module dependencies without compiling
     * them. The library repository is always writable, to allow the repository to cache modules
     * as they are linked together; use {@link #extractBuildRepo(ModuleRepository)} to get the
     * build repository from the library repository.
     *
     * @param path  a previously validated path of module directories and/or files
     *
     * @return the library repository, including a build repository at the head of the repo
     */
    protected ModuleRepository configureLibraryRepo(List<File> path) {
        if (path == null || path.isEmpty()) {
            // this is the easiest way to deliver an empty repository
            return makeBuildRepo();
        }

        ModuleRepository[] repos = new ModuleRepository[path.size() + 1];
        repos[0] = makeBuildRepo();
        for (int i = 0, c = path.size(); i < c; ++i) {
            File file = path.get(i);
            repos[i + 1] = file.isDirectory()
                ? new DirRepository(file, true)
                : new FileRepository(file, true);
        }
        return new LinkedRepository(true, repos);
    }

    /**
     * Factory method for a BuildRepository.
     *
     * @return a new BuildRepository
     */
    protected static BuildRepository makeBuildRepo() {
        return new BuildRepository();
    }

    /**
     * Obtain the BuildRepository from the library repository.
     *
     * @param repoLib the previously configured library repository
     *
     * @return the BuildRepository
     */
    protected static BuildRepository extractBuildRepo(ModuleRepository repoLib) {
        if (repoLib instanceof final BuildRepository repoBuild) {
            return repoBuild;
        }
        LinkedRepository repoLinked = (LinkedRepository) repoLib;
        return (BuildRepository) repoLinked.asList().getFirst();
    }

    /**
     * Configure the repository that is used to store modules  dependencies without compiling
     * them. The library repository is always writable, to allow the repository to cache modules
     * as they are linked together; use {@link #extractBuildRepo(ModuleRepository)} to get the
     * build repository from the library repository.
     *
     * @param fileDest  a previously validated destination for the module(s), or null to use the
     *                  current directory
     *
     * @return a module repository that will store to the specified destination
     */
    protected ModuleRepository configureResultRepo(File fileDest) {
        fileDest = resolveFile(fileDest);
        return fileDest.isDirectory()
                ? new DirRepository (fileDest, false)
                : new FileRepository(fileDest, false);
    }

    /**
     * Force load and link whatever modules are required by the compiler.
     * <p>
     * Note: This implementation assumes that the read-through option on LinkedRepository is being
     * used.
     *
     * @param reposLib  the repository to use, as it would be returned from {@link #configureLibraryRepo}
     */
    protected void prelinkSystemLibraries(ModuleRepository reposLib) {
        for (String moduleName : List.of(ECSTASY_MODULE, TURTLE_MODULE)) {
            ModuleStructure module = reposLib.loadModule(moduleName);
            if (module == null) {
                log(FATAL, "Unable to load module: {}", moduleName);
                // log(FATAL) throws LauncherException immediately - never gets here
                return;
            }

            final var struct = Objects.requireNonNull(module).getFileStructure();
            if (struct != null) {
                ModuleConstant idMissing = struct.linkModules(reposLib, false);
                if (idMissing != null) {
                    log(FATAL, "Unable to link module {} due to missing module: {}", moduleName, idMissing.getName());
                    // log(FATAL) throws LauncherException immediately - never gets here
                    return;
                }
            }
        }
    }

    /**
     * Display "xdk version" string.
     *
     * @param reposLib  the repository that contains the Ecstasy library
     */
    protected void showSystemVersion(ModuleRepository reposLib) {
        String sVer = null;
        try {
            sVer = reposLib.loadModule(ECSTASY_MODULE).getVersionString();
        } catch (Exception ignore) {}

        // Use version from single source of truth if module version is not available
        if (sVer == null) {
            sVer = BuildInfo.getXdkVersion();
        }

        // Build version string with optional git information
        final var version = new StringBuilder("xdk version ")
            .append(sVer)
            .append(" (")
            .append(VERSION_MAJOR_CUR)
            .append(".")
            .append(VERSION_MINOR_CUR)
            .append(")");

        // Add Git info to be woven into the build, if available.
        // NOTE: We use the full hash in code and CI for the commit
        final var gitCommit = BuildInfo.getGitCommit();
        if (!gitCommit.isEmpty()) {
            version.append(" [").append(gitCommit).append("]");
        }
        final var gitStatus = BuildInfo.getGitStatus();
        if (!gitStatus.isEmpty()) {
            version.append(" (").append(gitStatus).append(")");
        }

        out(version.toString());
    }


    // ----- file management -----------------------------------------------------------------------

    /**
     * Obtain the module information based on the specified file(s).
     *
     * @param fileSpec       the file or directory to analyze, which may or may not exist
     * @param resourceSpecs  (optional) an array of files and/or directories which represent (in
     *                       aggregate) the resource path; null indicates that the default resources
     *                       location should be used, while an empty array explicitly indicates
     *                       that there is no resource path; as provided to the compiler using
     *                       the "-rp" command line switch
     * @param binarySpec     (optional) the file or directory which represents the target of the
     *                       binary; as provided to the compiler using the "-o" command line switch
     */
    public ModuleInfo ensureModuleInfo(File fileSpec, List<File> resourceSpecs, File binarySpec) {
        assert resourceSpecs != null;
        final boolean deduce = options().mayDeduceLocations();
        // Cache only when using defaults (no explicit resources or binary location)
        // When fCache is true, binarySpec is null by definition, so we use null directly in the lambda
        if (resourceSpecs.isEmpty() && binarySpec == null) {
            return moduleCache.computeIfAbsent(fileSpec, _ -> new ModuleInfo(fileSpec, deduce));
        }
        return new ModuleInfo(fileSpec, deduce, resourceSpecs, binarySpec);
    }

    /**
     * Validate that the module path entries (-L) are existent directories and/or .xtc files.
     */
    protected final void validateModulePath() {
        for (var file : options().getModulePath()) {
            if (!file.exists()) {
                log(ERROR, "File or directory {} does not exist", quoted(file));
            } else if (file.isFile()) {
                validateReadableFile(file, ".xtc");
            } else if (!file.canRead()) {
                log(ERROR, "Directory {} is not readable", quoted(file));
            }
        }
    }

    /**
     * Validate that the specified file can be used as a source file or directory for .x file(s).
     *
     * @param file  the file or directory to read source code from
     *
     * @return the validated file or directory to read source code from
     */
    public File validateSourceInput(File file) {
        if (!file.exists() || file.isDirectory()) {
            // Try to locate module source for non-existent or directory inputs
            try {
                var info = ensureModuleInfo(file, List.of(), null);
                var srcFile = info == null ? null : info.getSourceFile();
                if (srcFile == null || !srcFile.exists()) {
                    log(ERROR, "Failed to locate the module source code for: {}", file);
                }
            } catch (RuntimeException e) {
                log(ERROR, "Failed to identify the module for: {} ({})", file, e);
            }
        } else {
            validateReadableFile(file, ".x");
        }
        return file;
    }

    /**
     * Validate that a file is readable and optionally has the expected extension.
     *
     * @param file              the file to validate
     * @param expectedExtension the expected extension (e.g. ".x", ".xtc"), or null to skip extension check
     */
    protected void validateReadableFile(File file, String expectedExtension) {
        if (!file.canRead()) {
            log(ERROR, "File not readable: {}", quoted(file));
        } else if (expectedExtension != null && !file.getName().endsWith(expectedExtension)) {
            log(WARNING, "File {} does not have the \"{}\" extension", quoted(file), expectedExtension);
        }
    }

    /**
     * Select modules to target for source code processing.
     *
     * @param listSources    a list of source locations
     * @param resourceSpecs  an optional array of resource locations
     * @param outputSpec     an optional location for storing the compilation result
     *
     * @return a list of "module files", each representing a module's source code
     */
    protected List<ModuleInfo> selectTargets(List<File> listSources, List<File> resourceSpecs, File outputSpec) {
        final var mapResults = new java.util.LinkedHashMap<File, ModuleInfo>();
        final var dups = new HashSet<File>();

        for (var file : listSources) {
            try {
                final var info = ensureModuleInfo(file, resourceSpecs, outputSpec);
                final var srcFile = info != null ? info.getSourceFile() : null;
                if (srcFile == null) {
                    log(ERROR, "Unable to find module source for file: {}", file);
                } else if (mapResults.containsKey(srcFile)) {
                    if (dups.add(srcFile)) {
                        log(WARNING, "Module source was specified multiple times: {}", srcFile);
                    }
                } else {
                    mapResults.put(srcFile, info);
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                final var msg = e.getMessage();
                log(ERROR, "Could not find module information for {} ({})", toPathString(file), msg != null ? msg : "Reason unknown");
            }
        }
        return List.copyOf(mapResults.values());
    }

    /**
     * Flush errors from the specified nodes, and then check for errors globally.
     *
     * @param nodes  the nodes to flush
     * @return 0 if no serious errors, 1 if serious errors exist (but not fatal enough to throw)
     * @throws LauncherException if errors are severe enough to abort
     */
    protected int flushAndCheckErrors(Node[] nodes) {
        if (nodes != null) {
            for (Node node : nodes) {
                if (node != null) {
                    node.logErrors(this);
                }
            }
        }
        return checkErrors();
    }

    /**
     * Clean up any transient state.
     * Resets severity tracking and clears module cache.
     */
    protected void reset() {
        m_sevWorst = NONE;
        m_cSuspended = 0;
        moduleCache.clear();
    }


    // ----- Console -------------------------------------------------------------------------------

    /**
     * RuntimeException thrown upon a launcher failure.
     * TODO: Ideally this should not be a runtime exception. We can have throws declaration and do more processing
     *   and recovery attempts if we explicitly declare how to handle LauncherException in subclasses in code.
     */
    static public class LauncherException extends RuntimeException {
        private final boolean error;
        private final int exitCode;

        public LauncherException(final boolean error, String msg) {
            this(error, msg, null);
        }

        public LauncherException(Throwable cause) {
            this(true, cause == null ? null : cause.getMessage(), cause);
        }

        public LauncherException(final boolean error, String msg, Throwable cause) {
            super(msg, cause);
            this.error = error;
            this.exitCode = error ? 1 : 0;
        }

        public int getExitCode() {
            return this.exitCode;
        }

        public boolean isError() {
            return this.error;
        }

        @Override
        public String toString() {
            return '[' + getClass().getSimpleName() + ": isError=" + isError() + ", msg=" + getMessage() + ']';
        }
    }

    // ----- constants -----------------------------------------------------------------------------
    /**
     * Unknown fatal error. {0}
     */
    public static final String FATAL_ERROR      = "LAUNCHER-01";
    /**
     * Duplicate name "{0}" detected in {1}
     */
    public static final String DUP_NAME         = "LAUNCHER-02";
    /**
     * No package node for {0}
     */
    public static final String MISSING_PKG_NODE = "LAUNCHER-03";
    /**
     * Failure reading: {0}
     */
    public static final String READ_FAILURE     = "LAUNCHER-04";

    /**
     * Debug prefixes to strip from launcher command names.
     * Checked in order, so "debug_" must come before "debug".
     */
    private static final String[] DEBUG_PREFIXES = {"debug_", "debug"};

    /**
     * The default Console implementation.
     */
    protected static final Console DEFAULT_CONSOLE = new Console() {};

    /**
     * Enum representing the different Stages of launchers.
     */
    protected enum Stage {Init, Parsed, Named, Linked}
}
