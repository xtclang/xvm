package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Float8e4Constant} (OCP "E4M3FN": no infinities, NaN only at 0x7F/0xFF, largest
 * finite 0x7E == 448) and {@link Float8e5Constant} (IEEE-style "E5M2": infinity at 0x7C/0xFC,
 * largest finite 0x7B == 57344).
 */
public class Float8ConstantTest {
    // ----- E4M3 (Float8e4) -----------------------------------------------------------------------

    @Test
    public void e4EncodesKnownValues() {
        assertEquals(0x38, Float8e4Constant.toBits(1.0f));
        assertEquals(0xB8, Float8e4Constant.toBits(-1.0f));
        assertEquals(0x40, Float8e4Constant.toBits(2.0f));
        assertEquals(0x30, Float8e4Constant.toBits(0.5f));
        assertEquals(0x7E, Float8e4Constant.toBits(448.0f));        // largest finite
        assertEquals(0x78, Float8e4Constant.toBits(256.0f));         // NOT infinity in E4M3FN
        assertEquals(0x08, Float8e4Constant.toBits(0x1p-6f));        // smallest normal
        assertEquals(0x01, Float8e4Constant.toBits(0x1p-9f));        // smallest subnormal
    }

    @Test
    public void e4DecodesKnownValues() {
        assertEquals(1.0f, Float8e4Constant.toFloat(0x38));
        assertEquals(-1.0f, Float8e4Constant.toFloat(0xB8));
        assertEquals(448.0f, Float8e4Constant.toFloat(0x7E));
        assertEquals(256.0f, Float8e4Constant.toFloat(0x78));
        assertEquals(0x1p-6f, Float8e4Constant.toFloat(0x08));
        assertEquals(0x1p-9f, Float8e4Constant.toFloat(0x01));
    }

    @Test
    public void e4PreservesSignedZero() {
        assertEquals(0x00, Float8e4Constant.toBits(0.0f));
        assertEquals(0x80, Float8e4Constant.toBits(-0.0f));
        assertEquals(Float.floatToRawIntBits(0.0f), Float.floatToRawIntBits(Float8e4Constant.toFloat(0x00)));
        assertEquals(Float.floatToRawIntBits(-0.0f), Float.floatToRawIntBits(Float8e4Constant.toFloat(0x80)));
    }

    @Test
    public void e4RoundsHalfToEven() {
        assertEquals(0x38, Float8e4Constant.toBits(1.0625f));   // tie between 1.0 (m=0) and 1.125 (m=1)
        assertEquals(0x3A, Float8e4Constant.toBits(1.1875f));   // tie between 1.125 (m=1) and 1.25 (m=2)
        assertEquals(0x00, Float8e4Constant.toBits(0x1p-10f));  // subnormal tie, rounds down to zero
        assertEquals(0x02, Float8e4Constant.toBits(0x1.8p-9f)); // subnormal tie, rounds up to even
    }

    @Test
    public void e4SaturatesOnOverflow() {
        // E4M3FN has no infinity: anything too large saturates to the largest finite value
        assertEquals(0x7E, Float8e4Constant.toBits(464.0f));    // tie towards the unrepresentable 480
        assertEquals(0x7E, Float8e4Constant.toBits(1000.0f));
        assertEquals(0xFE, Float8e4Constant.toBits(-1000.0f));
        assertEquals(0x7E, Float8e4Constant.toBits(Float.MAX_VALUE));
        assertEquals(0x7E, Float8e4Constant.toBits(Float.POSITIVE_INFINITY));
        assertEquals(0xFE, Float8e4Constant.toBits(Float.NEGATIVE_INFINITY));
    }

    @Test
    public void e4MapsNaN() {
        assertEquals(0x7F, Float8e4Constant.toBits(Float.NaN));
        assertEquals(0xFF, Float8e4Constant.toBits(Float.intBitsToFloat(0xFFC00000)));
        assertTrue(Float.isNaN(Float8e4Constant.toFloat(0x7F)));
        assertTrue(Float.isNaN(Float8e4Constant.toFloat(0xFF)));
        assertEquals(0xFFC00000, Float.floatToRawIntBits(Float8e4Constant.toFloat(0xFF)));
    }

    @Test
    public void e4UnderflowsToZero() {
        assertEquals(0x00, Float8e4Constant.toBits(Float.MIN_VALUE));
        assertEquals(0x80, Float8e4Constant.toBits(-Float.MIN_VALUE));
    }

    @Test
    public void e4RoundTripsEveryBitPattern() {
        for (int i = 0x00; i <= 0xFF; ++i) {
            final int nBits = i;
            assertEquals(nBits, Float8e4Constant.toBits(Float8e4Constant.toFloat(nBits)),
                    () -> String.format("E4M3 round trip failed for 0x%02X", nBits));
        }
    }

    // ----- E5M2 (Float8e5) -----------------------------------------------------------------------

    @Test
    public void e5EncodesKnownValues() {
        assertEquals(0x3C, Float8e5Constant.toBits(1.0f));
        assertEquals(0xBC, Float8e5Constant.toBits(-1.0f));
        assertEquals(0x40, Float8e5Constant.toBits(2.0f));
        assertEquals(0x38, Float8e5Constant.toBits(0.5f));
        assertEquals(0x7B, Float8e5Constant.toBits(57344.0f));       // largest finite
        assertEquals(0x04, Float8e5Constant.toBits(0x1p-14f));       // smallest normal
        assertEquals(0x01, Float8e5Constant.toBits(0x1p-16f));       // smallest subnormal
    }

    @Test
    public void e5DecodesKnownValues() {
        assertEquals(1.0f, Float8e5Constant.toFloat(0x3C));
        assertEquals(-1.0f, Float8e5Constant.toFloat(0xBC));
        assertEquals(57344.0f, Float8e5Constant.toFloat(0x7B));
        assertEquals(0x1p-14f, Float8e5Constant.toFloat(0x04));
        assertEquals(0x1p-16f, Float8e5Constant.toFloat(0x01));
    }

    @Test
    public void e5PreservesSignedZero() {
        assertEquals(0x00, Float8e5Constant.toBits(0.0f));
        assertEquals(0x80, Float8e5Constant.toBits(-0.0f));
        assertEquals(Float.floatToRawIntBits(0.0f), Float.floatToRawIntBits(Float8e5Constant.toFloat(0x00)));
        assertEquals(Float.floatToRawIntBits(-0.0f), Float.floatToRawIntBits(Float8e5Constant.toFloat(0x80)));
    }

    @Test
    public void e5RoundsHalfToEven() {
        assertEquals(0x3C, Float8e5Constant.toBits(1.125f));     // tie between 1.0 (m=0) and 1.25 (m=1)
        assertEquals(0x3E, Float8e5Constant.toBits(1.375f));     // tie between 1.25 (m=1) and 1.5 (m=2)
        assertEquals(0x00, Float8e5Constant.toBits(0x1p-17f));   // subnormal tie, rounds down to zero
        assertEquals(0x02, Float8e5Constant.toBits(0x1.8p-16f)); // subnormal tie, rounds up to even
    }

    @Test
    public void e5OverflowsToInfinity() {
        assertEquals(0x7B, Float8e5Constant.toBits(61439.0f));   // just below the round-to-infinity point
        assertEquals(0x7C, Float8e5Constant.toBits(61440.0f));   // tie rounds up, out of finite range
        assertEquals(0x7C, Float8e5Constant.toBits(Float.MAX_VALUE));
        assertEquals(0x7C, Float8e5Constant.toBits(Float.POSITIVE_INFINITY));
        assertEquals(0xFC, Float8e5Constant.toBits(Float.NEGATIVE_INFINITY));
        assertEquals(Float.POSITIVE_INFINITY, Float8e5Constant.toFloat(0x7C));
        assertEquals(Float.NEGATIVE_INFINITY, Float8e5Constant.toFloat(0xFC));
    }

    @Test
    public void e5MapsNaN() {
        assertEquals(0x7F, Float8e5Constant.toBits(Float.NaN));
        assertEquals(0xFF, Float8e5Constant.toBits(Float.intBitsToFloat(0xFFC00000)));
        for (int nBits : new int[] {0x7D, 0x7E, 0x7F, 0xFD, 0xFE, 0xFF}) {
            assertTrue(Float.isNaN(Float8e5Constant.toFloat(nBits)),
                    () -> String.format("E5M2 0x%02X should decode to NaN", nBits));
        }
        assertEquals(0xFFC00000, Float.floatToRawIntBits(Float8e5Constant.toFloat(0xFF)));
    }

    @Test
    public void e5UnderflowsToZero() {
        assertEquals(0x00, Float8e5Constant.toBits(Float.MIN_VALUE));
        assertEquals(0x80, Float8e5Constant.toBits(-Float.MIN_VALUE));
    }

    @Test
    public void e5RoundTripsEveryBitPattern() {
        for (int i = 0x00; i <= 0xFF; ++i) {
            final int nBits = i;
            // the non-canonical NaN payloads 0x7D/0x7E (and their negatives) canonicalise to 0x7F/0xFF
            int nExpect = (nBits & 0x7F) >= 0x7D ? nBits | 0x7F : nBits;
            assertEquals(nExpect, Float8e5Constant.toBits(Float8e5Constant.toFloat(nBits)),
                    () -> String.format("E5M2 round trip failed for 0x%02X", nBits));
        }
    }

    // ----- constant pool -------------------------------------------------------------------------

    @Test
    public void distinctValuesDoNotShareAPoolConstant() {
        FileStructure file = new FileStructure("test");
        ConstantPool  pool = file.getConstantPool();

        Float8e4Constant e4Zero = pool.ensureFloat8e4Constant(0.0f);
        Float8e4Constant e4One  = pool.ensureFloat8e4Constant(1.0f);
        assertNotEquals(e4Zero, e4One);
        assertEquals(0.0f, e4Zero.getValue());
        assertEquals(1.0f, e4One.getValue());

        Float8e5Constant e5Zero = pool.ensureFloat8e5Constant(0.0f);
        Float8e5Constant e5One  = pool.ensureFloat8e5Constant(1.0f);
        assertNotEquals(e5Zero, e5One);
        assertEquals(0.0f, e5Zero.getValue());
        assertEquals(1.0f, e5One.getValue());
    }
}
