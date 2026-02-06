package org.xvm.tool;

import java.io.File;
import java.io.IOException;

import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.xvm.asm.Version;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.help.TextHelpAppendable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.apache.commons.cli.Option.builder;

import org.jetbrains.annotations.NotNull;

/**
 * Base class for launcher command-line options that wraps Apache Commons CLI.
 * Provides both command-line parsing and programmatic building via Builder pattern.
 * Subclasses provide typed getters for specific launcher options.
 * <p>
 * This class must be public to allow its public static nested classes (CompilerOptions,
 * RunnerOptions, DisassemblerOptions) to be accessible from other packages.
 */
public abstract class LauncherOptions {

    private static final String PSEP = File.pathSeparator;

    public static final String OPTION_XUNIT_OUT = "xunit-out";
    public static final String ARG_XUNIT_OUT = "--" + OPTION_XUNIT_OUT;

    private static final Options COMMON_OPTIONS = new Options()
        .addOption(builder("d").longOpt("deduce").desc("Automatically deduce locations when possible").get())
        .addOption(builder("h").longOpt("help").desc("Display this help message").get())
        .addOption(builder("L").argName("path").hasArg()
            .desc("Module path (can be specified multiple times, or use " + PSEP + " separator)").get())
        .addOption(builder("v").longOpt("verbose").desc("Enable verbose logging and messages").get())
        .addOption(builder().longOpt("version").desc("Display the Ecstasy runtime version").get());

    /**
     * Apache Commons CLI Options schema for the compiler.
     */
    private static final Options COMPILER_OPTIONS = conflictingOptions(copyOptions(COMMON_OPTIONS)
        .addOption(builder().longOpt("rebuild").desc("Force rebuild").get())
        .addOption(builder().longOpt("qualify").desc("Use full module name for the output file name").get())
        .addOption(builder("r").argName("resource").hasArg()
            .desc("Files and/or directories to read resources from (can use " + PSEP + " separator)").get())
        .addOption(builder("o").argName("file").hasArg()
            .desc("File or directory to write output to").get())
        .addOption(builder().longOpt("set-version").argName("version").hasArg()
            .desc("Specify the version to stamp onto the compiled module(s)").get()));

    /**
     * Base Apache Commons CLI Options schema for the runners.
     */
    private static final Options BASE_RUNNER_OPTIONS = copyOptions(COMMON_OPTIONS)
        .addOption(builder("J").longOpt("jit").desc("Enable the JIT-to-Java back-end").get())
        .addOption(builder().longOpt("no-recompile").desc("Disable automatic compilation").get())
        .addOption(builder("o").argName("file").hasArg()
            .desc("If compilation is necessary, the file or directory to write compiler output to").get())
        .addOption(builder("I").longOpt("inject").argName("name=value").hasArg()
            .desc("Specifies name/value pairs for injection; format is 'name=value'").get());

    /**
     * Apache Commons CLI Options schema for the runner.
     */
    private static final Options RUNNER_OPTIONS = copyOptions(BASE_RUNNER_OPTIONS)
        .addOption(builder("M").longOpt("method").argName("method").hasArg()
            .desc("Method name; defaults to 'run'").get());

    /**
     * Apache Commons CLI Options schema for the test runner.
     */
    private static final Options TEST_RUNNER_OPTIONS = copyOptions(BASE_RUNNER_OPTIONS)
        .addOption(builder("c").longOpt("test-class").argName("class").hasArg()
            .desc("the fully qualified name of a class to execute tests in").get())
        .addOption(builder("g").longOpt("test-group").argName("group").hasArg()
            .desc("only execute tests with the specified @Test annotation group").get())
        .addOption(builder("p").longOpt("test-package").argName("package").hasArg()
            .desc("the name of a package to execute tests in").get())
        .addOption(builder("t").longOpt("test-method").argName("method").hasArg()
            .desc("the fully qualified name of a test method to execute").get())
        .addOption(builder("x").longOpt(OPTION_XUNIT_OUT).argName(OPTION_XUNIT_OUT).hasArg()
            .desc("The directory that XUnit will write any test output to").get());

    /**
     * Apache Commons CLI Options schema for the disassembler.
     */
    private static final Options DISASSEMBLER_OPTIONS = copyOptions(COMMON_OPTIONS)
        .addOption(builder().longOpt("files").desc("List all files embedded in the module").get())
        .addOption(builder().longOpt("findfile").argName("file").hasArg()
            .desc("File to search for in the module").get());

    /**
     * Apache Commons CLI Options schema for the initializer.
     */
    private static final Options INITIALIZER_OPTIONS = copyOptions(COMMON_OPTIONS)
        .addOption(builder("t").longOpt("type").argName("type").hasArg()
            .desc("Project type: application (default), library, or service").get())
        .addOption(builder("m").longOpt("multi-module")
            .desc("Create a multi-module project structure").get())
        .addOption(builder().longOpt("dir").argName("directory").hasArg()
            .desc("Directory to create the project in (default: current directory)").get())
        .addOption(builder().longOpt("local-and-snapshot-repos")
            .desc("Include mavenLocal() and maven-snapshots repository in settings.gradle.kts (default: true)").get())
        .addOption(builder().longOpt("no-local-and-snapshot-repos")
            .desc("Exclude mavenLocal() and maven-snapshots repository from settings.gradle.kts").get());

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

    private final String commandName;

    /**
     * Constructor from parsed command line.
     *
     * @param commandLine the parsed command line
     * @param schema the Options schema used to parse the command line
     * @param commandName the "alias" for the launcher in question, e.g. "xcc" or "xec"
     */
    protected LauncherOptions(final CommandLine commandLine, final Options schema, final String commandName) {
        this.commandLine = commandLine;
        this.schema = schema;
        this.commandName = commandName;
    }


    /**
     * Copy an Options object by adding all its options to a new Options instance.
     */
    private static Options copyOptions(@SuppressWarnings("SameParameterValue") final Options options) {
        return options.getOptions().stream().collect(Options::new, Options::addOption, (_, _) -> {});
    }

    /**
     * Add mutually exclusive warning options (--strict and --nowarn) to an Options instance.
     */
    private static Options conflictingOptions(final Options options) {
        OptionGroup group = new OptionGroup();
        group.addOption(builder().longOpt("strict").desc("Treat warnings as errors").get());
        group.addOption(builder().longOpt("nowarn").desc("Suppress all warnings").get());
        return options.addOptionGroup(group);
    }

    // ----- Protected Helpers for Apache CLI Access ---------------------------------------------

    /**
     * Get all values for an option as a list. Never returns null.
     * Use this instead of commandLine.getOptionValues() directly.
     *
     * @param option the option name (short or long)
     * @return list of values, empty if option not specified
     */
    protected List<String> optionValues(final String option) {
        final var values = commandLine.getOptionValues(option);
        return values == null ? List.of() : List.of(values);
    }

    /**
     * Get a single option value as an Optional.
     * Use this instead of commandLine.getOptionValue() directly.
     *
     * @param option the option name (short or long)
     * @return Optional containing the value, or empty if not specified
     */
    protected Optional<String> optionValue(final String option) {
        return Optional.ofNullable(commandLine.getOptionValue(option));
    }

    /**
     * Check if an option is present.
     *
     * @param option the option name (short or long)
     * @return true if the option was specified
     */
    protected boolean hasOption(final String option) {
        return commandLine.hasOption(option);
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
     * Common helper to parse path options that support both multiple invocations and path separator splitting.
     * For example: -L path1 -L path2 and -L path1:path2 both work.
     * <p>
     * NOTE: Tilde expansion and glob/wildcard expansion are handled by the shell before arguments reach Java.
     * For programmatic use via the Builder API, callers should provide resolved paths.
     *
     * @param optionName the short option name (e.g., "L", "r")
     * @return list of File objects parsed from the option values
     */
    protected List<File> getPathList(final String optionName) {
        return optionValues(optionName).stream()
                .flatMap(val -> Arrays.stream(val.split(PSEP)))
                .filter(path -> !path.isEmpty())
                .map(File::new)
                .toList();
    }

    /**
     * Check if verbose mode is enabled.
     */
    public boolean isVerbose() {
        return hasOption("v");
    }

    /**
     * Check if deduce mode is enabled.
     */
    public boolean mayDeduceLocations() {
        return hasOption("d");
    }

    /**
     * Check if help should be shown.
     */
    public boolean showHelp() {
        return hasOption("help");
    }

    /**
     * Check if version should be shown.
     */
    public boolean showVersion() {
        return hasOption("version");
    }

    /**
     * Convert this options instance back to command-line arguments.
     * Subclasses should override to add their specific options.
     */
    public String[] toCommandLine() {
        final List<String> args = new ArrayList<>();
        if (isVerbose()) {
            args.add("-v");
        }
        if (mayDeduceLocations()) {
            args.add("-d");
        }
        if (showVersion()) {
            args.add("--version");
        }
        getModulePath().stream().map(File::getPath).forEach(path -> args.addAll(List.of("-L", path)));
        return args.toArray(String[]::new);
    }

    /**
     * Convert this options instance to JSON for configuration files or IPC.
     * Automatically serializes all parsed options by introspecting the CommandLine.
     *
     * @return JSON string representation (pretty-printed with 2-space indent)
     */
    public String toJson() {
        final var json = new JsonObject();
        // Iterate through the schema to get ALL option values (handles repeated options like -L)
        for (final var schemaOpt : schema.getOptions()) {
            final var key = schemaOpt.getLongOpt() != null ? schemaOpt.getLongOpt() : schemaOpt.getOpt();
            if (!schemaOpt.hasArg()) {
                // Boolean flag - check if present
                if (hasOption(key)) {
                    json.addProperty(key, true);
                }
                continue;
            }
            // Option with values - use optionValues to get ALL values (handles repeated options)
            final var values = optionValues(key);
            if (!values.isEmpty()) {
                if (values.size() == 1) {
                    json.addProperty(key, values.getFirst());
                } else {
                    final var arr = new JsonArray();
                    values.forEach(arr::add);
                    json.add(key, arr);
                }
            }
        }

        // Add trailing args if present
        final var trailingArgs = getTrailingArgs();
        if (!trailingArgs.isEmpty()) {
            final var arr = new JsonArray();
            trailingArgs.forEach(arr::add);
            json.add("args", arr);
        }

        return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    }

    /**
     * Convert JSON back to command-line arguments.
     * Automatically reconstructs args from any JSON using the schema.
     *
     * @param jsonString the JSON configuration
     * @param schema the Options schema
     * @return array of command-line arguments
     */
    protected static String[] jsonToArgs(final String jsonString, final Options schema) {
        final JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        final List<String> args = new ArrayList<>();

        // Process each option from schema
        for (final var opt : schema.getOptions()) {
            final var key = opt.getLongOpt() != null ? opt.getLongOpt() : opt.getOpt();
            if (!json.has(key)) {
                continue;
            }

            final var flag = (opt.getLongOpt() != null ? "--" : "-") + key;
            final var value = json.get(key);

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                if (value.getAsBoolean()) args.add(flag);
            } else if (value.isJsonArray()) {
                for (final var elem : value.getAsJsonArray()) {
                    args.add(flag);
                    args.add(elem.getAsString());
                }
            } else {
                args.add(flag);
                args.add(value.getAsString());
            }
        }

        // Add trailing args
        if (json.has("args")) {
            for (final JsonElement elem : json.getAsJsonArray("args")) {
                args.add(elem.getAsString());
            }
        }

        return args.toArray(String[]::new);
    }

    /**
     * String representation of options for verbose output.
     * Shows the options in command-line format.
     */
    @Override
    public String toString() {
        return String.join(" ", toCommandLine());
    }

    /**
     * Parse command line arguments using Apache Commons CLI.
     * Flags are simple presence/absence switches (present = true, absent = false).
     * Stops at first non-option argument to collect trailing args.
     */
    protected static CommandLine parseCommandLine(final Options options, final String[] args) {
        try {
            // stopAtNonOption=false means we'll catch unknown options as errors
            // This gives better error messages for typos like --unknown
            final var parser = new DefaultParser();
            return parser.parse(options, args, false);
        } catch (final ParseException e) {
            throw new IllegalArgumentException("Error parsing command-line arguments: " + e.getMessage(), e);
        }
    }

    /**
     * Get trailing arguments (non-option arguments at end of command line).
     */
    protected List<String> getTrailingArgs() {
        final var trailing = commandLine.getArgs();
        assert trailing != null : "commandLine.getArgs() should never return null.";
        return List.of(trailing);
    }

    /**
     * Generate formatted help text using Apache CLI HelpFormatter.
     * Includes a usage line showing the command syntax.
     *
     * @return formatted help text, or null if no schema available
     */
    public final String getHelpText() {
        // Add usage line with command syntax
        assert schema != null;
        final var sb  = new StringBuilder("Usage:\n")
            .append("    ")
            .append(buildUsageLine(commandName))
            .append("\n\n");

        // Allow up to 120 characters per line
        // Left pad 2 spaces to the left of each line (default is 1)
        // Indent for paragraph wrapping (not used in option tables)
        // NOTE: setIndent() doesn't affect table column wrapping; HelpFormatter uses hardcoded TextStyle
        try {
            final var help = new TextHelpAppendable(sb);
            help.setMaxWidth(120);
            help.setMaxWidth(120);
            help.setLeftPad(2);
            help.setIndent(4);
            HelpFormatter.builder().setShowSince(false).setHelpAppendable(help).get().printOptions(schema);
        } catch (final IOException e) {
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
     * Build the usage line for help display.
     * Subclasses can override to customize the usage string.
     *
     * @param commandName the command name
     * @return the usage line string
     */
    protected String buildUsageLine(final String commandName) {
        return commandName + " [options]";
    }


    // ----- Base Builder --------------------------------------------------------------------------

    /**
     * Base Builder class for constructing LauncherOptions programmatically.
     * Provides common builder methods shared by all launcher types.
     *
     * @param <T> The concrete builder type for method chaining
     */
    protected abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {
        protected final List<@NotNull String> args = new ArrayList<>();

        protected boolean removeeArgsAndValues(final String arg) {
            boolean removed = false;
            int i;
            while ((i = args.indexOf(arg)) != -1) {
                removed = true;
                final var key = args.remove(i);
                final var value = args.remove(i);
                assert value.indexOf('-') == 0 : "Inconsistent key value arg: " + key + ' ' + value;
            }
            return removed;
        }

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
        public T enableVerbose(final boolean verbose) {
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
        public T enableDeduction(final boolean deduce) {
            args.remove("-d");
            if (deduce) {
                args.add("-d");
            }
            return (T) this;
        }

        /**
         * Enable version display (--version flag).
         * Not to be confused with setModuleVersion() which sets the module version.
         */
        @SuppressWarnings("unused")
        public T enableShowVersion() {
            return enableShowVersion(true);
        }

        /**
         * Enable version display (--version flag).
         * Not to be confused with setModuleVersion() which sets the module version.
         *
         * @param showVersion true to enable version display, false otherwise
         */
        @SuppressWarnings("unchecked")
        public T enableShowVersion(final boolean showVersion) {
            args.remove("--version");
            if (showVersion) {
                args.add("--version");
            }
            return (T) this;
        }

        public T setModulePath(final File path) {
            removeeArgsAndValues("-L");
            return addModulePath(path);
        }

        public T setModulePath(final Path path) {
            return setModulePath(path.toFile());
        }

        public T setModulePath(final String path) {
            return setModulePath(new File(path));
        }

        public T setModulePath(final List<File> paths) {
            removeeArgsAndValues("-L");
            return addModulePath(paths);
        }

        public T setModulePath(final File... paths) {
            return setModulePath(Arrays.asList(paths));
        }

        public T setModulePath(final String... paths) {
            return setModulePath(Arrays.stream(paths).map(File::new).toList());
        }

        public T addModulePath(final File path) {
            return addModulePath(List.of(path));
        }

        @SuppressWarnings("unchecked")
        public T addModulePath(final List<File> paths) {
            for (final var path : paths) {
                args.addAll(List.of("-L", path.getPath()));
            }
            return (T) this;
        }

        public T addModulePath(final File... paths) {
            return addModulePath(Arrays.asList(paths));
        }

        public T addModulePath(final String... paths) {
            return addModulePath(Arrays.stream(paths).map(File::new).toList());
        }

        /**
         * Add a directory or file to the module path.
         *
         * @param path the path to add as a string
         */
        public T addModulePath(final String path) {
            return addModulePath(new File(path));
        }
    }

    /**
     * Compiler command-line options.
     * Simple wrapper around Apache Commons CLI CommandLine with typed getters.
     */
    public static class CompilerOptions extends LauncherOptions {

        CompilerOptions(final CommandLine commandLine) {
            super(commandLine, COMPILER_OPTIONS, "build");
        }

        /**
         * Parse command-line arguments into CompilerOptions.
         */
        public static CompilerOptions parse(final String[] args) {
            return new CompilerOptions(parseCommandLine(COMPILER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing CompilerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(final String cmdName) {
            return cmdName + " [options] <source_files>";
        }

        // Typed getters - just delegate to Apache CLI
        public List<File> getInputLocations() {
            return getTrailingArgs().stream().map(File::new).toList();
        }

        public List<File> getResourceLocations() {
            return getPathList("r");
        }

        public boolean hasResourceLocation() {
            return hasOption("r");
        }

        public Optional<File> getOutputLocation() {
            return optionValue("o").map(File::new);
        }

        public Optional<Version> getVersion() {
            return optionValue("set-version").map(Version::new);
        }

        public boolean isForcedRebuild() {
            return hasOption("rebuild");
        }

        public boolean isStrict() {
            return hasOption("strict");
        }

        public boolean isNoWarn() {
            return hasOption("nowarn");
        }

        public boolean isOutputFilenameQualified() {
            return hasOption("qualify");
        }

        @Override
        public String[] toCommandLine() {
            final List<String> args = new ArrayList<>();
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
            getResourceLocations().stream().map(File::getPath).forEach(path -> args.addAll(List.of("-r", path)));
            getOutputLocation().ifPresent(output -> args.addAll(List.of("-o", output.getPath())));
            getVersion().ifPresent(version -> args.addAll(List.of("--set-version", version.toString())));
            getInputLocations().stream().map(File::getPath).forEach(args::add);
            return args.toArray(String[]::new);
        }

        /**
         * Create CompilerOptions from JSON string.
         *
         * @param jsonString the JSON configuration
         * @return CompilerOptions instance
         */
        public static CompilerOptions fromJson(final String jsonString) {
            return CompilerOptions.parse(jsonToArgs(jsonString, COMPILER_OPTIONS));
        }

        /**
         * Builder for programmatically constructing CompilerOptions.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends AbstractBuilder<Builder> {
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
            public Builder forceRebuild(final boolean rebuild) {
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
            public Builder enableStrictMode(final boolean strict) {
                args.remove("--strict");
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
            public Builder disableWarnings(final boolean disable) {
                args.remove("--nowarn");
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
            public Builder qualifyOutputNames(final boolean qualify) {
                args.remove("--qualify");
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
            public Builder addResourceLocation(final File resource) {
                args.addAll(List.of("-r", resource.getPath()));
                return this;
            }

            public Builder addResourceLocation(final Path resource) {
                return addResourceLocation(resource.toFile());
            }

            /**
             * Add a resource location for compilation.
             *
             * @param resource the resource file or directory path as a string
             */
            public Builder addResourceLocation(final String resource) {
                return addResourceLocation(new File(resource));
            }

            /**
             * Set the output location for compiled modules.
             *
             * @param output the output file or directory
             */
            public Builder setOutputLocation(final File output) {
                removeeArgsAndValues("-o");
                if (output != null) {
                    args.addAll(List.of("-o", output.getPath()));
                }
                return this;
            }

            public Builder setOutputLocation(final Path output) {
                return setOutputLocation(output == null ? null : output.toFile());
            }

            /**
             * Set the output location for compiled modules.
             *
             * @param output the output file or directory path as a string
             */
            public Builder setOutputLocation(final String output) {
                return setOutputLocation(output == null ? null : new File(output));
            }

            /**
             * Set the version to stamp on compiled modules.
             *
             * @param version the version string
             */
            public Builder setModuleVersion(final String version) {
                removeeArgsAndValues("--set-version");
                args.addAll(List.of("--set-version", version));
                return this;
            }

            /**
             * Set the version to stamp on compiled modules.
             *
             * @param version the Version object
             */
            public Builder setModuleVersion(final Version version) {
                return setModuleVersion(version.toString());
            }

            /**
             * Add an input source file to compile.
             *
             * @param input the source file
             */
            public Builder addInputFile(final File input) {
                args.add(input.getPath());
                return this;
            }

            /**
             * Add an input source file to compile.
             *
             * @param input the source file path as a string
             */
            @SuppressWarnings("unused")
            public Builder addInputFile(final String input) {
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

        RunnerOptions(final CommandLine commandLine) {
            this(commandLine, RUNNER_OPTIONS);
        }

        RunnerOptions(final CommandLine commandLine, final Options schema) {
            super(commandLine, schema, "run");
        }

        /**
         * Parse command-line arguments into RunnerOptions.
         * Throws IllegalArgumentException if there are any parsing errors.
         */
        public static RunnerOptions parse(final String[] args) {
            return new RunnerOptions(parseCommandLine(RUNNER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing RunnerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(final String cmdName) {
            return cmdName + " [options] <module_or_file> [args...]";
        }

        // Typed getters for all runner-specific options

        public String getMethodName() {
            return optionValue("M").orElse("run");
        }

        public boolean isCompileDisabled() {
            return hasOption("no-recompile");
        }

        public boolean isJit() {
            return hasOption("J");
        }

        public Optional<File> getTarget() {
            // First trailing arg is the module/file to execute
            final var trailing = getTrailingArgs();
            return trailing.isEmpty() ? Optional.empty() : Optional.of(new File(trailing.getFirst()));
        }

        public Optional<File> getOutputFile() {
            return optionValue("o").map(File::new);
        }

        public List<String> getMethodArgs() {
            // Everything after the first trailing arg goes to the method
            final var trailing = getTrailingArgs();
            if (trailing.size() <= 1) {
                return List.of();
            }
            return trailing.subList(1, trailing.size());
        }

        /**
         * Get injections as a map where each key maps to a list of values.
         * Multiple -I flags with the same key will accumulate values in the list.
         * A single value becomes a single-element list.
         *
         * @return map of injection name to list of values
         */
        public Map<String, List<String>> getInjections() {
            final var injections = new LinkedHashMap<String, List<String>>();
            for (final String val : optionValues("I")) {
                final int idx = val.indexOf('=');
                if (idx > 0) {
                    final String key = val.substring(0, idx);
                    final String value = val.substring(idx + 1);
                    injections.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
                }
            }
            // Return immutable copy with immutable lists
            final var result = new LinkedHashMap<String, List<String>>();
            injections.forEach((k, v) -> result.put(k, List.copyOf(v)));
            return Map.copyOf(result);
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

            args.addAll(additionalRunnerCommandLine());

            // Add output file
            getOutputFile().ifPresent(output -> args.addAll(List.of("-o", output.getPath())));

            // Add injections - each value in the list gets its own -I flag
            getInjections().forEach((key, values) ->
                values.forEach(value -> args.addAll(List.of("-I", key + "=" + value))));

            // Add target file and method args
            getTarget().ifPresent(target -> args.add(target.getPath()));
            args.addAll(getMethodArgs());
            return args.toArray(String[]::new);
        }

        /**
         * @return additional command line arguments specific to these options.
         */
        protected List<String> additionalRunnerCommandLine() {
            List<String> args = new ArrayList<>();
            // Add method name
            String method = getMethodName();
            if (!"run".equals(method)) {
                args.addAll(List.of("-M", method));
            }
            return args;
        }

        /**
         * Create RunnerOptions from JSON string.
         *
         * @param jsonString the JSON configuration
         * @return RunnerOptions instance
         */
        public static RunnerOptions fromJson(final String jsonString) {
            return RunnerOptions.parse(jsonToArgs(jsonString, RUNNER_OPTIONS));
        }

        /**
         * Builder for constructing RunnerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends AbstractBuilder<Builder> {
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
            public Builder enableJit(final boolean enable) {
                args.remove("-J");
                if (enable) {
                    args.add("-J");
                }
                return this;
            }

            /**
             * Disable automatic recompilation of sources.
             */
            @SuppressWarnings("unused")
            public Builder disableAutomaticCompilation() {
                return disableAutomaticCompilation(true);
            }

            // TODO: Test
            public Builder noRecompile() {
                return disableAutomaticCompilation(false);
            }

            /**
             * Disable automatic recompilation of sources.
             *
             * @param disable true to disable auto-compilation, false otherwise
             */
            public Builder disableAutomaticCompilation(final boolean disable) {
                args.remove("--no-recompile");
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
            public Builder setMethodName(final String methodName) {
                removeeArgsAndValues("-M");
                args.addAll(List.of("-M", methodName));
                return this;
            }

            /**
             * Set the output location for compilation (if needed).
             *
             * @param output the output file or directory
             */
            public Builder setOutputLocation(final File output) {
                removeeArgsAndValues("-o");
                args.addAll(List.of("-o", output.getPath()));
                return this;
            }

            /**
             * Set the output location for compilation (if needed).
             *
             * @param output the output file or directory path as a string
             */
            @SuppressWarnings("unused")
            public Builder setOutputLocation(final String output) {
                return setOutputLocation(new File(output));
            }

            /**
             * Add a name=value injection for the module.
             *
             * @param name the injection name
             * @param value the injection value
             */
            @SuppressWarnings("unused")
            public Builder addInjection(final String name, final String value) {
                args.addAll(List.of("-I", name + "=" + value));
                return this;
            }

            /**
             * Set the target module or file to execute.
             *
             * @param target the module or file to run
             */
            public Builder setTarget(final File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Set the target module or file to execute.
             *
             * @param target the module or file path as a string
             */
            public Builder setTarget(final String target) {
                return setTarget(new File(target));
            }

            /**
             * Set the target module or file to execute along with its arguments.
             *
             * @param target the module or file to run
             * @param methodArgs the arguments to pass to the executed method
             */
            public Builder setTarget(final File target, final List<String> methodArgs) {
                // TODO This shold be relativized and we should check that getPath doesn't mess up the cache again.
                args.add(target.getPath());
                args.addAll(methodArgs);
                return this;
            }

            public Builder setTarget(final String target, final List<String> methodArgs) {
                // TODO: We can accept a string module name here, but it is confusing with both files and names
                // It is also wrong - we shouldn't turn a target name to a file
                args.add(target);
                args.addAll(methodArgs);
                return this;
            }

            /**
             * Set the target module or file to execute along with its arguments.
             *
             * @param target the module or file path as a string
             * @param methodArgs the arguments to pass to the executed method
             */
            @SuppressWarnings("unused")
            public Builder setTarget(final String target, final String... methodArgs) {
                return setTarget(target, List.of(methodArgs));
            }

            public Builder setTarget(final File target, final String... methodArgs) {
                return setTarget(target, List.of(methodArgs));
            }

            /**
             * Add an argument to pass to the executed method.
             *
             * @param arg the argument
             */
            @SuppressWarnings("unused")
            public Builder addMethodArgument(final String arg) {
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


    // ----- TestRunnerOptions ---------------------------------------------------------------------

    /**
     * Test runner command-line options.
     * Extends RunnerOptions since it uses the same option schema but with different
     * execution behavior (runs xunit engine instead of the module directly).
     */
    public static class TestRunnerOptions extends RunnerOptions {

        TestRunnerOptions(final CommandLine commandLine) {
            super(commandLine, TEST_RUNNER_OPTIONS);
        }

        /**
         * Parse command-line arguments into TestRunnerOptions.
         */
        public static TestRunnerOptions parse(final String[] args) {
            return new TestRunnerOptions(parseCommandLine(TEST_RUNNER_OPTIONS, args));
        }

        @Override
        protected List<String> additionalRunnerCommandLine() {
            List<String> args = new ArrayList<>();
            // Add XUnit output
            optionValue(LauncherOptions.OPTION_XUNIT_OUT).map(File::new).ifPresent(dir ->
                    args.addAll(List.of(ARG_XUNIT_OUT, dir.getPath())));

            return args;
        }

        /**
         * Create a builder for programmatically constructing TestRunnerOptions.
         */
        public static TestRunnerOptions.Builder builder() {
            return new TestRunnerOptions.Builder();
        }

        @Override
        public Map<String, List<String>> getInjections() {
            final var injections = new LinkedHashMap<>(super.getInjections());

            final var testClasses = optionValues("c");
            if (!testClasses.isEmpty()) {
                injections.put(TestRunner.XUNIT_TEST_CLASSES_ARG, testClasses);
            }

            final var testGroups = optionValues("g");
            if (!testGroups.isEmpty()) {
                injections.put(TestRunner.XUNIT_TEST_GROUPS_ARG, testGroups);
            }

            final var testPackages = optionValues("p");
            if (!testPackages.isEmpty()) {
                injections.put(TestRunner.XUNIT_TEST_PACKAGES_ARG, testPackages);
            }

            final var testMethods = optionValues("t");
            if (!testMethods.isEmpty()) {
                injections.put(TestRunner.XUNIT_TEST_METHODS_ARG, testMethods);
            }

            return Map.copyOf(injections);
        }


        @Override
        protected String buildUsageLine(final String cmdName) {
            return "xtc test [options] <module_or_file>";
        }

        /**
         * Builder for constructing TestRunnerOptions programmatically.
         */
        public static class Builder extends RunnerOptions.Builder {
            /**
             * Set the XUnit output directory.
             *
             * @param dir the directory name
             */
            public Builder setXUnitOutputDirectory(final String dir) {
                removeeArgsAndValues(ARG_XUNIT_OUT);
                args.addAll(List.of(ARG_XUNIT_OUT, dir));
                return this;
            }

            /**
             * Build the TestRunnerOptions by parsing the accumulated arguments.
             */
            @Override
            public TestRunnerOptions build() {
                return TestRunnerOptions.parse(args.toArray(String[]::new));
            }
        }
    }


    // ----- InitializerOptions -------------------------------------------------------------------

    /**
     * Initializer (xtc init) command-line options.
     */
    public static class InitializerOptions extends LauncherOptions {

        InitializerOptions(final CommandLine commandLine) {
            super(commandLine, INITIALIZER_OPTIONS, "init");
        }

        /**
         * Parse command-line arguments into InitializerOptions.
         */
        public static InitializerOptions parse(final String[] args) {
            return new InitializerOptions(parseCommandLine(INITIALIZER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing InitializerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(final String cmdName) {
            return cmdName + " [options] <project-name>";
        }

        /**
         * Get the project name (first trailing argument).
         */
        public Optional<String> getProjectName() {
            final var trailing = getTrailingArgs();
            return trailing.isEmpty() ? Optional.empty() : Optional.of(trailing.getFirst());
        }

        /**
         * Get the project type (application, library, service).
         */
        public String getProjectType() {
            return optionValue("type").orElse("application");
        }

        /**
         * Check if multi-module project structure is requested.
         */
        public boolean isMultiModule() {
            return hasOption("multi-module");
        }

        /**
         * Get the output directory (where the project folder will be created).
         * If not specified, returns empty (meaning current directory).
         */
        public Optional<String> getOutputDirectory() {
            return optionValue("dir");
        }

        /**
         * Check if mavenLocal() and maven-snapshots repository should be included.
         * Default is true unless --no-local-and-snapshot-repos is specified.
         */
        public boolean isUseLocalAndSnapshotRepos() {
            return !hasOption("no-local-and-snapshot-repos");
        }

        @Override
        public String[] toCommandLine() {
            final List<String> args = new ArrayList<>();
            Collections.addAll(args, super.toCommandLine());
            args.addAll(List.of("-t", getProjectType()));
            if (isMultiModule()) {
                args.add("-m");
            }
            getOutputDirectory().ifPresent(dir -> args.addAll(List.of("--dir", dir)));
            if (!isUseLocalAndSnapshotRepos()) {
                args.add("--no-local-and-snapshot-repos");
            }
            getProjectName().ifPresent(args::add);
            return args.toArray(String[]::new);
        }

        /**
         * Create InitializerOptions from JSON string.
         *
         * @param jsonString the JSON configuration
         * @return InitializerOptions instance
         */
        public static InitializerOptions fromJson(final String jsonString) {
            return InitializerOptions.parse(jsonToArgs(jsonString, INITIALIZER_OPTIONS));
        }

        /**
         * Builder for constructing InitializerOptions programmatically.
         */
        public static class Builder extends AbstractBuilder<Builder> {

            /**
             * Set the project type.
             *
             * @param type the project type (application, library, service)
             */
            public Builder setProjectType(final String type) {
                removeeArgsAndValues("-t");
                args.addAll(List.of("-t", type));
                return this;
            }

            /**
             * Enable multi-module project structure.
             */
            public Builder enableMultiModule() {
                return enableMultiModule(true);
            }

            /**
             * Enable or disable multi-module project structure.
             *
             * @param multiModule true to enable multi-module structure
             */
            public Builder enableMultiModule(final boolean multiModule) {
                args.remove("-m");
                if (multiModule) {
                    args.add("-m");
                }
                return this;
            }

            /**
             * Set the output directory.
             *
             * @param directory the directory to create the project in
             */
            public Builder setOutputDirectory(final String directory) {
                args.removeIf(arg -> arg.equals("--dir"));
                // Remove the value after --dir if present
                for (int i = 0; i < args.size() - 1; i++) {
                    if (args.get(i).equals("--dir")) {
                        args.remove(i + 1);
                        args.remove(i);
                        break;
                    }
                }
                args.addAll(List.of("--dir", directory));
                return this;
            }

            /**
             * Set the project name.
             *
             * @param name the project name/path
             */
            public Builder setProjectName(final String name) {
                args.add(name);
                return this;
            }

            /**
             * Enable or disable mavenLocal() and maven-snapshots repository.
             *
             * @param useLocalAndSnapshotRepos true to include these repositories (default)
             */
            public Builder setUseLocalAndSnapshotRepos(final boolean useLocalAndSnapshotRepos) {
                args.remove("--local-and-snapshot-repos");
                args.remove("--no-local-and-snapshot-repos");
                if (!useLocalAndSnapshotRepos) {
                    args.add("--no-local-and-snapshot-repos");
                }
                return this;
            }

            /**
             * Build the InitializerOptions by parsing the accumulated arguments.
             */
            public InitializerOptions build() {
                return InitializerOptions.parse(args.toArray(String[]::new));
            }
        }
    }


    // ----- DisassemblerOptions -------------------------------------------------------------------

    /**
     * Disassembler command-line options.
     */
    public static class DisassemblerOptions extends LauncherOptions {

        DisassemblerOptions(final CommandLine commandLine) {
            super(commandLine, DISASSEMBLER_OPTIONS, "disass");
        }

        /**
         * Parse command-line arguments into DisassemblerOptions.
         */
        public static DisassemblerOptions parse(final String[] args) {
            return new DisassemblerOptions(parseCommandLine(DISASSEMBLER_OPTIONS, args));
        }

        /**
         * Create a builder for programmatically constructing DisassemblerOptions.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected String buildUsageLine(final String cmdName) {
            return cmdName + " [options] <module_file>";
        }

        public Optional<File> getTarget() {
            // First trailing arg is the module file to disassemble
            final var trailing = getTrailingArgs();
            return trailing.isEmpty() ? Optional.empty() : Optional.of(new File(trailing.getFirst()));
        }

        public boolean isListFiles() {
            return hasOption("files");
        }

        public Optional<File> getFindFile() {
            return optionValue("findfile").map(File::new);
        }

        @Override
        public String[] toCommandLine() {
            final List<String> args = new ArrayList<>();
            Collections.addAll(args, super.toCommandLine());
            if (isListFiles()) {
                args.add("--files");
            }
            getFindFile().ifPresent(findFile -> args.addAll(List.of("--findfile", findFile.getPath())));
            getTarget().ifPresent(target -> args.add(target.getPath()));
            return args.toArray(String[]::new);
        }

        /**
         * Create DisassemblerOptions from JSON string.
         *
         * @param jsonString the JSON configuration
         * @return DisassemblerOptions instance
         */
        public static DisassemblerOptions fromJson(final String jsonString) {
            return DisassemblerOptions.parse(jsonToArgs(jsonString, DISASSEMBLER_OPTIONS));
        }

        /**
         * Builder for constructing DisassemblerOptions programmatically.
         * Builds a synthetic command-line array and parses it.
         */
        public static class Builder extends AbstractBuilder<Builder> {

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
            public Builder listEmbeddedFiles(final boolean list) {
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
            public Builder findEmbeddedFile(final File file) {
                args.addAll(List.of("--findfile", file.getPath()));
                return this;
            }

            /**
             * Search for a specific file in the module.
             *
             * @param file the file path as a string
             */
            @SuppressWarnings("unused")
            public Builder findEmbeddedFile(final String file) {
                return findEmbeddedFile(new File(file));
            }

            /**
             * Set the target module file to disassemble.
             *
             * @param target the module file
             */
            public Builder setTarget(final File target) {
                args.add(target.getPath());
                return this;
            }

            /**
             * Set the target module file to disassemble.
             *
             * @param target the module file path as a string
             */
            public Builder setTarget(final String target) {
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
