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
     * Indicates whether this template has been "resolved", which means that it is ready to answer
     * all questions about the module's content (children, contributions, etc.)
     */
    @RO Boolean resolved;

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = qualifiedName.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = qualifiedName.appendTo(buf);
}