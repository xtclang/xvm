package org.xvm.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Core project creation logic for XTC projects.
 *
 * <p><b>IMPORTANT:</b> This class MUST remain standalone with no dependencies on other
 * javatools classes (LauncherOptions, BuildInfo, etc.). The reason is that this file
 * is copied verbatim to the IntelliJ plugin during build and compiled separately with
 * Java 21 (IntelliJ's runtime requirement). Any dependencies on javatools classes would
 * break the plugin build since those classes are not available in that context.
 *
 * <p>The CLI (via {@code Initializer}) extracts values from {@code LauncherOptions.InitializerOptions}
 * and passes them to the constructors here. The IDE plugin calls the constructors directly.
 */
public class XtcProjectCreator {

    /**
     * Default XTC plugin version to use if none specified.
     */
    public static final String DEFAULT_XTC_VERSION = "0.4.4";

    /**
     * Default Gradle version for generated projects.
     * Should match the version used by the XVM repository.
     */
    public static final String DEFAULT_GRADLE_VERSION = "9.2.1";

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
    private final String xtcVersion;
    private final String gradleVersion;
    private final boolean useLocalAndSnapshotRepos;

    /**
     * Create a project creator with default versions.
     */
    public XtcProjectCreator(@NotNull Path projectPath, @NotNull ProjectType type, boolean multiModule) {
        this(projectPath, type, multiModule, DEFAULT_XTC_VERSION, DEFAULT_GRADLE_VERSION, true);
    }

    /**
     * Create a project creator with specified versions.
     *
     * @param projectPath   path where the project will be created
     * @param type          the type of project to create
     * @param multiModule   whether to create a multi-module project
     * @param xtcVersion    XTC plugin version to use, null for default
     * @param gradleVersion Gradle version for the wrapper, null for default
     */
    public XtcProjectCreator(@NotNull Path projectPath,
                             @NotNull ProjectType type,
                             boolean multiModule,
                             @Nullable String xtcVersion,
                             @Nullable String gradleVersion) {
        this(projectPath, type, multiModule, xtcVersion, gradleVersion, true);
    }

    /**
     * Create a project creator with all options (legacy constructor for IDE plugin).
     *
     * @param projectPath              path where the project will be created
     * @param type                     the type of project to create
     * @param multiModule              whether to create a multi-module project
     * @param xtcVersion               XTC plugin version to use
     * @param gradleVersion            Gradle version for the wrapper
     * @param useLocalAndSnapshotRepos whether to include mavenLocal() and maven-snapshots repository
     */
    public XtcProjectCreator(Path projectPath, ProjectType type, boolean multiModule,
                             String xtcVersion, String gradleVersion, boolean useLocalAndSnapshotRepos) {
        this.projectPath = projectPath;
        this.type = type;
        this.multiModule = multiModule;
        this.xtcVersion = xtcVersion != null ? xtcVersion : DEFAULT_XTC_VERSION;
        this.gradleVersion = gradleVersion != null ? gradleVersion : DEFAULT_GRADLE_VERSION;
        this.useLocalAndSnapshotRepos = useLocalAndSnapshotRepos;
    }

    /**
     * Create the project.
     *
     * @return result indicating success or failure with a message
     */
    public Result create() {
        String moduleName = projectPath.getFileName().toString().trim();

        // Remove trailing dots (IntelliJ quirk)
        while (moduleName.endsWith(".")) {
            moduleName = moduleName.substring(0, moduleName.length() - 1);
        }

        if (moduleName.isEmpty()) {
            return Result.error("Invalid project path: " + projectPath);
        }

        // Validate module name for XTC compliance
        String validationError = validateModuleName(moduleName);
        if (validationError != null) {
            return Result.error(validationError);
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

    /**
     * Validate that a module name is XTC compliant.
     *
     * @return error message if invalid, null if valid
     */
    public static String validateModuleName(String name) {
        if (name == null || name.isEmpty()) {
            return "Module name cannot be empty";
        }

        // Must start with letter or underscore
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return "Module name '" + name + "' must start with a letter or underscore";
        }

        // Must contain only letters, digits, and underscores
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return "Module name '" + name + "' contains invalid character '" + c + "'. " +
                       "Use only letters, digits, and underscores (no hyphens or spaces)";
            }
        }

        return null; // Valid
    }

    private void createSingleModuleProject(String projectName) throws IOException {
        Files.createDirectories(projectPath);

        Path srcDir = projectPath.resolve("src/main/x");
        Files.createDirectories(srcDir);

        // Create test source set
        Path testDir = projectPath.resolve("src/test/x");
        Files.createDirectories(testDir);

        // Create version catalog
        createVersionCatalog();

        writeFile(projectPath.resolve("build.gradle.kts"), generateBuildGradle(projectName, type, false));
        writeFile(projectPath.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, false));
        writeFile(projectPath.resolve("gradle.properties"), generateGradleProperties(projectName));
        writeFile(srcDir.resolve(projectName + ".x"), generateModuleSource(projectName, type));
        writeFile(testDir.resolve(projectName + "Test.x"), generateTestSource(projectName, type));
        writeFile(projectPath.resolve(".gitignore"), generateGitignore());
        createGradleWrapper();
    }

    private void createMultiModuleProject(String projectName) throws IOException {
        Files.createDirectories(projectPath);

        // Create version catalog
        createVersionCatalog();

        // No root build.gradle.kts needed - subprojects are self-contained
        // settings.gradle.kts defines the project structure
        writeFile(projectPath.resolve("settings.gradle.kts"), generateSettingsGradle(projectName, true));
        writeFile(projectPath.resolve("gradle.properties"), generateGradleProperties(projectName));
        writeFile(projectPath.resolve(".gitignore"), generateGitignore());

        // Create app subproject (depends on lib)
        Path appDir = projectPath.resolve("app");
        Files.createDirectories(appDir.resolve("src/main/x"));
        Files.createDirectories(appDir.resolve("src/test/x"));
        writeFile(appDir.resolve("build.gradle.kts"), generateBuildGradle("app", type, true, true));
        writeFile(appDir.resolve("src/main/x/app.x"), generateAppWithLibImport());
        writeFile(appDir.resolve("src/test/x/appTest.x"), generateAppTestSource());

        // Create lib subproject
        Path libDir = projectPath.resolve("lib");
        Files.createDirectories(libDir.resolve("src/main/x"));
        Files.createDirectories(libDir.resolve("src/test/x"));
        writeFile(libDir.resolve("build.gradle.kts"), generateBuildGradle("lib", ProjectType.LIBRARY, true, false));
        writeFile(libDir.resolve("src/main/x/lib.x"), generateModuleSource("lib", ProjectType.LIBRARY));
        writeFile(libDir.resolve("src/test/x/libTest.x"), generateLibTestSource());

        createGradleWrapper();
    }

    private void createVersionCatalog() throws IOException {
        Path gradleDir = projectPath.resolve("gradle");
        Files.createDirectories(gradleDir);
        writeFile(gradleDir.resolve("libs.versions.toml"), generateVersionCatalog());
    }

    private String generateVersionCatalog() {
        return """
            [versions]
            xtc = "%s"

            [plugins]
            xtc = { id = "org.xtclang.xtc-plugin", version.ref = "xtc" }

            [libraries]
            xdk = { module = "org.xtclang:xdk", version.ref = "xtc" }
            """.formatted(xtcVersion);
    }

    private String generateBuildGradle(String moduleName, ProjectType projectType, boolean isSubproject) {
        return generateBuildGradle(moduleName, projectType, isSubproject, false);
    }

    private String generateBuildGradle(String moduleName, ProjectType projectType, boolean isSubproject, boolean dependsOnLib) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            plugins {
                alias(libs.plugins.xtc)
            }

            dependencies {
                xdkDistribution(libs.xdk)
            """);

        if (dependsOnLib) {
            sb.append("    xtcModule(projects.lib)\n");
        }

        sb.append("}\n");

        if (projectType == ProjectType.APPLICATION || projectType == ProjectType.SERVICE) {
            sb.append("""

                // Run configuration - can be overridden from command line:
                //   ./gradlew runXtc --module=other --method=main --args=arg1,arg2
                xtcRun {
                    module {
                        moduleName = "%s"
                    }
                }
                """.formatted(moduleName));
        }

        return sb.toString();
    }

    private String generateSettingsGradle(String projectName, boolean isMultiModule) {
        String localRepos = useLocalAndSnapshotRepos ? """
                        mavenLocal()
                        maven("https://central.sonatype.com/repository/maven-snapshots/") {
                            mavenContent { snapshotsOnly() }
                        }
                """ : "";

        if (isMultiModule) {
            return """
                @file:Suppress("UnstableApiUsage")

                rootProject.name = "%s"

                pluginManagement {
                    repositories {
                %s        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                // Enable type-safe project accessors for xtcModule(projects.xxx) syntax
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

                dependencyResolutionManagement {
                    repositories {
                %s        mavenCentral()
                    }
                }

                include("app")
                include("lib")
                """.formatted(projectName, localRepos, localRepos);
        }

        return """
            @file:Suppress("UnstableApiUsage")

            rootProject.name = "%s"

            pluginManagement {
                repositories {
            %s        mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
            %s        mavenCentral()
                }
            }
            """.formatted(projectName, localRepos, localRepos);
    }

    private String generateGradleProperties(String projectName) {
        return """
            # Project configuration
            group=%s
            version=0.1.0-SNAPSHOT

            # Enable native access for Java 24+
            org.gradle.jvmargs=--enable-native-access=ALL-UNNAMED

            # Gradle performance
            org.gradle.parallel=true
            org.gradle.caching=true
            org.gradle.configuration-cache=true
            """.formatted(projectName);
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
             *
             * Run with: ./gradlew runXtc
             * Run with args: ./gradlew runXtc --args=World,XTC
             */
            module %s {
                void run(String[] args=[]) {
                    @Inject Console console;
                    if (args.empty) {
                        console.print("Hello from %s!");
                        return;
                    }
                    for (String arg : args) {
                        console.print($"Hello, {arg}!");
                    }
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateAppWithLibImport() {
        return """
            /**
             * Application module that uses the lib module.
             */
            module app {
                package lib import lib;

                void run() {
                    @Inject Console console;

                    // Use the Greeter service from lib
                    lib.Greeter greeter = new lib.Greeter();
                    console.print(greeter.greet("World"));
                }
            }
            """;
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

    // ---- Test source generation ----

    private String generateTestSource(String moduleName, ProjectType projectType) {
        return switch (projectType) {
            case APPLICATION -> generateApplicationTestSource(moduleName);
            case LIBRARY -> generateLibraryTestSource(moduleName);
            case SERVICE -> generateServiceTestSource(moduleName);
        };
    }

    private String generateApplicationTestSource(String moduleName) {
        return """
            /**
             * Unit tests for the %s application module.
             */
            module %sTest {
                package app import %s;

                /**
                 * Basic tests for the application.
                 */
                class AppTest {
                    @Test
                    void shouldExist() {
                        // Placeholder test - add real tests for your application logic
                        assert True;
                    }
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateLibraryTestSource(String moduleName) {
        return """
            /**
             * Unit tests for the %s library module.
             */
            module %sTest {
                package lib import %s;

                /**
                 * Tests for the Greeter service.
                 */
                class GreeterTest {
                    @Test
                    void shouldGreetWithName() {
                        lib.Greeter greeter = new lib.Greeter();
                        String result = greeter.greet("World");
                        assert result == "Hello, World!";
                    }

                    @Test
                    void shouldGreetWithDifferentName() {
                        lib.Greeter greeter = new lib.Greeter();
                        String result = greeter.greet("XTC");
                        assert result == "Hello, XTC!";
                    }
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateServiceTestSource(String moduleName) {
        return """
            /**
             * Unit tests for the %s service module.
             */
            module %sTest {
                package svc import %s;

                /**
                 * Basic tests for the service.
                 */
                class ServiceTest {
                    @Test
                    void shouldExist() {
                        // Placeholder test - add real tests for your service logic
                        assert True;
                    }
                }
            }
            """.formatted(moduleName, moduleName, moduleName);
    }

    private String generateAppTestSource() {
        return """
            /**
             * Unit tests for the app module.
             */
            module appTest {
                package app import app;
                package lib import lib;

                /**
                 * Tests for the application using the lib module.
                 */
                class AppTest {
                    @Test
                    void shouldUseGreeterFromLib() {
                        lib.Greeter greeter = new lib.Greeter();
                        String result = greeter.greet("Test");
                        assert result == "Hello, Test!";
                    }
                }
            }
            """;
    }

    private String generateLibTestSource() {
        return """
            /**
             * Unit tests for the lib module.
             */
            module libTest {
                package lib import lib;

                /**
                 * Tests for the Greeter service.
                 */
                class GreeterTest {
                    @Test
                    void shouldGreetWithName() {
                        lib.Greeter greeter = new lib.Greeter();
                        String result = greeter.greet("World");
                        assert result == "Hello, World!";
                    }

                    @Test
                    void shouldGreetWithDifferentName() {
                        lib.Greeter greeter = new lib.Greeter();
                        String result = greeter.greet("XTC");
                        assert result == "Hello, XTC!";
                    }
                }
            }
            """;
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

        // Try to copy from classpath resources first (bundled in javatools jar)
        if (copyWrapperFromResources()) {
            return;
        }

        // Fall back to generating bootstrap script
        writeFile(wrapperDir.resolve("gradle-wrapper.properties"), generateWrapperProperties());

        Path gradlew = projectPath.resolve("gradlew");
        writeFile(gradlew, generateGradlewScript());
        gradlew.toFile().setExecutable(true);

        writeFile(projectPath.resolve("gradlew.bat"), generateGradlewBatScript());
    }

    private boolean copyWrapperFromResources() throws IOException {
        // Check if wrapper resources are available
        if (getClass().getResource("/gradle-wrapper/gradlew") == null) {
            return false;
        }

        // Copy gradlew
        try (InputStream in = getClass().getResourceAsStream("/gradle-wrapper/gradlew")) {
            if (in != null) {
                Path dest = projectPath.resolve("gradlew");
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                dest.toFile().setExecutable(true);
            }
        }

        // Copy gradlew.bat
        try (InputStream in = getClass().getResourceAsStream("/gradle-wrapper/gradlew.bat")) {
            if (in != null) {
                Files.copy(in, projectPath.resolve("gradlew.bat"), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Copy gradle-wrapper.jar
        try (InputStream in = getClass().getResourceAsStream("/gradle-wrapper/gradle/wrapper/gradle-wrapper.jar")) {
            if (in != null) {
                Files.copy(in, projectPath.resolve("gradle/wrapper/gradle-wrapper.jar"), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Generate gradle-wrapper.properties with the specified Gradle version
        // (don't copy from resources - we need the dynamic version)
        Path wrapperDir = projectPath.resolve("gradle/wrapper");
        writeFile(wrapperDir.resolve("gradle-wrapper.properties"), generateWrapperProperties());

        return true;
    }

    private String generateWrapperProperties() {
        return """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\\://services.gradle.org/distributions/gradle-%s-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.formatted(gradleVersion);
    }

    private String generateGradlewScript() {
        // Bootstrap script that downloads gradle-wrapper.jar if missing
        return """
            #!/bin/sh
            #
            # Gradle wrapper bootstrap script for XTC projects.
            # Downloads gradle-wrapper.jar on first run if missing.
            #

            set -e

            APP_HOME="$(cd "$(dirname "$0")" && pwd)"
            WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
            WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

            # Download wrapper jar if missing
            if [ ! -f "$WRAPPER_JAR" ]; then
                echo "Gradle wrapper not found. Bootstrapping..."

                # Extract gradle version from properties
                GRADLE_VERSION=$(grep "distributionUrl" "$WRAPPER_PROPS" | sed 's/.*gradle-\\([0-9.]*\\)-.*/\\1/')

                # Download gradle distribution
                DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
                TEMP_ZIP="$APP_HOME/.gradle-bootstrap.zip"

                echo "Downloading Gradle $GRADLE_VERSION..."
                if command -v curl >/dev/null 2>&1; then
                    curl -fsSL -o "$TEMP_ZIP" "$DIST_URL"
                elif command -v wget >/dev/null 2>&1; then
                    wget -q -O "$TEMP_ZIP" "$DIST_URL"
                else
                    echo "Error: curl or wget is required to bootstrap Gradle wrapper"
                    exit 1
                fi

                # Extract wrapper jar
                echo "Extracting wrapper..."
                if command -v unzip >/dev/null 2>&1; then
                    unzip -q -j "$TEMP_ZIP" "gradle-${GRADLE_VERSION}/lib/plugins/gradle-wrapper-*.jar" -d "$APP_HOME/gradle/wrapper/"
                    # Rename to standard name
                    mv "$APP_HOME/gradle/wrapper/gradle-wrapper-"*.jar "$WRAPPER_JAR"
                else
                    echo "Error: unzip is required to bootstrap Gradle wrapper"
                    rm -f "$TEMP_ZIP"
                    exit 1
                fi

                rm -f "$TEMP_ZIP"
                echo "Gradle wrapper installed."
            fi

            # Find Java
            if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
                JAVACMD="$JAVA_HOME/bin/java"
            else
                JAVACMD="java"
            fi

            # Run Gradle
            exec "$JAVACMD" -jar "$WRAPPER_JAR" "$@"
            """;
    }

    private String generateGradlewBatScript() {
        return """
            @echo off
            rem Gradle wrapper bootstrap script for XTC projects.

            setlocal

            set APP_HOME=%~dp0
            set WRAPPER_JAR=%APP_HOME%gradle\\wrapper\\gradle-wrapper.jar
            set WRAPPER_PROPS=%APP_HOME%gradle\\wrapper\\gradle-wrapper.properties

            if exist "%WRAPPER_JAR%" goto runGradle

            echo Gradle wrapper not found. Please run './gradlew' from a Unix shell first,
            echo or download Gradle and run 'gradle wrapper' in this directory.
            exit /b 1

            :runGradle
            if defined JAVA_HOME (
                set JAVACMD=%JAVA_HOME%\\bin\\java.exe
            ) else (
                set JAVACMD=java
            )

            "%JAVACMD%" -jar "%WRAPPER_JAR%" %*
            """;
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
