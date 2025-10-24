package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test that verifies the plugin's plugin-build-info.properties integration.
 * <p>
 * The plugin reads XDK version and default JVM arguments from plugin-build-info.properties,
 * which is generated at plugin build time and included in both the build directory
 * and the published JAR.
 * <p>
 * This test verifies:
 * 1. The plugin reads the XDK version from plugin-build-info.properties successfully
 * 2. The plugin reads default JVM arguments from plugin-build-info.properties successfully
 * 3. The xtcVersion property can be overridden by users regardless of default value
 * 4. This works both in local development (TestKit) and when published
 */
public class XdkPluginBuildInfoTest {

    private static final String[] GRADLE_FLAGS = {"--stacktrace", "--info", "--configuration-cache"};

    @TempDir
    Path testProjectDir;

    private File buildFile;
    private File settingsFile;

    @BeforeEach
    void setUp() {
        buildFile = testProjectDir.resolve("build.gradle.kts").toFile();
        settingsFile = testProjectDir.resolve("settings.gradle.kts").toFile();
    }

    private static String[] buildArgs(final String taskName) {
        final var args = new String[GRADLE_FLAGS.length + 1];
        args[0] = taskName;
        System.arraycopy(GRADLE_FLAGS, 0, args, 1, GRADLE_FLAGS.length);
        return args;
    }

    /**
     * Helper method to validate JVM args match expected values from plugin-build-info.properties.
     * This validation logic is used by multiple tests to ensure consistency.
     *
     * @param actualJvmArgs the actual JVM args string to validate (comma-separated)
     * @param testContext description of where this validation is called from (for better error messages)
     * @throws IOException if plugin-build-info.properties cannot be read
     */
    private void validateJvmArgsMatchBuildInfo(final String actualJvmArgs, final String testContext) throws IOException {
        // Load the expected args from plugin-build-info.properties
        final var resourceStream = getClass().getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH);
        assertNotNull(resourceStream, "plugin-build-info.properties should exist in plugin resources");
        final var props = new Properties();
        props.load(resourceStream);
        final var expectedJvmArgs = props.getProperty("defaultJvmArgs");
        assertNotNull(expectedJvmArgs, "defaultJvmArgs should be present in plugin-build-info.properties");

        System.out.println("[test] " + testContext + ": Validating JVM args");
        System.out.println("[test]   Expected (from plugin-build-info.properties): " + expectedJvmArgs);
        System.out.println("[test]   Actual: " + actualJvmArgs);

        // -ea should always be present
        assertTrue(actualJvmArgs.contains("-ea"),
            testContext + ": JVM args should always include -ea");

        // Only check for --enable-preview if it's in the build-time config
        if (expectedJvmArgs.contains("--enable-preview")) {
            assertTrue(actualJvmArgs.contains("--enable-preview"),
                testContext + ": JVM args should include --enable-preview (found in plugin-build-info.properties)");
        }

        // Only check for --enable-native-access if it's in the build-time config
        if (expectedJvmArgs.contains("--enable-native-access=ALL-UNNAMED")) {
            assertTrue(actualJvmArgs.contains("--enable-native-access=ALL-UNNAMED"),
                testContext + ": JVM args should include --enable-native-access=ALL-UNNAMED (found in plugin-build-info.properties)");
        }
    }

    @Test
    public void testBuildInfoPropertiesFileIsGenerated() throws IOException {
        // Load the plugin-build-info.properties from the plugin's resources
        final var resourceStream = getClass().getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH);
        assertNotNull(resourceStream, "plugin-build-info.properties should exist in plugin resources");

        final var props = new Properties();
        props.load(resourceStream);

        // Verify xdk.version is present and valid
        final var xdkVersion = props.getProperty("xdk.version");
        assertNotNull(xdkVersion, "xdk.version should be present in plugin-build-info.properties");
        assertTrue(xdkVersion.matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"),
            "xdk.version should be a valid version string: " + xdkVersion);

        // Verify jdk.version is present and valid
        final var jdkVersion = props.getProperty("jdk.version");
        assertNotNull(jdkVersion, "jdk.version should be present in plugin-build-info.properties");
        assertTrue(jdkVersion.matches("\\d+"),
            "jdk.version should be a valid integer: " + jdkVersion);

        // Verify defaultJvmArgs is present and valid using shared validation logic
        final var defaultJvmArgs = props.getProperty("defaultJvmArgs");
        assertNotNull(defaultJvmArgs, "defaultJvmArgs should be present in plugin-build-info.properties");
        validateJvmArgsMatchBuildInfo(defaultJvmArgs, "testBuildInfoPropertiesFileIsGenerated");

        System.out.println("[test] ✓ plugin-build-info.properties contains correct values:");
        System.out.println("[test]   xdk.version = " + xdkVersion);
        System.out.println("[test]   jdk.version = " + jdkVersion);
        System.out.println("[test]   defaultJvmArgs = " + defaultJvmArgs);
    }

    @Test
    public void testPluginReadsXdkVersionFromBuildInfo() throws IOException {
        // Create a minimal test project that applies the plugin
        Files.writeString(settingsFile.toPath(), """
            rootProject.name = "test-xdk-version"
            """);

        Files.writeString(buildFile.toPath(), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }

            // Capture the xtcVersion at configuration time
            val xtcVersionProvider = (extensions.getByName("xtcCompile") as org.xtclang.plugin.XtcCompilerExtension).xtcVersion

            // Print the xtcVersion that the plugin resolved
            tasks.register("printXtcVersion") {
                val versionValue = xtcVersionProvider
                doLast {
                    println("XTC_VERSION_TEST_OUTPUT: isPresent=" + versionValue.isPresent)
                    if (versionValue.isPresent) {
                        println("XTC_VERSION_TEST_OUTPUT: value=" + versionValue.get())
                    } else {
                        println("XTC_VERSION_TEST_OUTPUT: value=null")
                    }
                }
            }
            """);

        // Run the task using TestKit - this loads the plugin from the JAR under test
        final var result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(buildArgs("printXtcVersion"))
            .withPluginClasspath()  // This makes the plugin load from JAR, not build directory
            .build();

        final var output = result.getOutput();
        System.out.println("[test] TestKit output:");
        System.out.println(output);

        // Parse the output to check if xtcVersion was resolved
        final var lines = output.lines()
            .filter(line -> line.contains("XTC_VERSION_TEST_OUTPUT:"))
            .toList();

        assertTrue(!lines.isEmpty(), "Should have XTC_VERSION_TEST_OUTPUT in output");

        final var isPresentLine = lines.stream()
            .filter(line -> line.contains("isPresent="))
            .findFirst()
            .orElseThrow();

        final var valueLine = lines.stream()
            .filter(line -> line.contains("value="))
            .findFirst()
            .orElseThrow();

        System.out.println("[test] " + isPresentLine.trim());
        System.out.println("[test] " + valueLine.trim());

        // The plugin should successfully read XDK version from plugin-build-info.properties
        assertTrue(isPresentLine.contains("isPresent=true"),
            "Plugin should read XDK version from plugin-build-info.properties");
        assertTrue(valueLine.contains("value=") && !valueLine.contains("value=null"),
            "Plugin should have a valid XDK version value");

        // Verify it read from plugin-build-info.properties
        assertTrue(output.contains("Read XDK version from plugin-build-info.properties"),
            "Plugin should log that it read from plugin-build-info.properties");

        System.out.println("[test] ✓ Plugin successfully read XDK version from plugin-build-info.properties");
    }

    @Test
    public void testXtcVersionCanBeOverridden() throws IOException {
        // Create a test project that overrides xtcVersion
        Files.writeString(settingsFile.toPath(), """
            rootProject.name = "test-override"
            """);

        Files.writeString(buildFile.toPath(), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }

            xtcCompile {
                xtcVersion.set("1.2.3-OVERRIDE")
            }

            // Capture the xtcVersion at configuration time
            val versionProvider = (extensions.getByName("xtcCompile") as org.xtclang.plugin.XtcCompilerExtension).xtcVersion

            tasks.register("checkOverride") {
                val version = versionProvider
                doLast {
                    val actualVersion = version.get()
                    println("OVERRIDE_TEST_OUTPUT: " + actualVersion)
                    require(actualVersion == "1.2.3-OVERRIDE") { "Expected 1.2.3-OVERRIDE but got $actualVersion" }
                }
            }
            """);

        final var result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(buildArgs("checkOverride"))
            .withPluginClasspath()
            .build();

        final var output = result.getOutput();
        assertTrue(output.contains("OVERRIDE_TEST_OUTPUT: 1.2.3-OVERRIDE"),
            "Should use overridden xtcVersion");

        System.out.println("[test] ✓ Successfully verified xtcVersion can be overridden");
    }

    @Test
    public void testPluginReadsDefaultJvmArgsFromBuildInfo() throws IOException {
        // Create a minimal test project that applies the plugin
        Files.writeString(settingsFile.toPath(), """
            rootProject.name = "test-jvm-args"
            """);

        Files.writeString(buildFile.toPath(), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }

            // Capture the jvmArgs provider at configuration time
            val runTaskProvider = tasks.named("runXtc", org.xtclang.plugin.tasks.XtcRunTask::class)
            val jvmArgsProvider = runTaskProvider.flatMap { it.jvmArgs }

            // Print the jvmArgs that the plugin configured
            tasks.register("printJvmArgs") {
                val args = jvmArgsProvider
                doLast {
                    println("JVM_ARGS_TEST_OUTPUT: " + args.get().joinToString(","))
                }
            }
            """);

        // Run the task using TestKit
        final var result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(buildArgs("printJvmArgs"))
            .withPluginClasspath()
            .build();

        final var output = result.getOutput();

        // Verify default JVM args were loaded from plugin-build-info.properties
        assertTrue(output.contains("Loaded default JVM args:"),
            "Plugin should log that it loaded default JVM args");

        // Parse the actual JVM args output
        final var argsLine = output.lines()
            .filter(line -> line.contains("JVM_ARGS_TEST_OUTPUT:"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Should have JVM_ARGS_TEST_OUTPUT in output"));

        // Extract just the args portion (after the colon)
        final var actualJvmArgs = argsLine.substring(argsLine.indexOf(':') + 1).trim();
        // Use shared validation logic to verify the args match what's in plugin-build-info.properties
        validateJvmArgsMatchBuildInfo(actualJvmArgs, "testPluginReadsDefaultJvmArgsFromBuildInfo");
        System.out.println("[test] ✓ Plugin successfully read default JVM args from plugin-build-info.properties");
    }
}
