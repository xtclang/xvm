package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import org.xvm.javajit.Ctx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BitTest {

    @Test
    public void shouldBoxZero() {
        Bit bit = Bit.$box(0);
        assertEquals(0, bit.$value);
    }

    @Test
    public void shouldBoxOne() {
        Bit bit = Bit.$box(1);
        assertEquals(1, bit.$value);
    }

    @Test
    public void shouldMaskOnBox() {
        assertEquals(0, Bit.$box(0).$value);
        assertEquals(1, Bit.$box(1).$value);
        assertEquals(0, Bit.$box(2).$value);
        assertEquals(1, Bit.$box(3).$value);
        assertEquals(0, Bit.$box(256).$value);
        assertEquals(1, Bit.$box(255).$value);
    }

    @Test
    public void shouldReturnCachedInstances() {
        assertSame(Bit.$box(0), Bit.$box(0));
        assertSame(Bit.$box(1), Bit.$box(1));
    }

    @Test
    public void shouldConvertToBit() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toBit$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToInt8() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toInt8$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toInt16$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toInt32$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i = 0; i <= 1; i++) {
            Bit  bit = Bit.$box(i);
            long n   = Bit.toInt64$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i = 0; i <= 1; i++) {
            Bit  bit = Bit.$box(i);
            Ctx  ctx = new Ctx(null, null);
            long n   = Bit.toInt128$p(bit.$value, ctx, false, true);
            assertEquals(i, n);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToUInt8() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toUInt8$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToUInt16() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toUInt16$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = 0; i <= 1; i++) {
            Bit bit = Bit.$box(i);
            int n   = Bit.toUInt32$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = 0; i <= 1; i++) {
            Bit  bit = Bit.$box(i);
            long n   = Bit.toUInt64$p(bit.$value, null, false, true);
            assertEquals(i, n);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = 0; i <= 1; i++) {
            Bit  bit = Bit.$box(i);
            Ctx  ctx = new Ctx(null, null);
            long n   = Bit.toUInt128$p(bit.$value, ctx, false, true);
            assertEquals(i, n);
            assertEquals(0L, ctx.i0);
        }
    }

    @Test
    public void shouldConvertToBigDecimal() {
        assertEquals(BigDecimal.ZERO, Bit.$box(0).$toBigDecimal());
        assertEquals(BigDecimal.ONE, Bit.$box(1).$toBigDecimal());
    }

    @Test
    public void shouldHaveDebugToString() {
        assertEquals("Bit:0", Bit.$box(0).toString());
        assertEquals("Bit:1", Bit.$box(1).toString());
    }
}