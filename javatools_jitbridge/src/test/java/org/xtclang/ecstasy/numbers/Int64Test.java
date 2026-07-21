package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;
import org.xvm.javajit.Ctx;

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
            int   n2       = Int64.toInt8$p(i, null, false, true);
            int   expected = (byte) i;
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            if (i < Byte.MIN_VALUE || i > Byte.MAX_VALUE) {
                try {
                    Int64.toInt8$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = Int64.toInt8$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            int   n2       = Int64.toUInt8$p(i, null, false, true);
            int   expected = (int) (i & 0xFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt8WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            if (i < 0 || i > Byte.MAX_VALUE) {
                try {
                    Int64.toUInt8$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = Int64.toUInt8$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            int n2 = Int64.toInt16$p(i, null, false, true);
            assertEquals((short) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    Int64.toInt16$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = Int64.toInt16$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (long i : ensureLongTestData(0, Short.MAX_VALUE)) {
            int   n2       = Int64.toUInt16$p(i, null, false, true);
            int   expected = (int) (i & 0xFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (long i : ensureLongTestData(Short.MIN_VALUE, Short.MAX_VALUE)) {
            if (i < 0 || i > Short.MAX_VALUE) {
                try {
                    Int64.toUInt16$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = Int64.toUInt16$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (long i : ensureLongTestData()) {
            int   n2       = Int64.toInt32$p(i, null, false, true);
            int   expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (long i : ensureLongTestData()) {
            int   n2       = Int64.toUInt32$p(i, null, false, true);
            int   expected = (int) (i & 0xFFFFFFFFL);
            assertEquals(expected, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            if (i < 0 || i > Integer.MAX_VALUE) {
                try {
                    Int64.toUInt32$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = Int64.toUInt32$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (long i : ensureLongTestData()) {
            long n2 = Int64.toInt64$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (long i : ensureLongTestData()) {
            long n2 = Int64.toUInt64$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (long i : ensureLongTestData()) {
            if (i < 0) {
                try {
                    Int64.toUInt64$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = Int64.toUInt64$p(i, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (long i : ensureLongTestData()) {
            Ctx   ctx = new Ctx(null, null);
            long  n2  = Int64.toInt128$p(i, ctx, false, true);
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
        for (long i : ensureLongTestData()) {
            Ctx   ctx = new Ctx(null, null);
            long  n2  = Int64.toUInt128$p(i, ctx, false, true);
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
        for (long i : ensureLongTestData()) {
            Ctx   ctx = new Ctx(null, null);
            if (i < 0) {
                try {
                    Int64.toUInt128$p(i, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                long n2 = Int64.toUInt128$p(i, ctx, true, false);
                assertEquals(i, n2);
                assertEquals(0L, ctx.i0);
            }
        }
    }

    @Test
    public void shouldConvertToDec32() {
        for (long i : ensureLongTestData()) {
            Int64 n   = Int64.$box(i);
            Ctx   ctx = new Ctx(null, null);
            int   n2  = n.toDec32$p(ctx);
            Dec32 dec = Dec32.$box(n2);
            assertEquals(BigDecimal.valueOf(i).round(MathContext.DECIMAL32), dec.$toBigDecimal());
        }
    }

    @Test
    public void shouldConvertToDec64() {
        for (long i : ensureLongTestData()) {
            Int64 n   = Int64.$box(i);
            Ctx   ctx = new Ctx(null, null);
            long  n2  = n.toDec64$p(ctx);
            Dec64 dec = Dec64.$box(n2);
            assertEquals(BigDecimal.valueOf(i).round(MathContext.DECIMAL64), dec.$toBigDecimal());
        }
    }

    @Test
    public void shouldConvertToDec128() {
        for (long i : ensureLongTestData()) {
            Int64  n   = Int64.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = n.toDec128$p(ctx);
            Dec128 dec = Dec128.$box(n2, ctx.i0);
            assertEquals(BigDecimal.valueOf(i).round(MathContext.DECIMAL128), dec.$toBigDecimal());
        }
    }
}