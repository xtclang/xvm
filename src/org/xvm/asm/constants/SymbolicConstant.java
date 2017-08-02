package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.function.Consumer;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a symbolic constant, which is a named symbol which represents a notion of a constant
 * that can be resolved to a specific identity at runtime. The set of symbolic constants is both
 * fixed and well known:
 * <ul>
 * <li><tt>this:type</tt> - represents the auto-narrowing type of <i>this</i></li>
 * <li><tt>this:class</tt> - represents the auto-narrowing class of <i>this</i></li>
 * </ul>
 * <p/>
 * The SymbolicConstant is conceptually similar to the RegisterConstant, except that the register
 * constant refers to register numbers, while the symbolic constant refers to symbol names.
 */
public class SymbolicConstant
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
    public SymbolicConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iName = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a symbolic identifier.
     *
     * @param pool     the ConstantPool that will contain this Constant
     * @param sName    the symbolic name
     */
    public SymbolicConstant(ConstantPool pool, String sName)
        {
        super(pool);

        if (sName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        switch (sName)
            {
            case THIS_TYPE:
            case THIS_CLASS:
                break;

            default:
                throw new IllegalArgumentException("illegal name=" + sName);
            }

        m_constName = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the ClassTypeConstant for the public interface of this class
     */
    public ClassTypeConstant asTypeConstant()
        {
        return getConstantPool().ensureThisTypeConstant(Access.PUBLIC);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        return null;
        }

    @Override
    public String getName()
        {
        return m_constName.getValue();
        }

    @Override
    public List<IdentityConstant> getIdentityConstantPath()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public Component getComponent()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected StringBuilder buildPath()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Symbolic;
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
        return this.m_constName.compareTo(((SymbolicConstant) that).m_constName);
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
        return "name=" + getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    public static final String THIS_TYPE      = "this:type";
    public static final String THIS_CLASS     = "this:class";

    /**
     * During disassembly, this holds the index of the constant that specifies the name.
     */
    private int m_iName;

    /**
     * The constant that holds the symbolic name.
     */
    private CharStringConstant m_constName;
    }
