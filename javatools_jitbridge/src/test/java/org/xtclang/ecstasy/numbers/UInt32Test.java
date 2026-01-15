package org.xtclang.ecstasy.numbers;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class UInt32Test 
        extends BaseNumberTest {

    @Test
    public void shouldBox() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n = UInt32.$box(i);
            assertEquals(i, n.$value);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i : ensurePositiveIntTestData(255)) {
            UInt32 n  = UInt32.$box(i);
            Int8   n2 = n.toInt8(null, false, true);
            assertEquals((byte) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData(255)) {
            UInt32 n = UInt32.$box(i);
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
        for (int i : ensurePositiveIntTestData(255)) {
            UInt32 n        = UInt32.$box(i);
            UInt8 n2       = n.toUInt8(null, false, true);
            int   expected = i & 0xFF;
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData(255)) {
            UInt32 n = UInt32.$box(i);
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
        for (int i : ensurePositiveIntTestData(Short.MAX_VALUE)) {
            UInt32 n = UInt32.$box(i);
            Int16 n2 = n.toInt16(null, false, true);
            assertEquals((short) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData(Short.MAX_VALUE)) {
            UInt32 n = UInt32.$box(i);
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
        for (int i : ensurePositiveIntTestData(Short.MAX_VALUE)) {
            UInt32 n        = UInt32.$box(i);
            UInt16 n2       = n.toUInt16(null, false, true);
            int    expected = i & 0xFFFF;
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData(Short.MAX_VALUE)) {
            UInt32 n = UInt32.$box(i);
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
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n  = UInt32.$box(i);
            Int32 n2 = n.toInt32(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n  = UInt32.$box(i);
            UInt32 n2 = n.toUInt32(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n = UInt32.$box(i);
            if (i < 0) {
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
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n = UInt32.$box(i);
            Int64 n2 = n.toInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n  = UInt32.$box(i);
            UInt64 n2 = n.toUInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n = UInt32.$box(i);
            if (i < 0) {
                try {
                    n.toUInt64(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt64 n2 = n.toUInt64(null, true, false);
                assertEquals(i, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32 n  = UInt32.$box(i);
            Int128 n2 = n.toInt128(null, false, true);
            assertEquals(i, n2.$lowValue);
            assertEquals(0, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32  n  = UInt32.$box(i);
            UInt128 n2 = n.toUInt128(null, false, true);
            assertEquals(0, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (int i : ensurePositiveIntTestData()) {
            UInt32  n  = UInt32.$box(i);
            UInt128 n2 = n.toUInt128(null, true, false);
            assertEquals(i, n2.$lowValue);
            assertEquals(0, n2.$highValue);
        }
    }
}
