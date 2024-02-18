import mgmt.ModuleRepository;

/**
 * A FileTemplate is a representation of an Ecstasy portable binary (".xtc") file.
 */
interface FileTemplate
        extends ComponentTemplate {
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
     *
     * @throws Exception if some of the dependent modules cannot be resolved
     */
    FileTemplate resolve(ModuleRepository repository);

    /**
     * Obtain the specified module from the FileTemplate. Note that the returned template may not
     * be [resolved](`ModuleTemplate.resolved`).
     *
     * @param name  the qualified module name
     *
     * @return True iff the module exists
     * @return (conditional) the ModuleTemplate
     */
    conditional ModuleTemplate getModule(String name);

    /**
     * The date/time at which the FileTemplate was created. The value is not Null for FileTemplates
     * that are read from a persistent storage.
     */
    @RO Time? created;

    /**
     * The contents of the file template as a Byte array.
     */
    @RO Byte[] contents;

    /**
     * An array of qualified module names contained within this FileTemplate.
     */
    @RO String[] moduleNames.get() {
        ComponentTemplate[] children = children();
        return new String[](children.size, i -> children[i].as(ModuleTemplate).qualifiedName)
                .freeze(True);
    }

    @Override
    @RO FileTemplate containingFile.get() {
        return this;
    }
}