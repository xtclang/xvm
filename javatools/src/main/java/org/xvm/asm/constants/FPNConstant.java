package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.byteArrayToHexString;


/**
 * Represent a variable-length floating point constant.
 */
public class FPNConstant
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
    public FPNConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        this(pool, format, readVarBytes(in));
        }

    /**
     * Construct a constant whose value is a 126-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the floating point value, provided as an array of 16 bytes
     */
    public FPNConstant(ConstantPool pool, Format format, byte[] abVal)
        {
        super(pool);

        if (format == null)
            {
            throw new IllegalStateException("format required");
            }

        int cbMin;
        switch (format)
            {
            case DecN:
                cbMin = 4;
                break;

            case FloatN:
                cbMin = 2;
                break;

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }

        if (abVal == null)
            {
            throw new IllegalArgumentException("value required");
            }
        int cbVal = abVal.length;
        if (cbVal < cbMin || cbVal > 16384 || Integer.bitCount(cbVal) != 1)
            {
            throw new IllegalArgumentException("value length (" + cbVal
                    + ") must be a power-of-two between " + cbMin + " and 16384");
            }

        m_fmt   = format;
        m_abVal = abVal;
        }

    /**
     * Helper to read in the bytes of the variable length floating point value.
     *
     * @param in  the DataInput to read from
     *
     * @return the bytes of the floating point value, as a byte array
     *
     * @throws IOException  if an error occurs while reading
     */
    private static byte[] readVarBytes(DataInput in)
            throws IOException
        {
        int cb = 1 << in.readUnsignedByte();
        byte[] ab = new byte[cb];
        in.readFully(ab);
        return ab;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a byte array, which must be treated as an immutable
     */
    @Override
    public byte[] getValue()
        {
        return m_abVal;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof FPNConstant))
            {
            return -1;
            }
        // note: this is a simple byte-wise comparison; it does not actually determine the floating
        // point values represented by the bytes

        byte[] abThis = this.m_abVal;
        byte[] abThat = ((FPNConstant) that).m_abVal;

        int cbThis = abThis.length;
        int cbThat = abThat.length;
        if (cbThis != cbThat)
            {
            return cbThis - cbThat;
            }

        for (int of = 0; of < cbThis; ++of)
            {
            if (abThis[of] != abThat[of])
                {
                return (abThis[of] & 0xFF) - (abThat[of] & 0xFF);
                }
            }

        return 0;
        }

    @Override
    public String getValueString()
        {
        // TODO format a variable length floating point value into a string
        return "(unsupported)";
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        out.writeByte(Integer.numberOfTrailingZeros(Integer.highestOneBit(m_abVal.length)));
        out.write(m_abVal);
        }

    @Override
    public String getDescription()
        {
        return "bytes=" + byteArrayToHexString(m_abVal);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int nHash = 0;
        byte[] ab = m_abVal;
        int    cb = ab.length;
        for (int of = 0; of < cb; ++of)
            {
            nHash = nHash * 19 + ab[of];
            }
        return nHash;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant
     */
    private final Format m_fmt;

    /**
     * The constant value.
     */
    private final byte[] m_abVal;
    }
