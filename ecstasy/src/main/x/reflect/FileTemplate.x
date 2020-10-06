import mgmt.ModuleRepository;

/**
 * A FileTemplate is a representation of an Ecstasy portable binary (".xtc") file.
 */
interface FileTemplate
        extends ComponentTemplate
    {
    /**
     * The primary module that the FileTemplate represents.
     */
    @RO ModuleTemplate mainModule;

    /**
     * Indicates whether the FileTemplate has been "resolved", which means that it is ready to
     * answer all questions about the contained modules.
     */
    @RO Boolean resolved;

    /**
     * Resolve the dependent modules for this module, which means that the FileTemplate will
     * be ready to answer all questions about its children and their contributions.
     *
     * @return the resolved FileTemplate
     */
    FileTemplate resolve(ModuleRepository repository);

    /**
     * Obtain the specified module from the FileTemplate. Note that the returned template may not
     * be [resolved](`ModuleTemplate.resolved`).
     *
     * @param name  the qualified module name
     *
     * @return  the specified ModuleTemplate
     *
     * @throws IllegalArgument if the module does not exist
     */
    ModuleTemplate getModule(String name);
    }
