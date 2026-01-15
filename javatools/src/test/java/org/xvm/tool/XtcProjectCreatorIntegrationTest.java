package org.xvm.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.BuildInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link XtcProjectCreator} that verify generated projects
 * are valid Gradle projects that can be configured successfully.
 *
 * <p>These tests create actual projects and run {@code ./gradlew tasks} to verify
 * the build files are syntactically correct and the project structure is valid.
 *
 * <p>Requirements:
 * <ul>
 *   <li>The XTC plugin must be available in mavenLocal() - run {@code ./gradlew publishLocal} first</li>
 *   <li>Network access for Gradle wrapper download (first run only)</li>
 * </ul>
 *
 * <p>Skip these tests by setting environment variable: {@code SKIP_INTEGRATION_TESTS=true}
 */
@DisabledIfEnvironmentVariable(named = "SKIP_INTEGRATION_TESTS", matches = "true")
public class XtcProjectCreatorIntegrationTest {

    private static final int GRADLE_TIMEOUT_SECONDS = 120;

    @TempDir
    Path tempDir;

    @Test
    public void testApplicationProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("test-app");

        XtcProjectCreator creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, false);
        XtcProjectCreator.Result result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        GradleResult gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for application project.\nOutput:\n" + gradleResult.output());
    }

    @Test
    public void testLibraryProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("test-lib");

        XtcProjectCreator creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.LIBRARY, false);
        XtcProjectCreator.Result result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        GradleResult gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for library project.\nOutput:\n" + gradleResult.output());
    }

    @Test
    public void testServiceProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("test-svc");

        XtcProjectCreator creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.SERVICE, false);
        XtcProjectCreator.Result result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        GradleResult gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for service project.\nOutput:\n" + gradleResult.output());
    }

    @Test
    public void testMultiModuleProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("test-multi");

        XtcProjectCreator creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, true);
        XtcProjectCreator.Result result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        GradleResult gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for multi-module project.\nOutput:\n" + gradleResult.output());
    }

    /**
     * Create a project creator using the current build's XTC version.
     * This ensures tests use the locally-published plugin version.
     */
    private XtcProjectCreator createProjectCreator(Path projectPath, XtcProjectCreator.ProjectType type, boolean multiModule) {
        // Use the actual XDK version from this build so it matches what was published to mavenLocal
        String xtcVersion = BuildInfo.getXdkVersion();
        return new XtcProjectCreator(projectPath, type, multiModule, xtcVersion, null);
    }

    /**
     * Add mavenLocal() to the project's settings.gradle.kts so it can find
     * the locally-built XTC plugin during testing.
     */
    private void addMavenLocalToProject(Path projectPath) throws IOException {
        Path settingsFile = projectPath.resolve("settings.gradle.kts");
        String content = Files.readString(settingsFile);

        // Add mavenLocal() as first repository in pluginManagement
        String modified = content.replace(
            "pluginManagement {\n    repositories {",
            "pluginManagement {\n    repositories {\n        mavenLocal()"
        );

        // Also add to dependencyResolutionManagement if present
        modified = modified.replace(
            "dependencyResolutionManagement {\n    repositories {",
            "dependencyResolutionManagement {\n    repositories {\n        mavenLocal()"
        );

        Files.writeString(settingsFile, modified);
    }

    /**
     * Run Gradle in the specified project directory.
     */
    private GradleResult runGradle(Path projectPath, String... tasks) throws IOException, InterruptedException {
        String gradlewName = System.getProperty("os.name").toLowerCase().contains("win")
            ? "gradlew.bat"
            : "gradlew";

        String[] command = new String[tasks.length + 2];
        command[0] = projectPath.resolve(gradlewName).toString();
        command[1] = "--no-daemon";
        System.arraycopy(tasks, 0, command, 2, tasks.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(GRADLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new GradleResult(-1, "Gradle timed out after " + GRADLE_TIMEOUT_SECONDS + " seconds\n" + output);
        }

        return new GradleResult(process.exitValue(), output.toString());
    }

    private record GradleResult(int exitCode, String output) {}
}
