package org.xtclang.ecstasy.numbers;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;
import org.xvm.javajit.Ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Int128Test 
        extends BaseNumberTest {

    @Test
    public void shouldConvertToInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            int    n2       = n.toInt8$p(null, false, true);
            int    expected = (byte) i;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            int    n2       = n.toUInt8$p(null, false, true);
            int    expected = (int) (i & 0xFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
            int    n2 = n.toInt16$p(null, false, true);
            assertEquals((short) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            int    n2       = n.toUInt16$p(null, false, true);
            int    expected = (int) (i & 0xFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            Int128 n  = new Int128(i, i < 0L ? -1L : 0L);
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
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            int    n2       = n.toInt32$p(null, false, true);
            int    expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (long i : ensureLongTestData()) {
            Int128 n        = new Int128(i, i < 0L ? -1L : 0L);
            int    n2       = n.toUInt32$p(null, false, true);
            int    expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int128 n = new Int128(i, i < 0L ? -1L : 0L);
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
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n  = new Int128(low, high);
            long   n2 = n.toInt64$p(null, false, true);
            assertEquals(low, n2);
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
            long low  = rnd.nextLong();
            long high = switch (rnd.nextInt(3)) {
                case 0 -> 0L;  // fits within an Int64
                case 1 -> -1L; // fits within an UInt64
                default -> rnd.nextLong(); // will not fit in an Int64
            };
            Int128 n  = new Int128(low, high);
            long   n2 = n.toUInt64$p(null, false, true);
            assertEquals(low, n2);
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
            Int128 n    = new Int128(low, high);
            Ctx    ctx  = new Ctx(null, null);
            long   n2   = n.toInt128$p(ctx, false, true);
            assertEquals(n.$lowValue, n2);
            assertEquals(n.$highValue, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        Random rnd = new Random();
        for (int i = 0; i < 5000; i++) {
            long    low  = rnd.nextLong();
            long    high = rnd.nextLong();
            Int128  n    = new Int128(low, high);
            Ctx     ctx  = new Ctx(null, null);
            long    n2   = n.toUInt128$p(ctx, false, true);
            assertEquals(n.$lowValue, n2);
            assertEquals(n.$highValue, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            Int128 n   = new Int128(i, i < 0L ? -1L : 0L);
            Ctx    ctx = new Ctx(null, null);
            if (i < 0L) {
                try {
                    n.toUInt128$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = n.toUInt128$p(ctx, true, false);
                assertEquals(n.$lowValue, n2);
                assertEquals(n.$highValue, ctx.i0);
            }
        }
    }
}
