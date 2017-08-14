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
 * Represent an enum value as a constant value.
 */
public class EnumConstant
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
    public EnumConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iClass = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constClass  the class constant for the enum value
     */
    public EnumConstant(ConstantPool pool, ClassConstant constClass)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalStateException("enum value required");
            }

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
     * @return  the class constant for the enum value
     */
    @Override
    public ClassConstant getValue()
        {
        return m_constClass;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Enum;
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
        return this.getValue().compareTo(((EnumConstant) that).getValue());
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
        m_constClass = (ClassConstant) getConstantPool().getConstant(m_iClass);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = (ClassConstant) pool.register(m_constClass);
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
        return "enum-value=" + m_constClass.getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Used during deserialization: holds the index of the enum value's class constant.
     */
    private transient int m_iClass;

    /**
     * The Class Constant that is the identity of the enum value.
     */
    private ClassConstant m_constClass;
    }
