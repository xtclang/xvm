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
 * Represent a singleton instance of a const class (such as an enum value) as a constant value.
 */
public class SingletonConstant
        extends ValueConstant
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
    public SingletonConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_fmt    = format;
        m_iClass = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param format
     * @param constClass  the class constant for the singleton value
     */
    public SingletonConstant(ConstantPool pool, Format format, IdentityConstant constClass)
        {
        super(pool);

        if (format != Format.SingletonConst && format != Format.SingletonService)
            {
            throw new IllegalArgumentException("format must be SingletonConst or SingletonService");
            }

        if (constClass == null)
            {
            throw new IllegalArgumentException("class of the singleton value required");
            }

        m_fmt        = format;
        m_constClass = constClass;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return m_constClass.asTypeConstant();
        }

    /**
     * {@inheritDoc}
     * @return  the class constant for the singleton value
     */
    @Override
    public IdentityConstant getValue()
        {
        return m_constClass;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
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
    public Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.getValue().compareTo(((SingletonConstant) that).getValue());
        }

    @Override
    public String getValueString()
        {
        return m_constClass.getName();
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
        return "singleton-" + (m_fmt == Format.SingletonConst ? "const=" : "service=") + m_constClass.getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant; either SingletonConst or SingletonService.
     */
    private Format m_fmt;

    /**
     * Used during deserialization: holds the index of the class constant.
     */
    private transient int m_iClass;

    /**
     * The IdentityConstant for the class of the singleton value.
     */
    private IdentityConstant m_constClass;
    }
