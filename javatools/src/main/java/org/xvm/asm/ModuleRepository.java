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
     * For a specified domain name, obtain a set of qualified module names that are known by this
     * repository.
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
     * Obtain a set of all qualified module names known by this repository.
     *
     * @return a set of qualified module names
     */
    Set<String> getModuleNames();

    /**
     * Determine the set of available versions of the specified module.
     *
     * @param sModule  a fully qualified module name
     *
     * @return a VersionTree containing the available versions; an empty VersionTree indicates that
     *         a versionless module is available; null indicates no such module
     */
    default VersionTree<Boolean> getAvailableVersions(String sModule) {
        ModuleStructure module = loadModule(sModule);
        return module == null ? null : module.getVersions();
    }

    /**
     * Load the specified module. If the module is loaded from an .xtc bundle, the ModuleStructure
     * may contain multiple versions.
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
     * @param fExact   true to specify that exact version number; false to allow more updated
     *                 versions to be substituted
     *
     * @return a ModuleStructure, or null if the specified module is unavailable
     */
    default ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        ModuleStructure module = loadModule(sModule);
        if (module == null) {
            return null;
        }

        if (version == null) {
            version = Version.NONE;
        }

        Version useVersion = module.containsVersion(version)
                ? version
                : module.getVersions().selectVersion(version, fExact);

        return useVersion == null ? null : module.extractVersion(useVersion);
    }

    /**
     * Store the specified module in the repository.
     *
     * @param module  a ModuleStructure to store in the repository
     *
     * @throws IOException  various IO exceptions could be thrown to indicate that the repository is
     *         read-only, that the specified module won't be stored in the repository, etc.
     */
    void storeModule(ModuleStructure module)
            throws IOException;

    // ----- constants -----------------------------------------------------------------------------

    /**
     * A constant empty array of <tt>ModuleRepository</tt>.
     */
    ModuleRepository[] NO_REPOS = new ModuleRepository[0];
}
