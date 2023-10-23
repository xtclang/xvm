package org.xvm.tool;


import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.xvm.asm.FileRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.VersionTree;


/**
 * A ModuleRepository based on the information in ModuleInfo structures.
 */
public class ModuleInfoRepository
        implements ModuleRepository
    {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a ModuleRepository that represents the modules corresponding to the provided
     * ModuleInfos.
     *
     * @param infos     ModuleInfos, keyed by qualified module names
     * @param readOnly  true to make the repository "read-only"
     */
    public ModuleInfoRepository(Map<String, ModuleInfo> infos, boolean readOnly)
        {
        assert infos.keySet().stream().allMatch(Objects::nonNull);
        assert infos.values().stream().allMatch(info -> info != null
                && info.getQualifiedModuleName() != null
                && info.getBinaryFile() != null);

        this.infos    = infos;
        this.readOnly = readOnly;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly()
        {
        return readOnly;
        }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames()
        {
        return Collections.unmodifiableSet(infos.keySet());
        }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String name)
        {
        ModuleInfo info = infos.get(name);
        return info == null
                ? null
                : ensureRepo(info).getAvailableVersions(name);
        }

    @Override
    public ModuleStructure loadModule(String name)
        {
        ModuleInfo info = infos.get(name);
        return info == null
                ? null
                : ensureRepo(info).loadModule(name);
        }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException
        {
        if (readOnly)
            {
            throw new IOException("repository is read-only: " + this);
            }

        String     name = module.getName();
        ModuleInfo info = infos.get(name);
        if (info != null)
            {
            ensureRepo(info).storeModule(module);
            }
        }


    // ----- internal ------------------------------------------------------------------------------

    private FileRepository ensureRepo(ModuleInfo info)
        {
        String name = info.getQualifiedModuleName();
        FileRepository repo = repos.get(name);
        if (repo == null)
            {
            File file = info.getBinaryFile();
            File dir  = file.getParentFile();
            if (!readOnly && !dir.isDirectory())
                {
                if (!dir.mkdirs())
                    {
                    throw new IllegalStateException("could not create directory: " + dir);
                    }
                }
            repo = new FileRepository(file, readOnly);
            repos.put(name, repo);
            }
        return repo;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return infos.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this || !(obj instanceof ModuleInfoRepository that))
            {
            return obj == this;
            }

        return this.infos.equals(that.infos) &&
               this.readOnly == that.readOnly;
        }

    @Override
    public String toString()
        {
        return "ModuleInfoRepository(Infos=" + infos + ", RO=" + readOnly + ")";
        }


    // ----- fields --------------------------------------------------------------------------------

    private final Map<String, ModuleInfo>     infos;
    private final boolean                     readOnly;
    private final Map<String, FileRepository> repos = new HashMap<>();
    }