package org.xvm.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Version;

import org.xvm.util.ListMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.help.TextHelpAppendable;

import static org.apache.commons.cli.Option.builder;

/**
 * Base class for launcher command-line options that wraps Apache Commons CLI.
 * Provides both command-line parsing and programmatic building via Builder pattern.
 * Subclasses provide typed getters for specific launcher options.
 * <p>
 * This class must be public to allow its public static nested classes (CompilerOptions,
 * RunnerOptions, DisassemblerOptions) to be accessible from other packages.
 */
public abstract class LauncherOptions {

    /**
     * Parsed command line from Apache Commons CLI.
     * Provides direct access to all parsed options.
     */
    protected final CommandLine commandLine;

    /**
     * The Apache Commons CLI Options schema used to parse this command line.
     * Stored for help text generation.
     */
    private final Options schema;

    /**
     * Constructor from parsed command line.
     *
     * @param commandLine the parsed command line
     * @param schema the Options schema used to parse the command line
     */
    protected LauncherOptions(CommandLine commandLine, Options schema) {
        this.commandLine = commandLine;
        this.schema = schema;
    }


    // ----- Static Options Schemas ----------------------------------------------------------------

    /**
     * Common launcher options shared by all tools.
     */
    private static final Options LAUNCHER_OPTIONS = new Options()
            .addOption(builder("d").longOpt("deduce").desc("Automatically deduce locations when possible").get())
            .addOption(builder("h").longOpt("help").desc("Display this help message").get())
            .addOption(builder("L").argName("path").hasArg()
                    .desc("Module path (can be specified multiple times, or use " + File.pathSeparator + " separator)").get())
            .addOption(builder("v").longOpt("verbose").desc("Enable verbose logging and messages").get())
            .addOption(builder().longOpt("version").desc("Display the Ecstasy runtime version").get());

    /**
     * Apache Commons CLI Options schema for the compiler.
     */
    private static final Options COMPILER_OPTIONS = conflictingOptions(copyOptions(LAUNCHER_OPTIONS)
            .addOption(builder().longOpt("rebuild").desc("Force rebuild").get())
            .addOption(builder().longOpt("qualify").desc("Use full module name for the output file name").get())
            .addOption(builder("r").argName("resource").hasArg()
                    .desc("Files and/or directories to read resources from (can use " + File.pathSeparator + " separator)").get())
            .addOption(builder("o").argName("file").hasArg()
                    .desc("File or directory to write output to").get())
            .addOption(builder().longOpt("set-version").argName("version").hasArg()
                    .desc("Specify the version to stamp onto the compiled module(s)").get()));

    /**
     * Apache Commons CLI Options schema for the runner.
     */
    private static final Options RUNNER_OPTIONS = copyOptions(LAUNCHER_OPTIONS)
            .addOption(builder("J").longOpt("jit").desc("Enable the JIT-to-Java back-end").get())
            .addOption(builder().longOpt("no-recompile").desc("Disable automatic compilation").get())
            .addOption(builder("M").longOpt("method").argName("method").hasArg()
                    .desc("Method name; defaults to \"run\"").get())
            .addOption(builder("o").argName("file").hasArg()
                    .desc("If compilation is necessary, the file or directory to write compiler output to").get())
            .addOption(builder("I").longOpt("inject").argName("name=value").hasArg()
                    .desc("Specifies name/value pairs for injection; format is \"name=value\"").get());

    /**
     * Apache Commons CLI Options schema for the disassembler.
     */
    private static final Options DISASSEMBLER_OPTIONS = copyOptions(LAUNCHER_OPTIONS)
            .addOption(builder().longOpt("files").desc("List all files embedded in the module").get())
            .addOption(builder().longOpt("findfile").argName("file").hasArg()
                    .desc("File to search for in the module").get());

    /**
     * Copy an Options object by adding all its options to a new Options instance.
     */
    private static Options copyOptions(@SuppressWarnings("SameParameterValue") Options source) {
        return source.getOptions().stream().collect(Options::new, Options::addOption, (_, _) -> {});
    }

    /**
     * Add mutually exclusive warning options (--strict and --nowarn) to an Options instance.
     */
    private static Options conflictingOptions(Options options) {
        OptionGroup group = new OptionGroup();
        group.addOption(builder().longOpt("strict").desc("Treat warnings as errors").get());
        group.addOption(builder().longOpt("nowarn").desc("Suppress all warnings").get());
        return options.addOptionGroup(group);
    }


    // ----- Common Getters ------------------------------------------------------------------------

    /**
     * Get the module path.
     * Handles both multiple -L invocations and colon/semicolon-separated paths within a single -L.
     */
    public List<File> getModulePath() {
        return getPathList("L");
    }

    /**
     * Resolve a path string, expanding ~ for home directory and handling wildcards.
     *
     * @param sPath the path to resolve
     * @return list of resolved File objects (may contain multiple files if wildcards used)
     */
    protected static List<File> resolvePath(String sPath) {
        return resolvePath(sPath, new File("."));
    }

    /**
     * Resolve a path string, expanding ~ for home directory and handling wildcards. Note that is unsafe
     * to try to derive the current working directory from the current context, and it "happens" to work.
     * Hence, we would ideally like the user to know what the working directory is and be bsure to
     * specify it to the resolvePath method
     *
     * @param sPath the path to resolve
     * @param cwd the base directory for resolving relative paths and wildcards (null means current directory)
     * @return list of resolved File objects (may contain multiple files if wildcards used)
     */
    protected static List<File> resolvePath(String sPath, File cwd) {
        List<File> files = new ArrayList<>();

        // Expand tilde for home directory
        if (sPath.length() >= 2 && sPath.charAt(0) == '~'
                && (sPath.charAt(1) == '/' || sPath.charAt(1) == File.separatorChar)) {
            sPath = System.getProperty("user.home") + File.separatorChar + sPath.substring(2);
        }

        // Handle wildcards
        // TODO: Why are we handling wildcards here, that is ususually the job for the launching shell?
        if (sPath.indexOf('*') >= 0 || sPath.indexOf('?') >= 0) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cwd.toPath(), sPath)) {
                stream.forEach(path -> files.add(path.toFile()));
            } catch (IOException e) {
                // If wildcard resolution fails, treat as literal path
                files.add(new File(cwd, sPath));
            }
            return files;
        }
        files.add(new File(sPath));
        return files;
    }

    /**
     * Common helper to parse path options that support both multiple invocations and path separator splitting.
     * For example: -L path1 -L path2 and -L path1:path2 both work.
     * Also handles tilde expansion and wildcards.
     *
     * @param optionName the short option name (e.g., "L", "r")
     * @return list of File objects parsed from the option values
     */
    protected List<File> getPathList(String optionName) {
        String[] vals = commandLine.getOptionValues(optionName);
        if (vals == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(vals)
                .flatMap(val -> Arrays.stream(val.split(File.pathSeparator)))
                .filter(path -> !path.isEmpty())
                .flatMap(path -> resolvePath(path).stream())
                .toList();
    }

    /**
     * Check if verbose mode is enabled.
     */
    public boolean verbose() {
        return commandLine.hasOption("v");
    }

    /**
     * Check if deduce mode is enabled.
     */
    public boolean deduce() {
        return commandLine.hasOption("d");
    }

    /**
     * Check if help should be shown.
     */
    public boolean showHelp() {
        return commandLine.hasOption("help");
    }

    /**
     * Check if version should be shown.
     */
    public boolean showVersion() {
        return commandLine.hasOption("version");
    }

    /**
     * Convert this options instance back to command-line arguments.
     * Subclasses should override to add their specific options.
     */
    public String[] toCommandLine() {
        final List<String> args = new ArrayList<>();
        if (verbose()) {
            args.add("-v");
        }
        if (deduce()) {
            args.add("-d");
        }
        if (showVersion()) {
            args.add("--version");
        }
        getModulePath().stream()
                .map(File::getPath)
                .forEach(path -> args.addAll(List.of("-L", path)));
        return args.toArray(String[]::new);
    }

    /**
     * String representation of options for verbose output.
     * Shows the options in command-line format.
     */
    @Override
    public String toString() {
        String[] args = toCommandLine();
        return String.join(" ", args);
    }

    /**
     * Parse command line arguments using Apache Commons CLI.
     * Flags are simple presence/absence switches (present = true, absent = false).
     * Stops at first non-option argument to collect trailing args.
     */
    protected static CommandLine parseCommandLine(Options options, String[] args) throws IllegalArgumentException {
        try {
            CommandLineParser parser = new DefaultParser();
            // stopAtNonOption=false means we'll catch unknown options as errors
            // This gives better error messages for typos like --unknown
            return parser.parse(options, args, false);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing command-line arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Get trailing arguments (non-option arguments at end of command line).
     */
    protected String[] getTrailingArgs() {
        String[] trailing = commandLine.getArgs();
        return trailing != null ? trailing : new String[0];
    }

    /**
     * Generate formatted help text using Apache CLI HelpFormatter.
     * Includes a usage line showing the command syntax.
     *
     * @return formatted help text, or null if no schema available
     */
    public final String getHelpText() {
        if (schema == null) {
            return null;
        }

        var sb = new StringBuilder();

        // Add usage line with command syntax
        sb.append("Usage:\n");
        sb.append("    ");
        sb.append(buildUsageLine(getCommandName()));
        sb.append("\n\n");

        var helpAppendable = new TextHelpAppendable(sb);
        helpAppendable.setMaxWidth(120);  // Allow up to 120 characters per line
        helpAppendable.setLeftPad(2);      // Add 2 spaces to the left of each line (default is 1)
        // Note: setIndent() doesn't affect table column wrapping; HelpFormatter uses hardcoded TextStyle
        helpAppendable.setIndent(4);       // Indent for paragraph wrapping (not used in option tables)

        var formatter = HelpFormatter.builder()
                .setShowSince(false)
                .setHelpAppendable(helpAppendable)
                .get();
        try {
            formatter.printOptions(schema);
        } catch (IOException e) {
            return "Error generating help: " + e.getMessage();
        }

        // Add blank line after the "Options   Description" header line
        var result = sb.toString();
        int headerEnd = result.indexOf('\n', result.indexOf("Options"));
        if (headerEnd > 0) {
            result = result.substring(0, headerEnd + 1) + '\n' + result.substring(headerEnd + 1);
        }

        return result.stripTrailing();
    }

    /**
     * Get the command name for this launcher (e.g., "xcc", "xec", "xtc").
     * Subclasses must override to provide their command name.
     *
     * @return the command name
     */
    protected abstract String getCommandName();

    /**
     * Build the usage line for help display.
     * Subclasses can override to customize the usage string.
     *
     * @param cmdName the command name
     * @return the usage line string
     */
    protected String buildUsageLine(String cmdName) {
        return cmdName + " [options]";
    }


    // ----- Base Builder --------------------------------------------------------------------------

    /**
     * Base Builder class for constructing LauncherOptions programmatically.
     * Provides common builder methods shared by all launcher types.
     *
     * @param <T> The concrete builder type for method chaining
     */
    protected abstract static class BaseBuilder<T extends BaseBuilder<T>> {
        protected final List<String> args = new ArrayList<>();

        /**
         * Enable verbose output and logging.
         */
        @SuppressWarnings("unused")
        public T enableVerbose() {
            return enableVerbose(true);
        }

        /**
         * Enable verbose output and logging.
         *
         * @param verbose true to enable verbose mode, false otherwise
         */
        @SuppressWarnings("unchecked")
        public T enableVerbose(boolean verbose) {
            if (verbose) {
                args.add("-v");
            }
            return (T) this;
        }

        /**
         * Enable automatic deduction of file locations.
         */
        @SuppressWarnings("unused")
        public T enableDeduction() {
            return enableDeduction(true);
        }

        /**
         * Enable automatic deduction of file locations.
         *
         * @param deduce true to enable deduction, false otherwise
         */
        @SuppressWarnings("unchecked")
        public T enableDeduction(boolean deduce) {
            if (deduce) {
                args.add("-d");
            }
            return (T) this;
        }

        /**
         * Add a directory or file to the module path.
         *
         * @param path the path to add
         */
        @SuppressWarnings("unchecked")
        public T addModulePath(File path) {
            args.addAll(List.of("-L", path.getPath()));
            return (T) this;
        }

        /**
         * Add a directory or file to the module path.
         *
         * @param path the path to add as a string
         */
        @SuppressWarnings("unused")
        public T addModulePath(String path) {
            return addModulePath(new File(path));
        }
    }

    /**
     * Compiler command-line options.
     * Simple wrapper around Apache Commons CLI CommandLine with typed getters.
     */
    public static class CompilerOptions extends LauncherOptions {

        CompilerOptions(CommandLine commandLine) {
            super(commandLine, COMPILER_OPTIONS);
        }

        @Override
        protected String getCommandName() {
            return "xcc";
        }

        /**
         * Parse command-line arguments into CompilerOptions.
         */
        public static CompilerOptions parse(String[] args) {
            return new CompilerOptions(parseCommandLine(COMPILER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing CompilerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(String cmdName) {
            return cmdName + " [options] <source_files>";
        }

        // Typed getters - just delegate to Apache CLI

        public List<File> getInputLocations() {
            return Arrays.stream(getTrailingArgs()).map(File::new).toList();
        }

        public File[] getResourceLocation() {
            return getPathList("r").toArray(File[]::new);
        }

        public File getOutputLocation() {
            String val = commandLine.getOptionValue("o");
            return val != null ? new File(val) : null;
        }

        public Version getVersion() {
            String sVersion = commandLine.getOptionValue("set-version");
            return sVersion == null ? null : new Version(sVersion);
        }

        public boolean isForcedRebuild() {
            return commandLine.hasOption("rebuild");
        }

        public boolean isStrict() {
            return commandLine.hasOption("strict");
        }

        public boolean isNoWarn() {
            return commandLine.hasOption("nowarn");
        }

        public boolean isOutputFilenameQualified() {
            return commandLine.hasOption("qualify");
        }

        @Override
        public String[] toCommandLine() {
            List<String> args = new ArrayList<>();

            // Add common flags from base class
            Collections.addAll(args, super.toCommandLine());

            // Add compiler-specific flags
            if (isForcedRebuild()) {
                args.add("--rebuild");
            }
            if (isStrict()) {
                args.add("--strict");
            }
            if (isNoWarn()) {
                args.add("--nowarn");
            }
            if (isOutputFilenameQualified()) {
                args.add("--qualify");
            }

            // Add resource paths
            Arrays.stream(getResourceLocation())
                    .map(File::getPath)
                    .forEach(path -> args.addAll(List.of("-r", path)));

            // Add output location
            File output = getOutputLocation();
            if (output != null) {
                args.addAll(List.of("-o", output.getPath()));
            }

            // Add version
            Version version = getVersion();
            if (version != null) {
                args.addAll(List.of("--set-version", version.toString()));
            }

            // Add input files (trailing args)
            getInputLocations().stream()
                    .map(File::getPath)
                    .forEach(args::add);

            return args.toArray(String[]::new);
        }

        /**
         * Builder for programmatically constructing CompilerOptions.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends BaseBuilder<Builder> {
            /**
             * Force a complete rebuild of all sources.
             */
            public Builder forceRebuild() {
                return forceRebuild(true);
            }

            /**
             * Force a complete rebuild of all sources.
             *
             * @param rebuild true to force rebuild, false otherwise
             */
            public Builder forceRebuild(boolean rebuild) {
                if (rebuild) {
                    args.add("--rebuild");
                }
                return this;
            }

            /**
             * Enable strict mode (treat warnings as errors).
             */
            @SuppressWarnings("unused")
            public Builder enableStrictMode() {
                return enableStrictMode(true);
            }

            /**
             * Enable strict mode (treat warnings as errors).
             *
             * @param strict true to enable strict mode, false otherwise
             */
            public Builder enableStrictMode(boolean strict) {
                if (strict) {
                    args.add("--strict");
                }
                return this;
            }

            /**
             * Disable all compiler warnings.
             */
            @SuppressWarnings("unused")
            public Builder disableWarnings() {
                return disableWarnings(true);
            }

            /**
             * Disable all compiler warnings.
             *
             * @param disable true to disable warnings, false otherwise
             */
            public Builder disableWarnings(boolean disable) {
                if (disable) {
                    args.add("--nowarn");
                }
                return this;
            }

            /**
             * Use fully qualified module names for output file names.
             */
            public Builder qualifyOutputNames() {
                return qualifyOutputNames(true);
            }

            /**
             * Use fully qualified module names for output file names.
             *
             * @param qualify true to use qualified names, false otherwise
             */
            public Builder qualifyOutputNames(boolean qualify) {
                if (qualify) {
                    args.add("--qualify");
                }
                return this;
            }

            /**
             * Add a resource location for compilation.
             *
             * @param resource the resource file or directory
             */
            public Builder addResourceLocation(File resource) {
                args.addAll(List.of("-r", resource.getPath()));
                return this;
            }

            /**
             * Add a resource location for compilation.
             *
             * @param resource the resource file or directory path as a string
             */
            @SuppressWarnings("unused")
            public Builder addResourceLocation(String resource) {
                return addResourceLocation(new File(resource));
            }

            /**
             * Set the output location for compiled modules.
             *
             * @param output the output file or directory
             */
            public Builder setOutputLocation(File output) {
                args.addAll(List.of("-o", output.getPath()));
                return this;
            }

            /**
             * Set the output location for compiled modules.
             *
             * @param output the output file or directory path as a string
             */
            @SuppressWarnings("unused")
            public Builder setOutputLocation(String output) {
                return setOutputLocation(new File(output));
            }

            /**
             * Set the version to stamp on compiled modules.
             *
             * @param version the version string
             */
            public Builder setModuleVersion(String version) {
                args.addAll(List.of("--set-version", version));
                return this;
            }

            /**
             * Add an input source file to compile.
             *
             * @param input the source file
             */
            public Builder addInputFile(File input) {
                args.add(input.getPath());
                return this;
            }

            /**
             * Add an input source file to compile.
             *
             * @param input the source file path as a string
             */
            @SuppressWarnings("unused")
            public Builder addInputFile(String input) {
                return addInputFile(new File(input));
            }

            /**
             * Build the CompilerOptions by parsing the accumulated arguments.
             */
            public CompilerOptions build() {
                return CompilerOptions.parse(args.toArray(String[]::new));
            }
        }
    }


    // ----- RunnerOptions -------------------------------------------------------------------------

    /**
     * Runner command-line options.
     * Clean API with typed getters for all runner-specific options.
     */
    public static class RunnerOptions extends LauncherOptions {

        RunnerOptions(CommandLine commandLine) {
            super(commandLine, RUNNER_OPTIONS);
        }

        @Override
        protected String getCommandName() {
            return "xec";
        }

        /**
         * Parse command-line arguments into RunnerOptions.
         * Throws IllegalArgumentException if there are any parsing errors.
         */
        public static RunnerOptions parse(String[] args) {
            return new RunnerOptions(parseCommandLine(RUNNER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing RunnerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(String cmdName) {
            return cmdName + " [options] <module_or_file> [args...]";
        }

        // Typed getters for all runner-specific options

        public String getMethodName() {
            String method = commandLine.getOptionValue("M");
            return method != null ? method : "run";
        }

        public boolean isCompileDisabled() {
            return commandLine.hasOption("no-recompile");
        }

        public boolean isJit() {
            return commandLine.hasOption("J");
        }

        public File getTarget() {
            // First trailing arg is the module/file to execute
            String[] trailing = getTrailingArgs();
            return trailing.length > 0 ? new File(trailing[0]) : null;
        }

        public File getOutputFile() {
            String val = commandLine.getOptionValue("o");
            return val != null ? new File(val) : null;
        }

        public String[] getMethodArgs() {
            // Everything after the first trailing arg goes to the method
            String[] trailing = getTrailingArgs();
            if (trailing.length <= 1) {
                return new String[0];
            }
            String[] methodArgs = new String[trailing.length - 1];
            System.arraycopy(trailing, 1, methodArgs, 0, methodArgs.length);
            return methodArgs;
        }

        public Map<String, String> getInjections() {
            String[] vals = commandLine.getOptionValues("I");
            if (vals == null) {
                return Collections.emptyMap();
            }
            Map<String, String> injections = new ListMap<>();
            for (String val : vals) {
                int idx = val.indexOf('=');
                if (idx > 0) {
                    injections.put(val.substring(0, idx), val.substring(idx + 1));
                }
            }
            return Collections.unmodifiableMap(injections);
        }

        @Override
        public String[] toCommandLine() {
            List<String> args = new ArrayList<>();

            // Add common flags from base class
            Collections.addAll(args, super.toCommandLine());

            // Add runner-specific flags
            if (isJit()) {
                args.add("-J");
            }
            if (isCompileDisabled()) {
                args.add("--no-recompile");
            }

            // Add method name
            String method = getMethodName();
            if (!"run".equals(method)) {
                args.addAll(List.of("-M", method));
            }

            // Add output file
            File output = getOutputFile();
            if (output != null) {
                args.addAll(List.of("-o", output.getPath()));
            }

            // Add injections
            getInjections().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .forEach(injection -> args.addAll(List.of("-I", injection)));

            // Add target file (first trailing arg)
            File target = getTarget();
            if (target != null) {
                args.add(target.getPath());
            }

            // Add method args (remaining trailing args)
            Collections.addAll(args, getMethodArgs());

            return args.toArray(String[]::new);
        }

        /**
         * Builder for constructing RunnerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends BaseBuilder<Builder> {
            /**
             * Enable the JIT-to-Java back-end.
             */
            @SuppressWarnings("unused")
            public Builder enableJit() {
                return enableJit(true);
            }

            /**
             * Enable the JIT-to-Java back-end.
             *
             * @param enable true to enable JIT, false otherwise
             */
            public Builder enableJit(boolean enable) {
                if (enable) {
                    args.add("-J");
                }
                return this;
            }

            /**
             * Disable automatic recompilation of sources.
             */
            @SuppressWarnings("unused")
            public Builder disableRebuild() {
                return disableRebuild(true);
            }

            /**
             * Disable automatic recompilation of sources.
             *
             * @param disable true to disable auto-compilation, false otherwise
             */
            public Builder disableRebuild(boolean disable) {
                if (disable) {
                    args.add("--no-recompile");
                }
                return this;
            }

            /**
             * Set the method name to execute (defaults to "run").
             *
             * @param methodName the method name
             */
            public Builder setMethodName(String methodName) {
                args.addAll(List.of("-M", methodName));
                return this;
            }

            /**
             * Set the output location for compilation (if needed).
             *
             * @param output the output file or directory
             */
            public Builder setOutputLocation(File output) {
                args.addAll(List.of("-o", output.getPath()));
                return this;
            }

            /**
             * Set the output location for compilation (if needed).
             *
             * @param output the output file or directory path as a string
             */
            @SuppressWarnings("unused")
            public Builder setOutputLocation(String output) {
                return setOutputLocation(new File(output));
            }

            /**
             * Add a name=value injection for the module.
             *
             * @param name the injection name
             * @param value the injection value
             */
            @SuppressWarnings("unused")
            public Builder addInjection(String name, String value) {
                args.addAll(List.of("-I", name + "=" + value));
                return this;
            }

            /**
             * Set the target module or file to execute.
             *
             * @param target the module or file to run
             */
            public Builder setTarget(File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Set the target module or file to execute.
             *
             * @param target the module or file path as a string
             */
            public Builder setTarget(String target) {
                return setTarget(new File(target));
            }

            /**
             * Set the target module or file to execute along with its arguments.
             *
             * @param target the module or file to run
             * @param methodArgs the arguments to pass to the executed method
             */
            public Builder setTarget(File target, String... methodArgs) {
                args.add(target.getPath());
                Collections.addAll(args, methodArgs);
                return this;
            }

            /**
             * Set the target module or file to execute along with its arguments.
             *
             * @param target the module or file path as a string
             * @param methodArgs the arguments to pass to the executed method
             */
            @SuppressWarnings("unused")
            public Builder setTarget(String target, String... methodArgs) {
                return setTarget(new File(target), methodArgs);
            }

            /**
             * Add an argument to pass to the executed method.
             *
             * @param arg the argument
             */
            @SuppressWarnings("unused")
            public Builder addMethodArgument(String arg) {
                args.add(arg);
                return this;
            }

            /**
             * Build the RunnerOptions by parsing the accumulated arguments.
             */
            public RunnerOptions build() {
                return RunnerOptions.parse(args.toArray(String[]::new));
            }
        }
    }


    // ----- DisassemblerOptions -------------------------------------------------------------------

    /**
     * Disassembler command-line options.
     */
    public static class DisassemblerOptions extends LauncherOptions {

        DisassemblerOptions(CommandLine commandLine) {
            super(commandLine, DISASSEMBLER_OPTIONS);
        }

        @Override
        protected String getCommandName() {
            return "xtc";
        }

        /**
         * Parse command-line arguments into DisassemblerOptions.
         */
        public static DisassemblerOptions parse(String[] args) {
            return new DisassemblerOptions(parseCommandLine(DISASSEMBLER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing DisassemblerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(String cmdName) {
            return cmdName + " [options] <module_file>";
        }

        // Typed getters for all disassembler-specific options

        public File getTarget() {
            // First trailing arg is the module file to disassemble
            final var trailing = getTrailingArgs();
            return trailing.length > 0 ? new File(trailing[0]) : null;
        }

        public boolean isListFiles() {
            return commandLine.hasOption("files");
        }

        public File getFindFile() {
            String val = commandLine.getOptionValue("findfile");
            return val != null ? new File(val) : null;
        }

        @Override
        public String[] toCommandLine() {
            List<String> args = new ArrayList<>();

            // Add common flags from base class
            Collections.addAll(args, super.toCommandLine());

            // Add disassembler-specific flags
            if (isListFiles()) {
                args.add("--files");
            }

            // Add findfile
            File findFile = getFindFile();
            if (findFile != null) {
                args.addAll(List.of("--findfile", findFile.getPath()));
            }

            // Add target file
            File target = getTarget();
            if (target != null) {
                args.add(target.getPath());
            }

            return args.toArray(String[]::new);
        }

        /**
         * Builder for constructing DisassemblerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends BaseBuilder<Builder> {

            /**
             * List all files embedded in the module.
             */
            @SuppressWarnings("unused")
            public Builder listEmbeddedFiles() {
                return listEmbeddedFiles(true);
            }

            /**
             * List all files embedded in the module.
             *
             * @param list true to list files, false otherwise
             */
            public Builder listEmbeddedFiles(boolean list) {
                if (list) {
                    args.add("--files");
                }
                return this;
            }

            /**
             * Search for a specific file in the module.
             *
             * @param file the file to search for
             */
            public Builder findEmbeddedFile(File file) {
                args.addAll(List.of("--findfile", file.getPath()));
                return this;
            }

            /**
             * Search for a specific file in the module.
             *
             * @param file the file path as a string
             */
            @SuppressWarnings("unused")
            public Builder findEmbeddedFile(String file) {
                return findEmbeddedFile(new File(file));
            }

            /**
             * Set the target module file to disassemble.
             *
             * @param target the module file
             */
            public Builder setTarget(File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Set the target module file to disassemble.
             *
             * @param target the module file path as a string
             */
            public Builder setTarget(String target) {
                return setTarget(new File(target));
            }

            /**
             * Build the DisassemblerOptions by parsing the accumulated arguments.
             */
            public DisassemblerOptions build() {
                return DisassemblerOptions.parse(args.toArray(String[]::new));
            }
        }
    }
}
