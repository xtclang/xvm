package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.StructureContainer.PackageContainer;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


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
    public PackageStructure(XvmStructure structParent, PackageConstant constpackage)
        {
        super(structParent, constpackage);
        }


    // ----- XvmStructure methods ----------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constModule = (ModuleConstant) getConstantPool().getConstant(readIndex(in));

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constModule = (ModuleConstant) pool.register(m_constModule);

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_constModule == null ? -1 : m_constModule.getPosition());

        super.assemble(out);
        }

    // TODO validate

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", import-module=")
          .append(m_constModule == null ? "n/a" : m_constModule);
        return sb.toString();
        }


    // ----- Object methods ----------------------------------------------------

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
        return Handy.equals(this.m_constModule, that.m_constModule);
        }


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
