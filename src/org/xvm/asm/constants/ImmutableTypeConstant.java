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
 * Represent a constant that specifies an explicitly immutable form of an underlying type.
 */
public class ImmutableTypeConstant
        extends TypeConstant
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
    public ImmutableTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is an immutable type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a TypeConstant that this constant modifies to be immutable
     */
    public ImmutableTypeConstant(ConstantPool pool, TypeConstant constType)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        m_constType = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the underlying TypeConstant
     */
    public TypeConstant getResolvedType()
        {
        return m_constType;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ImmutableType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_constType.compareTo(((ImmutableTypeConstant) that).m_constType);
        }

    @Override
    public String getValueString()
        {
        return "immutable " + m_constType.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return -m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the type constant.
     */
    private int m_iType;

    /**
     * The type referred to.
     */
    private TypeConstant m_constType;
    }
