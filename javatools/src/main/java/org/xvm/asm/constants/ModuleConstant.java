package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import java.util.function.Consumer;

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
     * Construct a constant whose value is a module identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the qualified module name
     */
    public ModuleConstant(ConstantPool pool, String sName)
        {
        super(pool);

        m_constName = pool.ensureStringConstant(sName);
        }

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

    @Override
    protected void resolveConstants()
        {
        m_constName = (StringConstant) getConstantPool().getConstant(m_iName);
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

    /**
     * @return true iff this ModuleConstant represents the module containing the Ecstasy class
     *         library or a native (prototype) module
     */
    public boolean isCoreModule()
        {
        String sName = getName();
        return sName.equals(ECSTASY_MODULE) || sName.equals(PROTOTYPE_MODULE);
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
    public List<IdentityConstant> getPath()
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
            return null;
            }

        ModuleStructure structOrigin = struct.getFingerprintOrigin();
        return structOrigin == null
                ? struct
                : structOrigin;
        }

    @Override
    public String getPathString()
        {
        return "";
        }

    @Override
    protected StringBuilder buildPath()
        {
        return new StringBuilder();
        }

    @Override
    public boolean trailingPathEquals(IdentityConstant that, int cSegments)
        {
        return trailingSegmentEquals(that);
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        throw new IllegalStateException(this.toString());
        }

    @Override
    public String getNestedName()
        {
        return null;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Module;
        }

    @Override
    public boolean isClass()
        {
        return true;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constName);
        }

    @Override
    public Object getLocator()
        {
        return m_constName.getLocator();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof ModuleConstant))
            {
            return -1;
            }
        return this.m_constName.compareTo(((ModuleConstant) that).m_constName);
        }

    @Override
    public String getValueString()
        {
        return m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName = (StringConstant) pool.register(m_constName);
        }

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
    private StringConstant m_constName;
    }
