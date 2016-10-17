package org.xvm.asm;


import org.xvm.asm.ConstantPool.ModuleConstant;
import org.xvm.asm.ConstantPool.PackageConstant;
import org.xvm.asm.StructureContainer.PackageContainer;


/**
 * An XVM Structure that represents an entire Package.
 *
 * @author cp 2016.04.14
 */
public class PackageStructure
        extends PackageContainer
    {
    /**
     * Construct a PackageStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure, a
     *                      ModuleStructure, or a PackageStructure) that
     *                      contains this PackageStructure
     * @param constpackage  the constant that specifies the identity of the
     *                      Package
     */
    PackageStructure(XvmStructure structParent, ConstantPool.PackageConstant constpackage)
        {
        super(structParent, constpackage);
        }


    // ----- XvmStructure methods ----------------------------------------------

    // TODO disassemble
    // TODO registerConstants
    // TODO assemble
    // TODO validate
    // TODO getDescription
    // TODO equals

    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the PackageConstant that holds the identity of this Package.
     *
     * @return the PackageConstant representing the identity of this
     *         PackageStructure
     */
    public PackageConstant getPackageConstant()
        {
        return (PackageConstant) getIdentityConstant();
        }

    public ModuleConstant getImportedModule()
        {
        return m_constModule;
        }

    /**
     *
     * @param constModule
     */
    public void setImportedModule(ModuleConstant constModule)
        {
        checkModifiable();

        // TODO

        m_constModule = constModule;
        markModified();
        }


    // ----- data members ------------------------------------------------------

    /**
     * If this package is a placeholder in the namespace for an imported module,
     * this is the module that the package imports.
     */
    private ModuleConstant m_constModule;
    }
