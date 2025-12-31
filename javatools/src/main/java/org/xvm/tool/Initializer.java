package org.xvm.tool;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;

import org.xvm.util.Severity;

/**
 * The "xtc init" command for initializing new XTC projects.
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
     * Project types supported by the initializer.
     */
    public enum ProjectType {
        APPLICATION("application"),
        LIBRARY("library"),
        SERVICE("service");

        private final String name;

        ProjectType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static ProjectType fromString(String s) {
            if (s == null) {
                return APPLICATION;
            }
            return switch (s.toLowerCase()) {
                case "lib", "library" -> LIBRARY;
                case "svc", "service" -> SERVICE;
                default -> APPLICATION;
            };
        }
    }

    /**
     * Entry point from the OS.
     *
     * @param asArg command line arguments
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
     *
     * @param asArg command line arguments
     * @return the result of the {@link #process()} call
     * @throws LauncherException if an unrecoverable exception occurs
     */
    public static int launch(String[] asArg) throws LauncherException {
        return new Initializer(asArg, null).run();
    }

    /**
     * Initializer command constructor.
     *
     * @param asArg command line arguments
     */
    public Initializer(String[] asArg) {
        this(asArg, null);
    }

    /**
     * Initializer command constructor.
     *
     * @param asArg   command line arguments
     * @param console representation of the terminal within which this command is run
     */
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

        ProjectType type = opts.getProjectType();
        boolean multiModule = opts.isMultiModule();

        File projectDir = new File(projectPath);
        // Extract the module name from the directory name (handles both relative and absolute paths)
        String moduleName = projectDir.getName();

        if (moduleName.isEmpty()) {
            log(Severity.ERROR, "Invalid project path: " + projectPath);
            return 1;
        }

        if (projectDir.exists()) {
            log(Severity.ERROR, "Directory '" + projectPath + "' already exists");
            return 1;
        }

        out("Creating " + (multiModule ? "multi-module " : "") + type.getName() + " project: " + moduleName);

        try {
            if (multiModule) {
                createMultiModuleProject(projectDir, moduleName, type);
            } else {
                createSingleModuleProject(projectDir, moduleName, type);
            }
            out();
            out("Project '" + moduleName + "' created successfully!");
            out();
            out("Next steps:");
            out("  cd " + projectDir.getPath());
            out("  ./gradlew build");
            if (type == ProjectType.APPLICATION || type == ProjectType.SERVICE) {
                out("  ./gradlew run");
            }
        } catch (IOException e) {
            log(Severity.ERROR, "Failed to create project: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    /**
     * Create a single-module project.
     */
    private void createSingleModuleProject(File projectDir, String projectName, ProjectType type) throws IOException {
        Path root = projectDir.toPath();
        Files.createDirectories(root);

        // Create directory structure
        Path srcDir = root.resolve("src/main/x");
        Files.createDirectories(srcDir);

        // Create build.gradle.kts
        writeFile(root.resolve("build.gradle.kts"), generateBuildGradle(projectName, type, false));

        // Create settings.gradle.kts
        writeFile(root.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, false));

        // Create gradle.properties
        writeFile(root.resolve("gradle.properties"), generateGradleProperties());

        // Create main module source file
        writeFile(srcDir.resolve(projectName + ".x"), generateModuleSource(projectName, type));

        // Create .gitignore
        writeFile(root.resolve(".gitignore"), generateGitignore());

        // Copy gradle wrapper (simplified - in real impl would copy from XDK)
        createGradleWrapper(root);
    }

    /**
     * Create a multi-module project.
     */
    private void createMultiModuleProject(File projectDir, String projectName, ProjectType type) throws IOException {
        Path root = projectDir.toPath();
        Files.createDirectories(root);

        // Create root build.gradle.kts
        writeFile(root.resolve("build.gradle.kts"), generateRootBuildGradle());

        // Create settings.gradle.kts
        writeFile(root.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, true));

        // Create gradle.properties
        writeFile(root.resolve("gradle.properties"), generateGradleProperties());

        // Create .gitignore
        writeFile(root.resolve(".gitignore"), generateGitignore());

        // Create app subproject
        Path appDir = root.resolve("app");
        Files.createDirectories(appDir.resolve("src/main/x"));
        writeFile(appDir.resolve("build.gradle.kts"), generateBuildGradle("app", type, true));
        writeFile(appDir.resolve("src/main/x/app.x"), generateModuleSource("app", type));

        // Create lib subproject
        Path libDir = root.resolve("lib");
        Files.createDirectories(libDir.resolve("src/main/x"));
        writeFile(libDir.resolve("build.gradle.kts"), generateBuildGradle("lib", ProjectType.LIBRARY, true));
        writeFile(libDir.resolve("src/main/x/lib.x"), generateModuleSource("lib", ProjectType.LIBRARY));

        // Create gradle wrapper
        createGradleWrapper(root);
    }

    private String generateBuildGradle(String moduleName, ProjectType type, boolean isSubproject) {
        StringBuilder sb = new StringBuilder();

        sb.append("plugins {\n");
        sb.append("    id(\"org.xtclang.xtc\")\n");
        sb.append("}\n\n");

        if (type == ProjectType.APPLICATION || type == ProjectType.SERVICE) {
            sb.append("xtcRun {\n");
            sb.append("    module = \"").append(moduleName).append("\"\n");
            sb.append("}\n");
        }

        return sb.toString();
    }

    private String generateRootBuildGradle() {
        return """
            plugins {
                id("org.xtclang.xtc") apply false
            }
            """;
    }

    private String generateSettingsGradle(String projectName, boolean multiModule) {
        StringBuilder sb = new StringBuilder();

        sb.append("rootProject.name = \"").append(projectName).append("\"\n\n");

        sb.append("pluginManagement {\n");
        sb.append("    repositories {\n");
        sb.append("        mavenCentral()\n");
        sb.append("        gradlePluginPortal()\n");
        sb.append("    }\n");
        sb.append("}\n\n");

        sb.append("dependencyResolutionManagement {\n");
        sb.append("    repositories {\n");
        sb.append("        mavenCentral()\n");
        sb.append("    }\n");
        sb.append("}\n");

        if (multiModule) {
            sb.append("\ninclude(\"app\")\n");
            sb.append("include(\"lib\")\n");
        }

        return sb.toString();
    }

    private String generateGradleProperties() {
        return """
            # XTC project properties
            org.gradle.parallel=true
            org.gradle.caching=true
            """;
    }

    private String generateModuleSource(String moduleName, ProjectType type) {
        return switch (type) {
            case APPLICATION -> generateApplicationSource(moduleName);
            case LIBRARY -> generateLibrarySource(moduleName);
            case SERVICE -> generateServiceSource(moduleName);
        };
    }

    private String generateApplicationSource(String moduleName) {
        return """
            /**
             * %s application module.
             */
            module %s {
                void run() {
                    @Inject Console console;
                    console.print("Hello from %s!");
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateLibrarySource(String moduleName) {
        return """
            /**
             * %s library module.
             */
            module %s {
                /**
                 * A greeting service.
                 */
                service Greeter {
                    String greet(String name) {
                        return $"Hello, {name}!";
                    }
                }
            }
            """.formatted(moduleName, moduleName);
    }

    private String generateServiceSource(String moduleName) {
        return """
            /**
             * %s service module.
             */
            module %s {
                void run() {
                    @Inject Console console;
                    console.print("%s service starting...");

                    // TODO: Add your service logic here
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateGitignore() {
        return """
            # Gradle
            .gradle/
            build/

            # IDE
            .idea/
            *.iml
            .vscode/

            # XTC
            *.xtc

            # OS
            .DS_Store
            Thumbs.db
            """;
    }

    private void createGradleWrapper(Path root) throws IOException {
        // Create gradle wrapper directory
        Path wrapperDir = root.resolve("gradle/wrapper");
        Files.createDirectories(wrapperDir);

        // Create minimal gradlew script (in production, copy from XDK)
        writeFile(root.resolve("gradlew"), """
            #!/bin/sh
            # Gradle wrapper stub - replace with actual wrapper from XDK
            echo "Please install the Gradle wrapper from the XDK or run: gradle wrapper"
            exit 1
            """);

        // Make gradlew executable
        root.resolve("gradlew").toFile().setExecutable(true);

        writeFile(root.resolve("gradlew.bat"), """
            @echo off
            rem Gradle wrapper stub - replace with actual wrapper from XDK
            echo Please install the Gradle wrapper from the XDK or run: gradle wrapper
            exit /b 1
            """);
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    // ----- text output and error handling --------------------------------------------------------

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

    // ----- options -------------------------------------------------------------------------------

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

        /**
         * @return the project name from the trailing argument
         */
        public String getProjectName() {
            Object value = values().get(Trailing);
            if (value instanceof String s) {
                return s;
            }
            return null;
        }

        /**
         * @return the project type (defaults to APPLICATION)
         */
        public ProjectType getProjectType() {
            Object value = values().get("t");
            if (value instanceof String s) {
                return ProjectType.fromString(s);
            }
            return ProjectType.APPLICATION;
        }

        /**
         * @return true if multi-module project should be created
         */
        public boolean isMultiModule() {
            return specified("m");
        }
    }
}
