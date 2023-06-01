import ecstasy.lang.ErrorList;

import ecstasy.mgmt.ModuleRepository;

interface Compiler {
    /**
     * Provide a ModuleRepository of modules that are necessary for the compilation.
     */
    void setLibraryRepository(ModuleRepository repo);

    /**
     * Locations of resulting .xtc files representing compiled modules.
     *
     * TODO: supplement/replace with setResultRepository(ModuleRepository repo)
     */
    void setResultLocation(Directory outputDir);

    /**
     * Compile the specified source files.
     *
     * @param  sources locations of existing .x files representing modules to be compiled
     *
     * @return success True if the compilation was successful and .xtc modules were produced
     * @return errors  the errors (including warnings);  must not be empty if 'success' is False
     */
    (Boolean success, String[] errors) compile((Directory|File)[] sources);
}