
package org.xvm.asm.constants;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;

/**
 * Represent an 8-bit "FP8 E5M2" binary floating point constant.
 */
public class Float8e5Constant
        extends FloatConstant {
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
    public Float8e5Constant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);
        m_nBits = in.readUnsignedByte();
    }

    /**
     * Construct a constant whose value is an 8-bit binary floating point.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param flVal  the floating point value
     */
    public Float8e5Constant(ConstantPool pool, float flVal) {
        super(pool);
        m_nBits = toBits(flVal);
    }

    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Java Float
     */
    @Override
    public Float getValue() {
        return Float.valueOf(toFloat(m_nBits));
    }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Float8e5;
    }

    @Override
    protected Object getLocator() {
        return m_nBits;
    }

    @Override
    protected int compareDetails(Constant that) {
        if (that instanceof Float8e5Constant thatFP8) {
            int result = Float.compare(toFloat(this.m_nBits), toFloat(thatFP8.m_nBits));
            // NaN encodings retain their sign and payload in the constant pool.
            return result == 0 ? Integer.compare(this.m_nBits, thatFP8.m_nBits) : result;
        } else {
            return -1;
        }
    }

    @Override
    public String getValueString() {
        return Float.toString(toFloat(m_nBits));
    }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        out.writeByte(m_nBits);
    }

    @Override
    public String getDescription() {
        return "value=" + getValueString();
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of((byte) m_nBits);
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert an 8-bit floating point to a "full precision" 32-bit float.
     *
     * @param nBits  the 8-bit floating point value stored in a Java int, whose bits are encoded
     *               using the FP8 E5M2 binary-radix floating point format
     *
     * @return a 32-bit float
     */
    public static float toFloat(int nBits) {
        int     nExp = nBits >>> SIG_BITS & EXP_MASK;
        int     nSig = nBits              & SIG_MASK;
        boolean fNeg = (nBits & SIGN_MASK) != 0;

        // E5M2 follows the IEEE-754 convention of reserving the all-1s exponent for infinity (with
        // a zero significand) and NaN (with any other significand)
        if (nExp == EXP_MASK) {
            if (nSig == 0) {
                return fNeg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            }
            return fNeg ? Float.intBitsToFloat(0xFFC00000) : Float.NaN;
        }

        // a subnormal (exponent field 0) has no implicit leading 1 and a fixed exponent of emin;
        // a normal value has an implicit leading 1 and an exponent of E-bias. Either way the
        // significand is an integer, so it is scaled down by a further SIG_BITS.
        float flVal = nExp == 0
                ? Math.scalb((float) nSig, EMIN - SIG_BITS)
                : Math.scalb((float) (IMPLICIT_ONE | nSig), nExp - BIAS - SIG_BITS);
        return fNeg ? -flVal : flVal;
    }

    /**
     * Convert a "full precision" 32-bit float to an 8-bit FP8 E5M2 floating point value, rounding
     * to nearest with ties to even.
     *
     * @param flVal  a 32-bit float
     *
     * @return an 8-bit FP8 E5M2 floating point value stored in a Java int, whose bits are encoded
     *         using the FP8 E5M2 binary-radix floating point format
     */
    public static int toBits(float flVal) {
        // note: floatToRawIntBits() rather than floatToIntBits(), because the latter collapses
        // every NaN onto the canonical positive 0x7FC00000 and would thus lose the sign
        int nSign = Float.floatToRawIntBits(flVal) >>> 24 & SIGN_MASK;
        if (Float.isNaN(flVal)) {
            return nSign | NAN;
        }
        if (Float.isInfinite(flVal)) {
            return nSign | INFINITY;
        }

        // scale the magnitude so that the significand becomes an integer of SIG_BITS+1 bits, and
        // let rint() do the round-half-to-even; clamping the exponent at emin is what produces the
        // subnormals, whose significand then lands below the implicit leading 1
        double dflMag = Math.abs((double) flVal);
        int    nExp   = Math.max(Math.getExponent(dflMag), EMIN);
        int    nSig   = (int) Math.rint(Math.scalb(dflMag, SIG_BITS - nExp));
        if (nSig > MAX_SIG) {
            // rounding up carried the significand into the next binade
            nSig = IMPLICIT_ONE;
            ++nExp;
        }

        int nField = nSig < IMPLICIT_ONE ? 0 : nExp + BIAS;
        return nField >= EXP_MASK
                ? nSign | INFINITY                              // out of finite range, as IEEE-754
                : nSign | nField << SIG_BITS | nSig & SIG_MASK;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * The number of explicitly stored significand ("mantissa") bits: the "2" of "E5M2".
     */
    private static final int SIG_BITS = 2;

    /**
     * A mask for the stored significand bits.
     */
    private static final int SIG_MASK = (1 << SIG_BITS) - 1;

    /**
     * The implicit leading significand bit of a normal value, in the same scale as SIG_MASK.
     */
    private static final int IMPLICIT_ONE = 1 << SIG_BITS;

    /**
     * The largest significand (including the implicit leading bit) that stays within one binade.
     */
    private static final int MAX_SIG = (1 << SIG_BITS + 1) - 1;

    /**
     * A mask for the exponent field, which is also its all-1s value: the "5" of "E5M2" means a
     * 5-bit exponent.
     */
    private static final int EXP_MASK = 0x1F;

    /**
     * The exponent bias: a stored exponent field minus this yields the actual exponent.
     */
    private static final int BIAS = 15;

    /**
     * The exponent of the smallest normal value, which subnormals share.
     */
    private static final int EMIN = -14;

    /**
     * The sign bit.
     */
    private static final int SIGN_MASK = 0x80;

    /**
     * Infinity: the all-1s exponent with a zero significand.
     */
    private static final int INFINITY = EXP_MASK << SIG_BITS;

    /**
     * The canonical NaN: the all-1s exponent with an all-1s significand. The remaining NaN
     * encodings (0x7D and 0x7E, and their negatives) decode to NaN but are never produced.
     */
    private static final int NAN = INFINITY | SIG_MASK;

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The constant value, stored as a byte.
     */
    private final int m_nBits;
}
