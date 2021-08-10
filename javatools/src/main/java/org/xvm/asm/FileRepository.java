package org.xvm.asm;


import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.Set;


/**
 * A simple ModuleRepository for a single file with a single module.
 * TODO add support for more than one module in the file (i.e. if any other modules are embedded)
 */
public class FileRepository
        implements ModuleRepository
    {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a single-file ModuleRepository.
     *
     * @param file       the file that contains the single module
     * @param fReadOnly  true to make the repository "read-only"
     */
    public FileRepository(File file, boolean fReadOnly)
        {
        assert file != null && !file.isDirectory();

        String fname = file.getName();
        if (!fname.endsWith(".xtc"))
            {
            if (fname.endsWith(".x"))
                {
                file = new File(file.getParentFile(), fname.substring(0, fname.lastIndexOf('.')) + ".xtc");
                }
            else
                {
                file = new File(file.getParentFile(), fname + ".xtc");
                }
            }

        assert (fReadOnly && file.canRead()) || (!fReadOnly && file.canWrite());

        this.file = file;
        this.fRO = fReadOnly;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the module file (which may or may not exist)
     */
    public File getFile()
        {
        return file;
        }

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly()
        {
        return fRO;
        }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames()
        {
        checkCache();
        return file.exists() && name != null ? Collections.singleton(name) : Collections.EMPTY_SET;
        }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule)
        {
        checkCache();
        return !err && name.equals(sModule) ? versions : null;
        }

    @Override
    public ModuleStructure loadModule(String sModule)
        {
        ModuleStructure module = checkCache();
        if (sModule.equals(name))
            {
            return module == null ? ensureModule() : module;
            }
        return null;
        }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException
        {
        if (fRO)
            {
            throw new IOException("repository is read-only: " + this);
            }

        if (file.exists() && !file.delete())
            {
            err = true;
            throw new IOException("unable to delete " + file);
            }

        try
            {
            module.getFileStructure().writeTo(file);
            err = false;
            }
        catch (IOException e)
            {
            err = true;
            throw new IOException("Error writing module to file: " + file);
            }

        this.name      = module.getIdentityConstant().getName();
        this.versions  = module.getFileStructure().getVersionTree();
        this.timestamp = file.lastModified();
        this.size      = file.length();
        this.module    = module;
        this.lastScan  = System.currentTimeMillis();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return file.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this || !(obj instanceof FileRepository))
            {
            return obj == this;
            }

        FileRepository that = (FileRepository) obj;
        return this.file.equals(that.file)
                && this.fRO == that.fRO;
        }

    @Override
    public String toString()
        {
        return "FileRepository(Path=" + file.toString() + ", RO=" + fRO + ")";
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Make sure that the cache is up to date.
     *
     * @return the module, if it is handy, or null if it is not handy
     */
    ModuleStructure checkCache()
        {
        if (isCacheValid())
            {
            return module;  // might be null, or might not be
            }

        this.timestamp = file.lastModified();
        this.size      = file.length();
        this.err       = false;

        ModuleStructure module = tryLoad();
        if (module == null)
            {
            this.name     = null;
            this.versions = null;
            this.err      = true;
            }
        else
            {
            this.name     = module.getIdentityConstant().getName();
            this.versions = module.getFileStructure().getVersionTree();
            }

        this.module   = null;
        this.lastScan = System.currentTimeMillis();
        return module;
        }

    /**
     * Quick scan to make sure that the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private boolean isCacheValid()
        {
        // only scan once a second (at the most)
        if (System.currentTimeMillis() < lastScan + 1000)
            {
            return true;
            }

        if (!file.exists())
            {
            name   = null;
            module = null;
            return true;
            }

        if (name == null || timestamp != file.lastModified() || size != file.length())
            {
            return false;
            }

        return true;
        }

    private ModuleStructure ensureModule()
        {
        if (err)
            {
            return null;
            }

        if (module == null || module.isModified())
            {
            module = tryLoad();
            }

        return module;
        }

    private ModuleStructure tryLoad()
        {
        try
            {
            FileStructure struct = new FileStructure(file);
            return struct.getModule();
            }
        catch (Exception e)
            {
            System.out.println("Error loading module from file: " + file + "; " + e.getMessage());
            }

        err = true;
        return null;
        }


    // ----- fields --------------------------------------------------------------------------------

    private final File           file;
    private final boolean        fRO;

    private String               name;
    private VersionTree<Boolean> versions;
    private long                 timestamp;
    private long                 size;
    private ModuleStructure      module;
    private long                 lastScan;
    private boolean              err;
    }
