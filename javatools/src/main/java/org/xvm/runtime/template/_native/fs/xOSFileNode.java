package org.xvm.runtime.template._native.fs;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;

/**
 * Native base for OSFile and OSDirectory implementations.
 *
 * TODO currently, all "native" file nodes are converted to absolute paths, which is not "wrong" --
 *      per se -- but it seems like overkill at this point and we won't want to retain that behavior
 *      in the JIT
 * TODO design (standardize for Ecstasy representation) an appropriate escaping for path segments
 *      and implement Path.x toString() accordingly, which may involve exposing the path into
 *      Ecstasy as an array of String instead of as a String, and then (based on that work) simplify
 *      the approach to the non-POSIX file handling
 */
public class xOSFileNode
        extends xConst {
    public xOSFileNode(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        markNativeProperty("pathString");
        markNativeProperty("exists");
        markNativeProperty("readable");
        markNativeProperty("writable");
        markNativeProperty("createdMillis");
        markNativeProperty("accessedMillis");
        markNativeProperty("modifiedMillis");
        markNativeProperty("size");

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName) {
        case "pathString":
            return frame.assignValue(iReturn, xString.makeHandle(toStorePath(hNode.f_path)));

        case "exists":
            return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().exists()));

        case "readable":
            return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().canRead()));

        case "writable":
            return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().canWrite()));

        case "createdMillis":
            try {
                BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                return frame.assignValue(iReturn, xInt64.makeHandle(attr.creationTime().toMillis()));
            } catch (IOException e) {
                return raisePathException(frame, e, hNode.f_path);
            }

        case "accessedMillis":
            try {
                BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                return frame.assignValue(iReturn, xInt64.makeHandle(attr.lastAccessTime().toMillis()));
            } catch (IOException e) {
                return raisePathException(frame, e, hNode.f_path);
            }

        case "modifiedMillis":
            try {
                BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                return frame.assignValue(iReturn, xInt64.makeHandle(attr.lastModifiedTime().toMillis()));
            } catch (IOException e) {
                return raisePathException(frame, e, hNode.f_path);
            }

        case "size":
            try {
                Path path = hNode.f_path;
                if (Files.exists(path)) {
                    BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.size()));
                } else {
                    return frame.assignValue(iReturn, xInt64.makeHandle(0));
                }
            } catch (IOException e) {
                return raisePathException(frame, e, hNode.f_path);
            }
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    /**
     * Construct a new {@link NodeHandle} representing the specified file or directory.
     *
     * @param frame      the current frame
     * @param hOSStore   the "host" OSStore handle
     * @param path       the node's path
     * @param fDir       true iff the path represents a directory; false otherwise
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    static int createHandle(Frame frame, ObjectHandle hOSStore, Path path, boolean fDir, int iReturn) {
        return fDir
            ? xOSDirectory.INSTANCE.createHandle(frame, hOSStore, path, iReturn)
            : xOSFile     .INSTANCE.createHandle(frame, hOSStore, path, iReturn);
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Convert an OS path to the '/'-separated form of an Ecstasy {@code Path}, which does not
     * recognize '\' as a separator. On Windows, see {@link #windowsToStorePath}; elsewhere, the
     * path is returned as is.
     *
     * @param path  an OS path
     *
     * @return the path in the form of an Ecstasy {@code Path}
     */
    public static String toStorePath(Path path) {
        return WINDOWS ? windowsToStorePath(path.toString(), CURRENT_DRIVE) : path.toString();
    }

    /**
     * Convert a path string to an OS path. Accepts both OS paths and the Ecstasy {@code Path} form
     * produced by {@link #toStorePath}; on Windows, see {@link #storeToWindowsPath}.
     *
     * @param sPath  an OS path or a path in the form of an Ecstasy {@code Path}
     *
     * @return the OS path
     */
    public static Path toOsPath(String sPath) {
        return Path.of(WINDOWS ? storeToWindowsPath(sPath) : sPath);
    }

    /**
     * Convert a Windows path to the form of an Ecstasy {@code Path}. Paths on the current drive are
     * rooted at "/" ("D:\a\b" and "\a\b" become "/a/b"), other drives become a leading segment
     * ("C:\a" becomes "/C:/a" and "C:\" becomes "/C:"), and any other path only has its separators
     * replaced. Works on any OS, so the Windows mapping can be tested everywhere.
     *
     * @param sPath          a Windows path, such as "C:\a\b"
     * @param sCurrentDrive  the drive of the current directory, such as "D:", or null if none
     *
     * @return the path in the form of an Ecstasy {@code Path}
     */
    static String windowsToStorePath(String sPath, String sCurrentDrive) {
        Matcher drive = WINDOWS_DRIVE_PATH.matcher(sPath);
        if (drive.matches()) {
            String sRest = toStoreSeparators(drive.group(2));
            if (drive.group(1).equalsIgnoreCase(sCurrentDrive)) {
                return SEPARATOR + sRest;
            }
            String sDrive = SEPARATOR + drive.group(1);
            return sRest.isEmpty() ? sDrive : sDrive + SEPARATOR + sRest;
        }
        Matcher rooted = WINDOWS_ROOTED_PATH.matcher(sPath);
        if (rooted.matches()) {
            return SEPARATOR + toStoreSeparators(rooted.group(1));
        }
        return toStoreSeparators(sPath);
    }

    /**
     * Convert a path in the form of an Ecstasy {@code Path} to a string that names the same Windows
     * path: "/C:/a" becomes "C:/a", and "/C:" becomes the drive root "C:/" rather than "C:", which
     * means the current directory on that drive. Any other path is returned as is. Works on any OS,
     * so the Windows mapping can be tested everywhere.
     *
     * @param sPath  a path in the form of an Ecstasy {@code Path}, or a Windows path
     *
     * @return a Windows path string
     */
    static String storeToWindowsPath(String sPath) {
        Matcher drive = DRIVE_STORE_PATH.matcher(sPath);
        if (drive.matches()) {
            String sRest = drive.group(2);
            return drive.group(1) + (sRest == null ? SEPARATOR : sRest);
        }
        return sPath;
    }

    /**
     * @return the drive of a Windows path, such as "C:" for "C:\a", or null if it has none
     */
    private static String driveOf(String sPath) {
        Matcher drive = WINDOWS_DRIVE_PATH.matcher(sPath);
        return drive.matches() ? drive.group(1) : null;
    }

    /**
     * @return the path with its Windows separators replaced by the separator of an Ecstasy {@code Path}
     */
    private static String toStoreSeparators(String sPath) {
        return sPath.replace("\\", SEPARATOR);
    }

    public static int raisePathException(Frame frame, Throwable e, Path path) {
        if (e instanceof ExecutionException ee) {
            e = ee.getCause();
            if (e instanceof IOException ioe) {
                return raisePathException(frame, ioe, path);
            }
        }

        return frame.raiseException(e.getMessage());
    }

    public static int raisePathException(Frame frame, IOException e, Path path) {
        // TODO: how to get the natural Path efficiently from path?
        // TODO: consider translating IOExceptions into corresponding natural exceptions

        // strip the exception name from the exception class and prepend to the message
        Class<? extends IOException> clzException = e.getClass();
        String sException  = clzException == IOException.class
                ? ""
                : clzException.getSimpleName().replace("Exception", "") + ": ";
        return frame.raiseException(
            xException.pathException(frame, sException + e.getMessage(), xNullable.NULL));
    }

    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class NodeHandle
            extends GenericHandle {
        protected final Path f_path;

        // TODO: lazy file channel, etc.

        protected NodeHandle(TypeComposition clazz, Path path, ObjectHandle hOSStore) {
            super(clazz);

            f_path = path;

            setField(null, "store", hOSStore);
        }

        public Path getPath() {
            return f_path;
        }

        @Override
        public String toString() {
            return super.toString() + " " + f_path;
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * True iff the OS uses '\' as its path separator.
     */
    private static final boolean WINDOWS = File.separatorChar == '\\';

    /**
     * The separator of an Ecstasy {@code Path}; a path that starts with it is absolute.
     */
    private static final String SEPARATOR = "/";

    /**
     * A Windows path on a drive, such as "C:\" or "C:\a\b"; the groups are the drive, such as "C:",
     * and the rest of the path, such as "a\b".
     */
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("([A-Za-z]:)\\\\(.*)");

    /**
     * A Windows path rooted on the current drive, such as "\" or "\a\b" but not a UNC path such as
     * "\\server\share"; the group is the rest of the path, such as "a\b".
     */
    private static final Pattern WINDOWS_ROOTED_PATH = Pattern.compile("\\\\(?!\\\\)(.*)");

    /**
     * A path on a drive in the form of an Ecstasy {@code Path}, such as "/C:" or "/C:/a"; the groups
     * are the drive, such as "C:", and the optional rest of the path, such as "/a".
     */
    private static final Pattern DRIVE_STORE_PATH = Pattern.compile("/([A-Za-z]:)(/.*)?");

    /**
     * On Windows, the drive that holds the current directory, such as "D:"; otherwise null.
     */
    private static final String CURRENT_DRIVE = driveOf(Path.of("").toAbsolutePath().getRoot().toString());
}
