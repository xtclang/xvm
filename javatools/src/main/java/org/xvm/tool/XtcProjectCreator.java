package org.xvm.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Core project creation logic for XTC projects. This class is designed to be
 * standalone with no dependencies on the Launcher infrastructure, making it
 * suitable for use both from the CLI (via Initializer) and from IDE plugins.
 *
 * <p>This class is synced to the IntelliJ plugin during build.
 */
public class XtcProjectCreator {

    /**
     * Project types supported by the creator.
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
                case "app", "application" -> APPLICATION;
                case "lib", "library" -> LIBRARY;
                case "svc", "service" -> SERVICE;
                default -> APPLICATION;
            };
        }
    }

    /**
     * Result of project creation.
     */
    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }
        public static Result error(String message) {
            return new Result(false, message);
        }
    }

    private final Path projectPath;
    private final ProjectType type;
    private final boolean multiModule;

    /**
     * Create a project creator.
     *
     * @param projectPath path where the project will be created
     * @param type        the type of project to create
     * @param multiModule whether to create a multi-module project
     */
    public XtcProjectCreator(Path projectPath, ProjectType type, boolean multiModule) {
        this.projectPath = projectPath;
        this.type = type;
        this.multiModule = multiModule;
    }

    /**
     * Create the project.
     *
     * @return result indicating success or failure with a message
     */
    public Result create() {
        String moduleName = projectPath.getFileName().toString();

        if (moduleName.isEmpty()) {
            return Result.error("Invalid project path: " + projectPath);
        }

        if (Files.exists(projectPath) && !isEmptyDirectory(projectPath)) {
            return Result.error("Directory '" + projectPath + "' already exists and is not empty");
        }

        try {
            if (multiModule) {
                createMultiModuleProject(moduleName);
            } else {
                createSingleModuleProject(moduleName);
            }
            return Result.ok("Project '" + moduleName + "' created at " + projectPath);
        } catch (IOException e) {
            return Result.error("Failed to create project: " + e.getMessage());
        }
    }

    private void createSingleModuleProject(String projectName) throws IOException {
        Files.createDirectories(projectPath);

        Path srcDir = projectPath.resolve("src/main/x");
        Files.createDirectories(srcDir);

        writeFile(projectPath.resolve("build.gradle.kts"), generateBuildGradle(projectName, type));
        writeFile(projectPath.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, false));
        writeFile(projectPath.resolve("gradle.properties"), generateGradleProperties());
        writeFile(srcDir.resolve(projectName + ".x"), generateModuleSource(projectName, type));
        writeFile(projectPath.resolve(".gitignore"), generateGitignore());
        createGradleWrapper();
    }

    private void createMultiModuleProject(String projectName) throws IOException {
        Files.createDirectories(projectPath);

        writeFile(projectPath.resolve("build.gradle.kts"), generateRootBuildGradle());
        writeFile(projectPath.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, true));
        writeFile(projectPath.resolve("gradle.properties"), generateGradleProperties());
        writeFile(projectPath.resolve(".gitignore"), generateGitignore());

        // Create app subproject
        Path appDir = projectPath.resolve("app");
        Files.createDirectories(appDir.resolve("src/main/x"));
        writeFile(appDir.resolve("build.gradle.kts"), generateBuildGradle("app", type));
        writeFile(appDir.resolve("src/main/x/app.x"), generateModuleSource("app", type));

        // Create lib subproject
        Path libDir = projectPath.resolve("lib");
        Files.createDirectories(libDir.resolve("src/main/x"));
        writeFile(libDir.resolve("build.gradle.kts"), generateBuildGradle("lib", ProjectType.LIBRARY));
        writeFile(libDir.resolve("src/main/x/lib.x"), generateModuleSource("lib", ProjectType.LIBRARY));

        createGradleWrapper();
    }

    private String generateBuildGradle(String moduleName, ProjectType projectType) {
        StringBuilder sb = new StringBuilder();
        sb.append("plugins {\n");
        sb.append("    id(\"org.xtclang.xtc\")\n");
        sb.append("}\n\n");

        if (projectType == ProjectType.APPLICATION || projectType == ProjectType.SERVICE) {
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

    private String generateSettingsGradle(String projectName, boolean isMultiModule) {
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

        if (isMultiModule) {
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

    private String generateModuleSource(String moduleName, ProjectType projectType) {
        return switch (projectType) {
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

    private void createGradleWrapper() throws IOException {
        Path wrapperDir = projectPath.resolve("gradle/wrapper");
        Files.createDirectories(wrapperDir);

        Path gradlew = projectPath.resolve("gradlew");
        writeFile(gradlew, """
            #!/bin/sh
            # Gradle wrapper stub - replace with actual wrapper from XDK
            echo "Please install the Gradle wrapper from the XDK or run: gradle wrapper"
            exit 1
            """);
        gradlew.toFile().setExecutable(true);

        writeFile(projectPath.resolve("gradlew.bat"), """
            @echo off
            rem Gradle wrapper stub - replace with actual wrapper from XDK
            echo Please install the Gradle wrapper from the XDK or run: gradle wrapper
            exit /b 1
            """);
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    private boolean isEmptyDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (var entries = Files.list(path)) {
            // Consider directory empty if it only contains IDE metadata
            return entries.allMatch(this::isIdeMetadata);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isIdeMetadata(Path path) {
        String name = path.getFileName().toString();
        return name.equals(".idea") || name.endsWith(".iml");
    }
}
