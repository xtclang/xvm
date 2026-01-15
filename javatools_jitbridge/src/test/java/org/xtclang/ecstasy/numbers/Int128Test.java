package org.xtclang.ecstasy.numbers;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Int128Test 
        extends BaseNumberTest {

    @Test
    public void shouldConvertToInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n       = new Int128(i, i < 0L ? -1L : 0L);
            Int8   n2      = n.toInt8(null, false, true);
            int   expected = (byte) i;
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            UInt8  n2       = n.toUInt8(null, false, true);
            int    expected = (int) (i & 0xFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
            Int16  n2 = n.toInt16(null, false, true);
            assertEquals((short) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            UInt16 n2       = n.toUInt16(null, false, true);
            int    expected = (int) (i & 0xFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            Int32  n2       = n.toInt32(null, false, true);
            int    expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (long i : ensureLongTestData()) {
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            UInt32 n2       = n.toUInt32(null, false, true);
            int    expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int128 n = new Int128(i, i < 0L ? -1L : 0L);
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
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n  = new Int128(low, high);
            Int64  n2 = n.toInt64(null, false, true);
            assertEquals(low, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt64WithBoundsCheck() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n = new Int128(low, high);
            if (high != 0L && high != -1L) {
                try {
                    n.toInt64(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                Int64 n2 = n.toInt64(null, true, false);
                assertEquals(low, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n  = new Int128(low, high);
            UInt64 n2 = n.toUInt64(null, false, true);
            assertEquals(low, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n = new Int128(low, high);
            if (high != 0L && high != -1L) {
                try {
                    n.toUInt64(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt64 n2 = n.toUInt64(null, true, false);
                assertEquals(low, n2.$value);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long   low  = rnd.nextLong();
            long   high = rnd.nextLong();
            Int128 n    = new Int128(low, high);
            Int128 n2 = n.toInt128(null, false, true);
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
            Int128  n    = new Int128(low, high);
            UInt128 n2   = n.toUInt128(null, false, true);
            assertEquals(low, n2.$lowValue);
            assertEquals(high, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
            if (i < 0L) {
                try {
                    n.toUInt128(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                UInt128 n2 = n.toUInt128(null, true, false);
                assertEquals(n.$lowValue, n2.$lowValue);
                assertEquals(0, n2.$highValue);
            }
        }
    }
}
