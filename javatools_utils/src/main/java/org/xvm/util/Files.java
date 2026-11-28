package org.xvm.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.ByteBuffer;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import java.nio.file.attribute.BasicFileAttributes;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File system operations used by both XVM runtimes.
 */
public final class Files {
    private Files() {}

    // ----- path conversion -----------------------------------------------------------------------

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

    // ----- file store operations -----------------------------------------------------------------

    /**
     * Perform the requested filesystem operation on the backing file system.
     */
    public static Path copyOrMove(Path source, Path dest, String destString, long operation,
                                  boolean requireMissingDest) throws IOException {
        if (operation != REPLACE) {
            // a rename must reject directory aliases that Files.move would accept as a no-op
            boolean conflict = requireMissingDest
                    ? java.nio.file.Files.exists(dest, LinkOption.NOFOLLOW_LINKS)
                    : java.nio.file.Files.exists(dest) && !java.nio.file.Files.isDirectory(dest);
            if (conflict) {
                throw new FileAlreadyExistsException("Could not overwrite file or directory: " + destString);
            }
        }

        return switch (Math.toIntExact(operation)) {
            case COPY    -> java.nio.file.Files.copy(source, dest);
            case MOVE    -> java.nio.file.Files.move(source, dest);
            case REPLACE -> {
                if (java.nio.file.Files.isDirectory(source) || java.nio.file.Files.isDirectory(dest)) {
                    throw new FileAlreadyExistsException(dest.toString(), null,
                            "Atomic replacement is only supported for files");
                }
                // ATOMIC_MOVE uses the provider's rename operation; do not retry without it
                yield java.nio.file.Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE);
            }
            default      -> throw new IllegalArgumentException("Unknown file operation: " + operation);
        };
    }

    public static void requireSource(Path source, String sourceString) throws IOException {
        if (java.nio.file.Files.notExists(source)) {
            throw new NoSuchFileException("Could not find file or directory: " + sourceString);
        }
    }

    public static long totalSpace() {
        return ROOT.getTotalSpace();
    }

    public static long freeSpace() {
        return ROOT.getFreeSpace();
    }

    public static long usedSpace() {
        return totalSpace() - freeSpace();
    }

    public static String[] listNames(Path path) {
        String[] names = path.toFile().list();
        return names == null ? NO_NAMES : names;
    }

    public static boolean createDirectory(Path path) {
        if (java.nio.file.Files.exists(path) && java.nio.file.Files.isDirectory(path)) {
            return false;
        }
        return path.toFile().mkdirs();
    }

    public static boolean createFile(Path path) throws IOException {
        if (java.nio.file.Files.exists(path) && !java.nio.file.Files.isDirectory(path)) {
            return false;
        }

        Path parent = path.getParent();
        if (!java.nio.file.Files.exists(parent)) {
            throw new IOException("Cannot create file, parent directory does not exist: " + path);
        }
        if (!java.nio.file.Files.isDirectory(parent)) {
            throw new IOException("Cannot create file, parent is not a directory: " + path);
        }
        return path.toFile().createNewFile();
    }

    public static boolean delete(Path path) {
        return java.nio.file.Files.exists(path) && path.toFile().delete();
    }

    public static boolean deleteRecursively(Path path) throws IOException {
        if (!path.toFile().isDirectory()) {
            return false;
        }
        java.nio.file.Files.walkFileTree(path, DELETE_VISITOR);
        return true;
    }

    // ----- file metadata -------------------------------------------------------------------------

    public static long createdMillis(Path path) throws IOException {
        return attributes(path).creationTime().toMillis();
    }

    public static long accessedMillis(Path path) throws IOException {
        return attributes(path).lastAccessTime().toMillis();
    }

    public static long modifiedMillis(Path path) throws IOException {
        return attributes(path).lastModifiedTime().toMillis();
    }

    public static long sizeIfExists(Path path) throws IOException {
        return java.nio.file.Files.exists(path) ? attributes(path).size() : 0L;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return java.nio.file.Files.readAttributes(path, BasicFileAttributes.class);
    }

    // ----- file I/O -------------------------------------------------------------------------------

    /**
     * Read the raw bytes contained in the specified file.
     *
     * @param file  the file to read
     *
     * @return the binary contents of the file
     *
     * @throws IOException  indicates a failure to read the binary contents
     *         of the specified file
     */
    public static byte[] readFileBytes(File file)
            throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file required");
        }

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException(file.toString());
        }

        long lcb = file.length();
        if (lcb == 0L) {
            return Handy.EMPTY_BYTE_ARRAY;
        } else if (lcb > Integer.MAX_VALUE - 1024) {
            // assume a maximum byte array size just under 2GB
            throw new IOException("file exceeds max supported length (2GB): "
                                          + file + "=" + lcb + " bytes");
        }

        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    public static int readAt(Path path, ByteBuffer buffer, long position) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            return channel.read(buffer, position);
        }
    }

    public static void truncate(Path path, long size) throws IOException {
        try (FileChannel channel = size == 0
                ? FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                : FileChannel.open(path, StandardOpenOption.WRITE)) {
            if (size > 0) {
                channel.truncate(size);
            }
        }
    }

    public static OutputStream openAppendStream(Path path) throws IOException {
        return new FileOutputStream(path.toFile(), /*append*/ true);
    }

    public static void appendFile(Path source, Path dest) throws IOException {
        try (InputStream in = new FileInputStream(source.toFile());
             OutputStream out = openAppendStream(dest)) {
            byte[] buffer = new byte[1024];
            int    count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
    }

    public static int readChunk(FileChannel channel, ByteBuffer buffer) throws IOException {
        try {
            return channel.read(buffer);
        } catch (NonReadableChannelException e) {
            return -2; // InputShutdown
        } catch (ClosedChannelException e) {
            return -3; // closed
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    public static final Set<OpenOption> NO_ACCESS  = Set.of();
    public static final Set<OpenOption> READ_ONLY  = Set.of(StandardOpenOption.READ);
    public static final Set<OpenOption> WRITE_ONLY = Set.of(StandardOpenOption.WRITE);
    public static final Set<OpenOption> READ_WRITE = Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE);

    public static final int CHANNEL_READ_SIZE = 8192;

    private static final int COPY    = 0;
    private static final int MOVE    = 1;
    private static final int REPLACE = 2;

    private static final File ROOT = new File("/");
    private static final String[] NO_NAMES = new String[0];

    /**
     * A file visitor to recursively delete files and directories.
     */
    private static final SimpleFileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            java.nio.file.Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            java.nio.file.Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

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
