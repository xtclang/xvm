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
    }