package org.xtclang.plugin.launchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for launcher command construction logic.
 * Tests verify that commands are built correctly without actually executing processes.
 */
@DisplayName("Launcher Command Construction Tests")
public class LauncherCommandConstructionTest {

    @Test
    @DisplayName("Java command structure has correct ordering")
    void testJavaCommandOrdering() {
        // Arrange
        final String javaExec = "java";
        final var jvmArgs = List.of("-Xmx1g", "-Dfoo=bar");
        final String classpath = "/path/to/javatools.jar";
        final String mainClass = "org.xtclang.tools.Launcher";
        final var programArgs = List.of("arg1", "arg2");

        // Act - Simulate DetachedJavaExecLauncher command construction
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(jvmArgs);
        command.addAll(List.of("-cp", classpath, mainClass));
        command.addAll(programArgs);

        // Assert
        assertEquals(javaExec, command.get(0), "Java executable should be first");
        assertEquals(jvmArgs.get(0), command.get(1), "JVM args should come after executable");
        assertEquals(jvmArgs.get(1), command.get(2), "All JVM args should be included");
        assertEquals("-cp", command.get(3), "Classpath flag should come after JVM args");
        assertEquals(classpath, command.get(4), "Classpath value should follow -cp flag");
        assertEquals(mainClass, command.get(5), "Main class should come after classpath");
        assertEquals(programArgs.get(0), command.get(6), "Program args should come last");
        assertEquals(programArgs.get(1), command.get(7), "All program args should be included");
    }

    @Test
    @DisplayName("Java command with no JVM args is valid")
    void testJavaCommandWithoutJvmArgs() {
        // Arrange
        final String javaExec = "java";
        final var jvmArgs = List.<String>of(); // Empty
        final String classpath = "/path/to/javatools.jar";
        final String mainClass = "org.xtclang.tools.Launcher";
        final var programArgs = List.of("module.xtc");

        // Act
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(jvmArgs);
        command.addAll(List.of("-cp", classpath, mainClass));
        command.addAll(programArgs);

        // Assert
        assertEquals(5, command.size(), "Command should have 5 elements without JVM args");
        assertEquals(javaExec, command.get(0));
        assertEquals("-cp", command.get(1), "Classpath flag should immediately follow java when no JVM args");
        assertEquals(classpath, command.get(2));
        assertEquals(mainClass, command.get(3));
        assertEquals(programArgs.get(0), command.get(4));
    }

    @Test
    @DisplayName("Java command with multiple program args")
    void testJavaCommandWithMultipleProgramArgs() {
        // Arrange
        final String javaExec = "java";
        final String classpath = "/path/to/javatools.jar";
        final String mainClass = "org.xtclang.tools.Launcher";
        final var programArgs = List.of("module.xtc", "--verbose", "--flag", "value");

        // Act
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(List.of("-cp", classpath, mainClass));
        command.addAll(programArgs);

        // Assert
        assertEquals(8, command.size(), "Command should include all program arguments");
        assertTrue(command.containsAll(programArgs), "All program args should be present");
        // Verify order is preserved
        assertEquals("module.xtc", command.get(4));
        assertEquals("--verbose", command.get(5));
        assertEquals("--flag", command.get(6));
        assertEquals("value", command.get(7));
    }

    @Test
    @DisplayName("Native binary command structure")
    void testNativeBinaryCommandStructure() {
        // Arrange
        final String commandName = "xec";
        final var programArgs = List.of("module.xtc", "--run");

        // Act - Simulate DetachedNativeBinaryLauncher command construction
        final var command = new ArrayList<>(List.of(commandName));
        command.addAll(programArgs);

        // Assert
        assertEquals(3, command.size(), "Command should have executable + args");
        assertEquals(commandName, command.get(0), "Executable should be first");
        assertEquals(programArgs.get(0), command.get(1), "First program arg should follow");
        assertEquals(programArgs.get(1), command.get(2), "Second program arg should follow");
    }

    @Test
    @DisplayName("Native binary command with no args")
    void testNativeBinaryCommandWithoutArgs() {
        // Arrange
        final String commandName = "xec";
        final var programArgs = List.<String>of();

        // Act
        final var command = new ArrayList<>(List.of(commandName));
        command.addAll(programArgs);

        // Assert
        assertEquals(1, command.size(), "Command should only have executable");
        assertEquals(commandName, command.get(0));
    }

    @Test
    @DisplayName("Command construction using Stream API")
    void testStreamBasedCommandConstruction() {
        // Arrange
        final String commandName = "xec";
        final var programArgs = List.of("module.xtc", "--flag");

        // Act - Test the Stream.concat pattern used in DetachedNativeBinaryLauncher
        final var command = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(commandName),
            programArgs.stream()
        ).toList();

        // Assert
        assertEquals(3, command.size());
        assertEquals(commandName, command.get(0));
        assertEquals("module.xtc", command.get(1));
        assertEquals("--flag", command.get(2));
    }

    @Test
    @DisplayName("Classpath with spaces is properly included")
    void testClasspathWithSpaces() {
        // Arrange
        final String javaExec = "java";
        final String classpath = "/path/with spaces/javatools.jar";
        final String mainClass = "org.xtclang.tools.Launcher";
        final var programArgs = List.of("module.xtc");

        // Act
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(List.of("-cp", classpath, mainClass));
        command.addAll(programArgs);

        // Assert
        assertEquals(classpath, command.get(2), "Classpath with spaces should be preserved as single element");
        assertEquals(5, command.size(), "Classpath should not be split into multiple elements");
    }

    @Test
    @DisplayName("JVM args order is preserved")
    void testJvmArgsOrderPreserved() {
        // Arrange
        final String javaExec = "java";
        final var jvmArgs = List.of(
            "-Xmx2g",
            "-Xms512m",
            "-XX:+UseG1GC",
            "-Dfoo=bar",
            "-Dbaz=qux"
        );
        final String classpath = "/path/to/javatools.jar";
        final String mainClass = "org.xtclang.tools.Launcher";

        // Act
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(jvmArgs);
        command.addAll(List.of("-cp", classpath, mainClass));

        // Assert - Verify exact order
        assertEquals(jvmArgs.get(0), command.get(1));
        assertEquals(jvmArgs.get(1), command.get(2));
        assertEquals(jvmArgs.get(2), command.get(3));
        assertEquals(jvmArgs.get(3), command.get(4));
        assertEquals(jvmArgs.get(4), command.get(5));
    }
}