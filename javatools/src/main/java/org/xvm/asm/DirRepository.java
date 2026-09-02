package org.xvm.asm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

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
        assert dir != null && dir.isDirectory();

        m_dir = dir;
        m_fRO = fReadOnly;
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
        ModuleInfo info = modulesByName.get(sModule);
        return info == null ? null : info.ensureModule();
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
        File fileCache = getCacheFile();
        if (fileCache == null || !fileCache.isFile()) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(fileCache.toPath())))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION ||
                    !in.readUTF().equals(m_dir.getCanonicalPath())) {
                return null;
            }

            int  cModules  = in.readInt();
            long cbEntries = fileCache.length() - MIN_CACHE_HEADER_SIZE;
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

                File   file      = new File(m_dir, fileName);
                long   timestamp = in.readLong();
                long   size      = in.readLong();
                boolean err      = in.readBoolean();
                String name      = err ? null : in.readUTF();

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
        File fileCache = getCacheFile();
        if (fileCache == null) {
            return;
        }

        Path pathCache = fileCache.toPath();
        Path pathTemp  = null;
        try {
            Path pathDir = pathCache.getParent();
            Files.createDirectories(pathDir);
            pathTemp = Files.createTempFile(pathDir, fileCache.getName(), ".tmp");

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(pathTemp)))) {
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

            try {
                Files.move(pathTemp, pathCache,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(pathTemp, pathCache, StandardCopyOption.REPLACE_EXISTING);
            }
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
     * @return the persistent cache file for this repository, or null if it cannot be determined
     */
    private File getCacheFile() {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (tempDir == null) {
            return null;
        }

        try {
            String path = m_dir.getCanonicalPath();
            String key  = Integer.toUnsignedString(path.hashCode(), 16);
            return new File(new File(tempDir, CACHE_DIRECTORY), key + CACHE_SUFFIX);
        } catch (IOException | RuntimeException e) {
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

    // ----- constants -----------------------------------------------------------------------------

    public static final FileFilter ModulesOnly = file ->
            file.getName().length() > 4 && file.getName().endsWith(".xtc") &&
            file.exists() && file.isFile() && file.canRead() && file.length() > 0;

    private static final int    CACHE_MAGIC           = 0xEC57CA11;
    private static final int    CACHE_VERSION         = 1;
    private static final int    MIN_CACHE_HEADER_SIZE = Integer.BYTES * 3 + Short.BYTES;
    private static final int    MIN_CACHE_ENTRY_SIZE  = Long.BYTES * 2 + Short.BYTES + Byte.BYTES;
    private static final String CACHE_DIRECTORY       = "xvm-dir-repository";
    private static final String CACHE_SUFFIX          = ".cache";

    // ----- fields --------------------------------------------------------------------------------

    private final File    m_dir;
    private final boolean m_fRO;

    private       Map<File  , ModuleInfo> modulesByFile = new HashMap<>();
    private final Map<String, ModuleInfo> modulesByName = new TreeMap<>();
    private       long lastScan;
}
