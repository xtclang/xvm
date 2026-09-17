package org.xvm.asm.constants;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BFloat16Constant}'s codec.
 *
 * <p>A BFloat16 is simply the top half of a float32, so encoding is a truncation and the only
 * question is how it rounds. It used to round by multiplying the value by {@code 1.001957f},
 * described in a comment as a "magic number [that] rounds the result instead of truncating it".
 * Scaling is not rounding: it leaves exactly representable values alone, so round trips looked
 * clean, but it moved 12.5% of the values that actually need rounding to the wrong neighbour. It
 * also used {@code floatToIntBits}, which collapses every NaN onto the canonical positive one and
 * so lost the sign of a negative NaN.</p>
 */
public class BFloat16ConstantTest {
    /**
     * Round to nearest, ties to even - add half an LSB plus the LSB before truncating.
     */
    private static int reference(float flVal) {
        int nBits = Float.floatToRawIntBits(flVal);
        int nLsb  = nBits >>> 16 & 0x1;
        return nBits + 0x7FFF + nLsb >>> 16 & 0xFFFF;
    }

    @Test
    public void everyBitPatternRoundTrips() {
        for (int i = 0x0000; i <= 0xFFFF; ++i) {
            final int nBits = i;
            float flVal = BFloat16Constant.toFloat(nBits);
            if (Float.isNaN(flVal)) {
                continue;                               // NaN payloads need not survive verbatim
            }
            assertEquals(nBits, BFloat16Constant.toHalf(flVal) & 0xFFFF,
                    () -> String.format("0x%04X did not round trip", nBits));
        }
    }

    /**
     * The defect: values that are not exactly representable have to reach their nearest neighbour.
     * Round trips alone never saw this, because an exactly representable value needs no rounding.
     */
    @Test
    public void valuesNeedingRoundingReachTheNearestNeighbour() {
        var rnd = new Random(7);
        for (int i = 0; i < 200_000; ++i) {
            float flVal = Float.intBitsToFloat(rnd.nextInt());
            if (!Float.isFinite(flVal)) {
                continue;
            }
            final float flTest = flVal;
            assertEquals(reference(flTest), BFloat16Constant.toHalf(flTest) & 0xFFFF,
                    () -> "wrong neighbour for " + flTest);
        }
    }

    @Test
    public void tiesGoToEven() {
        // exactly halfway between 0x3F80 (1.0) and 0x3F81: the even neighbour is 0x3F80
        float flDown = Float.intBitsToFloat(0x3F808000);
        assertEquals(0x3F80, BFloat16Constant.toHalf(flDown) & 0xFFFF);

        // halfway between 0x3F81 and 0x3F82: the even neighbour is 0x3F82
        float flUp = Float.intBitsToFloat(0x3F818000);
        assertEquals(0x3F82, BFloat16Constant.toHalf(flUp) & 0xFFFF);
    }

    @Test
    public void knownValues() {
        assertEquals(0x3F80, BFloat16Constant.toHalf(1.0f) & 0xFFFF);
        assertEquals(0x4000, BFloat16Constant.toHalf(2.0f) & 0xFFFF);
        assertEquals(0xBF80, BFloat16Constant.toHalf(-1.0f) & 0xFFFF);
        assertEquals(1.0f, BFloat16Constant.toFloat(0x3F80));
        assertEquals(-1.0f, BFloat16Constant.toFloat(0xBF80));
    }

    @Test
    public void signedZeroSurvives() {
        assertEquals(0x0000, BFloat16Constant.toHalf(0.0f) & 0xFFFF);
        assertEquals(0x8000, BFloat16Constant.toHalf(-0.0f) & 0xFFFF);
        assertEquals(Float.floatToRawIntBits(0.0f),
                Float.floatToRawIntBits(BFloat16Constant.toFloat(0x0000)));
        assertEquals(Float.floatToRawIntBits(-0.0f),
                Float.floatToRawIntBits(BFloat16Constant.toFloat(0x8000)));
    }

    @Test
    public void infinitiesAndNaNsKeepTheirSign() {
        assertEquals(0x7F80, BFloat16Constant.toHalf(Float.POSITIVE_INFINITY) & 0xFFFF);
        assertEquals(0xFF80, BFloat16Constant.toHalf(Float.NEGATIVE_INFINITY) & 0xFFFF);

        int nNaN = BFloat16Constant.toHalf(Float.NaN) & 0xFFFF;
        assertTrue(Float.isNaN(BFloat16Constant.toFloat(nNaN)), "positive NaN must stay a NaN");
        assertEquals(0, nNaN & 0x8000, "positive NaN must stay positive");

        int nNegNaN = BFloat16Constant.toHalf(Float.intBitsToFloat(0xFFC00000)) & 0xFFFF;
        assertTrue(Float.isNaN(BFloat16Constant.toFloat(nNegNaN)), "negative NaN must stay a NaN");
        assertEquals(0x8000, nNegNaN & 0x8000, "negative NaN must keep its sign");
    }

    /**
     * Rounding a value past the largest finite BFloat16 has to carry into an infinity rather than
     * wrapping the exponent.
     */
    @Test
    public void overflowBecomesInfinity() {
        assertEquals(0x7F80, BFloat16Constant.toHalf(Float.MAX_VALUE) & 0xFFFF);
        assertEquals(0xFF80, BFloat16Constant.toHalf(-Float.MAX_VALUE) & 0xFFFF);
        assertEquals(0x7F7F, BFloat16Constant.toHalf(BFloat16Constant.toFloat(0x7F7F)) & 0xFFFF);
    }
}
