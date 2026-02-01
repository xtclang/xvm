package org.xvm.asm;


import java.io.IOException;

import java.util.Set;
import java.util.TreeSet;


/**
 * An interface representing the ability to find Modules by identity.
 */
public interface ModuleRepository {
    /**
     * Obtain a set of domain names that are known by this repository.
     *
     * @return a set of domain names
     */
    default Set<String> getDomainNames() {
        Set<String> modules = getModuleNames();
        Set<String> domains = new TreeSet<>();
        for (String module : modules) {
            int of = module.indexOf('.');
            if (of >= 0) {
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
    default Set<String> getModuleNames(String sDomain) {
        Set<String> modules = getModuleNames();
        Set<String> names = new TreeSet<>();
        for (String module : modules) {
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
    default VersionTree<Boolean> getAvailableVersions(String sModule) {
        ModuleStructure module = loadModule(sModule);
        return module == null ? null : module.getVersions();
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
    default ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        ModuleStructure module = loadModule(sModule);
        if (module == null || version == null) {
            return module;
        }

        // First check for exact containment
        if (module.containsVersion(version)) {
            return module.extractVersion(version);
        }

        // Find the lowest (oldest) version that is substitutable for the requested version
        Version useVersion = module.getVersions().findLowestSubstitutable(version);
        if (useVersion == null) {
            return null;
        }

        // Check if it's a "same version" match (mutually substitutable, e.g., 2.0 vs 2.0.0)
        boolean sameVersion = version.isSubstitutableFor(useVersion);
        if (sameVersion || !fExact) {
            return module.extractVersion(useVersion);
        }

        // fExact was requested but only a non-exact substitute was found
        return null;
    }

    /**
     * Store the specified module in the repository.
     *
     * @param module  a ModuleStructure to store in the repository
     *
     * @throws IOException  various IO exceptions could be thrown to
     *         indicate that the repository is read-only, that the specified
     *         module won't be stored in the repository, etc.
     */
    void storeModule(ModuleStructure module)
            throws IOException;


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A constant empty array of <tt>ModuleRepository</tt>.
     */
    ModuleRepository[] NO_REPOS = new ModuleRepository[0];
}