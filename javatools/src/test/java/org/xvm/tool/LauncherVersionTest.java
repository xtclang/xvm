package org.xvm.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.xvm.asm.BuildInfo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for launcher version display functionality.
 * Tests that the --version output contains expected git and API information.
 */
public class LauncherVersionTest {
    private static final Pattern PATTERN = Pattern.compile("xdk version .+ \\(\\d+\\.\\d+\\).*");

    /**
     * Test console that captures all output lines.
     */
    private static final class CaptureConsole implements Console {
        private final List<String> lines = new ArrayList<>();

        @Override
        public String out(final Object o) {
            if (o != null) {
                lines.add(o.toString());
            }
            return "";
        }

        @SuppressWarnings("unused")
        public List<String> getLines() {
            return lines;
        }

        public String getAllOutput() {
            return String.join("\n", lines);
        }
    }

    @Test
    public void testVersionOutputFormat() {
        // Capture output via console
        final var console = new CaptureConsole();
        final var args = new String[]{"--version"};

        // Run xcc --version command
        Launcher.launch("xcc", args, console, null);

        final var output = console.getAllOutput().trim();
        assertFalse(output.isEmpty(), "Version output should not be empty");

        // Test that output follows expected format: "xdk version X.Y.Z (major.minor) [commit] (status)"
        assertTrue(output.startsWith("xdk version"),
                  "Version output should start with 'xdk version'");

        // Test that XDK version is present
        final var xdkVersion = BuildInfo.getXdkVersion();
        assertTrue(output.contains(xdkVersion),
                  "Version output should contain XDK version: " + xdkVersion);

        // Test that XVM version is present in (major.minor) format
        final var xvmVersion = BuildInfo.getXvmVersionMajor() + "." + BuildInfo.getXvmVersionMinor();
        assertTrue(output.contains("(" + xvmVersion + ")"),
                  "Version output should contain XVM version: (" + xvmVersion + ")");

        // Test git information if available
        final var gitCommit = BuildInfo.getGitCommit();
        final var gitStatus = BuildInfo.getGitStatus();

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
        assertTrue(PATTERN.matcher(output).matches(),
                  "Version output should match expected pattern");
    }

    @Test
    public void testXecVersionOutput() {
        // Test that xec --version also works
        final var console = new CaptureConsole();
        final var args = new String[]{"--version"};

        Launcher.launch("xec", args, console, null);

        final var output = console.getAllOutput().trim();
        assertFalse(output.isEmpty(), "XEC version output should not be empty");
        assertTrue(output.startsWith("xdk version"),
                  "XEC version output should also start with 'xdk version'");
    }

    @Test
    public void testXccVersionOutput() {
        // Test that xcc --version also works
        final var console = new CaptureConsole();
        final var args = new String[]{"--version"};

        Launcher.launch("xcc", args, console, null);

        final var output = console.getAllOutput().trim();
        assertFalse(output.isEmpty(), "XCC version output should not be empty");
        assertTrue(output.startsWith("xdk version"),
                  "XCC version output should also start with 'xdk version'");
    }
}