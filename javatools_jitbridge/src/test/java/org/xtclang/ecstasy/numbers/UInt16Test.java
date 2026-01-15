package org.xtclang.ecstasy.numbers;

import org.junit.jupiter.api.Test;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nException;

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
            UInt16 n = UInt16.$box(i);
            Int8  n2 = n.toInt8(null, false, true);
            assertEquals((byte) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt8WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            if (i > Byte.MAX_VALUE) {
                try {
                    n.toUInt8(null, true, false);
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
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            UInt8 n2 = n.toUInt8(null, false, true);
            assertEquals(n.$value & 0xFF, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt16() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            Int16 n2 = n.toInt16(null, false, true);
            assertEquals((short) i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt16WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            if (i > Short.MAX_VALUE) {
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
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n        = UInt16.$box(i);
            UInt16 n2       = n.toUInt16(null, false, true);
            assertEquals(n.$value, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt16WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            UInt16 n2 = n.toUInt16(null, true, false);
            assertEquals(n.$value, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt32() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            Int32 n2 = n.toInt32(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            UInt32 n2 = n.toUInt32(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt32WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            UInt32 n2 = n.toUInt32(null, true, false);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt64() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            Int64 n2 = n.toInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n  = UInt16.$box(i);
            UInt64 n2 = n.toUInt64(null, false, true);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToUInt64WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            UInt64 n2 = n.toUInt64(null, true, false);
            assertEquals(i, n2.$value);
        }
    }

    @Test
    public void shouldConvertToInt128() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            Int128 n2 = n.toInt128(null, false, true);
            assertEquals(i, n2.$lowValue);
            assertEquals(0, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16  n  = UInt16.$box(i);
            UInt128 n2 = n.toUInt128(null, false, true);
            assertEquals(0, n2.$highValue);
        }
    }

    @Test
    public void shouldConvertToUInt128WithBoundsCheck() {
        for (int i = 0; i <= 0xFFFF; i++) {
            UInt16 n = UInt16.$box(i);
            UInt128 n2 = n.toUInt128(null, true, false);
            assertEquals(i, n2.$lowValue);
            assertEquals(0, n2.$highValue);
        }
    }
}
