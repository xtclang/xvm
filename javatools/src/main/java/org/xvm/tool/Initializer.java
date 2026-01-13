package org.xvm.tool;

import java.io.File;
import java.nio.file.Path;

import org.xvm.util.Severity;

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
 * </pre>
 */
public class Initializer extends Launcher {

    /**
     * Entry point from the OS.
     */
    public static void main(String[] asArg) {
        try {
            System.exit(launch(asArg));
        } catch (LauncherException e) {
            System.exit(e.error ? 1 : 0);
        }
    }

    /**
     * Helper method for external launchers.
     */
    public static int launch(String[] asArg) throws LauncherException {
        return new Initializer(asArg, null).run();
    }

    public Initializer(String[] asArg) {
        this(asArg, null);
    }

    public Initializer(String[] asArg, Console console) {
        super(asArg, console);
    }

    @Override
    protected int process() {
        Options opts = options();

        String projectPath = opts.getProjectName();
        if (projectPath == null || projectPath.isEmpty()) {
            log(Severity.ERROR, "Project name is required. Usage: xtc init <name> [--type=application|library|service] [--multi-module]");
            return 1;
        }

        XtcProjectCreator.ProjectType type = XtcProjectCreator.ProjectType.fromString(opts.getProjectType());
        boolean multiModule = opts.isMultiModule();

        File projectDir = new File(projectPath);
        String moduleName = projectDir.getName();

        out("Creating " + (multiModule ? "multi-module " : "") + type.getName() + " project: " + moduleName);

        XtcProjectCreator creator = new XtcProjectCreator(
            Path.of(projectPath),
            type,
            multiModule
        );

        XtcProjectCreator.Result result = creator.create();

        if (result.success()) {
            out();
            out("Project '" + moduleName + "' created successfully!");
            out();
            out("Next steps:");
            out("  cd " + projectDir.getPath());
            out("  ./gradlew build");
            if (type == XtcProjectCreator.ProjectType.APPLICATION || type == XtcProjectCreator.ProjectType.SERVICE) {
                out("  ./gradlew run");
            }
            return 0;
        } else {
            log(Severity.ERROR, result.message());
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
            and build files ready for development.
            """;
    }

    @Override
    public Options options() {
        return (Options) super.options();
    }

    @Override
    protected Options instantiateOptions() {
        return new Options();
    }

    /**
     * Initializer command-line options.
     */
    public class Options extends Launcher.Options {

        public Options() {
            super();
            addOption("t", "type", Form.String, false,
                    "Project type: application (default), library, or service");
            addOption("m", "multi-module", Form.Name, false,
                    "Create a multi-module project structure");
            addOption(Trailing, null, Form.String, false,
                    "Project name");
        }

        public String getProjectName() {
            Object value = values().get(Trailing);
            return value instanceof String s ? s : null;
        }

        public String getProjectType() {
            Object value = values().get("t");
            return value instanceof String s ? s : null;
        }

        public boolean isMultiModule() {
            return specified("m");
        }
    }
}
