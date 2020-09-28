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

    // TODO
    }
