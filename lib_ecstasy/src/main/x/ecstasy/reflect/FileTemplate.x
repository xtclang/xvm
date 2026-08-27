import mgmt.ModuleRepository;

/**
 * `FileTemplate` is a representation of an Ecstasy portable binary (".xtc") file.
 */
interface FileTemplate
        extends ComponentTemplate {
    /**
     * The persistent shape of an Ecstasy portable binary file.
     */
    enum Kind {
        /**
         * A FileTemplate containing a Single primary module, and the fingerprints of the modules
         * that the primary module depends upon. This Kind corresponds to the output of the
         * compiler.
         */
        Single,

        /**
         * A Library FileTemplate can contain multiple modules and/or versions of those modules that
         * have been bundled together, plus all of the module fingerprints of the dependencies of
         * those modules. This Kind corresponds to the contents managed by a repository, and also
         * is used for deploying an application composed of multiple modules as a single file.
         */
        Library,

        /**
         * A Linked FileTemplate is a fully linked, transitively closed module graph, with no
         * fingerprints. This Kind corresponds to the representation of a linked module at runtime,
         * and can also be used to pre-link and store an application in a manner that it is more
         * quickly loaded when being executed from an OS terminal.
         */
        Linked,
    }

    /**
     * The primary module that the `FileTemplate` represents.
     */
    @RO ModuleTemplate mainModule;

    /**
     * The persistent file kind.
     */
    @RO Kind kind;

    /**
     * The names of the real modules physically contained in the file. Module fingerprints are not
     * included.
     */
    @RO String[] moduleNames;

    /**
     * Indicates whether the file physically contains a library or Linked bundle.
     */
    @RO Boolean bundle.get() = kind != Single;

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
        return new ModuleTemplate[](children.size, i -> children[i].as(ModuleTemplate))
                .toArray(Constant, True);
    }

    @Override
    @RO FileTemplate containingFile.get() = this;
}
