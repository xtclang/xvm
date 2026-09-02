package org.xvm.asm;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.xvm.asm.FileStructure.FileInfo;

import org.xvm.asm.constants.ModuleConstant;

import static org.xvm.util.Handy.BINARY_EXTENSION;
import static org.xvm.util.Handy.hasBinaryExtension;
import static org.xvm.util.Handy.removeSourceExtension;
import static org.xvm.util.Handy.resolveFile;

/**
 * A simple ModuleRepository for a single file. The file is most commonly a single-module .xtc, but
 * an .xtc file is a module container and may hold multiple modules (a "bundle"), in which case
 * every real (non-fingerprint) module in the container is exposed by this repository.
 */
public class FileRepository
        implements ModuleRepository {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a single-file ModuleRepository.
     *
     * @param file      the file that contains the module(s)
     * @param readOnly  true to make the repository "read-only"
     */
    public FileRepository(File file, boolean readOnly) {
        assert file != null && !file.isDirectory();

        this.file     = moduleFile(file);
        this.readOnly = readOnly;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the module file (which may or may not exist)
     */
    public File getFile() {
        return file;
    }

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public synchronized Set<String> getModuleNames() {
        return validateCache() ? cachedVersionsByName.keySet() : Set.of();
    }

    @Override
    public synchronized VersionTree<Boolean> getAvailableVersions(String sModule) {
        return validateCache() ? cachedVersionsByName.get(sModule) : null;
    }

    @Override
    public synchronized ModuleStructure loadModule(String sModule) {
        ModuleStructure module = null;
        if (getModuleNames().contains(sModule) && ensureFileStructure()) {
            if (cachedModuleStructures != null) {
                module = cachedModuleStructures.get(moduleId(sModule));
            }
            if (module == null) {
                module = cachedFileStructure.getChild(sModule);
                if (requiresSeparation(module)) {
                    module = forceSeparation(moduleId(sModule), module);
                }
            }
        }
        return module;
    }

    @Override
    public synchronized ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        // verify that the module name is known
        if (!getModuleNames().contains(sModule)) {
            return null;
        }

        if (version == null) {
            return loadModule(sModule);
        }

        // verify that the module name has known versions
        VersionTree<Boolean> versions = getAvailableVersions(sModule);
        if (versions.isEmpty()) {
            return null;
        }

        // check the module cache
        ModuleConstant idModule = moduleId(sModule, version);
        if (cachedModuleStructures != null) {
            ModuleStructure module = cachedModuleStructures.get(idModule);
            if (module != null) {
                return module;
            }
        }

        // verify that a desired version exists
        Version selectedVersion = versions.selectVersion(version, fExact);
        if (selectedVersion == null) {
            return null;
        }

        // re-check the module cache (in case the selected version differs from the first check)
        if (!selectedVersion.equals(version)) {
            idModule = moduleId(sModule, selectedVersion);
            if (cachedModuleStructures != null) {
                ModuleStructure module = cachedModuleStructures.get(idModule);
                if (module != null) {
                    return module;
                }
            }
        }

        // obtain the selected version of the ModuleStructure from the FileStructure
        if (!ensureFileStructure()) {
            return null;
        }
        ModuleStructure module = cachedFileStructure.getChild(idModule);
        if (module == null) {
            // the FileStructure can bundle any number of versions of a module as siblings under the
            // name of the module
            module = loadModule(sModule).extractVersion(selectedVersion);
            ensureModuleCache().put(idModule, module);
        }

        if (requiresSeparation(module)) {
            module = forceSeparation(idModule, module);
        }
        return module;
    }

    @Override
    public synchronized void storeModule(ModuleStructure module)
            throws IOException {
        if (readOnly || file.exists() && !file.isFile()) {
            throw new IOException("repository is read-only: " + this);
        }

        String sModule  = module.getName();
        File   dir      = resolveFile(file).getParentFile();
        File   workFile = File.createTempFile("tmp." + sModule, ".xtc", dir);

        cachedVersionsByName   = Map.of();
        cachedFileStructure    = null;
        cachedModuleStructures = null;
        cacheOk                = false;

        FileStructure fileStructure = module.getFileStructure();
        try {
            fileStructure.writeTo(workFile);
            file.delete();
            if (workFile.renameTo(file)) {
                cachedVersionsByName   = fileStructure.buildFileInfo().modules();
                cachedFileStructure    = fileStructure;
                cacheOk                = true;
            }
        } catch (IOException e) {
            throw new IOException("Error writing module to file: " + file, e);
        } finally {
            if (workFile.exists()) {
                if (!workFile.delete()) {
                    workFile.deleteOnExit();
                }
            }
        }

        this.timestamp = file.lastModified();
        this.size      = file.length();
        this.lastScan  = System.currentTimeMillis();
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || obj instanceof FileRepository that
                && this.file.equals(that.file)
                && this.readOnly == that.readOnly;
    }

    @Override
    public String toString() {
        return "FileRepository(Path=" + file.toString() + ", RO=" + readOnly + ")";
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Convert a source or extensionless module file name to its compiled module file name.
     *
     * @param file  the module file
     *
     * @return the same file if it already names an .xtc file, otherwise the .xtc sibling
     */
    private static File moduleFile(File file) {
        return Optional.of(file.getName())
                .filter(sName -> !hasBinaryExtension(sName))
                .map(sName -> file.toPath()
                        .resolveSibling(removeSourceExtension(sName) + BINARY_EXTENSION)
                        .toFile())
                .orElse(file);
    }

    /**
     * Make sure that the cache is up to date.
     */
    private boolean validateCache() {
        // assume cache is up-to-date if it has been checked recently; assumption is that activity
        // comes in bursts; also assume cache is up-to-date if nothing appears to have changed
        if (System.currentTimeMillis() < lastScan + 60/*ms*/
                || timestamp == file.lastModified() && size == file.length()) {
            return cacheOk;
        }

        // cache is not up-to-date; clear whatever was cached before
        timestamp              = file.lastModified();
        size                   = file.length();
        cachedVersionsByName   = Map.of();
        cachedFileStructure    = null;
        cachedModuleStructures = null;
        cacheOk                = false;
        lastScan               = System.currentTimeMillis();

        // load the cache if possible
        if (file.exists() && file.isFile() && file.canRead()) {
            // read just the module contents of the FileStructure (not the entire FileStructure)
            FileInfo info = readFileInfo();
            if (info != null) {
                cachedVersionsByName = info.modules();
                cacheOk              = true;
            }
        }
        return cacheOk;
    }

    // TODO GG review - do we want to do this?!?
    private boolean requiresSeparation(ModuleStructure module) {
        return module != null && !module.isMainModule() && module.getFileStructure() == cachedFileStructure;
    }

    // TODO GG review - do we want to do this?!?
    private ModuleStructure forceSeparation(ModuleConstant idModule, ModuleStructure module) {
        // a non-main module of a multi-module container ("bundle") is served as a detached
        // copy (memoized), so that every consumer - the linker, the runtime compiler's
        // fingerprint hoisting, reflection - sees the single-module-file shape it expects
        module = module.detachedCopy();
        ensureModuleCache().put(idModule, module);
        return module;
    }

    /**
     * Verify that the FileStructure is loaded and current, loading or re-loading it if necessary.
     *
     * @return true iff the FileStructure is loaded and current
     */
    private boolean ensureFileStructure() {
        if (validateCache() && cachedFileStructure == null) {
            if ((cachedFileStructure = readFileStructure()) == null) {
                cacheOk = false;
            }
        }
        return cacheOk;
    }

    /**
     * @return the FileInfo freshly read from the file system, or `null` on any failure
     */
    private FileInfo readFileInfo() {
        try {
            return FileStructure.readFileInfo(file);
        } catch (Exception e) {
            if (!reportedFileStructureError) {
                reportedFileStructureError = true;
                System.out.println("Error loading FileInfo from file: " + file + "; " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * @return the FileStructure freshly read from the file system, or `null` on any failure
     */
    private FileStructure readFileStructure() {
        try {
            return new FileStructure(file);
        } catch (Exception e) {
            if (!reportedFileInfoError) {
                reportedFileInfoError = true;
                System.out.println("Error loading FileStructure from file: " + file + "; " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * @param sModule  qualified module name
     *
     * @return the corresponding ModuleConstant
     */
    private ModuleConstant moduleId(String sModule) {
        assert ensureFileStructure();
        return cachedFileStructure.getConstantPool().ensureModuleConstant(sModule);
    }

    /**
     * @param sModule  qualified module name
     * @param version  the module version
     *
     * @return the corresponding ModuleConstant
     */
    private ModuleConstant moduleId(String sModule, Version version) {
        assert ensureFileStructure();
        return cachedFileStructure.getConstantPool().ensureModuleConstant(sModule, version);
    }

    /**
     * @return a cache of ModuleStructures that were created or stored by this repository
     */
    private Map<ModuleConstant, ModuleStructure> ensureModuleCache() {
        assert cacheOk;
        Map<ModuleConstant, ModuleStructure> cache = cachedModuleStructures;
        if (cache == null) {
            cachedModuleStructures = cache = new HashMap<>();
        }
        return cache;
    }

    // ----- fields --------------------------------------------------------------------------------

    private final File    file;
    private final boolean readOnly;

    private long    timestamp;
    private long    size;
    private long    lastScan;
    private boolean cacheOk;

    private boolean reportedFileInfoError;
    private boolean reportedFileStructureError;

    /**
     * Cached file contents: module names and versions thereof.
     */
    private Map<String, VersionTree<Boolean>> cachedVersionsByName = Map.of();

    /**
     * Cached file contents: the entire FileStructure
     */
    private FileStructure cachedFileStructure;

    /**
     * Cached ModuleStructures.
     */
    private Map<ModuleConstant, ModuleStructure> cachedModuleStructures = null;
}
