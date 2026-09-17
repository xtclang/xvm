package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Float16Constant}'s IEEE-754 binary16 codec.
 *
 * <p>The decoder used to carry a "smooth transition" special case from the algorithm it was
 * adapted from, which set the low ten significand bits of the resulting float whenever the half's
 * significand was zero. Every exact power of two, {@code 1.0} included, therefore decoded 1023 ULPs
 * high - {@code 1.0} as {@code 1.000122}, {@code 2.0} as {@code 2.000244} - across 58 of the 65536
 * patterns. The JIT worked around it in {@code Builder.loadConstant}, which is why the two
 * back-ends disagreed with each other on Float16.</p>
 */
public class Float16ConstantTest {
    /**
     * The JDK has had binary16 conversion since 20, and it is the reference here: agreeing with it
     * over the whole domain is a stronger statement than any set of hand-picked cases.
     */
    @Test
    public void everyBitPatternDecodesAsTheJdkDoes() {
        for (int i = 0x0000; i <= 0xFFFF; ++i) {
            final int nBits = i;
            float flXvm = Float16Constant.toFloat(nBits);
            float flJdk = Float.float16ToFloat((short) nBits);

            if (Float.isNaN(flJdk)) {
                assertTrue(Float.isNaN(flXvm),
                        () -> String.format("0x%04X should decode to NaN", nBits));
            } else {
                assertEquals(Float.floatToRawIntBits(flJdk), Float.floatToRawIntBits(flXvm),
                        () -> String.format("0x%04X decoded as %s, expected %s",
                                nBits, Float16Constant.toFloat(nBits),
                                Float.float16ToFloat((short) nBits)));
            }
        }
    }

    @Test
    public void everyBitPatternEncodesAsTheJdkDoes() {
        for (int i = 0x0000; i <= 0xFFFF; ++i) {
            final int nBits = i;
            float flVal = Float.float16ToFloat((short) nBits);
            if (Float.isNaN(flVal)) {
                continue;                               // NaN payloads are not required to survive
            }
            assertEquals(Float.floatToFloat16(flVal) & 0xFFFF, Float16Constant.toHalf(flVal) & 0xFFFF,
                    () -> String.format("re-encoding the value of 0x%04X disagrees with the JDK",
                            nBits));
        }
    }

    /**
     * The specific shape of the old defect: a half whose significand is zero is an exact power of
     * two, and has to decode to exactly that power of two.
     */
    @Test
    public void powersOfTwoDecodeExactly() {
        assertEquals(1.0f, Float16Constant.toFloat(0x3C00));
        assertEquals(2.0f, Float16Constant.toFloat(0x4000));
        assertEquals(4.0f, Float16Constant.toFloat(0x4400));
        assertEquals(0.5f, Float16Constant.toFloat(0x3800));
        assertEquals(1024.0f, Float16Constant.toFloat(0x6400));
        assertEquals(-1.0f, Float16Constant.toFloat(0xBC00));
        assertEquals(-2.0f, Float16Constant.toFloat(0xC000));

        // every one of them, rather than a sample: exponent field 1 through 30, significand zero
        for (int i = 1; i <= 30; ++i) {
            final int nExp  = i;
            final int nBits = nExp << 10;
            float flVal = Float16Constant.toFloat(nBits);
            assertEquals(Math.scalb(1.0f, nExp - 15), flVal,
                    () -> String.format("0x%04X should be 2^%d", nBits, nExp - 15));
            assertEquals(0, Float.floatToRawIntBits(flVal) & 0x007FFFFF,
                    () -> String.format("0x%04X decoded with a non-zero significand", nBits));
        }
    }

    @Test
    public void knownValuesRoundTrip() {
        assertEquals(0x3C00, Float16Constant.toHalf(1.0f) & 0xFFFF);
        assertEquals(0x4000, Float16Constant.toHalf(2.0f) & 0xFFFF);
        assertEquals(0xBC00, Float16Constant.toHalf(-1.0f) & 0xFFFF);
        assertEquals(0x7BFF, Float16Constant.toHalf(65504.0f) & 0xFFFF);   // largest finite
        assertEquals(65504.0f, Float16Constant.toFloat(0x7BFF));
    }

    @Test
    public void signedZeroAndSpecialsSurvive() {
        assertEquals(Float.floatToRawIntBits(0.0f),
                Float.floatToRawIntBits(Float16Constant.toFloat(0x0000)));
        assertEquals(Float.floatToRawIntBits(-0.0f),
                Float.floatToRawIntBits(Float16Constant.toFloat(0x8000)));

        assertEquals(Float.POSITIVE_INFINITY, Float16Constant.toFloat(0x7C00));
        assertEquals(Float.NEGATIVE_INFINITY, Float16Constant.toFloat(0xFC00));
        assertTrue(Float.isNaN(Float16Constant.toFloat(0x7E00)));

        assertEquals(0x7C00, Float16Constant.toHalf(Float.POSITIVE_INFINITY) & 0xFFFF);
        assertEquals(0xFC00, Float16Constant.toHalf(Float.NEGATIVE_INFINITY) & 0xFFFF);
    }

    /**
     * Subnormals were never affected by the defect, but they share the decode path that the
     * special case sat in front of, so they are worth pinning.
     */
    @Test
    public void subnormalsDecodeExactly() {
        assertEquals(Math.scalb(1.0f, -24), Float16Constant.toFloat(0x0001));  // smallest subnormal
        assertEquals(Math.scalb(1023.0f, -24), Float16Constant.toFloat(0x03FF)); // largest subnormal
        assertEquals(Math.scalb(1.0f, -14), Float16Constant.toFloat(0x0400));  // smallest normal
    }
}
