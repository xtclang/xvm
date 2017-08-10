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
    public IntConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        this(pool, format, new PackedInteger(in));
        }

    /**
     * Construct a constant whose value is a PackedInteger.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the Constant Format
     * @param pint    the PackedInteger value
     */
    public IntConstant(ConstantPool pool, Format format, PackedInteger pint)
        {
        super(pool);

        if (format == null)
            {
            throw new IllegalStateException("format required");
            }
        if (pint == null)
            {
            throw new IllegalStateException("integer value required");
            }
        pint.verifyInitialized();

        int     cBytes;
        boolean fUnsigned;
        switch (format)
            {
            case Int16:
                cBytes    = 2;
                fUnsigned = false;
                break;
            case Int32:
                cBytes    = 4;
                fUnsigned = false;
                break;
            case Int64:
                cBytes    = 8;
                fUnsigned = false;
                break;
            case Int128:
                cBytes    = 16;
                fUnsigned = false;
                break;
            case VarInt:
                cBytes    = 16384;      // some arbitrary limit
                fUnsigned = false;
                break;

            case UInt16:
                cBytes    = 2;
                fUnsigned = true;
                break;
            case UInt32:
                cBytes    = 4;
                fUnsigned = true;
                break;
            case UInt64:
                cBytes    = 8;
                fUnsigned = true;
                break;
            case UInt128:
                cBytes    = 16;
                fUnsigned = true;
                break;
            case VarUInt:
                cBytes    = 16384;
                fUnsigned = true;
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        if (fUnsigned)
            {
            if (pint.compareTo(PackedInteger.ZERO) < 0)
                {
                throw new IllegalStateException("illegal unsigned value: " + pint);
                }
            if (pint.getUnsignedByteSize() > cBytes)
                {
                throw new IllegalStateException("value exceeds " + cBytes + " bytes: " + pint);
                }
            }
        else  if (pint.getSignedByteSize() > cBytes)
            {
            throw new IllegalStateException("value exceeds " + cBytes + " bytes: " + pint);
            }

        m_fmt  = format;
        m_pint = pint;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a PackedInteger
     */
    @Override
    public PackedInteger getValue()
        {
        return m_pint;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
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
        return "value=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_pint.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant
     */
    private final Format m_fmt;

    /**
     * The constant integer value stored as a PackedInteger.
     */
    private final PackedInteger m_pint;
    }
