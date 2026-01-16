package org.xtclang.ecstasy.numbers;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class UInt128Test 
        extends BaseNumberTest {

    @Test
    public void shouldConvertToInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            UInt128 n        = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2       = n.toInt8$p(null, false, true);
            int     expected = (byte) i;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            UInt128 n = new UInt128(i, i < 0L ? -1L : 0L);
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
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            UInt128 n        = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2       = n.toUInt8$p(null, false, true);
            int     expected = (int) (i & 0xFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            UInt128 n = new UInt128(i, i < 0L ? -1L : 0L);
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
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            UInt128 n  = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2 = n.toInt16$p(null, false, true);
            assertEquals((short) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            UInt128 n = new UInt128(i, i < 0L ? -1L : 0L);
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
        for (long i : ensureLongTestData(0, Short.MAX_VALUE)) {
            UInt128 n        = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2       = n.toUInt16$p(null, false, true);
            int     expected = (int) (i & 0xFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            UInt128 n = new UInt128(i, i < 0L ? -1L : 0L);
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
        for (long i : ensureLongTestData()) {
            UInt128 n        = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2       = n.toInt32$p(null, false, true);
            int     expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (long i : ensureLongTestData()) {
            UInt128 n        = new UInt128(i, i < 0L ? -1L : 0L);
            int     n2       = n.toUInt32$p(null, false, true);
            int     expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            UInt128 n = new UInt128(i, i < 0L ? -1L : 0L);
            if (i < 0 || i > Integer.MAX_VALUE) {
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
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextBoolean() ? 0L : rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            long    n2   = n.toInt64$p(null, false, true);
            assertEquals(low, n2);
        }
    }

    @Test
    public void shouldConvertToInt64WithBoundsCheck() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextBoolean() ? 0L : rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            if (high != 0) {
                try {
                    n.toInt64$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = n.toInt64$p(null, true, false);
                assertEquals(low, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextBoolean() ? 0L : rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            long    n2   = n.toUInt64$p(null, false, true);
            assertEquals(low, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextBoolean() ? 0L : rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            if (high != 0) {
                try {
                    n.toUInt64$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = n.toUInt64$p(null, true, false);
                assertEquals(low, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long   low  = rnd.nextLong();
            long   high = rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            Int128 n2 = n.toInt128$p(null, false, true);
            assertEquals(n.$lowValue, n2.$lowValue);
            assertEquals(n.$highValue, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextLong();
            UInt128 n    = new UInt128(low, high);
            UInt128 n2   = n.toUInt128$p(null, false, true);
            assertEquals(low, n2.$lowValue);
            assertEquals(high, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            UInt128 n  = new UInt128(i, i < 0L ? -1L : 0L);
            if (i < 0) {
                try {
                    n.toUInt128$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt128 n2 = n.toUInt128$p(null, true, false);
                assertEquals(n.$lowValue, n2.$lowValue);
                assertEquals(0, n2.$highValue);
            }
        }
    }
}
