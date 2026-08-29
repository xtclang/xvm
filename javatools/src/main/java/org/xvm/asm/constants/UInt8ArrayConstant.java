package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.byteArrayToHexString;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;
import org.xvm.util.FrozenByteArray;


/**
 * Represent an octet string (string of unsigned 8-bit bytes) constant.
 */
public final class UInt8ArrayConstant
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
    public UInt8ArrayConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        int    cb = readMagnitude(in);
        byte[] ab = new byte[cb];
        in.readFully(ab);
        m_abVal = FrozenByteArray.adopt(ab);
    }

    /**
     * Construct a constant whose value is an octet string. The array is copied so the constant's
     * hash/equality value cannot be changed by later caller mutation.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the octet string value
     */
    public UInt8ArrayConstant(ConstantPool pool, byte[] abVal) {
        super(pool);

        assert abVal != null;
        m_abVal = FrozenByteArray.copyOf(abVal);
    }

    /**
     * Internal: construct over an already-frozen payload, sharing it rather than copying.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param abVal  the frozen octet string value
     */
    private UInt8ArrayConstant(ConstantPool pool, FrozenByteArray abVal) {
        super(pool);
        m_abVal = abVal;
    }

    @Override
    protected UInt8ArrayConstant copyForAdoption(AdoptionContext context) {
        // the payload is frozen, so the adopting pool's constant can share it
        return new UInt8ArrayConstant(context.pool(), m_abVal);
    }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType() {
        return getConstantPool().typeBinary();
    }

    /**
     * {@inheritDoc}
     * @return  the constant's octet string value; frozen, so the immutability the caller was
     *          previously asked to honour by convention is now structural
     */
    @Override
    public FrozenByteArray getValue() {
        return m_abVal;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.UInt8Array;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof UInt8ArrayConstant)) {
            return -1;
        }
        FrozenByteArray abThis = this.m_abVal;
        FrozenByteArray abThat = ((UInt8ArrayConstant) that).m_abVal;

        int cbThis  = abThis.size();
        int cbThat  = abThat.size();
        for (int of = 0, cb = Math.min(cbThis, cbThat); of < cb; ++of) {
            if (abThis.get(of) != abThat.get(of)) {
                return (abThis.get(of) & 0xFF) - (abThat.get(of) & 0xFF);
            }
        }
        return cbThis - cbThat;
    }

    @Override
    public String getValueString() {
        return byteArrayToHexString(m_abVal.unsafeArray());
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        final byte[] ab = m_abVal.unsafeArray();
        writePackedLong(out, ab.length);
        out.write(ab);
    }

    @Override
    public String getDescription() {
        return "byte-string=" + getValueString();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    protected int computeHashCode() {
        return Hash.of(m_abVal.unsafeArray());
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant octet string value. Frozen: this is pool-interned and shared across every
     * consumer and container, and {@link #computeHashCode} caches a hash over its contents, so a
     * mutable alias could silently invalidate hash/equality for every holder of the constant.
     */
    private final FrozenByteArray m_abVal;
}
