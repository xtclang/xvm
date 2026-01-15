package org.xvm.tool;

import java.nio.file.Path;

import org.xvm.asm.BuildInfo;
import org.xvm.asm.ErrorListener;

import org.xvm.tool.LauncherOptions.InitializerOptions;

import static org.xvm.util.Severity.ERROR;

/**
 * The "xtc init" command for initializing new XTC projects.
 * This is a thin wrapper around {@link XtcProjectCreator} that provides
 * CLI argument parsing and output formatting.
 *
 * <p>Usage examples:
 * <pre>
 *   xtc init myapp                        # Create application project
 *   xtc init mylib --type=library         # Create library project
 *   xtc init myproj --multi-module        # Create multi-module project
 *   xtc init myapp --type=service         # Create service project
 *   xtc init myapp --dir=/path/to/parent  # Create in specific directory
 * </pre>
 */
public class Initializer extends Launcher<InitializerOptions> {

    /**
     * Initializer constructor for programmatic use.
     *
     * @param options     pre-configured initializer options
     * @param console     representation of the terminal within which this command is run, or null
     * @param errListener optional ErrorListener to receive errors, or null for no delegation
     */
    public Initializer(InitializerOptions options, Console console, ErrorListener errListener) {
        super(options, console, errListener);
    }

    /**
     * Entry point from the OS. Delegates to Launcher.
     *
     * @param args command line arguments
     */
    static void main(String[] args) {
        Launcher.main(insertCommand(CMD_INIT, args));
    }

    @Override
    protected void validateOptions() {
        if (options().getProjectName().isEmpty()) {
            log(ERROR, "Project name is required. Usage: xtc init <name> [--type=application|library|service] [--multi-module]");
        }
    }

    @Override
    protected int process() {
        final var opts = options();

        final var projectName = opts.getProjectName().orElseThrow(); // validated above
        final var type = XtcProjectCreator.ProjectType.fromString(opts.getProjectType());
        final var multiModule = opts.isMultiModule();

        // Compute project path: if --dir is specified, create project in that directory
        Path projectPath = opts.getOutputDirectory()
            .map(dir -> Path.of(dir).resolve(projectName))
            .orElse(Path.of(projectName));

        String moduleName = projectPath.getFileName().toString();

        out("Creating " + (multiModule ? "multi-module " : "") + type.getName() + " project: " + moduleName);

        // Use exact version from build info (including -SNAPSHOT if present)
        String xtcVersion = BuildInfo.getXdkVersion();

        XtcProjectCreator creator = new XtcProjectCreator(
            projectPath,
            type,
            multiModule,
            xtcVersion,
            null  // Use default Gradle version
        );

        XtcProjectCreator.Result result = creator.create();

        if (result.success()) {
            out();
            out("Project '" + moduleName + "' created successfully!");
            out();
            out("Next steps:");
            out("  cd " + projectPath);
            out("  ./gradlew build");
            if (type == XtcProjectCreator.ProjectType.APPLICATION || type == XtcProjectCreator.ProjectType.SERVICE) {
                out("  ./gradlew run");
            }
            return 0;
        } else {
            log(ERROR, result.message());
            return 1;
        }
    }

    @Override
    public String desc() {
        return """
            XTC Project Initializer

            Usage:
                xtc init <project-name> [options]

            Creates a new XTC project with the standard directory structure
            and build files ready for development.""";
    }
}
