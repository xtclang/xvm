package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;
import org.xvm.javajit.Ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class UInt16Test {

    @Test
    public void shouldBox() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            assertEquals(i & 0xFFFF, n.$value);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toInt8$p(n.$value, null, false, true);
            assertEquals((byte) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            if (i > Byte.MAX_VALUE) {
                try {
                    UInt16.toInt8$p(n.$value, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = UInt16.toInt8$p(n.$value, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toUInt8$p(n.$value, null, false, true);
            assertEquals(n.$value & 0xFF, n2);
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toInt16$p(n.$value, null, false, true);
            assertEquals((short) i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            if (i > Short.MAX_VALUE) {
                try {
                    UInt16.toInt16$p(n.$value, null, true, false);
                } catch (nException e) {
                    assertInstanceOf(OutOfBounds.class, e.exception);
                }
            } else {
                int n2 = UInt16.toInt16$p(n.$value, null, true, false);
                assertEquals(i, n2);
            }
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toUInt16$p(n.$value, null, false, true);
            assertEquals(n.$value, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toUInt16$p(n.$value, null, true, false);
            assertEquals(n.$value, n2);
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toInt32$p(n.$value, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toUInt32$p(n.$value, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            int    n2 = UInt16.toUInt32$p(n.$value, null, true, false);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            long   n2 = UInt16.toInt64$p(n.$value, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            long   n2 = UInt16.toUInt64$p(n.$value, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            long   n2 = UInt16.toUInt64$p(n.$value, null, true, false);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = UInt16.toInt128$p(n.$value, ctx, false, true);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = UInt16.toUInt128$p(n.$value, ctx, false, true);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = UInt16.toUInt128$p(n.$value, ctx, true, false);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToDec32() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            int    n2  = n.toDec32$p(ctx);
            Dec32  dec = Dec32.$box(n2);
            assertEquals(BigDecimal.valueOf(i), dec.$toBigDecimal());
        }
    }

    @Test
    public void shouldConvertToDec64() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = n.toDec64$p(ctx);
            Dec64  dec = Dec64.$box(n2);
            assertEquals(BigDecimal.valueOf(i), dec.$toBigDecimal());
        }
    }

    @Test
    public void shouldConvertToDec128() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n   = UInt16.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = n.toDec128$p(ctx);
            Dec128 dec = Dec128.$box(n2, ctx.i0);
            assertEquals(BigDecimal.valueOf(i), dec.$toBigDecimal());
        }
    }
}