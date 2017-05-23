package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Module constant. A Module constant is composed of a qualified module name, which
 * itself is composed of a domain name and an unqualified (simple) module name. For example, the
 * domain name "xtclang.org" can be combined with the simple module name "ecstasy" to create a
 * qualified module name of "ecstasy.xtclang.org".
 */
public class ModuleConstant
        extends IdentityConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public ModuleConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iName = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a module identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the qualified module name
     */
    public ModuleConstant(ConstantPool pool, String sName)
        {
        super(pool);

        m_constName = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Extract the unqualified name of the module.
     *
     * @return the unqualified module name
     */
    public String getUnqualifiedName()
        {
        String sName = getName();
        int ofDot = sName.indexOf('.');
        return ofDot < 0 ? sName : sName.substring(0, ofDot);
        }

    /**
     * Get the domain name for the Module constant.
     *
     * @return the constant's domain information as a {@code String}, or {@code null} if the module
     *         name is not qualified (i.e. does not contain a domain name)
     */
    public String getDomainName()
        {
        String sName = getName();
        int ofDot = sName.indexOf('.');
        return ofDot < 0 ? null : sName.substring(ofDot + 1);
        }

    /**
     * Determine if this ModuleConstant is the Ecstasy core module.
     *
     * @return true iff this ModuleConstant represents the module containing the Ecstasy class
     *         library
     */
    public boolean isEcstasyModule()
        {
        return getName().equals(ECSTASY_MODULE);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        return null;
        }

    /**
     * Get the qualified name of the Module.
     * <p/>
     * The qualified name for the module is constructed by combining the unqualified module name, a
     * separating '.', and the domain name.
     *
     * @return the qualified Module name
     */
    @Override
    public String getName()
        {
        return m_constName.getValue();
        }

    @Override
    public ModuleConstant getModuleConstant()
        {
        return this;
        }

    @Override
    public List<IdentityConstant> getIdentityConstantPath()
        {
        List<IdentityConstant> list = new ArrayList<>();
        list.add(this);
        return list;
        }

    @Override
    public Component getComponent()
        {
        String          sName  = getName();
        ModuleStructure struct = getFileStructure().getModule(sName);
        if (struct == null)
            {
            throw new IllegalStateException("could not find module: " + sName);
            }
        return struct;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Module;
        }

    @Override
    public Object getLocator()
        {
        return m_constName.getLocator();
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constName = (CharStringConstant) getConstantPool().getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName = (CharStringConstant) pool.register(m_constName);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_constName.compareTo(((ModuleConstant) that).m_constName);
        }

    @Override
    public String getValueString()
        {
        return m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "module=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the name.
     */
    private int m_iName;

    /**
     * The constant that holds the qualified name of the module.
     */
    private CharStringConstant m_constName;
    }
