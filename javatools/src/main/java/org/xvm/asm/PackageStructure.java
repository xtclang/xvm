package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


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
        return m_constModule != null;
        }

    /**
     * Obtain the module that this package imports.
     *
     * @return the associated module, or null if this package is not a module import
     */
    public ModuleStructure getImportedModule()
        {
        return isModuleImport()
                ? (ModuleStructure) getFileStructure().getChild(m_constModule)
                : null;
        }

    /**
     * Specify the module that this package imports.
     * <p/>
     * This method must only be called once.
     *
     * @param module
     */
    public void setImportedModule(ModuleStructure module)
        {
        assert module != null;
        assert module.getFileStructure() == getFileStructure();
        assert m_constModule == null;
        assert getChildByNameMap().isEmpty();

        m_constModule = module.getIdentityConstant();
        markModified();
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
        return m_constModule == null
                ? super.resolveName(sName, access, collector)
                : m_constModule.getComponent().resolveName(sName, access, collector);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        m_constModule = (ModuleConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_constModule = (ModuleConstant) pool.register(m_constModule);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, Constant.indexOf(m_constModule));
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", import-module=")
          .append(m_constModule == null ? "n/a" : m_constModule);
        return sb.toString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof PackageStructure that && super.equals(obj)))
            {
            return false;
            }

        // compare imported modules
        return Handy.equals(this.m_constModule, that.m_constModule);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * If this package is a placeholder in the namespace for an imported module, this is the module
     * that the package imports.
     */
    private ModuleConstant m_constModule;
    }