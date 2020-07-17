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
     * Obtain a module template for the specified module.
     */
    ModuleTemplate getModule(String name);
    }