package org.xtclang.ecstasy.numbers;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Int64Test 
        extends BaseNumberTest {

    @Test
    public void shouldBox() {
        for (long i : ensureLongTestData()) {
            Int64 n = Int64.$box(i);
            assertEquals(i, n.$value);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int64 n       = Int64.$box(i);
            Int8  n2      = n.toInt8(null, false, true);
            int  expected = (byte) i;
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int64 n = Int64.$box(i);
            if (i < Byte.MIN_VALUE || i > Byte.MAX_VALUE) {
                try {
                    n.toInt8(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                Int8 n2 = n.toInt8(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int64 n        = Int64.$box(i);
            UInt8 n2       = n.toUInt8(null, false, true);
            int   expected = (int) (i & 0xFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int64 n = Int64.$box(i);
            if (i < 0 || i > Byte.MAX_VALUE) {
                try {
                    n.toUInt8(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt8 n2 = n.toUInt8(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int64 n = Int64.$box(i);
            Int16 n2 = n.toInt16(null, false, true);
            assertEquals((short) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int64 n = Int64.$box(i);
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    n.toInt16(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                Int16 n2 = n.toInt16(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (long i : ensureLongTestData(0, Short.MAX_VALUE)) {
            Int64  n        = Int64.$box(i);
            UInt16 n2       = n.toUInt16(null, false, true);
            int    expected = (int) (i & 0xFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int64 n = Int64.$box(i);
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    n.toUInt16(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt16 n2 = n.toUInt16(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (long i : ensureLongTestData()) {
            Int64 n        = Int64.$box(i);
            Int32 n2       = n.toInt32(null, false, true);
            int   expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (long i : ensureLongTestData()) {
            Int64  n        = Int64.$box(i);
            UInt32 n2       = n.toUInt32(null, false, true);
            int    expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int64 n = Int64.$box(i);
            if (i < 0 || i > Integer.MAX_VALUE) {
                try {
                    n.toUInt32(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt32 n2 = n.toUInt32(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (long i : ensureLongTestData()) {
            Int64 n = Int64.$box(i);
            Int64 n2 = n.toInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (long i : ensureLongTestData()) {
            Int64  n        = Int64.$box(i);
            UInt64 n2       = n.toUInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int64 n = Int64.$box(i);
            if (i < 0) {
                try {
                    n.toUInt64(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt64 n2       = n.toUInt64(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (long i : ensureLongTestData()) {
            Int64  n  = Int64.$box(i);
            Int128 n2 = n.toInt128(null, false, true);
            assertEquals(i, n2.$lowValue);
            if (i < 0) {
                assertEquals(-1, n2.$highValue);
            } else {
                assertEquals(0, n2.$highValue);
            }
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (long i : ensureLongTestData()) {
            Int64   n        = Int64.$box(i);
            UInt128 n2       = n.toUInt128(null, false, true);
            assertEquals(i, n2.$lowValue);
            if (i < 0) {
                assertEquals(-1, n2.$highValue);
            } else {
                assertEquals(0, n2.$highValue);
            }
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int64 n = Int64.$box(i);
            if (i < 0) {
                try {
                    n.toUInt128(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt128 n2 = n.toUInt128(null, true, false);
                assertEquals(i, n2.$lowValue);
                assertEquals(0, n2.$highValue);
            }
        }
    }

}
