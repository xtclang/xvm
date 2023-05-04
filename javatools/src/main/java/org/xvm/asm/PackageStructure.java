package org.xvm.asm;


import java.util.List;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.SingletonConstant;


/**
 * An XVM Structure that represents an entire Package.
 */
public class PackageStructure
        extends ClassStructure
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a PackageStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected PackageStructure(XvmStructure xsParent, int nFlags, PackageConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors --------------------------------------------------------------------------------------

    /**
     * Obtain the PackageConstant that holds the identity of this Package.
     *
     * @return the PackageConstant representing the identity of this PackageStructure
     */
    public PackageConstant getPackageConstant()
        {
        return (PackageConstant) getIdentityConstant();
        }

    /**
     * Determine if this package exists to import a module.
     *
     * @return true iff this package is a module import
     */
    public boolean isModuleImport()
        {
        return findContribution(Composition.Import) != null;
        }

    /**
     * Obtain the module that this package imports.
     *
     * @return the associated module, or null if this package is not a module import
     */
    public ModuleStructure getImportedModule()
        {
        Contribution contrib = findContribution(Composition.Import);
        return contrib == null
                ? null
                : (ModuleStructure) getFileStructure().getChild(contrib.getModuleConstant());
        }

    /**
     * @return for a package that imports a module, get the injector that is used to override
     *         the injections for that module, if any
     */
    public SingletonConstant getModuleInjector()
        {
        Contribution contrib = findContribution(Composition.Import);
        return contrib == null
            ? null
            : contrib.getInjector();
        }

    /**
     * @return a list of specific injections specified to be handled by the injector; null
     *         indicates that all injections are handled by the injector
     */
    public List<Injection> getModuleInjections()
        {
        Contribution contrib = findContribution(Composition.Import);
        return contrib == null
            ? null
            : contrib.getInjections();
        }

    /**
     * Specify the module that this package imports.
     * <p/>
     * This method must only be called once.
     *
     * @param module         the module being imported
     */
    public void setImportedModule(ModuleStructure module)
        {
        assert module != null;
        assert module.getFileStructure() == getFileStructure();
        assert !isModuleImport();
        assert getChildByNameMap().isEmpty();

        addImport(module.getIdentityConstant());
        }

    /**
     * Specify the injector for the module that this package imports.
     * <p/>
     * This method must only be called once.
     *
     * @param constInjector  optional injector
     * @param listInject     optional list of injections
     */
    public void setImportedModuleInjector(SingletonConstant constInjector, List<Injection> listInject)
        {
        assert isModuleImport();
        assert listInject == null || constInjector != null;

        findContribution(Composition.Import).addInjector(constInjector, listInject);
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isPackageContainer()
        {
        return !isModuleImport();
        }

    @Override
    public boolean isClassContainer()
        {
        return !isModuleImport();
        }

    @Override
    public boolean isMethodContainer()
        {
        return !isModuleImport();
        }

    @Override
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector)
        {
        Contribution contrib = findContribution(Composition.Import);
        return contrib == null
                ? super.resolveName(sName, access, collector)
                : contrib.getModuleConstant().getComponent().resolveName(sName, access, collector);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        return obj == this || (obj instanceof PackageStructure && super.equals(obj));
        }


    // ----- fields --------------------------------------------------------------------------------
    }