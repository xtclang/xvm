package org.xtclang.ecstasy.numbers;

import org.junit.jupiter.api.Test;

import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Int32Test 
        extends BaseNumberTest {

    @Test
    public void shouldBox() {
        for (int i : ensureIntTestData()) {
            Int32 n = Int32.$box(i);
            assertEquals(i, n.$value);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i : ensureIntTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int32 n        = Int32.$box(i);
            int   n2       = n.toInt8$p(null, false, true);
            int   expected = (byte) i;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (int i : ensureIntTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int32 n = Int32.$box(i);
            if (i < Byte.MIN_VALUE || i > Byte.MAX_VALUE) {
                try {
                    n.toInt8$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = n.toInt8$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (int i : ensureIntTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int32 n        = Int32.$box(i);
            int   n2       = n.toUInt8$p(null, false, true);
            int   expected = i & 0xFF;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (int i : ensureIntTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int32 n = Int32.$box(i);
            if (i < 0 || i > Byte.MAX_VALUE) {
                try {
                    n.toUInt8$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = n.toUInt8$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i : ensureIntTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int32 n  = Int32.$box(i);
            int   n2 = n.toInt16$p(null, false, true);
            assertEquals((short) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (int i : ensureIntTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int32 n = Int32.$box(i);
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    n.toInt16$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = n.toInt16$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i : ensureIntTestData(0, Short.MAX_VALUE)) {
            Int32 n        = Int32.$box(i);
            int   n2       = n.toUInt16$p(null, false, true);
            int   expected = i & 0xFFFF;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (int i : ensureIntTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int32 n = Int32.$box(i);
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    n.toUInt16$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = n.toUInt16$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i : ensureIntTestData()) {
            Int32 n  = Int32.$box(i);
            int   n2 = n.toInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i : ensureIntTestData()) {
            Int32 n  = Int32.$box(i);
            int   n2 = n.toUInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (int i : ensureIntTestData()) {
            Int32 n = Int32.$box(i);
            if (i < 0) {
                try {
                    n.toUInt32$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = n.toUInt32$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i : ensureIntTestData()) {
            Int32 n  = Int32.$box(i);
            long  n2 = n.toInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i : ensureIntTestData()) {
            Int32 n  = Int32.$box(i);
            long  n2 = n.toUInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (int i : ensureIntTestData()) {
            Int32 n = Int32.$box(i);
            if (i < 0) {
                try {
                    n.toUInt64$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = n.toUInt64$p(null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i : ensureIntTestData()) {
            Int32  n  = Int32.$box(i);
            Int128 n2 = n.toInt128$p(null, false, true);
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
        for (int i : ensureIntTestData()) {
            Int32   n        = Int32.$box(i);
            UInt128 n2       = n.toUInt128$p(null, false, true);
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
        for (int i : ensureIntTestData()) {
            Int32 n = Int32.$box(i);
            if (i < 0) {
                try {
                    n.toUInt128$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt128 n2 = n.toUInt128$p(null, true, false);
                assertEquals(i, n2.$lowValue);
                assertEquals(0, n2.$highValue);
            }
        }
    }
}
