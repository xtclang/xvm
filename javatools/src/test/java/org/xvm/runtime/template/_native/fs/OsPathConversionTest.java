package org.xvm.runtime.template._native.fs;

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
        assertEquals("/",    xOSFileNode.windowsToStorePath("D:\\", "D:"));
        assertEquals("/a/b", xOSFileNode.windowsToStorePath("D:\\a\\b", "D:"));
        assertEquals("/",    xOSFileNode.windowsToStorePath("\\", "D:"));
        assertEquals("/a/b", xOSFileNode.windowsToStorePath("\\a\\b", "D:"));
    }

    @Test
    public void driveLettersMatchTheCurrentDriveIgnoringCase() {
        assertEquals("/a", xOSFileNode.windowsToStorePath("d:\\a", "D:"));
        assertEquals("/a", xOSFileNode.windowsToStorePath("D:\\a", "d:"));
    }

    @Test
    public void otherDrivesBecomeALeadingSegment() {
        assertEquals("/C:",     xOSFileNode.windowsToStorePath("C:\\", "D:"));
        assertEquals("/C:/a/b", xOSFileNode.windowsToStorePath("C:\\a\\b", "D:"));
        assertEquals("/D:/a",   xOSFileNode.windowsToStorePath("D:\\a", null));
    }

    @Test
    public void otherWindowsPathsOnlyChangeTheirSeparators() {
        assertEquals("a/b",              xOSFileNode.windowsToStorePath("a\\b", "D:"));
        assertEquals("//server/share/a", xOSFileNode.windowsToStorePath("\\\\server\\share\\a", "D:"));
        assertEquals("C:a",              xOSFileNode.windowsToStorePath("C:a", "D:"));
    }

    @Test
    public void storePathsOnADriveBecomeWindowsPaths() {
        assertEquals("C:/",    xOSFileNode.storeToWindowsPath("/C:"));
        assertEquals("C:/",    xOSFileNode.storeToWindowsPath("/C:/"));
        assertEquals("C:/a/b", xOSFileNode.storeToWindowsPath("/C:/a/b"));
        assertEquals("c:/a",   xOSFileNode.storeToWindowsPath("/c:/a"));
    }

    @Test
    public void otherStorePathsAreUnchanged() {
        for (String sPath : List.of("/", "/a/b", "a/b", "/CC:/x", "/C:x", "/1:/x", "C:\\a\\b")) {
            assertEquals(sPath, xOSFileNode.storeToWindowsPath(sPath));
        }
    }

    @Test
    public void pathsOnOtherDrivesRoundTrip() {
        for (String sPath : List.of("C:\\", "C:\\a", "C:\\a\\b c\\d.x")) {
            String sStore = xOSFileNode.windowsToStorePath(sPath, "D:");
            assertEquals(sPath, xOSFileNode.storeToWindowsPath(sStore).replace('/', '\\'));
        }
    }

    @Test
    public void pathsOnTheCurrentDriveRoundTripWithoutTheDrive() {
        // "\a\b" names "D:\a\b" while the current directory is on D:
        String sStore = xOSFileNode.windowsToStorePath("D:\\a\\b", "D:");
        assertEquals("\\a\\b", xOSFileNode.storeToWindowsPath(sStore).replace('/', '\\'));
    }

    // ----- the OS-specific wrappers --------------------------------------------------------------

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void pathsAreUnchangedOutsideWindows() {
        assertEquals("/a/b", xOSFileNode.toStorePath(Path.of("/a/b")));
        assertEquals("/", xOSFileNode.toStorePath(Path.of("/")));
        assertEquals(Path.of("/a/b"), xOSFileNode.toOsPath("/a/b"));
        assertEquals(Path.of("/C:/a"), xOSFileNode.toOsPath("/C:/a"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void windowsPathsOnTheCurrentDriveAreRootedAtSlash() {
        Path root = Path.of("").toAbsolutePath().getRoot();

        assertEquals("/", xOSFileNode.toStorePath(root));
        assertEquals("/a/b", xOSFileNode.toStorePath(root.resolve("a\\b")));
        assertEquals(root.resolve("a\\b"), xOSFileNode.toOsPath("/a/b").toAbsolutePath());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void windowsPathsOnOtherDrivesBecomeALeadingSegment() {
        String root  = Path.of("").toAbsolutePath().getRoot().toString().toUpperCase();
        String drive = root.startsWith("Z:") ? "Y:" : "Z:";

        assertEquals("/" + drive, xOSFileNode.toStorePath(Path.of(drive + "\\")));
        assertEquals("/" + drive + "/a/b", xOSFileNode.toStorePath(Path.of(drive + "\\a\\b")));
        assertEquals(Path.of(drive + "\\"), xOSFileNode.toOsPath("/" + drive));
        assertEquals(Path.of(drive + "\\a\\b"), xOSFileNode.toOsPath("/" + drive + "/a/b"));
    }
}
