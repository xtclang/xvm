package org.xvm.tool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Initializer} command ({@code xtc init}).
 *
 * Tests all flags and options:
 * <ul>
 *   <li>{@code <project-name>} - required trailing argument</li>
 *   <li>{@code -t, --type} - project type (application, library, service)</li>
 *   <li>{@code -m, --multi-module} - create multi-module project</li>
 * </ul>
 */
public class InitializerTest {

    private Path tempDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a temp directory for test projects
        tempDir = Files.createTempDirectory("xtc-init-test");

        // Save and redirect output streams
        originalOut = System.out;
        originalErr = System.err;
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Restore output streams
        System.setOut(originalOut);
        System.setErr(originalErr);

        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    /**
     * Get the absolute path for a project in the temp directory.
     */
    private String projectPath(String name) {
        return tempDir.resolve(name).toString();
    }

    // -------------------------------------------------------------------------
    // Default Application Project Tests
    // -------------------------------------------------------------------------

    @Test
    public void testCreateDefaultApplicationProject() throws Exception {
        String projectName = "myapp";
        int result = runInitializer(projectPath(projectName));

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertApplicationModule(projectName);
    }

    @Test
    public void testCreateApplicationProjectExplicitType() throws Exception {
        String projectName = "myapp2";
        int result = runInitializer(projectPath(projectName), "--type=application");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertApplicationModule(projectName);
    }

    @Test
    public void testCreateApplicationProjectShortFlag() throws Exception {
        String projectName = "myapp3";
        int result = runInitializer(projectPath(projectName), "-t", "application");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertApplicationModule(projectName);
    }

    // -------------------------------------------------------------------------
    // Library Project Tests
    // -------------------------------------------------------------------------

    @Test
    public void testCreateLibraryProject() throws Exception {
        String projectName = "mylib";
        int result = runInitializer(projectPath(projectName), "--type=library");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertLibraryModule(projectName);
    }

    @Test
    public void testCreateLibraryProjectShortType() throws Exception {
        String projectName = "mylib2";
        int result = runInitializer(projectPath(projectName), "--type=lib");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertLibraryModule(projectName);
    }

    @Test
    public void testCreateLibraryProjectShortFlag() throws Exception {
        String projectName = "mylib3";
        int result = runInitializer(projectPath(projectName), "-t", "library");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertLibraryModule(projectName);
    }

    // -------------------------------------------------------------------------
    // Service Project Tests
    // -------------------------------------------------------------------------

    @Test
    public void testCreateServiceProject() throws Exception {
        String projectName = "mysvc";
        int result = runInitializer(projectPath(projectName), "--type=service");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertServiceModule(projectName);
    }

    @Test
    public void testCreateServiceProjectShortType() throws Exception {
        String projectName = "mysvc2";
        int result = runInitializer(projectPath(projectName), "--type=svc");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertServiceModule(projectName);
    }

    @Test
    public void testCreateServiceProjectShortFlag() throws Exception {
        String projectName = "mysvc3";
        int result = runInitializer(projectPath(projectName), "-t", "service");

        assertEquals(0, result, "Initializer should succeed");
        assertProjectStructure(projectName, false);
        assertServiceModule(projectName);
    }

    // -------------------------------------------------------------------------
    // Multi-Module Project Tests
    // -------------------------------------------------------------------------

    @Test
    public void testCreateMultiModuleProject() throws Exception {
        String projectName = "multiproj";
        int result = runInitializer(projectPath(projectName), "--multi-module");

        assertEquals(0, result, "Initializer should succeed");
        assertMultiModuleStructure(projectName);
    }

    @Test
    public void testCreateMultiModuleProjectShortFlag() throws Exception {
        String projectName = "multiproj2";
        int result = runInitializer(projectPath(projectName), "-m");

        assertEquals(0, result, "Initializer should succeed");
        assertMultiModuleStructure(projectName);
    }

    @Test
    public void testCreateMultiModuleLibraryProject() throws Exception {
        String projectName = "multilib";
        int result = runInitializer(projectPath(projectName), "--type=library", "--multi-module");

        assertEquals(0, result, "Initializer should succeed");
        assertMultiModuleStructure(projectName);
    }

    @Test
    public void testCreateMultiModuleServiceProject() throws Exception {
        String projectName = "multisvc";
        int result = runInitializer(projectPath(projectName), "-t", "service", "-m");

        assertEquals(0, result, "Initializer should succeed");
        assertMultiModuleStructure(projectName);
    }

    // -------------------------------------------------------------------------
    // Error Case Tests
    // -------------------------------------------------------------------------

    @Test
    public void testMissingProjectName() {
        int result = runInitializer();

        assertEquals(1, result, "Initializer should fail without project name");
    }

    @Test
    public void testDirectoryAlreadyExists() throws Exception {
        String projectName = "existing";

        // Create the directory first
        Files.createDirectory(tempDir.resolve(projectName));

        int result = runInitializer(projectPath(projectName));

        assertEquals(1, result, "Initializer should fail when directory exists");
        String output = outContent.toString() + errContent.toString();
        assertTrue(output.contains("already exists"),
                   "Error message should mention directory exists");
    }

    @Test
    public void testHelpFlag() {
        // Help should cause the launcher to abort (not error)
        try {
            runInitializer("--help");
        } catch (Launcher.LauncherException e) {
            assertFalse(e.error, "Help should not be an error");
        }
    }

    // -------------------------------------------------------------------------
    // Build File Content Tests
    // -------------------------------------------------------------------------

    @Test
    public void testBuildGradleContent() throws Exception {
        String projectName = "buildtest";
        runInitializer(projectPath(projectName));

        Path buildGradle = tempDir.resolve(projectName).resolve("build.gradle.kts");
        assertTrue(Files.exists(buildGradle), "build.gradle.kts should exist");

        String content = Files.readString(buildGradle);
        assertTrue(content.contains("org.xtclang.xtc"), "Should use XTC plugin");
        assertTrue(content.contains("xtcRun"), "Application should have xtcRun config");
    }

    @Test
    public void testLibraryBuildGradleContent() throws Exception {
        String projectName = "libbuildtest";
        runInitializer(projectPath(projectName), "--type=library");

        Path buildGradle = tempDir.resolve(projectName).resolve("build.gradle.kts");
        assertTrue(Files.exists(buildGradle), "build.gradle.kts should exist");

        String content = Files.readString(buildGradle);
        assertTrue(content.contains("org.xtclang.xtc"), "Should use XTC plugin");
        assertFalse(content.contains("xtcRun"), "Library should NOT have xtcRun config");
    }

    @Test
    public void testSettingsGradleContent() throws Exception {
        String projectName = "settingstest";
        runInitializer(projectPath(projectName));

        Path settingsGradle = tempDir.resolve(projectName).resolve("settings.gradle.kts");
        assertTrue(Files.exists(settingsGradle), "settings.gradle.kts should exist");

        String content = Files.readString(settingsGradle);
        assertTrue(content.contains("rootProject.name"), "Should set project name");
        assertTrue(content.contains(projectName), "Should contain project name");
        assertTrue(content.contains("pluginManagement"), "Should have pluginManagement");
    }

    @Test
    public void testMultiModuleSettingsContent() throws Exception {
        String projectName = "multisettings";
        runInitializer(projectPath(projectName), "--multi-module");

        Path settingsGradle = tempDir.resolve(projectName).resolve("settings.gradle.kts");
        String content = Files.readString(settingsGradle);

        assertTrue(content.contains("include(\"app\")"), "Should include app subproject");
        assertTrue(content.contains("include(\"lib\")"), "Should include lib subproject");
    }

    @Test
    public void testGitignoreContent() throws Exception {
        String projectName = "gitignoretest";
        runInitializer(projectPath(projectName));

        Path gitignore = tempDir.resolve(projectName).resolve(".gitignore");
        assertTrue(Files.exists(gitignore), ".gitignore should exist");

        String content = Files.readString(gitignore);
        assertTrue(content.contains(".gradle/"), "Should ignore .gradle");
        assertTrue(content.contains("build/"), "Should ignore build");
        assertTrue(content.contains(".idea/"), "Should ignore .idea");
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Run the initializer with the given arguments.
     */
    private int runInitializer(String... args) {
        try {
            // Build full args array: "init" + provided args
            String[] fullArgs = new String[args.length + 1];
            fullArgs[0] = "init";
            System.arraycopy(args, 0, fullArgs, 1, args.length);

            return Launcher.launch(fullArgs);
        } catch (Launcher.LauncherException e) {
            return e.error ? 1 : 0;
        }
    }

    /**
     * Assert that basic project structure exists.
     */
    private void assertProjectStructure(String projectName, boolean multiModule) throws Exception {
        Path projectDir = tempDir.resolve(projectName);

        assertTrue(Files.isDirectory(projectDir), "Project directory should exist");
        assertTrue(Files.exists(projectDir.resolve("build.gradle.kts")), "build.gradle.kts should exist");
        assertTrue(Files.exists(projectDir.resolve("settings.gradle.kts")), "settings.gradle.kts should exist");
        assertTrue(Files.exists(projectDir.resolve("gradle.properties")), "gradle.properties should exist");
        assertTrue(Files.exists(projectDir.resolve(".gitignore")), ".gitignore should exist");
        assertTrue(Files.exists(projectDir.resolve("gradlew")), "gradlew should exist");
        assertTrue(Files.exists(projectDir.resolve("gradlew.bat")), "gradlew.bat should exist");

        if (!multiModule) {
            assertTrue(Files.isDirectory(projectDir.resolve("src/main/x")),
                       "src/main/x directory should exist");
            assertTrue(Files.exists(projectDir.resolve("src/main/x/" + projectName + ".x")),
                       "Main module source should exist");
        }
    }

    /**
     * Assert multi-module project structure.
     */
    private void assertMultiModuleStructure(String projectName) throws Exception {
        Path projectDir = tempDir.resolve(projectName);

        assertTrue(Files.isDirectory(projectDir), "Project directory should exist");
        assertTrue(Files.exists(projectDir.resolve("build.gradle.kts")), "Root build.gradle.kts should exist");
        assertTrue(Files.exists(projectDir.resolve("settings.gradle.kts")), "settings.gradle.kts should exist");

        // App subproject
        assertTrue(Files.isDirectory(projectDir.resolve("app")), "app subproject should exist");
        assertTrue(Files.exists(projectDir.resolve("app/build.gradle.kts")), "app/build.gradle.kts should exist");
        assertTrue(Files.exists(projectDir.resolve("app/src/main/x/app.x")), "app source should exist");

        // Lib subproject
        assertTrue(Files.isDirectory(projectDir.resolve("lib")), "lib subproject should exist");
        assertTrue(Files.exists(projectDir.resolve("lib/build.gradle.kts")), "lib/build.gradle.kts should exist");
        assertTrue(Files.exists(projectDir.resolve("lib/src/main/x/lib.x")), "lib source should exist");
    }

    /**
     * Assert application module content.
     */
    private void assertApplicationModule(String projectName) throws Exception {
        Path moduleFile = tempDir.resolve(projectName).resolve("src/main/x/" + projectName + ".x");
        String content = Files.readString(moduleFile);

        assertTrue(content.contains("module " + projectName), "Should declare module");
        assertTrue(content.contains("void run()"), "Application should have run method");
        assertTrue(content.contains("@Inject Console"), "Should inject Console");
    }

    /**
     * Assert library module content.
     */
    private void assertLibraryModule(String projectName) throws Exception {
        Path moduleFile = tempDir.resolve(projectName).resolve("src/main/x/" + projectName + ".x");
        String content = Files.readString(moduleFile);

        assertTrue(content.contains("module " + projectName), "Should declare module");
        assertTrue(content.contains("service Greeter"), "Library should have Greeter service");
        assertFalse(content.contains("void run()"), "Library should NOT have run method");
    }

    /**
     * Assert service module content.
     */
    private void assertServiceModule(String projectName) throws Exception {
        Path moduleFile = tempDir.resolve(projectName).resolve("src/main/x/" + projectName + ".x");
        String content = Files.readString(moduleFile);

        assertTrue(content.contains("module " + projectName), "Should declare module");
        assertTrue(content.contains("void run()"), "Service should have run method");
        assertTrue(content.contains("service"), "Service source should mention 'service'");
    }
}
