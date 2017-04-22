package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.PackedInteger;


/**
 * Represent a 64-bit signed integer constant.
 */
public class IntConstant
        extends Constant
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
    public IntConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_pint = new PackedInteger(in);
        }

    /**
     * Construct a constant whose value is a PackedInteger.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param pint  the PackedInteger value
     */
    public IntConstant(ConstantPool pool, PackedInteger pint)
        {
        super(pool);
        pint.verifyInitialized();
        m_pint = pint;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the value of the constant.
     *
     * @return  the constant's PackedInteger value
     */
    public PackedInteger getValue()
        {
        return m_pint;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Type getType()
        {
        return Type.Int;
        }

    @Override
    public Format getFormat()
        {
        return Format.Int;
        }

    @Override
    public Object getLocator()
        {
        return m_pint;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_pint.compareTo(((IntConstant) that).m_pint);
        }

    @Override
    public String getValueString()
        {
        return m_pint.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        m_pint.writeObject(out);
        }

    @Override
    public String getDescription()
        {
        return "int=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_pint.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant integer value stored as a PackedInteger.
     */
    private final PackedInteger m_pint;
    }
