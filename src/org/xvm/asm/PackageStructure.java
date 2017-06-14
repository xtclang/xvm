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
 *
 * @author cp 2016.04.14
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

    public ModuleStructure getImportedModule()
        {
        return m_module;
        }

    /**
     *
     * @param module
     */
    public void setImportedModule(ModuleStructure module)
        {
        assert m_module == null;

        m_module = module;
        markModified();
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isPackageContainer()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // TODO m_module = (ModuleConstant) getConstantPool().getConstant(readIndex(in));
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // TODO review m_module = (ModuleConstant) pool.register(m_module);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_module == null ? -1 : m_module.getModuleConstant().getPosition());
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", import-module=")
          .append(m_module == null ? "n/a" : m_module);
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

        if (!(obj instanceof PackageStructure && super.equals(obj)))
            {
            return false;
            }

        // compare imported modules
        PackageStructure that = (PackageStructure) obj;
        return Handy.equals(this.m_module, that.m_module);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * If this package is a placeholder in the namespace for an imported module, this is the module
     * that the package imports.
     */
    private ModuleStructure m_module;
    }
