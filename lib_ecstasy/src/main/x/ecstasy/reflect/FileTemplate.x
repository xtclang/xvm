import mgmt.ModuleRepository;

/**
 * `FileTemplate` is a representation of an Ecstasy portable binary (".xtc") file.
 */
interface FileTemplate
        extends ComponentTemplate {
    /**
     * The primary module that the `FileTemplate` represents.
     */
    @RO ModuleTemplate mainModule;

    /**
     * Indicates whether the `FileTemplate` has been "resolved", which means that it is ready to
     * answer all questions about the contained modules.
     */
    @RO Boolean resolved;

    /**
     * Resolve the dependent modules for this module, which means that the `FileTemplate` will
     * be ready to answer all questions about its children and their contributions.
     *
     * @return the resolved `FileTemplate`
     *
     * @throws Exception if some of the dependent modules cannot be resolved
     */
    FileTemplate resolve(ModuleRepository repository);

    /**
     * Obtain the specified version of the main module from the `FileTemplate`.
     *
     * If the version is specified, choose whichever of the present module versions
     * [satisfies](Version.satisfies) it, otherwise take any available (latest) version.
     *
     * Note: the returned `ModuleTemplate` may not be [resolved](ModuleTemplate.resolved).
     * Note2: the returned `ModuleTemplate` may have a different parent `FileTemplate`.
     *
     * @param version  (optional) the module version
     *
     * @return True iff there is a module that satisfies the specified version
     * @return (conditional) the `ModuleTemplate`
     */
    conditional ModuleTemplate extractVersion(Version? version = Null);

    /**
     * The date/time at which the `FileTemplate` was created. The value is not `Null` for
     * `FileTemplates` that are read from a persistent storage.
     */
    @RO Time? created;

    /**
     * The contents of the file template as a Byte array.
     */
    @RO immutable Byte[] contents;

    /**
     * An array of modules contained within this `FileTemplate`.
     *
     * Note: the modules are most probably unresolved (fingerprints).
     */
    @RO ModuleTemplate[] modules.get() {
        ComponentTemplate[] children = children();
        return new ModuleTemplate[](children.size, i -> children[i].as(ModuleTemplate)).freeze(True);
    }

    @Override
    @RO FileTemplate containingFile.get() = this;
}