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
            int n2 = Nibble.toNibble$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toInt8$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toInt16$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toInt32$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i = 0; i <= 15; i++) {
            long n2 = Nibble.toInt64$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i = 0; i <= 15; i++) {
            Ctx    ctx = new Ctx(null, null);
            long   n2  = Nibble.toInt128$p(i, ctx, false, true);
            assertEquals(i, n2);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toUInt8$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toUInt16$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = 0; i <= 15; i++) {
            int n2 = Nibble.toUInt32$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = 0; i <= 15; i++) {
            long n2 = Nibble.toUInt64$p(i, null, false, true);
            assertEquals(i, n2);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = 0; i <= 15; i++) {
            Ctx    ctx = new Ctx(null, null);
            long   n2  = Nibble.toUInt128$p(i, ctx, false, true);
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