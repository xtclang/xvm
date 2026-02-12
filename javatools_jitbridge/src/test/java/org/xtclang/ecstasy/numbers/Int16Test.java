package org.xtclang.ecstasy.numbers;

import org.junit.jupiter.api.Test;

import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;
import org.xvm.javajit.Ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Int16Test {

    @Test
    public void shouldBox() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
            assertEquals(i, n.$value);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n       = Int16.$box(i);
            int   n2      = n.toInt8$p(null, false, true);
            int  expected = (byte) i;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
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
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n        = Int16.$box(i);
            int   n2       = n.toUInt8$p(null, false, true);
            int   expected = i & 0xFF;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
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
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
            int   n2 = n.toInt16$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n        = Int16.$box(i);
            int   n2       = n.toUInt16$p(null, false, true);
            int   expected = i & 0xFFFF;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
            if (i < 0) {
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
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n  = Int16.$box(i);
            int   n2 = n.toInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n  = Int16.$box(i);
            int   n2 = n.toUInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
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
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n  = Int16.$box(i);
            long  n2 = n.toInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n  = Int16.$box(i);
            long  n2 = n.toUInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n = Int16.$box(i);
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
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n   = Int16.$box(i);
            Ctx   ctx = new Ctx(null, null);
            long  n2  = n.toInt128$p(ctx, false, true);
            assertEquals(i, n2);
            if (i < 0) {
                assertEquals(-1L, ctx.i0);
            } else {
                assertEquals(0L, ctx.i0);
            }
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n   = Int16.$box(i);
            Ctx   ctx = new Ctx(null, null);
            long  n2  = n.toUInt128$p(ctx, false, true);
            assertEquals(i, n2);
            if (i < 0) {
                assertEquals(-1L, ctx.i0);
            } else {
                assertEquals(0L, ctx.i0);
            }
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            Int16 n   = Int16.$box(i);
            Ctx   ctx = new Ctx(null, null);
            if (i < 0) {
                try {
                    n.toUInt128$p(null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = n.toUInt128$p(ctx, true, false);
                assertEquals(i, n2);
                assertEquals(0L, ctx.i0);
            }
        }
    }
}
