package org.xvm.asm;


import java.util.Set;

import org.xvm.asm.ConstantPool.VersionConstant;


/**
 * An interface representing the ability to find Modules by identity.
 *
 * @author cp 2016.10.10
 */
public interface ModuleRepository
    {
    /**
     * Obtain a set of domain names that are known by this repository.
     *
     * @return a set of domain names
     */
    public Set<String> getDomainNames();

    /**
     * For a specified domain name, obtain a set of qualified module names
     * that are known by this repository.
     *
     * @param sDomain  a domain name
     *
     * @return a set of qualified module names
     */
    public Set<String> getModuleNames(String sDomain);

    /**
     * Obtain a set of all of the qualified module names known by this
     * repository.
     *
     * @return a set of qualified module names
     */
    public Set<String> getModuleNames();

    /**
     * Determine the set of available versions of the specified module.
     *
     * @param sModule  a fully qualified module name
     *
     * @return a non-null set of versions available for the specified module;
     *         note that the set may contain a null value, indicating a
     *         versionless module
     */
    public Set<VersionConstant> getAvailableVersions(String sModule);

    /**
     * Load the specified module.
     *
     * @param sModule  a fully qualified module name
     *
     * @return a ModuleStructure, or null if the specified module is unavailable
     */
    public ModuleStructure loadModule(String sModule);

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
    public ModuleStructure loadModule(String sModule, VersionConstant version, boolean fExact);

    /**
     * Store the specified module in the repository.
     *
     * @param module  a ModuleStructure to store in the repository
     *
     * @throws RuntimeException  various runtime exceptions could be thrown to
     *         indicate that the repository is read-only, that the specified
     *         module is not able to be stored in the repository, etc.
     */
    public void storeModule(ModuleStructure module);
    }
