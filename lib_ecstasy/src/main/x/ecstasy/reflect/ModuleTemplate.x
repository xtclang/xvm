/**
 * A ModuleTemplate is a representation of an Ecstasy `module`.
 */
interface ModuleTemplate
        extends ClassTemplate {
    /**
     * The fully qualified name of the module, such as "ecstasy.xtclang.org".
     */
    @RO String qualifiedName;

    /**
     * (Optional) The module version. For unresolved modules (fingerprints) it would indicate a
     *            desired version.
     */
    @RO Version? version;

    /**
     * The modules that this module depends on by linkage, both directly and indirectly.
     * The map's key is a module path.
     */
    @RO immutable Map<String, ModuleTemplate> modulesByPath;

    /**
     * Module's parent is always a FileTemplate.
     */
    @Override
    @RO FileTemplate parent;

    @Override
    @RO ModuleTemplate containingModule.get() = this;

    @Override
    @RO String path.get() = qualifiedName + ':';

    @Override
    @RO String displayName.get() {
        ModuleTemplate mainModule = containingFile.mainModule;
        return mainModule.qualifiedName == this.qualifiedName
                ? name
                : qualifiedName;
    }

    /**
     * Indicates whether this ModuleTemplate is a module fingerprint.
     *
     * A module fingerprint is a ModuleTemplate containing only the subset of the identities within
     * that module that is depended upon by other modules within a FileTemplate, and thus required
     * for linking to. This structure is called a "module fingerprint", because it represents only
     * the outline of the module, and multiple versions of that module may be able to match
     * (fulfill the requirements of) that same fingerprint. As part of the linking process, a module
     * fingerprint that is depended upon is replaced with an actual, complete module definition that
     * matches the fingerprint.
     *
     * A module with `fingerprint == True` must be `resolved == False`.
     *
     * TODO the default implementation of this property corresponds to the pre-existing behavior
     *      of the FileStructure and ModuleStructure implementations. The default implementation
     *      should be removed as soon as all class implementations of this interface have been
     *      updated
     */
    @RO Boolean fingerprint.get() = !resolved && this != parent.mainModule;

    /**
     * Indicates whether this ModuleTemplate has been linked, for example by [FileTemplate.resolve].
     *
     * An unresolved module may be a [fingerprint] module. A `resolved` module is never a
     * [fingerprint] module.
     */
    @RO Boolean resolved;

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = qualifiedName.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = qualifiedName.appendTo(buf);
}
