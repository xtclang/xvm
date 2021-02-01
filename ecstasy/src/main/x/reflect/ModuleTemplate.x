/**
 * A ModuleTemplate is a representation of an Ecstasy `module`.
 */
interface ModuleTemplate
        extends PackageTemplate
    {
    /**
     * The fully qualified name of the module, such as "ecstasy.xtclang.org".
     */
    @RO String qualifiedName;

    /**
     * The modules that this module depends on by linkage, both directly and indirectly.
     * The map's key is a module path; the value is the module qualified name.
     */
    @RO immutable Map<String, String> moduleNamesByPath;

    /**
     * Module's parent is always a FileTemplate.
     */
    @Override
    @RO FileTemplate parent;

    @Override
    @RO String path.get()
        {
        return qualifiedName;
        }

    /**
     * Indicates whether this template has been "resolved", which means that it is ready to answer
     * all questions about the module's content (children, contributions, etc.)
     */
    @RO Boolean resolved.get()
        {
        return parent.resolved;
        }
    }
