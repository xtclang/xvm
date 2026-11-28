package org.xvm.asm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.time.Duration;
import java.time.Instant;

import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import java.util.stream.Stream;

import org.xvm.util.Lazy;

/**
 * A simple ModuleRepository that manages its contents in a directory.
 */
/*
 * THREAD SAFETY: the three public entry points below are synchronized on the repository.
 *
 * The scan cache is a plain HashMap (modulesByFile, a non-final field reassigned wholesale) and a
 * plain TreeMap (modulesByName, rebuilt with clear() + a put() loop). Concurrent callers - two
 * parallel compiles resolving library modules, say - otherwise race those rebuilds against get()
 * and against the live keySet() view getModuleNames() hands out, which throws
 * ConcurrentModificationException (and can spin or silently drop entries).
 *
 * A coarse per-repository lock is the right granularity here, not a pessimisation: this is a
 * directory-scan cache consulted once per module lookup, not a hot inner loop. Note that swapping
 * in concurrent maps, or copy-on-write with a volatile swap, would NOT be sufficient on its own,
 * because ModuleInfo.ensureModule() ALSO lazily deserializes ("if (module == null) module =
 * tryLoad()") - two threads would still duplicate that work and could publish a half-built module.
 * The lock covers both the cache rebuild and the lazy materialization reached through it.
 */
public class DirRepository
        implements ModuleRepository {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a File System ModuleRepository.
     *
     * @param dir        the directory that contains the repository contents
     * @param fReadOnly  true to make the repository "read-only"
     */
    public DirRepository(File dir, boolean fReadOnly) {
        this(dir, fReadOnly, USER_CACHE);
    }

    /**
     * Construct a File System ModuleRepository that keeps its persistent cache in the given place.
     *
     * @param dir        the directory that contains the repository contents
     * @param fReadOnly  true to make the repository "read-only"
     * @param cache      the directory to keep the persistent cache in
     */
    DirRepository(File dir, boolean fReadOnly, CacheDirectory cache) {
        this(dir, fReadOnly, () -> cache);
    }

    private DirRepository(File dir, boolean fReadOnly, Supplier<CacheDirectory> cache) {
        assert dir != null && dir.isDirectory();

        m_dir   = dir;
        m_fRO   = fReadOnly;
        m_cache = cache;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the directory containing the module files
     */
    public File getDir() {
        return m_dir;
    }

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly() {
        return m_fRO;
    }

    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public synchronized Set<String> getModuleNames() {
        ensureCache();
        return Collections.unmodifiableSet(modulesByName.keySet());
    }

    @Override
    public synchronized ModuleStructure loadModule(String sModule) {
        ensureCache();
        ModuleStructure module = loadIndexed(sModule);
        if (module == null || module.getIdentityConstant().getName().equals(sModule)) {
            return module;
        }

        // the index named a file that holds another module, so the persistent cache it came from
        // is stale or forged; trust none of it and read every file again
        Optional.ofNullable(m_dir.listFiles(ModulesOnly))
                .ifPresentOrElse(files -> scan(files, Map.of(), true), this::clearCache);

        module = loadIndexed(sModule);
        return module == null || module.getIdentityConstant().getName().equals(sModule)
                ? module
                : null;
    }

    @Override
    public synchronized void storeModule(ModuleStructure module)
            throws IOException {
        if (m_fRO) {
            throw new IOException("repository is read-only: " + this);
        }

        String name = module.getIdentityConstant().getName();
        ModuleInfo info = modulesByName.get(name);
        File file = (info == null)
                ? new File(m_dir, module.getIdentityConstant().getUnqualifiedName() + ".xtc")
                : info.file;

        if (file.exists() && !file.delete()) {
            throw new IOException("unable to delete " + file);
        }

        module.getFileStructure().writeTo(file);

        if (file.exists()) {
            info = createModuleInfo(file);
            modulesByName.put(name, info);
            modulesByFile.put(file, info);
        } else {
            modulesByName.remove(name);
            modulesByFile.remove(file);
        }

        writeCache();
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return m_dir.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this || !(obj instanceof DirRepository that)) {
            return obj == this;
        }

        return this.m_dir.equals(that.m_dir) &&
               this.m_fRO     == that.m_fRO;
    }

    @Override
    public String toString() {
        return "DirRepository(Path=" + m_dir.toString() + ", RO=" + m_fRO + ")";
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Make sure that the cache is up to date.
     */
    protected void ensureCache() {
        if (isCacheValid()) {
            return;
        }

        Optional.ofNullable(m_dir.listFiles(ModulesOnly))
                .ifPresentOrElse(this::rebuildCache, this::clearCache);
    }

    private void rebuildCache(File[] files) {
        Map<File, ModuleInfo> oldModulesByFile = modulesByFile;
        boolean               fWriteCache      = false;
        if (oldModulesByFile.isEmpty()) {
            Map<File, ModuleInfo> cachedModulesByFile = readCache();
            if (cachedModulesByFile == null) {
                fWriteCache = true;
            } else {
                oldModulesByFile = cachedModulesByFile;
            }
        }

        scan(files, oldModulesByFile, fWriteCache);
    }

    /**
     * Index the module files, reusing the information known for each file that has not changed.
     *
     * @param files             the module files
     * @param oldModulesByFile  the information known for the files
     * @param fWriteCache       true to save the persistent cache even if nothing changed
     */
    private void scan(File[] files, Map<File, ModuleInfo> oldModulesByFile, boolean fWriteCache) {
        Map<File, ModuleInfo> newModulesByFile = new HashMap<>();

        modulesByFile = newModulesByFile;
        modulesByName.clear();

        fWriteCache |= files.length != oldModulesByFile.size();
        for (File file : files) {
            ModuleInfo info = oldModulesByFile.get(file);
            if (info == null || info.timestamp != file.lastModified() || info.size != file.length()) {
                // build a new one to cache
                info = createModuleInfo(file);
                fWriteCache = true;
            }

            newModulesByFile.put(file, info);
            if (!info.err) {
                modulesByName.put(info.name, info);
            }
        }

        lastScan = System.currentTimeMillis();
        if (fWriteCache) {
            writeCache();
        }
    }

    private void clearCache() {
        modulesByFile.clear();
        modulesByName.clear();
        lastScan = System.currentTimeMillis();
    }

    /**
     * Load a module through the index, which may have come from the persistent cache.
     *
     * @param sModule  the module name
     *
     * @return the module that the index names a file for, or null
     */
    private ModuleStructure loadIndexed(String sModule) {
        ModuleInfo info = modulesByName.get(sModule);
        return info == null ? null : info.ensureModule();
    }

    /**
     * Create the information cached for a module file.
     *
     * @param file  the module file
     *
     * @return the module information
     */
    protected ModuleInfo createModuleInfo(File file) {
        return new ModuleInfo(file);
    }

    /**
     * Read the module information saved by a previous instance of this repository.
     *
     * @return the cached module information, or null if no valid cache exists
     */
    private Map<File, ModuleInfo> readCache() {
        Path pathCache = getCacheFile();
        if (pathCache == null) {
            return null;
        }

        long cbCache;
        try {
            BasicFileAttributes attrs = Files.readAttributes(pathCache, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile()) {
                return null;
            }
            cbCache = attrs.size();
        } catch (IOException | RuntimeException e) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(pathCache, LinkOption.NOFOLLOW_LINKS)))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION ||
                    !in.readUTF().equals(m_dir.getCanonicalPath())) {
                return null;
            }

            int  cModules  = in.readInt();
            long cbEntries = cbCache - MIN_CACHE_HEADER_SIZE;
            if (cModules < 0 || cbEntries < (long) cModules * MIN_CACHE_ENTRY_SIZE) {
                // probably a corrupted file
                return null;
            }

            Map<File, ModuleInfo> modulesByFile = new HashMap<>(cModules);
            for (int i = 0; i < cModules; ++i) {
                String fileName = in.readUTF();
                if (!fileName.equals(new File(fileName).getName())) {
                    return null;
                }

                File    file      = new File(m_dir, fileName);
                long    timestamp = in.readLong();
                long    size      = in.readLong();
                boolean err       = in.readBoolean();
                String  name      = err ? null : in.readUTF();

                if (modulesByFile.put(file,
                        new ModuleInfo(file, name, timestamp, size, err)) != null) {
                    return null;
                }
            }
            return in.read() < 0 ? modulesByFile : null;
        } catch (IOException | RuntimeException e) {
            // a persistent cache is only a performance aid
            return null;
        }
    }

    /**
     * Save enough module information to avoid deserializing unchanged files in the next process.
     */
    private void writeCache() {
        Path pathCache = getCacheFile();
        if (pathCache == null) {
            return;
        }

        Path pathTemp = null;
        try {
            // a new file of this process's own, next to the cache file and moved over it, so that
            // a reader sees the old cache or the new one, and a link at either name is never
            // written through
            pathTemp = Files.createTempFile(pathCache.getParent(),
                    pathCache.getFileName().toString(), ".tmp");

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(pathTemp, StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS)))) {
                out.writeInt(CACHE_MAGIC);
                out.writeInt(CACHE_VERSION);
                out.writeUTF(m_dir.getCanonicalPath());
                out.writeInt(modulesByFile.size());

                for (ModuleInfo info : modulesByFile.values()) {
                    out.writeUTF(info.file.getName());
                    out.writeLong(info.timestamp);
                    out.writeLong(info.size);
                    out.writeBoolean(info.err);
                    if (!info.err) {
                        out.writeUTF(info.name);
                    }
                }
            }

            // without an atomic move there is no cache, rather than one that a reader sees half
            // written
            Files.move(pathTemp, pathCache,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            pathTemp = null;
        } catch (IOException | RuntimeException ignore) {
            // a persistent cache is only a performance aid
        } finally {
            if (pathTemp != null) {
                deleteTempCacheFile(pathTemp);
            }
        }
    }

    private static void deleteTempCacheFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException _) {
            path.toFile().deleteOnExit();
        }
    }

    /**
     * @return the persistent cache file for this repository, named by the SHA-256 of the
     *         repository's canonical path, or null if the repository is a throwaway one or there
     *         is no cache directory to use
     */
    private Path getCacheFile() {
        try {
            CacheDirectory cache    = m_cache.get();
            String         sPath    = m_dir.getCanonicalPath();
            Path           dirCache = cache.caches(Path.of(sPath)) ? cache.checked() : null;
            if (dirCache == null) {
                return null;
            }

            byte[] abHash = MessageDigest.getInstance("SHA-256")
                    .digest(sPath.getBytes(StandardCharsets.UTF_8));
            return dirCache.resolve(HexFormat.of().formatHex(abHash) + CACHE_SUFFIX);
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Quick scan to make sure that the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private boolean isCacheValid() {
        // only scan once a second (at the most)
        if (System.currentTimeMillis() < lastScan + 1000) {
            return true;
        }

        File[] files = m_dir.listFiles(ModulesOnly);
        if (files == null || files.length != modulesByFile.size()) {
            return false;
        }

        for (File file : files) {
            ModuleInfo info = modulesByFile.get(file);
            if (info == null || info.timestamp != file.lastModified() || info.size != file.length()) {
                return false;
            }
        }

        return true;
    }

    // ----- inner class: ModuleInfo ---------------------------------------------------------------

    protected static class ModuleInfo {
        public ModuleInfo(File file) {
            this.file      = file;
            this.timestamp = file.lastModified();
            this.size      = file.length();

            ModuleStructure module = tryLoad();
            if (module == null) {
                this.name     = null;
                this.versions = null;
                this.err      = true;
            } else {
                this.name     = module.getIdentityConstant().getName();
                this.versions = module.getVersions();
                this.err      = false;
            }
        }

        private ModuleInfo(File file, String name, long timestamp, long size, boolean err) {
            this.file      = file;
            this.name      = name;
            this.versions  = null;
            this.timestamp = timestamp;
            this.size      = size;
            this.err       = err;
        }

        ModuleStructure tryLoad() {
            try {
                FileStructure struct = new FileStructure(file);
                return struct.getModule();
            } catch (Exception e) {
                System.out.println("Error loading module from file: " + file + "; " + e.getMessage());
            }

            return null;
        }

        ModuleStructure ensureModule() {
            if (err) {
                return null;
            }

            if (module == null || module.isModified()) {
                module = tryLoad();
            }

            return module;
        }

        public final String               name;
        public final File                 file;
        public final VersionTree<Boolean> versions;
        public final long                 timestamp;
        public final long                 size;
        public final boolean              err;

        /**
         * Cached instance of the module struct. If the caller changes it, we will detect it and
         * reload it as necessary.
         */
        private transient ModuleStructure module;
    }

    // ----- inner class: CacheDirectory -----------------------------------------------------------

    /**
     * The directory that keeps a user's persistent caches: the first of its candidates that is, or
     * can be made, a directory of the user's own that no one else can write, in a parent that no
     * one else can rename it out of. Each use checks it again without following links; a link, a
     * directory that another user owns, or one that its group or others can write is never used.
     * Without a directory to use, there is no persistent cache, and a repository reads its module
     * files instead. The first time a directory passes its check in this process, the cache files
     * in it that have not been written for {@link #MAX_AGE} are deleted, so that the caches of
     * repositories that are gone do not pile up.
     */
    static final class CacheDirectory {
        /**
         * The candidate directories, in order of preference.
         */
        private final List<Path> candidates;

        /**
         * The real path of the temporary directory, whose repositories are throwaway ones that are
         * not worth caching, or null to cache repositories anywhere.
         */
        private final Path temporary;

        /**
         * Whether the current user owns a path, without following a link.
         */
        private final Predicate<Path> owned;

        /**
         * The directories already pruned in this process.
         */
        private final Set<Path> pruned = ConcurrentHashMap.newKeySet();

        /**
         * Construct a cache directory that the current user must own.
         *
         * @param candidates  the candidate directories, in order of preference
         * @param temporary   the real path of the directory whose repositories are not cached, or
         *                    null to cache repositories anywhere
         */
        CacheDirectory(List<Path> candidates, Path temporary) {
            this(candidates, temporary, ownedByCurrentUser());
        }

        /**
         * Construct a cache directory.
         *
         * @param candidates  the candidate directories, in order of preference
         * @param temporary   the real path of the directory whose repositories are not cached, or
         *                    null to cache repositories anywhere
         * @param owned       whether the current user owns a path, without following a link
         */
        CacheDirectory(List<Path> candidates, Path temporary, Predicate<Path> owned) {
            this.candidates = List.copyOf(candidates);
            this.temporary  = temporary;
            this.owned      = owned;
        }

        /**
         * @return the candidate directories, in order of preference
         */
        List<Path> getCandidates() {
            return candidates;
        }

        /**
         * @param pathRepo  the canonical path of a repository directory
         *
         * @return true if the repository is worth caching: one that is not in the temporary
         *         directory
         */
        boolean caches(Path pathRepo) {
            return temporary == null || !pathRepo.startsWith(temporary);
        }

        /**
         * @return the first candidate that is a directory of the current user's that no one else
         *         can write, made for its owner alone if it did not exist, or null if there is none
         */
        Path checked() {
            for (Path dir : candidates) {
                if (usable(made(dir))) {
                    if (pruned.add(dir)) {
                        prune(dir);
                    }
                    return dir;
                }
            }
            return null;
        }

        /**
         * @param dir  a candidate directory
         *
         * @return true if the directory is a directory, not a link, that the current user owns,
         *         that its group and others cannot write, and that others cannot rename away
         */
        private boolean usable(Path dir) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                // a Windows junction is "other"
                if (attrs.isSymbolicLink() || attrs.isOther() || !attrs.isDirectory() ||
                        !owned.test(dir)) {
                    return false;
                }

                PosixFileAttributeView view = Files.getFileAttributeView(dir,
                        PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (view != null) {
                    Set<PosixFilePermission> perms = view.readAttributes().permissions();
                    if (perms.contains(PosixFilePermission.GROUP_WRITE) ||
                            perms.contains(PosixFilePermission.OTHERS_WRITE)) {
                        return false;
                    }
                }

                // in a parent that anyone can write, only the sticky bit keeps others from
                // renaming the directory away and putting their own in its place
                Path parent = dir.getParent();
                if (parent != null &&
                        dir.getFileSystem().supportedFileAttributeViews().contains("unix")) {
                    int nMode = (Integer) Files.getAttribute(parent, "unix:mode");
                    return (nMode & OTHERS_WRITABLE) == 0 || (nMode & STICKY) != 0;
                }
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        /**
         * Delete the cache files and the temporary files in a checked directory that have not been
         * written for {@link #MAX_AGE}, if they are this user's regular files; never what a link
         * names.
         *
         * @param dir  the directory
         */
        private void prune(Path dir) {
            FileTime timeOldest = FileTime.from(Instant.now().minus(MAX_AGE));
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.{cache,tmp}")) {
                for (Path file : files) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file,
                                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        if (attrs.isRegularFile() && owned.test(file) &&
                                attrs.lastModifiedTime().compareTo(timeOldest) < 0) {
                            Files.deleteIfExists(file);
                        }
                    } catch (IOException | RuntimeException e) {
                        // another file may still be pruned
                    }
                }
            } catch (IOException | RuntimeException e) {
                // pruning is only housekeeping
            }
        }

        /**
         * Make a directory for its owner alone where the file system has permissions, unless
         * something is already there, which {@link #usable} then checks.
         *
         * @param dir  the directory
         *
         * @return the directory
         */
        private static Path made(Path dir) {
            try {
                Path parent = dir.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                    Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectory(dir);
                }
            } catch (FileAlreadyExistsException e) {
                // made by an earlier process, or by someone else, which usable() checks
            } catch (IOException | RuntimeException e) {
                // usable() finds nothing to use
            }
            return dir;
        }

        /**
         * @return the current user's cache directory
         */
        static CacheDirectory forCurrentUser() {
            return forUser(System.getProperty("os.name", ""), System::getenv, System::getProperty);
        }

        /**
         * @param sOS         the name of the operating system
         * @param env         the environment variables, null for the ones not set
         * @param properties  the system properties, null for the ones not set
         *
         * @return the user's cache directory: {@code xtclang/dir-repository} in
         *         {@code ~/Library/Caches} on macOS, in {@code %LOCALAPPDATA%} on Windows, and in
         *         {@code $XDG_CACHE_HOME} or {@code ~/.cache} on other systems, else a directory
         *         named for the user in {@code java.io.tmpdir}; repositories in
         *         {@code java.io.tmpdir} are not cached
         */
        static CacheDirectory forUser(String sOS, Function<String, String> env,
                                      Function<String, String> properties) {
            Optional<Path> home = absolute(properties.apply("user.home"));
            Optional<Path> dirBase;
            if (sOS.startsWith("Windows")) {
                dirBase = absolute(env.apply("LOCALAPPDATA"));
            } else if (sOS.startsWith("Mac")) {
                dirBase = home.map(path -> path.resolve("Library").resolve("Caches"));
            } else {
                dirBase = absolute(env.apply("XDG_CACHE_HOME"))
                        .or(() -> home.map(path -> path.resolve(".cache")));
            }
            Optional<Path> dirUser = dirBase.map(path ->
                    path.resolve("xtclang").resolve("dir-repository"));

            String         sUser       = Objects.requireNonNullElse(properties.apply("user.name"), "");
            Optional<Path> dirTemp     = absolute(properties.apply("java.io.tmpdir"));
            Optional<Path> dirFallback = dirTemp.map(path -> path.resolve(
                    "xtclang-dir-repository-" + sUser.replaceAll("[^A-Za-z0-9._-]", "_")));
            return new CacheDirectory(
                    Stream.of(dirUser, dirFallback).flatMap(Optional::stream).toList(),
                    dirTemp.flatMap(CacheDirectory::real).orElse(null));
        }

        /**
         * @param sPath  a path, or null
         *
         * @return the path, if it is an absolute one
         */
        private static Optional<Path> absolute(String sPath) {
            if (sPath == null || sPath.isEmpty()) {
                return Optional.empty();
            }

            try {
                return Optional.of(Path.of(sPath)).filter(Path::isAbsolute);
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
        }

        /**
         * @param path  a path
         *
         * @return the real path, if there is one
         */
        private static Optional<Path> real(Path path) {
            try {
                return Optional.of(path.toRealPath());
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        }

        /**
         * @return whether the current user, the owner of a file that this process makes, owns a
         *         path; true of every path on a file system without owners
         */
        private static Predicate<Path> ownedByCurrentUser() {
            UserPrincipal user;
            try {
                Path probe = Files.createTempFile("xvm-owner-", null);
                try {
                    user = Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
                } finally {
                    Files.deleteIfExists(probe);
                }
            } catch (UnsupportedOperationException e) {
                return path -> true;
            } catch (IOException | RuntimeException e) {
                return path -> false;
            }

            return path -> {
                try {
                    return user.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS));
                } catch (IOException e) {
                    return false;
                }
            };
        }

        /**
         * How long a cache file may go unwritten before it is deleted.
         */
        static final Duration MAX_AGE = Duration.ofDays(30);

        private static final int OTHERS_WRITABLE = 0002;
        private static final int STICKY          = 01000;
    }

    // ----- constants -----------------------------------------------------------------------------

    public static final FileFilter ModulesOnly = file ->
            file.getName().length() > 4 && file.getName().endsWith(".xtc") &&
            file.exists() && file.isFile() && file.canRead() && file.length() > 0;

    private static final int    CACHE_MAGIC           = 0xEC57CA11;
    private static final int    CACHE_VERSION         = 1;
    private static final int    MIN_CACHE_HEADER_SIZE = Integer.BYTES * 3 + Short.BYTES;
    private static final int    MIN_CACHE_ENTRY_SIZE  = Long.BYTES * 2 + Short.BYTES + Byte.BYTES;
    private static final String CACHE_SUFFIX          = ".cache";

    /**
     * The current user's cache directory, found on first use.
     */
    private static final Lazy<CacheDirectory> USER_CACHE = Lazy.of(CacheDirectory::forCurrentUser);

    // ----- fields --------------------------------------------------------------------------------

    private final File                     m_dir;
    private final boolean                  m_fRO;
    private final Supplier<CacheDirectory> m_cache;

    private       Map<File  , ModuleInfo> modulesByFile = new HashMap<>();
    private final Map<String, ModuleInfo> modulesByName = new TreeMap<>();
    private       long lastScan;
}
