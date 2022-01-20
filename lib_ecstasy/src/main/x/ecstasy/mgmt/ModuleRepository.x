import reflect.ModuleTemplate;

/**
 * Represents the source of compiled module structures.
 */
interface ModuleRepository
    {
    /**
     * Set of domain names that are known by this repository.
     */
    @RO immutable Set<String> moduleNames;

    /**
     * Obtain a module template for the specified module. Note, that the returned template may not
     * be [resolved](`ModuleTemplate.resolved`)
     *
     * @throws IllegalArgument if the module does not exist
     */
    ModuleTemplate getModule(String name);

    /**
     * Obtain a resolved module template for the specified module.
     *
     * @throws IllegalArgument if the module does not exist
     * @throws Exception       if the module cannot be resolved
     */
    ModuleTemplate getResolvedModule(String name)
        {
        return getModule(name).parent.resolve(this).mainModule;
        }

    /**
     * Store the specified module template in the repository.
     *
     * @param template  a ModuleTemplate to store in the repository
     *
     * @throws Exception  various runtime exceptions could be thrown to
     *         indicate that the repository is read-only, that the specified
     *         module is not able to be stored in the repository, etc.
     */
    void storeModule(ModuleTemplate template);
    }