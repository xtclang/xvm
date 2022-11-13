package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.byteArrayToHexString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an octet string (string of unsigned 8-bit bytes) constant.
 */
public class UInt8ArrayConstant
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
    public UInt8ArrayConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        int    cb = readMagnitude(in);
        byte[] ab = new byte[cb];
        in.readFully(ab);
        m_abVal = ab;
        }

    /**
     * Construct a constant whose value is an octet string. Note that this constructor does not make
     * a copy of the passed {@code byte[]}.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the octet string value
     */
    public UInt8ArrayConstant(ConstantPool pool, byte[] abVal)
        {
        super(pool);

        assert abVal != null;
        m_abVal = abVal;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().typeBinary();
        }

    /**
     * {@inheritDoc}
     * @return  the constant's octet string value as a <tt>byte[]</tt>; the caller must treat the
     *          returned value as immutable
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
        return Format.UInt8Array;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof UInt8ArrayConstant))
            {
            return -1;
            }
        byte[] abThis = this.m_abVal;
        byte[] abThat = ((UInt8ArrayConstant) that).m_abVal;

        int cbThis  = abThis.length;
        int cbThat  = abThat.length;
        for (int of = 0, cb = Math.min(cbThis, cbThat); of < cb; ++of)
            {
            if (abThis[of] != abThat[of])
                {
                return (abThis[of] & 0xFF) - (abThat[of] & 0xFF);
                }
            }
        return cbThis - cbThat;
        }

    @Override
    public String getValueString()
        {
        return byteArrayToHexString(m_abVal);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        final byte[] ab = m_abVal;
        writePackedLong(out, ab.length);
        out.write(ab);
        }

    @Override
    public String getDescription()
        {
        return "byte-string=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    protected int computeHashCode()
        {
        return Hash.of(m_abVal);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant octet string value stored as a <tt>byte[]</tt>.
     */
    private final byte[] m_abVal;
    }
