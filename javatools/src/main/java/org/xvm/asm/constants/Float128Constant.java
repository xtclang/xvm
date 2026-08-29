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
 * Represent a 128-bit binary floating point constant.
 */
public final class Float128Constant
        extends ValueConstant<FrozenByteArray> {
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
    public Float128Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        byte[] ab = new byte[16];
        in.readFully(ab);
        m_abVal = FrozenByteArray.adopt(ab);
    }

    /**
     * Construct a constant whose value is a 126-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the floating point value, provided as an array of 16 bytes
     */
    public Float128Constant(ConstantPool pool, byte[] abVal) {
        super(pool);
        if (abVal == null || abVal.length != 16) {
            throw new ArithmeticException("Float128Constant requires an array of 16 bytes");
        }
        m_abVal = FrozenByteArray.copyOf(abVal);
    }

    /**
     * Internal: construct over an already-frozen payload, sharing it rather than copying.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the frozen encoded value
     */
    private Float128Constant(ConstantPool pool, FrozenByteArray abVal) {
        super(pool);
        m_abVal = abVal;
    }

    @Override
    protected Float128Constant copyForAdoption(AdoptionContext context) {
        // the payload is frozen, so the adopting pool's constant can share it
        return new Float128Constant(context.pool(), m_abVal);
    }


    // ----- type-specific methods -----------------------------------------------------------------

    /**
     * Add another FloatConstant to the value of this FloatConstant.
     *
     * @param that  a Float128Constant
     *
     * @return the sum, as a Float128Constant
     */
    public Float128Constant add(Float128Constant that) {
        throw new UnsupportedOperationException("(unsupported)");
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
        return Format.Float128;
    }

    @Override
    protected Object getLocator() {
        return getValue();
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof Float128Constant)) {
            return -1;
        }
        FrozenByteArray abThis = this.m_abVal;
        FrozenByteArray abThat = ((Float128Constant) that).m_abVal;

        for (int of = 0; of < 16; ++of) {
            if (abThis.get(of) != abThat.get(of)) {
                return (abThis.get(of) & 0xFF) - (abThat.get(of) & 0xFF);
            }
        }
        return 0;
    }

    @Override
    public String getValueString() {
        // TODO format a 128-bit binary floating point value into a string
        return "(unsupported)";
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
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
     * The constant value. Frozen: pool-interned and shared across every consumer and container,
     * and {@link #computeHashCode} hashes its contents, so a mutable alias could silently
     * invalidate hash/equality for every holder.
     */
    private final FrozenByteArray m_abVal;
}
