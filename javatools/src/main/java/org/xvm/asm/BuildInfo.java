package org.xvm.asm;

import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;

/**
 * Utility class to access build-time information from generated resources.
 *
 * This class reads version and git information from build-info.properties,
 * which is generated at build time from version.properties and git commands.
 */
public final class BuildInfo {
    private static final Properties BUILD_INFO = loadBuildInfo();

    private static Properties loadBuildInfo() {
        Properties props = new Properties();
        try (InputStream is = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Ignore - will use fallback values from version.properties if available
        }

        // Fallback to version.properties if build-info.properties not found
        if (props.isEmpty()) {
            try (InputStream is = BuildInfo.class.getResourceAsStream("/version.properties")) {
                if (is != null) {
                    props.load(is);
                }
            } catch (IOException e) {
                // Ignore - will use default values
            }
        }

        return props;
    }

    /**
     * Get the XDK version string (e.g., "0.4.4-SNAPSHOT").
     * Used by xcc --version, xec --version, and build system.
     */
    public static String getXdkVersion() {
        return BUILD_INFO.getProperty("xdk.version", "0.0.0-unknown");
    }

    /**
     * Get the XVM major version number.
     * Controls XTC file format compatibility.
     */
    public static int getXvmVersionMajor() {
        return Integer.parseInt(BUILD_INFO.getProperty("xvm.version.major"));
    }

    /**
     * Get the XVM minor version number.
     * Controls XTC file format compatibility.
     */
    public static int getXvmVersionMinor() {
        return Integer.parseInt(BUILD_INFO.getProperty("xvm.version.minor"));
    }

    /**
     * Get the git commit hash, if available.
     * @return git commit hash or empty string if not available
     */
    public static String getGitCommit() {
        // Use full commit ID instead of abbreviated version
        return BUILD_INFO.getProperty("git.commit.id", BUILD_INFO.getProperty("git.commit", ""));
    }

    /**
     * Get the git status, if available.
     * @return git status ("clean" or "detached-head") or empty string if not available
     */
    public static String getGitStatus() {
        return BUILD_INFO.getProperty("git.status", "");
    }
}
