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

    // TODO
    }
