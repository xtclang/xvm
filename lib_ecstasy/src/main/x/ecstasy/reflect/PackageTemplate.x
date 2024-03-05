/**
 * A PackageTemplate is a representation of an Ecstasy `module` or `package`.
 */
interface PackageTemplate
        extends ClassTemplate {
    /**
     * Obtain the module that this package imports.
     *
     * @return True iff this package represents an imported module
     * @return (conditional) the ModuleTemplate this package represents
     */
    conditional ModuleTemplate imported();
}
