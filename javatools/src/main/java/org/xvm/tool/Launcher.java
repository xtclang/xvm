package org.xvm.tool;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.xvm.asm.BuildInfo;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.compiler.BuildRepository;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.tool.ModuleInfo.isExplicitCompiledFile;
import static org.xvm.util.Handy.resolveFile;
import static org.xvm.util.Handy.toPathString;
import static org.xvm.util.Severity.ERROR;
import static org.xvm.util.Severity.FATAL;
import static org.xvm.util.Severity.INFO;
import static org.xvm.util.Severity.NONE;
import static org.xvm.util.Severity.WARNING;


/**
 * The "launcher" commands:
 *
 * <ul><li> <code>xcc</code> <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> <code>xec</code> <i>("exec")</i> routes to {@link Runner}
 * </li></ul>
 *
 * @param <T> the Options type for this launcher
 */
public abstract class Launcher<T extends LauncherOptions> implements ErrorListener {

    private static final int JDK_VERSION_MIN = 21;

    /**
     * Constructor for programmatic invocation (uses pre-built Options).
     *
     * @param options the pre-configured Options
     * @param console representation of the terminal within which this command is run
     * @param errListener ErrorListener to receive errors
     */
    protected Launcher(T options, Console console, ErrorListener errListener) {
        m_console = console == null ? DEFAULT_CONSOLE : console;
        m_errDelegate = errListener;
        m_options = options;
    }

    /**
     * Entry point from the OS. The only thing main should do is turn any return values from processing
     * into a {@code System.exit}, that terminates the process with a system exit code based on this status
     *
     * @param asArg  command line arguments (first arg is command name: xcc, xec, or xtc)
     */
    static void main(String[] asArg) {
        try {
            // use System.exit() to communicate the result of execution back to the caller
            System.exit(launch(asArg));
        } catch (LauncherException e) {
            System.exit(e.getExitCode());
        }
    }

    /**
     * Helper method for external launchers.

     * @param asArg  command line arguments
     *
     * @return the result of the corresponding tool "launch" call
     *
     * @throws LauncherException if an unrecoverable exception occurs
     */
    public static int launch(String[] asArg) throws LauncherException {
        if (asArg.length < 1) {
            System.err.println("Command name is missing");
            return 1;
        }
        return launch(
                stripDebugPrefix(asArg[0]),
                Arrays.copyOfRange(asArg, 1, asArg.length),
                DEFAULT_CONSOLE,
                null);
    }

    /**
     * Strips the debug prefix from a command name if present.
     * Supports both "debug_xec" and "debugxec" formats.
     *
     * @param cmd the raw command name potentially with debug prefix
     * @return the command name with debug prefix stripped, or the original if no prefix found
     */
    private static String stripDebugPrefix(String cmd) {
        String cmdLower = cmd.toLowerCase();
        return Arrays.stream(DEBUG_PREFIXES)
                .filter(cmdLower::startsWith)
                .findFirst()
                .map(String::length)
                .map(cmd::substring)
                .orElse(cmd);
    }

    /**
     * Executes a launcher command and returns an exit code.
     * Use this when calling from a daemon or other long-running process.
     *
     * @param cmd command name (xcc or xec)
     * @param args command line arguments (without the command name)
     * @param console console for output (must not be null)
     * @param errListener optional ErrorListener to receive errors, or null
     * @return exit code (0 for success, non-zero for error)
     */
    public static int launch(String cmd, String[] args, Console console, ErrorListener errListener) {
        Launcher<?> launcher;
        try {
            launcher = switch (cmd) {
                case "xcc" -> new Compiler(CompilerOptions.parse(args), console, errListener);
                case "xec" -> new Runner(RunnerOptions.parse(args), console, errListener);
                default -> {
                    console.log(ERROR, "Command name \"" + cmd + "\" is not supported");
                    yield null;
                }
            };

            if (launcher == null) {
                return 1;
            }

            return launcher.run();
        } catch (IllegalArgumentException e) {
            console.log(ERROR, e.getMessage());
            return 1;
        } catch (LauncherException e) {
            if (e.isError()) {
                console.log(ERROR, e.getMessage());
            }
            return e.getExitCode();
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
     * Execute the Launcher tool.
     *
     * @return the result of the {@link #process} call.
     */
    @SuppressWarnings({"AssertWithSideEffects", "ConstantValue"})
    public int run() {
        final T options = options();

        final Runtime.Version jdkVersion = Runtime.version();
        if (jdkVersion.version().getFirst() < JDK_VERSION_MIN) {
            log(INFO, "The suggested minimum JVM version is 21; this JVM version (" + Runtime.version() + ") appears to be older");
        } else {
            log(INFO, "JVM version: " + Runtime.version());
            boolean fAssertsEnabled = false;
            assert  fAssertsEnabled = true;
            log(INFO, "Java assertions are " + (fAssertsEnabled ? "enabled" : "disabled"));
        }

        if (options.showHelp()) {
            displayHelp();
            return 0;
        }

        if (options.verbose()) {
            out();
            out("Options: " + options);
            out();
        }

        validateOptions();
        checkErrors();

        int result = process();

        if (options.verbose()) {
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
     * Log a message of a specified severity.
     *
     * @param sev   the severity (may indicate an error)
     * @param sMsg  the message or error to display
     */
    protected void log(Severity sev, String sMsg) {
        if (errorsSuspended()) {
            return;
        }

        if (sev.compareTo(m_sevWorst) > 0) {
            m_sevWorst = sev;
        }

        if (isBadEnoughToPrint(sev)) {
            m_console.log(sev, sMsg);
        }

        if (sev == FATAL) {
            throw abort(true, sMsg);
        }
    }

    /**
     * Log a sequence of errors from an ErrorList.
     *
     * @param errs  the ErrorList
     */
    protected void log(ErrorList errs) {
        for (ErrorInfo err : errs.getErrors()) {
            log(err.getSeverity(), err.toString());
        }
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
        }
    }

    /**
     * Determine if a previously logged error should cause the program to exit, and if so, exit.
     */
    protected void checkErrors() {
        if (isBadEnoughToAbort(m_sevWorst)) {
            throw abort(true);
        }
    }

    /**
     * Determine if a previously logged error should cause the program to exit, and if so, exit.
     *
     * @param fHelp  true iff the help message should be displayed and the program should exit
     */
    protected void checkErrors(boolean fHelp) {
        if (fHelp) {
            displayHelp();
        }

        if (fHelp || isBadEnoughToAbort(m_sevWorst)) {
            throw abort(isBadEnoughToAbort(m_sevWorst));
        }
    }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status (otherwise end of run, but not an error)
     */
    protected void abortThrowing(boolean fError) {
        throw abort(fError, null);
    }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status (otherwise end of run, but not an error)
     * @param msg     an optional message to display with the exception when it is caught and parsed.
     */
    protected void abortThrowing(@SuppressWarnings("SameParameterValue") boolean fError, String msg) {
        throw abort(fError, msg);
    }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status (otherwise end of run, but not an error)
     */
    protected LauncherException abort(boolean fError) {
        return abort(fError, null, null);
    }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status (otherwise end of run, but not an error)
     * @param msg     an optional message to display with the exception when it is caught and parsed.
     */
    protected LauncherException abort(boolean fError, String msg) {
        return abort(fError, msg, null);
    }

    /**
     * Abort the command line with or without an error status.
     *
     * @param fError  true to abort with an error status (otherwise end of run, but not an error)
     * @param msg     an optional message to display with the exception when it is caught and parsed.
     * @para, cause   an underlying excpeption and cause.
     */
    protected LauncherException abort(@SuppressWarnings("SameParameterValue") boolean fError, String msg, Exception cause) {
        return new LauncherException(fError, msg, cause);
    }

    /**
     * Display a help message describing how to use this command-line tool.
     * Delegates to the options class for help text generation.
     */
    public void displayHelp() {
        out();
        out(desc());
        final var helpText = options().getHelpText();
        if (helpText != null && !helpText.isEmpty()) {
            out();
            out(helpText);
            out();
            return;
        }
        out();
        out("Use --help for detailed options");
        out();
    }

    /**
     * @return a description of this command-line tool
     */
    public String desc() {
        return this.getClass().getSimpleName();
    }


    // ----- ErrorListener interface ---------------------------------------------------------------

    @Override
    public boolean log(ErrorInfo err) {
        // Forward to delegate (always non-null, default is ErrorList)
        if (m_errDelegate != null) { m_errDelegate.log(err); }

        log(err.getSeverity(), err.toString());
        return isAbortDesired();
    }

    @Override
    public boolean isAbortDesired() {
        return isBadEnoughToAbort(m_sevWorst);
    }

    @Override
    public boolean hasSeriousErrors() {
        return m_sevWorst.compareTo(ERROR) >= 0;
    }

    @Override
    public boolean isSilent() {
        return errorsSuspended();
    }


    // ----- options -------------------------------------------------------------------------------

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
     * Determine if a message or error of a particular severity should be displayed.
     * Subclasses can override this to implement custom severity filtering.
     *
     * @param sev the severity to evaluate
     * @return true if an error of that severity should be displayed
     */
    protected boolean isBadEnoughToPrint(Severity sev) {
        return options().verbose() || sev.compareTo(WARNING) >= 0;
    }

    /**
     * Determine if a message or error of a particular severity should cause the program to exit.
     * Subclasses can override this to implement custom abort behavior.
     *
     * @param sev the severity to evaluate
     * @return true if an error of that severity should exit the program
     */
    protected boolean isBadEnoughToAbort(Severity sev) {
        return sev.compareTo(ERROR) >= 0;
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
    protected BuildRepository makeBuildRepo() {
        return new BuildRepository();
    }

    /**
     * Obtain the BuildRepository from the library repository.
     *
     * @param repoLib the previously configured library repository
     *
     * @return the BuildRepository
     */
    protected BuildRepository extractBuildRepo(ModuleRepository repoLib) {
        if (repoLib instanceof BuildRepository repoBuild) {
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
     * @param reposLib  the repository to use, as it would be returned from
     *                  {@link #configureLibraryRepo}
     */
    protected void prelinkSystemLibraries(ModuleRepository reposLib) {
        ModuleStructure moduleEcstasy = reposLib.loadModule(Constants.ECSTASY_MODULE);
        if (moduleEcstasy == null) {
            log(FATAL, "Unable to load module: " + Constants.ECSTASY_MODULE);
        }

        FileStructure structEcstasy = Objects.requireNonNull(moduleEcstasy).getFileStructure();
        if (structEcstasy != null) {
            ModuleConstant idMissing = structEcstasy.linkModules(reposLib, false);
            if (idMissing != null) {
                log(FATAL, "Unable to link module " + Constants.ECSTASY_MODULE
                    + " due to missing module:" + idMissing.getName());
            }
        }

        ModuleStructure moduleTurtle = reposLib.loadModule(Constants.TURTLE_MODULE);
        if (moduleTurtle == null) {
            log(FATAL, "Unable to load module: " + Constants.TURTLE_MODULE);
        }

        FileStructure structTurtle = Objects.requireNonNull(moduleTurtle).getFileStructure();
        if (structTurtle != null) {
            ModuleConstant idMissing = structTurtle.linkModules(reposLib, false);
            if (idMissing != null) {
                log(FATAL, "Unable to link module " + Constants.TURTLE_MODULE
                    + " due to missing module:" + idMissing.getName());
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
            sVer = reposLib.loadModule(Constants.ECSTASY_MODULE).getVersionString();
        } catch (Exception ignore) {}

        // Use version from single source of truth if module version is not available
        if (sVer == null) {
            sVer = BuildInfo.getXdkVersion();
        }

        // Build version string with optional git information
        StringBuilder version = new StringBuilder();
        version.append("xdk version ").append(sVer)
               .append(" (").append(Constants.VERSION_MAJOR_CUR).append(".").append(Constants.VERSION_MINOR_CUR).append(")");

        // Add git info if available
        String gitCommit = BuildInfo.getGitCommit();
        String gitStatus = BuildInfo.getGitStatus();
        if (!gitCommit.isEmpty()) {
            // Use full commit ID for better traceability
            version.append(" [").append(gitCommit).append("]");
        }
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
    public ModuleInfo ensureModuleInfo(File fileSpec, File[] resourceSpecs, File binarySpec) {
        boolean fCache = (resourceSpecs == null || resourceSpecs.length == 0) && binarySpec == null;
        if (fCache && moduleCache != null) {
            ModuleInfo info = moduleCache.get(fileSpec);
            if (info != null) {
                return info;
            }
        }

        ModuleInfo info = new ModuleInfo(fileSpec, options().deduce(), resourceSpecs, binarySpec);

        if (fCache) {
            if (moduleCache == null) {
                moduleCache = new HashMap<>();
            }
            moduleCache.put(fileSpec, info);
        }

        return info;
    }

    /**
     * Validate that the contents of the path are existent directories and/or .xtc files.
     *
     * @param modulePath a list of individual module paths.
     */
    public void validateModulePath(List<File> modulePath) throws LauncherException {
        for (File file : modulePath) {
            String sMsg = "File or directory";
            if (file.isDirectory()) {
                sMsg = "Directory";
            } else if (file.isFile()) {
                sMsg = "File";
            }

            if (!file.exists()) {
                log(ERROR, "File or directory \"" + file + "\" does not exist");
            } else if (!file.canRead()) {
                log(ERROR, sMsg + " \"" + file + "\" does not exist");
            } else if (file.isFile() && !file.getName().endsWith(".xtc")) {
                log(WARNING, "File \"" + file + "\" does not have the \".xtc\" extension");
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
        // this is expected to be the name of a file to compile
        if (!file.exists() || file.isDirectory()) {
            try {
                ModuleInfo info = ensureModuleInfo(file, null, null);
                File srcFile = info == null ? null : info.getSourceFile();
                if (srcFile == null || !srcFile.exists()) {
                    log(ERROR, "Failed to locate the module source code for: " + file);
                }
            } catch (RuntimeException e) {
                log(ERROR, "Failed to identify the module for: " + file + " (" + e + ")");
            }
        } else if (!file.canRead()) {
            log(ERROR, "File not readable: " + file);
        } else if (!file.getName().endsWith(".x")) {
            log(WARNING, "Source file does not have a \".x\" extension: " + file);
        }
        return file;
    }

    /**
     * Validate that the specified file can be used as a destination file or directory for
     * .xtc file(s).
     *
     * @param file    the file or directory to write module(s) to; may be null (which implies some
     *                default)
     * @param fMulti  true indicates that multiple modules will be written
     */
    public void validateModuleOutput(File file, boolean fMulti) {
        if (file == null) {
            return;
        }

        boolean fSingle = isExplicitCompiledFile(file.getName());
        if (fSingle && fMulti) {
            log(ERROR, "The single file " + file + " is specified, but multiple modules are expected");
            return;
        }

        File dir = fSingle ? file.getParentFile() : file;
        if (dir != null && !dir.exists()) {
            log(INFO, "Creating directory " + dir);
            // ignore any errors here; errors would end up being reported further down
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        if (file.exists()) {
            if (!file.isDirectory()) {
                if (!fSingle) {
                    log(WARNING, "File " + file + " does not have the \".xtc\" extension");
                }

                if (fMulti) {
                    log(ERROR, "The single file " + file
                            + " is specified, but multiple modules are expected");
                }

                if (!file.canWrite()) {
                    log(ERROR, "File " + file + " can not be written to");
                }
            }
        } else if (dir != null && !dir.exists()) {
            log(ERROR, "Directory " + dir + " is missing");
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
    protected List<ModuleInfo> selectTargets(List<File> listSources, File[] resourceSpecs, File outputSpec) {
        ListMap<File, ModuleInfo> mapResults = new ListMap<>();

        Set<File> setDups = null;
        for (File file : listSources) {
            ModuleInfo info    = null;
            File       srcFile = null;
            try {
                info = ensureModuleInfo(file, resourceSpecs, outputSpec);
                if (info != null) {
                    srcFile = info.getSourceFile();
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                String msg = e.getMessage();
                log(ERROR, "Could not find module information for " + toPathString(file)
                        + " (" + (msg == null ? "Reason unknown" : msg) + ")");
            }
            if (srcFile == null) {
                log(ERROR, "Unable to find module source for file: " + file);
            } else if (mapResults.containsKey(srcFile)) {
                if (setDups == null) {
                    setDups = new HashSet<>();
                }
                if (!setDups.contains(srcFile)) {
                    log(WARNING, "Module source was specified multiple times: " + srcFile);
                    setDups.add(srcFile);
                }
            } else {
                mapResults.put(srcFile, info);
            }
        }

        return new ArrayList<>(mapResults.values());
    }

    /**
     * Flush errors from the specified nodes, and then check for errors globally.
     *
     * @param nodes  the nodes to flush
     */
    protected void flushAndCheckErrors(Node[] nodes) {
        if (nodes != null) {
            for (Node node : nodes) {
                if (node != null) {
                    node.logErrors(this);
                }
            }
        }
        checkErrors();
    }

    /**
     * Clean up any transient state
     */
    protected void reset() {
        m_sevWorst   = NONE;
        m_cSuspended = 0;
        moduleCache  = null;
    }


    // ----- Console -------------------------------------------------------------------------------

    /**
     * An interface representing this tool's interaction with the terminal.
     */
    public interface Console {
        /**
         * Print a blank line to the terminal.
         */
        default void out() {
            out("");
        }

        /**
         * Print the String value of some object to the terminal.
         */
        default void out(Object o) {
            System.out.println(o);
        }

        /**
         * Print a blank line to the terminal.
         */
        default void err() {
            err("");
        }

        /**
         * Print the String value of some object to the terminal.
         */
        default void err(Object o) {
            System.err.println(o);
        }

        /**
         * Log a message of a specified severity.
         *
         * @param sev   the severity (may indicate an error)
         * @param sMsg  the message or error to display
         */
        default void log(Severity sev, String sMsg) {
            out(sev.desc() + ": " + sMsg);
        }
    }

    /**
     * RuntimeException thrown upon a launcher failure.
     * TODO: Ideally this should not be a runtime exception. We can have throws declaration and do more processing
     *   and recovery attempts if we explcitly declarte how to handle LauncherException in subclasses in code.
     */
    static public class LauncherException extends RuntimeException {
        private final boolean error;
        private final int exitCode;

        public LauncherException(final boolean error) {
            this(error, null, null);
        }

        public LauncherException(final boolean error, final String msg) {
            this(error, msg, null);
        }

        public LauncherException(final boolean error, final Throwable e) {
            this(error, e.getMessage(), null);
        }

        public LauncherException(final boolean error, final String msg, final Throwable cause) {
            super(msg, cause);
            this.error = error;
            this.exitCode = error ? 0 : 1;
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
    private static final Console DEFAULT_CONSOLE = new Console() {};

    /**
     * Enum representing ther different Stages of launchers.
     */
    protected enum Stage {Init, Parsed, Named, Linked}


    // ----- fields --------------------------------------------------------------------------------

    /**
     * A representation of the Console (e.g. terminal) that this tool is running in.
     */
    protected final Console m_console;

    /**
     * The parsed options.
     */
    private final T m_options;

    /**
     * Get the parsed options for this launcher.
     *
     * @return the options instance
     */
    protected final T options() {
        return m_options;
    }

    /**
     * The worst severity issue encountered thus far.
     */
    protected Severity m_sevWorst = NONE;

    /**
     * Delegate ErrorListener that receives errors in addition to this Launcher's own logging.
     * This allows callers to capture errors programmatically while still using the Launcher's
     * built-in error tracking and reporting. If null was passed to the constructor, this will
     * be a default ErrorList instance.
     */
    protected final ErrorListener m_errDelegate;

    /**
     * The number of times that errors have been suspended without being resumed.
     */
    protected int m_cSuspended;

    protected Map<File, ModuleInfo> moduleCache;
}
