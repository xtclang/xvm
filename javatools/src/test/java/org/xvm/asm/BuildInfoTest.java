package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for BuildInfo functionality and version information.
 */
public class BuildInfoTest {
    @Test
    public void testBuildInfoLoaded() {
        // Test that BuildInfo can load properties
        String xdkVersion = BuildInfo.getXdkVersion();
        assertNotNull(xdkVersion, "XDK version should not be null");
        assertFalse(xdkVersion.isEmpty(), "XDK version should not be empty");
        assertFalse(xdkVersion.equals("0.0.0-unknown"), "XDK version should not be default fallback");
    }

    @Test
    public void testXvmVersions() {
        // Test that XVM versions are valid
        int majorVersion = BuildInfo.getXvmVersionMajor();
        int minorVersion = BuildInfo.getXvmVersionMinor();

        assertTrue(majorVersion >= 0, "XVM major version must be non-negative");
        assertTrue(minorVersion >= 0, "XVM minor version must be non-negative");

        // Minor version should be a date-like number (8 digits for YYYYMMDD format)
        if (majorVersion == 0) { // Pre-production uses date format
            assertTrue(minorVersion >= 20250101, "XVM minor version must be an ISO-8601 date for major version 0");
            assertTrue(minorVersion <= 20991231, "XVM minor version must be an ISO-8601 date within the 21st century for major version 0");
        }
    }

    @Test
    public void testGitInformation() {
        // Test that git information is available (if in git environment)
        String gitCommit = BuildInfo.getGitCommit();
        String gitStatus = BuildInfo.getGitStatus();

        // Git info might not be available in all build environments, so allow empty
        if (!gitCommit.isEmpty()) {
            assertTrue(gitCommit.length() >= 7, "Git commit should be at least 7 characters (short hash)");
            assertTrue(gitCommit.matches("[a-f0-9]+"), "Git commit should be hexadecimal");
        }

        if (!gitStatus.isEmpty()) {
            assertTrue(gitStatus.equals("clean") || gitStatus.equals("detached-head"),
                      "Git status should be 'clean' or 'detached-head'");
        }
    }

    @Test
    public void testConstantsUseBuildInfo() {
        // Test that Constants properly uses BuildInfo values
        assertEquals(BuildInfo.getXvmVersionMajor(), Constants.VERSION_MAJOR_CUR,
                    "Constants should use BuildInfo for major version");
        assertEquals(BuildInfo.getXvmVersionMinor(), Constants.VERSION_MINOR_CUR,
                    "Constants should use BuildInfo for minor version");
    }
}