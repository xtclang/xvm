package org.xvm.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * <p><b>DISABLED BY DEFAULT</b>: These tests spawn separate Gradle processes for each
 * generated project, which downloads dependencies, the Gradle wrapper, and configures
 * the project from scratch. With an empty cache, this adds several minutes to the build.
 *
 * <p>Requirements:
 * <ul>
 *   <li>The XTC plugin must be available in mavenLocal() - run {@code ./gradlew publishLocal} first</li>
 *   <li>Network access for Gradle wrapper download (first run only)</li>
 * </ul>
 *
 * <p>Enable these tests by setting environment variable: {@code RUN_INTEGRATION_TESTS=true}
 *
 * // TODO: Consider running these tests in CI on a scheduled basis (nightly) rather than on every build
 * // TODO: Consider using TestKit with a shared Gradle home to speed up repeated runs
 * // TODO: Consider caching the Gradle wrapper and dependencies in CI
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
    disabledReason = "Spawns multiple Gradle builds which are slow with empty cache")
public class XtcProjectCreatorIntegrationTest {

    private static final int GRADLE_TIMEOUT_SECONDS = 120;

    @TempDir
    Path tempDir;

    @Test
    public void testApplicationProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("testapp");

        var creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, false);
        var result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        var gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for application project.\nOutput:\n" + gradleResult.output());
    }

    @Test
    public void testLibraryProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("testlib");

        var creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.LIBRARY, false);
        var result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        var gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for library project.\nOutput:\n" + gradleResult.output());
    }

    @Test
    public void testServiceProjectConfigures() throws Exception {
        Path projectPath = tempDir.resolve("testsvc");

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
        Path projectPath = tempDir.resolve("testmulti");

        var creator = createProjectCreator(projectPath, XtcProjectCreator.ProjectType.APPLICATION, true);
        var result = creator.create();
        assertTrue(result.success(), "Project creation should succeed: " + result.message());

        addMavenLocalToProject(projectPath);

        var gradleResult = runGradle(projectPath, "tasks");
        assertEquals(0, gradleResult.exitCode(),
            "Gradle tasks should succeed for multi-module project.\nOutput:\n" + gradleResult.output());
    }

    /**
     * Create a project creator using the current build's XTC version.
     * This ensures tests use the locally-published plugin version.
     */
    private XtcProjectCreator createProjectCreator(Path projectPath, XtcProjectCreator.ProjectType type, boolean multiModule) {
        // Use the actual XDK version from this build so it matches what was published to mavenLocal
        return new XtcProjectCreator(projectPath, type, multiModule, BuildInfo.getXdkVersion(), null);
    }

    /**
     * Add mavenLocal() to the project's settings.gradle.kts so it can find
     * the locally-built XTC plugin during testing.
     */
    private void addMavenLocalToProject(Path projectPath) throws IOException {
        var settingsFile = projectPath.resolve("settings.gradle.kts");
        var content = Files.readString(settingsFile);

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
    private GradleResult runGradle(Path projectPath, @SuppressWarnings("SameParameterValue") String... tasks) throws IOException, InterruptedException {
        String gradlewName = System.getProperty("os.name").toLowerCase().contains("win")
            ? "gradlew.bat"
            : "gradlew";

        String[] command = Stream.concat(
            Stream.of(projectPath.resolve(gradlewName).toString(), "--no-daemon"),
            Arrays.stream(tasks)
        ).toArray(String[]::new);

        var pb = new ProcessBuilder(command);
        pb.directory(projectPath.toFile());
        pb.redirectErrorStream(true);

        var process = pb.start();

        var output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
