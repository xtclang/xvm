package org.xvm.asm;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BuildInfo functionality and version information.
 */
public class BuildInfoTest {
    private static final Pattern PATTERN = Pattern.compile("[a-f0-9]+");

    @Test
    public void testBuildInfoLoaded() {
        // Test that BuildInfo can load properties
        String xdkVersion = BuildInfo.getXdkVersion();
        assertNotNull(xdkVersion, "XDK version should not be null");
        assertFalse(xdkVersion.isEmpty(), "XDK version should not be empty");
        assertNotEquals("0.0.0-unknown", xdkVersion, "XDK version should not be default fallback");
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
            assertTrue(gitCommit.length() >= 12, "Git commit should be at least long hash");
            assertTrue(PATTERN.matcher(gitCommit).matches(), "Git commit should be hexadecimal");
        }

        // Git status now contains branch name or "detached-head"
        assertNotNull(gitStatus, "Git status should not be null");
        assertFalse(gitStatus.isEmpty(), "Git status should not be empty");
    }

    @Test
    public void testConstantsUseBuildInfo() {
        // Test that Constants properly uses BuildInfo values
        assertEquals(Constants.VERSION_MAJOR_CUR, BuildInfo.getXvmVersionMajor(),
                    "Constants should use BuildInfo for major version");
        assertEquals(Constants.VERSION_MINOR_CUR, BuildInfo.getXvmVersionMinor(),
                    "Constants should use BuildInfo for minor version");
    }
}