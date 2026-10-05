package org.xvm.runtime.template._native.fs;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Conversions between OS paths and the '/'-separated form of an Ecstasy {@code Path}.
 */
public class OSPathConversionTest {
    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void pathsAreUnchangedOutsideWindows() {
        assertEquals("/a/b", xOSFileNode.toStorePath(Path.of("/a/b")));
        assertEquals("/", xOSFileNode.toStorePath(Path.of("/")));
        assertEquals(Path.of("/a/b"), xOSFileNode.toOSPath("/a/b"));
        assertEquals(Path.of("/C:/a"), xOSFileNode.toOSPath("/C:/a"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void currentDrivePathsAreRootedAtSlash() {
        Path root = Path.of("").toAbsolutePath().getRoot();

        assertEquals("/", xOSFileNode.toStorePath(root));
        assertEquals("/a/b", xOSFileNode.toStorePath(root.resolve("a\\b")));
        assertEquals(root.resolve("a\\b"), xOSFileNode.toOSPath("/a/b").toAbsolutePath());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void otherDrivesBecomeALeadingSegment() {
        char   current = Path.of("").toAbsolutePath().getRoot().toString().charAt(0);
        String drive   = (Character.toUpperCase(current) == 'Z' ? 'Y' : 'Z') + ":";

        assertEquals("/" + drive, xOSFileNode.toStorePath(Path.of(drive + "\\")));
        assertEquals("/" + drive + "/a/b", xOSFileNode.toStorePath(Path.of(drive + "\\a\\b")));
        assertEquals(Path.of(drive + "\\"), xOSFileNode.toOSPath("/" + drive));
        assertEquals(Path.of(drive + "\\a\\b"), xOSFileNode.toOSPath("/" + drive + "/a/b"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void osPathsPassThrough() {
        assertEquals(Path.of("C:\\a\\b"), xOSFileNode.toOSPath("C:\\a\\b"));
        assertEquals("a/b", xOSFileNode.toStorePath(Path.of("a\\b")));
    }
}
