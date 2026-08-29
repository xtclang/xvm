package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.byteArrayToHexString;
import org.xvm.util.FrozenByteArray;


/**
 * Represent a variable-length floating point constant.
 */
public final class FPNConstant
        extends ValueConstant {
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
            throws IOException {
        this(pool, format, readVarBytes(in));
    }

    /**
     * Construct a constant whose value is an n-bit binary or decimal floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the floating point value, provided as an array of 16 bytes
     */
    public FPNConstant(ConstantPool pool, Format format, byte[] abVal) {
        super(pool);

        if (format == null) {
            throw new IllegalStateException("format required");
        }

        int cbMin = switch (format) {
            case DecN -> 4;
            case FloatN -> 2;
            default -> throw new IllegalStateException("unsupported format: " + format);
        };

        if (abVal == null) {
            throw new IllegalArgumentException("value required");
        }
        int cbVal = abVal.length;
        if (cbVal < cbMin || cbVal > 16384 || Integer.bitCount(cbVal) != 1) {
            throw new ArithmeticException("value length (" + cbVal
                    + ") must be a power-of-two between " + cbMin + " and 16384");
        }

        m_fmt   = format;
        m_abVal = FrozenByteArray.copyOf(abVal);
    }

    /**
     * Internal: construct over an already-frozen payload, sharing it rather than copying.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the constant
     * @param abVal   the frozen encoded value
     */
    private FPNConstant(ConstantPool pool, Format format, FrozenByteArray abVal) {
        super(pool);
        m_fmt   = format;
        m_abVal = abVal;
    }

    @Override
    protected FPNConstant copyForAdoption(AdoptionContext context) {
        // the payload is frozen, so the adopting pool's constant can share it
        return new FPNConstant(context.pool(), m_fmt, m_abVal);
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
            throws IOException {
        int cb = 1 << in.readUnsignedByte();
        byte[] ab = new byte[cb];
        in.readFully(ab);
        return ab;
    }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's encoded value; frozen, so the immutability the caller was previously
     *          asked to honour by convention is now structural
     */
    @Override
    public FrozenByteArray getValue() {
        return m_abVal;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return m_fmt;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof FPNConstant)) {
            return -1;
        }
        // note: this is a simple byte-wise comparison; it does not actually determine the floating
        // point values represented by the bytes

        FrozenByteArray abThis = this.m_abVal;
        FrozenByteArray abThat = ((FPNConstant) that).m_abVal;

        int cbThis = abThis.size();
        int cbThat = abThat.size();
        if (cbThis != cbThat) {
            return cbThis - cbThat;
        }

        for (int of = 0; of < cbThis; ++of) {
            if (abThis.get(of) != abThat.get(of)) {
                return (abThis.get(of) & 0xFF) - (abThat.get(of) & 0xFF);
            }
        }

        return 0;
    }

    @Override
    public String getValueString() {
        // TODO format a variable length floating point value into a string
        return "(unsupported)";
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        out.writeByte(Integer.numberOfTrailingZeros(Integer.highestOneBit(m_abVal.size())));
        out.write(m_abVal.unsafeArray());
    }

    @Override
    public String getDescription() {
        return "bytes=" + byteArrayToHexString(m_abVal.unsafeArray());
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_abVal.unsafeArray());
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant
     */
    private final Format m_fmt;

    /**
     * The constant value. Frozen: pool-interned and shared across every consumer and container,
     * and {@link #computeHashCode} hashes its contents, so a mutable alias could silently
     * invalidate hash/equality for every holder.
     */
    private final FrozenByteArray m_abVal;
}
