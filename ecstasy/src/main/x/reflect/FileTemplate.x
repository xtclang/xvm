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
    }
