package org.xvm.util;

import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Conversions between OS paths and the '/'-separated form of an Ecstasy {@code Path}. The Windows
 * mapping is tested on every OS; the OS-specific wrappers only on the OS they apply to.
 */
public class OsPathConversionTest {

    // ----- the Windows mapping, on any OS --------------------------------------------------------

    @Test
    public void currentDrivePathsAreRootedAtSlash() {
        assertEquals("/",    Files.windowsToStorePath("D:\\", "D:"));
        assertEquals("/a/b", Files.windowsToStorePath("D:\\a\\b", "D:"));
        assertEquals("/",    Files.windowsToStorePath("\\", "D:"));
        assertEquals("/a/b", Files.windowsToStorePath("\\a\\b", "D:"));
    }

    @Test
    public void driveLettersMatchTheCurrentDriveIgnoringCase() {
        assertEquals("/a", Files.windowsToStorePath("d:\\a", "D:"));
        assertEquals("/a", Files.windowsToStorePath("D:\\a", "d:"));
    }

    @Test
    public void otherDrivesBecomeALeadingSegment() {
        assertEquals("/C:",     Files.windowsToStorePath("C:\\", "D:"));
        assertEquals("/C:/a/b", Files.windowsToStorePath("C:\\a\\b", "D:"));
        assertEquals("/D:/a",   Files.windowsToStorePath("D:\\a", null));
    }

    @Test
    public void otherWindowsPathsOnlyChangeTheirSeparators() {
        assertEquals("a/b",              Files.windowsToStorePath("a\\b", "D:"));
        assertEquals("//server/share/a", Files.windowsToStorePath("\\\\server\\share\\a", "D:"));
        assertEquals("C:a",              Files.windowsToStorePath("C:a", "D:"));
    }

    @Test
    public void storePathsOnADriveBecomeWindowsPaths() {
        assertEquals("C:/",    Files.storeToWindowsPath("/C:"));
        assertEquals("C:/",    Files.storeToWindowsPath("/C:/"));
        assertEquals("C:/a/b", Files.storeToWindowsPath("/C:/a/b"));
        assertEquals("c:/a",   Files.storeToWindowsPath("/c:/a"));
    }

    @Test
    public void otherStorePathsAreUnchanged() {
        for (String sPath : List.of("/", "/a/b", "a/b", "/CC:/x", "/C:x", "/1:/x", "C:\\a\\b")) {
            assertEquals(sPath, Files.storeToWindowsPath(sPath));
        }
    }

    @Test
    public void pathsOnOtherDrivesRoundTrip() {
        for (String sPath : List.of("C:\\", "C:\\a", "C:\\a\\b c\\d.x")) {
            String sStore = Files.windowsToStorePath(sPath, "D:");
            assertEquals(sPath, Files.storeToWindowsPath(sStore).replace('/', '\\'));
        }
    }

    @Test
    public void pathsOnTheCurrentDriveRoundTripWithoutTheDrive() {
        // "\a\b" names "D:\a\b" while the current directory is on D:
        String sStore = Files.windowsToStorePath("D:\\a\\b", "D:");
        assertEquals("\\a\\b", Files.storeToWindowsPath(sStore).replace('/', '\\'));
    }

    // ----- the OS-specific wrappers --------------------------------------------------------------

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void pathsAreUnchangedOutsideWindows() {
        assertEquals("/a/b", Files.toStorePath(Path.of("/a/b")));
        assertEquals("/", Files.toStorePath(Path.of("/")));
        assertEquals(Path.of("/a/b"), Files.toOsPath("/a/b"));
        assertEquals(Path.of("/C:/a"), Files.toOsPath("/C:/a"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void windowsPathsOnTheCurrentDriveAreRootedAtSlash() {
        Path root = Path.of("").toAbsolutePath().getRoot();

        assertEquals("/", Files.toStorePath(root));
        assertEquals("/a/b", Files.toStorePath(root.resolve("a\\b")));
        assertEquals(root.resolve("a\\b"), Files.toOsPath("/a/b").toAbsolutePath());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void windowsPathsOnOtherDrivesBecomeALeadingSegment() {
        String root  = Path.of("").toAbsolutePath().getRoot().toString().toUpperCase();
        String drive = root.startsWith("Z:") ? "Y:" : "Z:";

        assertEquals("/" + drive, Files.toStorePath(Path.of(drive + "\\")));
        assertEquals("/" + drive + "/a/b", Files.toStorePath(Path.of(drive + "\\a\\b")));
        assertEquals(Path.of(drive + "\\"), Files.toOsPath("/" + drive));
        assertEquals(Path.of(drive + "\\a\\b"), Files.toOsPath("/" + drive + "/a/b"));
    }
}
