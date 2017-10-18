package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent the auto-narrowing class of <i>this</i></li>.
 */
public class ThisClassConstant
        extends PseudoConstant
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
    public ThisClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iClass = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is the auto-narrowing identifier "this:class".
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public ThisClassConstant(ConstantPool pool, IdentityConstant constClass)
        {
        super(pool);

        m_constClass = constClass;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public IdentityConstant getDeclarationLevelClass()
        {
        return m_constClass;
        }

    @Override
    public Format getFormat()
        {
        return Format.ThisClass;
        }

    @Override
    public boolean isClass()
        {
        return true;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }

    @Override
    protected Object getLocator()
        {
        return THIS_CLASS;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constClass.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constClass = (IdentityConstant) m_constClass.simplify();
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constClass);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_constClass.compareTo(((ThisClassConstant) that).m_constClass);
        }

    @Override
    public String getValueString()
        {
        return THIS_CLASS;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constClass = (IdentityConstant) getConstantPool().getConstant(m_iClass);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = (IdentityConstant) pool.register(m_constClass);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constClass.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "name=" + THIS_CLASS
                + ", decl-level=" + m_constClass;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return -m_constClass.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The source code identifier of the auto-narrowing "this class".
     */
    public static final String THIS_CLASS = "this:class";

    /**
     * During disassembly, this holds the index of the class constant.
     */
    private int m_iClass;

    /**
     * The declaration-level class that the this:class refers to (from which auto-narrowing occurs).
     */
    private IdentityConstant m_constClass;
    }
