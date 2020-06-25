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
     * Obtain a binary image of the specified module.
     */
    immutable Byte[] getModule(String name);
    }