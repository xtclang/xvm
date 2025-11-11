package org.xvm.tool;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;
import org.xvm.asm.BuildInfo;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for launcher version display functionality.
 * Tests that the --version output contains expected git and API information.
 */
public class LauncherVersionTest {
    @Test
    public void testVersionOutputFormat() {
        // Capture system output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Run xcc --version command
            String[] args = {"xcc", "--version"};
            Launcher.launch(args);

            String output = outputStream.toString().trim();
            assertNotNull(output, "Version output should not be null");

            // Test that output follows expected format: "xdk version X.Y.Z (major.minor) [commit] (status)"
            assertTrue(output.startsWith("xdk version"),
                      "Version output should start with 'xdk version'");

            // Test that XDK version is present
            String xdkVersion = BuildInfo.getXdkVersion();
            assertTrue(output.contains(xdkVersion),
                      "Version output should contain XDK version: " + xdkVersion);

            // Test that XVM version is present in (major.minor) format
            String xvmVersion = BuildInfo.getXvmVersionMajor() + "." + BuildInfo.getXvmVersionMinor();
            assertTrue(output.contains("(" + xvmVersion + ")"),
                      "Version output should contain XVM version: (" + xvmVersion + ")");

            // Test git information if available
            String gitCommit = BuildInfo.getGitCommit();
            String gitStatus = BuildInfo.getGitStatus();

            if (!gitCommit.isEmpty()) {
                // Expect full commit ID for better traceability
                assertTrue(output.contains("[" + gitCommit + "]"),
                          "Version output should contain full git commit: [" + gitCommit + "]");
            }

            if (!gitStatus.isEmpty()) {
                assertTrue(output.contains("(" + gitStatus + ")"),
                          "Version output should contain git status: (" + gitStatus + ")");
            }

            // Test overall format pattern
            assertTrue(output.matches("xdk version .+ \\(\\d+\\.\\d+\\).*"),
                      "Version output should match expected pattern");
        } finally {
            // Restore system output
            System.setOut(originalOut);
        }
    }

    @Test
    public void testXecVersionOutput() {
        // Test that xec --version also works
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Run xec --version command
            String[] args = {"xec", "--version"};
            Launcher.launch(args);

            String output = outputStream.toString().trim();
            assertNotNull(output, "XEC version output should not be null");
            assertTrue(output.startsWith("xdk version"),
                      "XEC version output should also start with 'xdk version'");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testXtcVersionOutput() {
        // Test that xtc --version also works
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Run xtc --version command
            String[] args = {"xtc", "--version"};
            Launcher.launch(args);

            String output = outputStream.toString().trim();
            assertNotNull(output, "XTC version output should not be null");
            assertTrue(output.startsWith("xdk version"),
                      "XTC version output should also start with 'xdk version'");
        } finally {
            System.setOut(originalOut);
        }
    }
}