package org.xvm.asm;


import java.io.IOException;

import java.util.Set;
import java.util.TreeSet;


/**
 * An interface representing the ability to find Modules by identity.
 */
public interface ModuleRepository
    {
    /**
     * Obtain a set of domain names that are known by this repository.
     *
     * @return a set of domain names
     */
    default Set<String> getDomainNames()
        {
        Set<String> modules = getModuleNames();
        Set<String> domains = new TreeSet<>();
        for (String module : modules)
            {
            int of = module.indexOf('.');
            if (of >= 0)
                {
                domains.add(module.substring(of + 1));
                }
            }
        return domains;
        }

    /**
     * For a specified domain name, obtain a set of qualified module names
     * that are known by this repository.
     *
     * @param sDomain  a domain name
     *
     * @return a set of qualified module names
     */
    default Set<String> getModuleNames(String sDomain)
        {
        Set<String> modules = getModuleNames();
        Set<String> names = new TreeSet<>();
        for (String module : modules)
            {
            int of = module.indexOf('.');
            names.add(of < 0 ? module : module.substring(0, of));
            }
        return names;
        }

    /**
     * Obtain a set of all of the qualified module names known by this
     * repository.
     *
     * @return a set of qualified module names
     */
    Set<String> getModuleNames();

    /**
     * Determine the set of available versions of the specified module.
     *
     * @param sModule  a fully qualified module name
     *
     * @return a non-null set of versions available for the specified module;
     *         note that the set may contain a null value, indicating a
     *         versionless module; or null if the module does not exist
     */
    default VersionTree<Boolean> getAvailableVersions(String sModule)
        {
        ModuleStructure module = loadModule(sModule);
        return module == null ? null : module.getFileStructure().getVersionTree();
        }

    /**
     * Load the specified module.
     *
     * @param sModule  a fully qualified module name
     *
     * @return a ModuleStructure, or null if the specified module is unavailable
     */
    ModuleStructure loadModule(String sModule);

    /**
     * Load the specified version of the specified module.
     *
     * @param sModule  a fully qualified module name
     * @param version  a version number, or null to specify a versionless module
     * @param fExact   true to specify that exact version number; false to allow
     *                 more updated versions to be substituted
     *
     * @return a ModuleStructure, or null if the specified module is unavailable
     */
    default ModuleStructure loadModule(String sModule, Version version, boolean fExact)
        {
        ModuleStructure module = loadModule(sModule);
        if (module == null)
            {
            return null;
            }

        Version       useVersion = null;
        FileStructure container  = module.getFileStructure();
        if (container.containsVersion(version))
            {
            useVersion = version;
            }
        else
            {
            // check each version in the module to see if it would work; keep the most appropriate one
            for (Version possibleVer : container.getVersionTree())
                {
                if (possibleVer.isSubstitutableFor(version))
                    {
                    if (version.isSubstitutableFor(possibleVer))
                        {
                        // use that version; it's the same as this version (except for .0 etc.)
                        useVersion = possibleVer;
                        break;
                        }

                    if (!fExact)
                        {
                        if (useVersion == null || useVersion.isSubstitutableFor(possibleVer))
                            {
                            // use the oldest available version that matches
                            useVersion = possibleVer;
                            }
                        }
                    }
                }

            if (useVersion == null)
                {
                return null;
                }
            }

        if (container.getVersionTree().size() > 1)
            {
            container.purgeVersionsExcept(useVersion);
            }

        return module;
        }

    /**
     * Store the specified module in the repository.
     *
     * @param module  a ModuleStructure to store in the repository
     *
     * @throws IOException  various IO exceptions could be thrown to
     *         indicate that the repository is read-only, that the specified
     *         module is not able to be stored in the repository, etc.
     */
    void storeModule(ModuleStructure module)
            throws IOException;


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A constant empty array of <tt>ModuleRepository</tt>.
     */
    ModuleRepository[] NO_REPOS = new ModuleRepository[0];
    }