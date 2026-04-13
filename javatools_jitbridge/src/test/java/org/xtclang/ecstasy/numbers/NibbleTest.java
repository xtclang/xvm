package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import org.xvm.javajit.Ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NibbleTest {

    @Test
    public void shouldBox() {
        for (int i = 0; i <= 15; i++) {
            Nibble n = Nibble.$box(i);
            assertEquals(i, n.$value);
        }
    }

    @Test
    public void shouldMaskOnBox() {
        assertEquals(0, Nibble.$box(0).$value);
        assertEquals(15, Nibble.$box(15).$value);
        assertEquals(0, Nibble.$box(16).$value);
        assertEquals(1, Nibble.$box(17).$value);
        assertEquals(15, Nibble.$box(255).$value);
    }

    @Test
    public void shouldReturnCachedInstances() {
        for (int i = 0; i <= 15; i++) {
            assertSame(Nibble.$box(i), Nibble.$box(i));
        }
    }

    @Test
    public void shouldConvertToNibble() {
        for (int i = 0; i <= 15; i++) {
            Nibble n   = Nibble.$box(i);
            int    n2  = n.toNibble$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toInt8$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toInt16$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            long   n2 = n.toInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i = 0; i <= 15; i++) {
            Nibble n   = Nibble.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = n.toInt128$p(ctx, false, true);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toUInt8$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toUInt16$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            int    n2 = n.toUInt32$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = 0; i <= 15; i++) {
            Nibble n  = Nibble.$box(i);
            long   n2 = n.toUInt64$p(null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = 0; i <= 15; i++) {
            Nibble n   = Nibble.$box(i);
            Ctx    ctx = new Ctx(null, null);
            long   n2  = n.toUInt128$p(ctx, false, true);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToBigDecimal() {
        for (int i = 0; i <= 15; i++) {
            assertEquals(BigDecimal.valueOf(i), Nibble.$box(i).$toBigDecimal());
        }
    }

    @Test
    public void shouldHaveDebugToString() {
        assertEquals("Nibble:0", Nibble.$box(0).toString());
        assertEquals("Nibble:9", Nibble.$box(9).toString());
        assertEquals("Nibble:15", Nibble.$box(15).toString());
    }
}
