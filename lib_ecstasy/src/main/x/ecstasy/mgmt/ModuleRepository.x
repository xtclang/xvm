import reflect.ModuleTemplate;

/**
 * Represents the source of compiled module structures.
 */
interface ModuleRepository {
    /**
     * Set of domain names that are known by this repository.
     */
    @RO immutable Set<String> moduleNames;

    /**
     * Obtain a module template for the specified module. Note, that the returned template may not
     * be [resolved](`ModuleTemplate.resolved`)
     *
     * @param name  the module name (qualified)
     *
     * @return True iff the module exists
     * @return (conditional) the ModuleTemplate
     */
    conditional ModuleTemplate getModule(String name);

    /**
     * Obtain a resolved module template for the specified name.
     *
     * @param name  the module name (qualified)
     *
     * @return the resolved ModuleTemplate
     *
     * @throws IllegalArgument if the module does not exist
     * @throws Exception       if the module cannot be resolved
     */
    ModuleTemplate getResolvedModule(String name) {
        assert ModuleTemplate template := getModule(name) as $"Missing module {name}";
        return template.parent.resolve(this).mainModule;
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