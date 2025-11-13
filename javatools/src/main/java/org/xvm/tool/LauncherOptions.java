package org.xvm.tool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Version;

import org.xvm.util.ListMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Base class for launcher command-line options that wraps Apache Commons CLI.
 * Provides both command-line parsing and programmatic building via Builder pattern.
 * Subclasses provide typed getters for specific launcher options.
 *
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
     * Constructor from parsed command line.
     */
    protected LauncherOptions(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    /**
     * Get the module path.
     * Handles both multiple -L invocations and colon/semicolon-separated paths within a single -L.
     */
    public List<File> getModulePath() {
        String[] vals = commandLine.getOptionValues("L");
        if (vals == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>();
        for (String val : vals) {
            // Split by File.pathSeparator to handle both -L path1:path2 and -L path1 -L path2
            String[] paths = val.split(File.pathSeparator);
            for (String path : paths) {
                if (!path.isEmpty()) {
                    files.add(new File(path));
                }
            }
        }
        return Collections.unmodifiableList(files);
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
        List<String> args = new ArrayList<>();

        // Add common flags
        if (verbose()) {
            args.add("-v");
        }
        if (deduce()) {
            args.add("-d");
        }
        if (showVersion()) {
            args.add("--version");
        }

        // Add module path
        List<File> modulePath = getModulePath();
        for (File path : modulePath) {
            args.add("-L");
            args.add(path.getPath());
        }

        return args.toArray(new String[0]);
    }


    /**
     * Fluent wrapper for building Options schema.
     * Makes it cleaner to define multiple options and leverages Apache CLI validation.
     */
    protected static class OptionsBuilder {
        private final Options options = new Options();

        /**
         * Add a simple flag option (no argument).
         */
        public OptionsBuilder option(String shortOpt, String longOpt, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).desc(desc).get());
            return this;
        }

        /**
         * Add a simple flag option with short name only.
         */
        public OptionsBuilder option(String shortOpt, String desc) {
            options.addOption(Option.builder(shortOpt).desc(desc).get());
            return this;
        }

        /**
         * Add a simple flag option with long name only.
         */
        public OptionsBuilder longOption(String longOpt, String desc) {
            options.addOption(Option.builder().longOpt(longOpt).desc(desc).get());
            return this;
        }

        /**
         * Add an option that takes a single argument.
         */
        public OptionsBuilder optionWithArg(String shortOpt, String longOpt, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).hasArg().desc(desc).get());
            return this;
        }

        /**
         * Add an option with short name only that takes a single argument.
         */
        public OptionsBuilder optionWithArg(String shortOpt, String desc) {
            options.addOption(Option.builder(shortOpt).hasArg().desc(desc).get());
            return this;
        }

        /**
         * Add an option with long name only that takes a single argument.
         */
        public OptionsBuilder longOptionWithArg(String longOpt, String desc) {
            options.addOption(Option.builder().longOpt(longOpt).hasArg().desc(desc).get());
            return this;
        }

        /**
         * Add an option that can be specified multiple times (unlimited arguments).
         * Useful for options like -L that can appear multiple times: -L path1 -L path2
         */
        public OptionsBuilder optionWithMultipleArgs(String shortOpt, String longOpt, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).hasArgs().desc(desc).get());
            return this;
        }

        /**
         * Add an option with short name only that can be specified multiple times.
         */
        public OptionsBuilder optionWithMultipleArgs(String shortOpt, String desc) {
            options.addOption(Option.builder(shortOpt).hasArgs().desc(desc).get());
            return this;
        }

        /**
         * Add an option with specific number of arguments.
         * Useful for options that require exactly N arguments.
         */
        public OptionsBuilder optionWithNArgs(String shortOpt, String longOpt, int numArgs, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).numberOfArgs(numArgs).desc(desc).get());
            return this;
        }

        /**
         * Add a required option with an argument.
         * Apache CLI will automatically validate that this option is present.
         */
        public OptionsBuilder requiredOption(String shortOpt, String longOpt, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).hasArg().required().desc(desc).get());
            return this;
        }

        /**
         * Add an option with a display name for its argument (shown in help text).
         * Example: -L <path> where "path" is the argName.
         */
        public OptionsBuilder optionWithArgName(String shortOpt, String longOpt, String argName, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).hasArg().argName(argName).desc(desc).get());
            return this;
        }

        /**
         * Add an option that takes multiple arguments with a display name.
         * Example: -L <paths> where "paths" is the argName.
         */
        public OptionsBuilder optionWithMultipleArgName(String shortOpt, String longOpt, String argName, String desc) {
            options.addOption(Option.builder(shortOpt).longOpt(longOpt).hasArgs().argName(argName).desc(desc).get());
            return this;
        }

        public Options build() {
            return options;
        }
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
     * Generate formatted help text for all options.
     * Subclasses should override getOptionsSchema() to provide their specific options.
     *
     * @return formatted help text, or null if no schema available
     */
    public String getHelpText() {
        Options schema = getOptionsSchema();
        if (schema == null) {
            return null;
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);

        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);

        formatter.printOptions(pw, formatter.getWidth(), schema, 2, 4);
        pw.flush();

        return sw.toString();
    }

    /**
     * Subclasses should override this to provide their Options schema for help display.
     * This keeps Apache CLI types internal to the options system.
     *
     * @return the Apache CLI Options schema, or null if not available
     */
    protected Options getOptionsSchema() {
        return null;
    }


    // ----- CompilerOptions -----------------------------------------------------------------------

    /**
     * Common launcher options shared by all tools.
     * Pre-configured OptionsBuilder ready to be extended with tool-specific options.
     */
    private static OptionsBuilder commonLauncherOptions() {
        return new OptionsBuilder()
                .option("d", "deduce", "Automatically deduce locations when possible")
                .option("v", "verbose", "Enable verbose logging and messages")
                .longOption("version", "Display the Ecstasy runtime version")
                .optionWithArgName("L", null, "path",
                        "Module path (can be specified multiple times, or use " + File.pathSeparator + " separator)");
    }

    /**
     * Apache Commons CLI Options schema for the compiler.
     * Static final to avoid repeated construction.
     */
    private static final Options COMPILER_OPTIONS = commonLauncherOptions()
            // Compiler-specific options
            .longOption("rebuild", "Force rebuild")
            .longOption("strict", "Treat warnings as errors")
            .longOption("nowarn", "Suppress all warnings")
            .longOption("qualify", "Use full module name for the output file name")
            // Options with arguments - using argName for better help display
            .optionWithArgName("r", null, "resource", "Files and/or directories to read resources from (can use " + File.pathSeparator + " separator)")
            .optionWithArgName("o", null, "file", "File or directory to write output to")
            .optionWithArgName(null, "set-version", "version", "Specify the version to stamp onto the compiled module(s)")
            .build();

    /**
     * Compiler command-line options.
     * Simple wrapper around Apache Commons CLI CommandLine with typed getters.
     */
    public static class CompilerOptions extends LauncherOptions {

        CompilerOptions(CommandLine commandLine) {
            super(commandLine);
        }

        /**
         * Parse command-line arguments into CompilerOptions.
         */
        public static CompilerOptions parse(String[] args) {
            CommandLine cmd = parseCommandLine(COMPILER_OPTIONS, args);
            return new CompilerOptions(cmd);
        }

        /**
         * Create a builder for programmatically constructing CompilerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected Options getOptionsSchema() {
            return COMPILER_OPTIONS;
        }

        // Typed getters - just delegate to Apache CLI

        public List<File> getInputLocations() {
            String[] trailing = getTrailingArgs();
            if (trailing.length == 0) {
                return Collections.emptyList();
            }
            List<File> files = new ArrayList<>(trailing.length);
            for (String arg : trailing) {
                files.add(new File(arg));
            }
            return files;
        }

        public File[] getResourceLocation() {
            String[] vals = commandLine.getOptionValues("r");
            if (vals == null || vals.length == 0) {
                return ModuleInfo.NO_FILES;
            }
            // Handle both multiple -r invocations and path-separator-separated paths
            List<File> files = new ArrayList<>();
            for (String val : vals) {
                String[] paths = val.split(File.pathSeparator);
                for (String path : paths) {
                    if (!path.isEmpty()) {
                        files.add(new File(path));
                    }
                }
            }
            return files.toArray(new File[0]);
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
            String[] baseArgs = super.toCommandLine();
            Collections.addAll(args, baseArgs);

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
            File[] resources = getResourceLocation();
            for (File resource : resources) {
                args.add("-r");
                args.add(resource.getPath());
            }

            // Add output location
            File output = getOutputLocation();
            if (output != null) {
                args.add("-o");
                args.add(output.getPath());
            }

            // Add version
            Version version = getVersion();
            if (version != null) {
                args.add("--set-version");
                args.add(version.toString());
            }

            // Add input files (trailing args)
            List<File> inputs = getInputLocations();
            for (File input : inputs) {
                args.add(input.getPath());
            }

            return args.toArray(new String[0]);
        }

        /**
         * Builder for programmatically constructing CompilerOptions.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder {
            private final List<String> args = new ArrayList<>();

            /**
             * Enable forced rebuild.
             */
            public Builder rebuild(boolean rebuild) {
                if (rebuild) {
                    args.add("--rebuild");
                }
                return this;
            }

            /**
             * Enable strict mode (warnings as errors).
             */
            public Builder strict(boolean strict) {
                if (strict) {
                    args.add("--strict");
                }
                return this;
            }

            /**
             * Suppress warnings.
             */
            public Builder nowarn(boolean nowarn) {
                if (nowarn) {
                    args.add("--nowarn");
                }
                return this;
            }

            /**
             * Use fully qualified module names for output.
             */
            public Builder qualify(boolean qualify) {
                if (qualify) {
                    args.add("--qualify");
                }
                return this;
            }

            /**
             * Enable verbose output.
             */
            public Builder verbose(boolean verbose) {
                if (verbose) {
                    args.add("-v");
                }
                return this;
            }

            /**
             * Enable deduce mode.
             */
            public Builder deduce(boolean deduce) {
                if (deduce) {
                    args.add("-d");
                }
                return this;
            }

            /**
             * Add a module path entry.
             */
            public Builder modulePath(File modulePath) {
                args.add("-L");
                args.add(modulePath.getPath());
                return this;
            }

            /**
             * Add a resource location.
             */
            public Builder resource(File resource) {
                args.add("-r");
                args.add(resource.getPath());
                return this;
            }

            /**
             * Set the output location.
             */
            public Builder output(File output) {
                args.add("-o");
                args.add(output.getPath());
                return this;
            }

            /**
             * Set the version to stamp on the module.
             */
            public Builder version(String version) {
                args.add("--set-version");
                args.add(version);
                return this;
            }

            /**
             * Add an input file to compile.
             */
            public Builder input(File input) {
                args.add(input.getPath());
                return this;
            }

            /**
             * Build the CompilerOptions by parsing the accumulated arguments.
             */
            public CompilerOptions build() {
                return CompilerOptions.parse(args.toArray(new String[0]));
            }
        }
    }


    // ----- RunnerOptions -------------------------------------------------------------------------

    /**
     * Apache Commons CLI Options schema for the runner.
     * Static final to avoid repeated construction.
     */
    private static final Options RUNNER_OPTIONS = commonLauncherOptions()
            // Runner-specific options
            .option("J", "jit", "Enable the JIT-to-Java back-end")
            .longOption("no-recompile", "Disable automatic compilation")
            .optionWithArg("M", "method", "Method name; defaults to \"run\"")
            .optionWithArg("o", "If compilation is necessary, the file or directory to write compiler output to")
            .optionWithArg("I", "inject", "Specifies name/value pairs for injection; format is \"name=value\"")
            .build();

    /**
     * Runner command-line options.
     * Clean API with typed getters for all runner-specific options.
     */
    public static class RunnerOptions extends LauncherOptions {

        RunnerOptions(CommandLine commandLine) {
            super(commandLine);
        }

        /**
         * Parse command-line arguments into RunnerOptions.
         * Throws IllegalArgumentException if there are any parsing errors.
         */
        public static RunnerOptions parse(String[] args) {
            CommandLine cmd = parseCommandLine(RUNNER_OPTIONS, args);
            return new RunnerOptions(cmd);
        }

        /**
         * Create a builder for programmatically constructing RunnerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected Options getOptionsSchema() {
            return RUNNER_OPTIONS;
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
                return null;
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
                    String name = val.substring(0, idx);
                    String value = val.substring(idx + 1);
                    injections.put(name, value);
                }
            }
            return Collections.unmodifiableMap(injections);
        }

        @Override
        public String[] toCommandLine() {
            List<String> args = new ArrayList<>();

            // Add common flags from base class
            String[] baseArgs = super.toCommandLine();
            Collections.addAll(args, baseArgs);

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
                args.add("-M");
                args.add(method);
            }

            // Add output file
            File output = getOutputFile();
            if (output != null) {
                args.add("-o");
                args.add(output.getPath());
            }

            // Add injections
            Map<String, String> injections = getInjections();
            for (Map.Entry<String, String> entry : injections.entrySet()) {
                args.add("-I");
                args.add(entry.getKey() + "=" + entry.getValue());
            }

            // Add target file (first trailing arg)
            File target = getTarget();
            if (target != null) {
                args.add(target.getPath());
            }

            // Add method args (remaining trailing args)
            String[] methodArgs = getMethodArgs();
            if (methodArgs != null) {
                Collections.addAll(args, methodArgs);
            }

            return args.toArray(new String[0]);
        }

        /**
         * Builder for constructing RunnerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder {
            private final List<String> args = new ArrayList<>();

            /**
             * Enable JIT back-end.
             */
            public Builder jit(boolean jit) {
                if (jit) {
                    args.add("-J");
                }
                return this;
            }

            /**
             * Disable automatic compilation.
             */
            public Builder noRecompile(boolean noRecompile) {
                if (noRecompile) {
                    args.add("--no-recompile");
                }
                return this;
            }

            /**
             * Enable verbose output.
             */
            public Builder verbose(boolean verbose) {
                if (verbose) {
                    args.add("-v");
                }
                return this;
            }

            /**
             * Enable deduce mode.
             */
            public Builder deduce(boolean deduce) {
                if (deduce) {
                    args.add("-d");
                }
                return this;
            }

            /**
             * Add a module path entry.
             */
            public Builder modulePath(File modulePath) {
                args.add("-L");
                args.add(modulePath.getPath());
                return this;
            }

            /**
             * Set the method name to execute.
             */
            public Builder method(String method) {
                args.add("-M");
                args.add(method);
                return this;
            }

            /**
             * Set the output location for compilation.
             */
            public Builder output(File output) {
                args.add("-o");
                args.add(output.getPath());
                return this;
            }

            /**
             * Add an injection (name=value pair).
             */
            public Builder inject(String name, String value) {
                args.add("-I");
                args.add(name + "=" + value);
                return this;
            }

            /**
             * Set the target module/file to execute.
             */
            public Builder target(File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Add a method argument.
             */
            public Builder methodArg(String arg) {
                args.add(arg);
                return this;
            }

            /**
             * Build the RunnerOptions by parsing the accumulated arguments.
             */
            public RunnerOptions build() {
                return RunnerOptions.parse(args.toArray(new String[0]));
            }
        }
    }


    // ----- DisassemblerOptions -------------------------------------------------------------------

    /**
     * Apache Commons CLI Options schema for the disassembler.
     * Static final to avoid repeated construction.
     */
    private static final Options DISASSEMBLER_OPTIONS = commonLauncherOptions()
            // Disassembler-specific options
            .longOption("files", "List all files embedded in the module")
            .longOptionWithArg("findfile", "File to search for in the module")
            .build();

    /**
     * Disassembler command-line options.
     */
    public static class DisassemblerOptions extends LauncherOptions {

        DisassemblerOptions(CommandLine commandLine) {
            super(commandLine);
        }

        /**
         * Parse command-line arguments into DisassemblerOptions.
         */
        public static DisassemblerOptions parse(String[] args) {
            CommandLine cmd = parseCommandLine(DISASSEMBLER_OPTIONS, args);
            return new DisassemblerOptions(cmd);
        }

        /**
         * Create a builder for programmatically constructing DisassemblerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected Options getOptionsSchema() {
            return DISASSEMBLER_OPTIONS;
        }

        // Typed getters for all disassembler-specific options

        public File getTarget() {
            // First trailing arg is the module file to disassemble
            String[] trailing = getTrailingArgs();
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
            String[] baseArgs = super.toCommandLine();
            Collections.addAll(args, baseArgs);

            // Add disassembler-specific flags
            if (isListFiles()) {
                args.add("--files");
            }

            // Add findfile
            File findFile = getFindFile();
            if (findFile != null) {
                args.add("--findfile");
                args.add(findFile.getPath());
            }

            // Add target file
            File target = getTarget();
            if (target != null) {
                args.add(target.getPath());
            }

            return args.toArray(new String[0]);
        }

        /**
         * Builder for constructing DisassemblerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder {
            private final List<String> args = new ArrayList<>();

            /**
             * Enable verbose output.
             */
            public Builder verbose(boolean verbose) {
                if (verbose) {
                    args.add("-v");
                }
                return this;
            }

            /**
             * Enable deduce mode.
             */
            public Builder deduce(boolean deduce) {
                if (deduce) {
                    args.add("-d");
                }
                return this;
            }

            /**
             * Add a module path entry.
             */
            public Builder modulePath(File modulePath) {
                args.add("-L");
                args.add(modulePath.getPath());
                return this;
            }

            /**
             * Enable listing files in the module.
             */
            public Builder listFiles(boolean listFiles) {
                if (listFiles) {
                    args.add("--files");
                }
                return this;
            }

            /**
             * Set the file to find in the module.
             */
            public Builder findFile(File findFile) {
                args.add("--findfile");
                args.add(findFile.getPath());
                return this;
            }

            /**
             * Set the target module file to disassemble.
             */
            public Builder target(File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Build the DisassemblerOptions by parsing the accumulated arguments.
             */
            public DisassemblerOptions build() {
                return DisassemblerOptions.parse(args.toArray(new String[0]));
            }
        }
    }
}
