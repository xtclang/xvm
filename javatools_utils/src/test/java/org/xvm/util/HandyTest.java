package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.Arrays;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xvm.util.Handy.appendByteArrayAsHex;
import static org.xvm.util.Handy.appendByteAsHex;
import static org.xvm.util.Handy.appendChar;
import static org.xvm.util.Handy.appendIntAsHex;
import static org.xvm.util.Handy.appendLongAsHex;
import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.byteArrayToHexDump;
import static org.xvm.util.Handy.byteArrayToHexString;
import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.countChar;
import static org.xvm.util.Handy.countHexDigits;
import static org.xvm.util.Handy.digitValue;
import static org.xvm.util.Handy.hexStringToByteArray;
import static org.xvm.util.Handy.hexitValue;
import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.isCharEscaped;
import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.isHexit;
import static org.xvm.util.Handy.longToHexString;
import static org.xvm.util.Handy.nibbleToChar;
import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.renderByteToHex;
import static org.xvm.util.Handy.renderIntToHex;
import static org.xvm.util.Handy.renderLongToHex;
import static org.xvm.util.Handy.writeUtf8String;

/**
 * Tests of the Handy class.
 */
public class HandyTest {
    @Test
    public void testNibbleToChar() {
        // TODO: These are in the wrong order. The "actual" value should always be the first parameter for reports to make sense.
        assertEquals('0', nibbleToChar(0x00));
        assertEquals('1', nibbleToChar(0x01));
        assertEquals('2', nibbleToChar(0x02));
        assertEquals('3', nibbleToChar(0x03));
        assertEquals('4', nibbleToChar(0x04));
        assertEquals('5', nibbleToChar(0x05));
        assertEquals('6', nibbleToChar(0x06));
        assertEquals('7', nibbleToChar(0x07));
        assertEquals('8', nibbleToChar(0x08));
        assertEquals('9', nibbleToChar(0x09));
        assertEquals('a', nibbleToChar(0x0A));
        assertEquals('b', nibbleToChar(0x0B));
        assertEquals('c', nibbleToChar(0x0C));
        assertEquals('d', nibbleToChar(0x0D));
        assertEquals('e', nibbleToChar(0x0E));
        assertEquals('f', nibbleToChar(0x0F));
        assertEquals('0', nibbleToChar(0x10));
        assertEquals('1', nibbleToChar(0x21));
        assertEquals('2', nibbleToChar(0x32));
        assertEquals('3', nibbleToChar(0x43));
        assertEquals('4', nibbleToChar(0x54));
        assertEquals('5', nibbleToChar(0x65));
        assertEquals('6', nibbleToChar(0x76));
        assertEquals('7', nibbleToChar(0x87));
        assertEquals('8', nibbleToChar(0x98));
        assertEquals('9', nibbleToChar(0xA9));
        assertEquals('a', nibbleToChar(0xBA));
        assertEquals('b', nibbleToChar(0xCB));
        assertEquals('c', nibbleToChar(0xDC));
        assertEquals('d', nibbleToChar(0xED));
        assertEquals('e', nibbleToChar(0xFE));
        assertEquals('f', nibbleToChar(0xFF));
    }

    @Test
    public void testAppendByteAsHex() {
        StringBuilder sb = new StringBuilder();

        sb.append("0x");
        assertSame(sb, appendByteAsHex(sb, 0x12345678));
        assertEquals("0x78", sb.toString());
    }

    @Test
    public void testByteToHexString() {
        assertEquals("0x00", byteToHexString(0));
        assertEquals("0x78", byteToHexString(0x12345678));
        assertEquals("0xff", byteToHexString(0xFF));
    }

    @Test
    public void testAppendByteArrayAsHex() {
        StringBuilder sb = new StringBuilder();
        assertSame(sb, appendByteArrayAsHex(sb,
                new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}));
        assertEquals("01ff785a", sb.toString());
    }

    @Test
    public void testAppendByteArrayAsHex2() {
        StringBuilder sb = new StringBuilder();
        assertSame(sb, appendByteArrayAsHex(sb, new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}, 1, 2));
        assertEquals("ff78", sb.toString());
    }

    @Test
    public void testByteArrayToHexString() {
        assertEquals("0x", byteArrayToHexString(new byte[] {}));
        assertEquals("0x01ff785a", byteArrayToHexString(new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}));
        assertEquals("0xff78", byteArrayToHexString(new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}, 1, 2));
    }

    @Test
    public void testHexStringToByteArray() {
        assertArrayEquals(new byte[]{0x01, (byte) 0xFF, 0x78, 0x5A}, hexStringToByteArray("01FF785A"));
        assertArrayEquals(new byte[]{0x01, (byte) 0xFF, 0x78, 0x5A}, hexStringToByteArray("0x01FF785A"));
    }

    @Test
    public void testByteArrayToHexDump() {
        assertEquals("00: 41 42 AB\n02: 43    C ", byteArrayToHexDump("ABC".getBytes(), 2));
    }

    @Test
    public void testByteArrayToHexDump2() {
        byte[] ab = new byte[0x700];
        ab[0x500] = (byte) 'A';
        ab[0x501] = (byte) 'B';
        ab[0x502] = (byte) 'C';
        assertEquals("0500: 41 42 43 00 ABC.\n0504: 00 00 00    ... ", byteArrayToHexDump(ab, 0x500, 7, 4));
    }

    @Test
    public void testCountHexDigits() {
        assertEquals(1, countHexDigits(0x00000000));
        assertEquals(1, countHexDigits(0x00000007));
        assertEquals(2, countHexDigits(0x000000FF));
        assertEquals(3, countHexDigits(0x00000A18));
        assertEquals(4, countHexDigits(0x00001000));
        assertEquals(4, countHexDigits(0x00002000));
        assertEquals(4, countHexDigits(0x00004000));
        assertEquals(4, countHexDigits(0x00008000));
        assertEquals(4, countHexDigits(0x0000A000));
        assertEquals(4, countHexDigits(0x0000B000));
        assertEquals(4, countHexDigits(0x0000F000));
        assertEquals(8, countHexDigits(0x12345678));
    }

    @Test
    public void testAppendIntAsHex() {
        StringBuilder sb = new StringBuilder();

        assertSame(sb, appendIntAsHex(sb, 0x12345678));
        assertEquals("12345678", sb.toString());
    }

    @Test
    public void testAppendIntAsHex2() {
        StringBuilder sb = new StringBuilder();

        assertSame(sb, appendIntAsHex(sb, 0x12345678, 4));
        assertEquals("5678", sb.toString());
    }

    @Test
    public void testIntToHexString() {
        assertEquals("0x00000000", intToHexString(0));
        assertEquals("0x12345678", intToHexString(0x12345678));
        assertEquals("0xffffffff", intToHexString(-1));
    }

    @Test
    public void testCountHexDigits2() {
        assertEquals(1, countHexDigits(0x00000000L));
        assertEquals(1, countHexDigits(0x00000007L));
        assertEquals(2, countHexDigits(0x000000FFL));
        assertEquals(3, countHexDigits(0x00000A18L));
        assertEquals(4, countHexDigits(0x00001000L));
        assertEquals(4, countHexDigits(0x00002000L));
        assertEquals(4, countHexDigits(0x00004000L));
        assertEquals(4, countHexDigits(0x00008000L));
        assertEquals(4, countHexDigits(0x0000A000L));
        assertEquals(4, countHexDigits(0x0000B000L));
        assertEquals(4, countHexDigits(0x0000F000L));
        assertEquals(8, countHexDigits(0x12345678L));
        assertEquals(15, countHexDigits(0x01FFFFFFFFFFFFFFL));
        assertEquals(15, countHexDigits(0x0FFFFFFFFFFFFFFFL));
        assertEquals(16, countHexDigits(0x7FFFFFFFFFFFFFFFL));
        assertEquals(16, countHexDigits(0xFFFFFFFFFFFFFFFFL));
    }

    @Test
    public void testAppendLongAsHex() {
        StringBuilder sb = new StringBuilder();

        assertSame(sb, appendLongAsHex(sb, 0x123456789ABCDEF0L));
        assertEquals("123456789abcdef0", sb.toString());
    }

    @Test
    public void testAppendLongAsHex2() {
        StringBuilder sb = new StringBuilder();

        assertSame(sb, appendLongAsHex(sb, 0x123456789ABCDEF0L, 9));
        assertEquals("89abcdef0", sb.toString());
    }

    @Test
    public void testLongToHexString() {
        assertEquals("0x0000000000000000", longToHexString(0));
        assertEquals("0x123456789abcdef0", longToHexString(0x123456789ABCDEF0L));
        assertEquals("0xffffffffffffffff", longToHexString(-1));
    }

    @Test
    public void testRenderByteToHex() {
        char[] ach = new char[2];
        assertEquals(2, renderByteToHex(0x1F, ach, 0));
        assertEquals("1f", new String(ach));
    }

    @Test
    public void testRenderIntToHex() {
        char[] ach = new char[4];
        assertEquals(4, renderIntToHex(0x1F2EABCD, ach, 0, 4));
        assertEquals("abcd", new String(ach));

        ach = new char[10];
        Arrays.fill(ach, ' ');
        assertEquals(9, renderIntToHex(0x1F2EABCD, ach, 1, 8));
        assertEquals(" 1f2eabcd ", new String(ach));
    }

    @Test
    public void testRenderLongToHex() {
        char[] ach = new char[4];
        assertEquals(4, renderLongToHex(0x1F2EABCDL, ach, 0, 4));
        assertEquals("abcd", new String(ach));

        ach = new char[18];
        Arrays.fill(ach, ' ');
        assertEquals(17, renderLongToHex(0x1CEDCAFE1F2EABCDL, ach, 1, 16));
        assertEquals(" 1cedcafe1f2eabcd ", new String(ach));
    }

    @Test
    public void testCountChar() {
        assertEquals(0, countChar("", '.'));
        assertEquals(1, countChar(".", '.'));
        assertEquals(1, countChar("x.", '.'));
        assertEquals(1, countChar(".x", '.'));
        assertEquals(2, countChar(".x.", '.'));
        assertEquals(2, countChar("x..x", '.'));
        assertEquals(3, countChar("...", '.'));
        assertEquals(3, countChar("x...x", '.'));
        assertEquals(3, countChar("x.x.x.x", '.'));
    }

    @Test
    public void testIndentLines() {
        assertEquals("", indentLines("", "   "));
        assertEquals("  x", indentLines("x", "  "));
        assertEquals("..x\n..y", indentLines("x\ny", ".."));
    }

    @Test
    public void testIsDigit() {
        assertTrue(isDigit('0'));
        assertTrue(isDigit('1'));
        assertTrue(isDigit('2'));
        assertTrue(isDigit('3'));
        assertTrue(isDigit('4'));
        assertTrue(isDigit('5'));
        assertTrue(isDigit('6'));
        assertTrue(isDigit('7'));
        assertTrue(isDigit('8'));
        assertTrue(isDigit('9'));
        assertFalse(isDigit('.'));
        assertFalse(isDigit('/'));
        assertFalse(isDigit(':'));
        assertFalse(isDigit(';'));
        assertFalse(isDigit((char) 0));
        assertFalse(isDigit(' '));
        assertFalse(isDigit('~'));
        assertFalse(isDigit('A'));
        assertFalse(isDigit('a'));
        assertFalse(isDigit('F'));
        assertFalse(isDigit('f'));
    }

    @Test
    public void testDigitValue() {
        assertEquals(0, digitValue('0'));
        assertEquals(1, digitValue('1'));
        assertEquals(2, digitValue('2'));
        assertEquals(3, digitValue('3'));
        assertEquals(4, digitValue('4'));
        assertEquals(5, digitValue('5'));
        assertEquals(6, digitValue('6'));
        assertEquals(7, digitValue('7'));
        assertEquals(8, digitValue('8'));
        assertEquals(9, digitValue('9'));
    }

    @Test
    public void testIsHexit() {
        assertTrue(isHexit('0'));
        assertTrue(isHexit('1'));
        assertTrue(isHexit('2'));
        assertTrue(isHexit('3'));
        assertTrue(isHexit('4'));
        assertTrue(isHexit('5'));
        assertTrue(isHexit('6'));
        assertTrue(isHexit('7'));
        assertTrue(isHexit('8'));
        assertTrue(isHexit('9'));
        assertTrue(isHexit('A'));
        assertTrue(isHexit('B'));
        assertTrue(isHexit('C'));
        assertTrue(isHexit('D'));
        assertTrue(isHexit('E'));
        assertTrue(isHexit('F'));
        assertTrue(isHexit('a'));
        assertTrue(isHexit('b'));
        assertTrue(isHexit('c'));
        assertTrue(isHexit('d'));
        assertTrue(isHexit('e'));
        assertTrue(isHexit('f'));
        assertFalse(isHexit('.'));
        assertFalse(isHexit('/'));
        assertFalse(isHexit(':'));
        assertFalse(isHexit(';'));
        assertFalse(isHexit((char) 0));
        assertFalse(isHexit(' '));
        assertFalse(isHexit('~'));
        assertFalse(isHexit('@'));
        assertFalse(isHexit('`'));
        assertFalse(isHexit('G'));
        assertFalse(isHexit('g'));
    }

    @Test
    public void testHexitValue() {
        assertEquals(0, hexitValue('0'));
        assertEquals(1, hexitValue('1'));
        assertEquals(2, hexitValue('2'));
        assertEquals(3, hexitValue('3'));
        assertEquals(4, hexitValue('4'));
        assertEquals(5, hexitValue('5'));
        assertEquals(6, hexitValue('6'));
        assertEquals(7, hexitValue('7'));
        assertEquals(8, hexitValue('8'));
        assertEquals(9, hexitValue('9'));

        assertEquals(0x0A, hexitValue('A'));
        assertEquals(0x0B, hexitValue('B'));
        assertEquals(0x0C, hexitValue('C'));
        assertEquals(0x0D, hexitValue('D'));
        assertEquals(0x0E, hexitValue('E'));
        assertEquals(0x0F, hexitValue('F'));

        assertEquals(0x0A, hexitValue('a'));
        assertEquals(0x0B, hexitValue('b'));
        assertEquals(0x0C, hexitValue('c'));
        assertEquals(0x0D, hexitValue('d'));
        assertEquals(0x0E, hexitValue('e'));
        assertEquals(0x0F, hexitValue('f'));
    }

    @Test
    public void testIsCharEscaped() {
        // the automatically-escaped portion of the ascii range
        for (int i = 0; i < 0x20; ++i) {
            assertTrue(isCharEscaped((char) i));
        }

        // the rest of the ascii range
        for (int i = 0x20; i <= 0x7F; ++i) {
            assertEquals(i == '\\' || i == '\'' || i == '\"' || i == 0x7F, isCharEscaped((char) i));
        }

        // other chars that are used as line separators
        assertTrue(isCharEscaped((char) 0x0085));  // NEL
        assertTrue(isCharEscaped((char) 0x2028));  // line sep
        assertTrue(isCharEscaped((char) 0x2029));  // para sep

        // some random non-escaped chars outside of ASCII range
        for (int i = 0x20A0; i <= 0x20BE; ++i) {
            assertFalse(isCharEscaped((char) i));
        }
    }

    @Test
    public void testAppendChar() {
        StringBuilder sb = new StringBuilder();
        assertSame(sb, appendChar(sb, 'x'));
        assertSame(sb, appendChar(sb, '\t'));
        assertSame(sb, appendChar(sb, '\n'));
        assertSame(sb, appendChar(sb, (char) 0x7f));
        assertEquals("x\\t\\n\\d", sb.toString());
    }

    @Test
    public void testQuotedChar() {
        assertEquals("'x'", quotedChar('x'));
        assertEquals("'\\n'", quotedChar('\n'));
        assertEquals("'\\0'", quotedChar((char) 0));
        assertEquals("'\\".concat("u0001'"), quotedChar((char) 1));
    }

    @Test
    public void testAppendString() {
        StringBuilder sb = new StringBuilder();
        assertSame(sb, appendString(sb, "hello\nworld"));
        assertEquals("hello\\nworld", sb.toString());
    }

    @Test
    public void testQuotedString() {
        assertEquals("\"x\\ny\"", quotedString("x\ny"));
    }

    @Test
    public void testReadAndWriteUtf8String()
            throws IOException {
        String[] as = {
            "",
            "hello world!",
            "this\nis\ta test of the \nemergency broadcasting system! eèêeėēeęįœøō",
        };

        for (final String s : as) {
            var sb = new StringBuilder();
            writeUtf8String(dos(sb), s);
            assertEquals(s, readUtf8String(dis(sb.toString())));
        }
    }


    // ----- helpers -------------------------------------------------------------------------------

    static DataInput dis(final String s) {
        return new DataInputStream(new ByteArrayInputStream(hexStringToByteArray(s)));
    }

    static @NotNull DataOutput dos(final @NotNull StringBuilder sb) {
        return new DataOutputStream(new OutputStream() {
            @Override
            public void write(final int b) {
                appendByteAsHex(sb, b);
            }

            @Override
            public void write(final @NotNull byte[] b) {
                appendByteArrayAsHex(sb, b);
            }

            @Override
            public void write(final @NotNull byte[] b, final int off, final int len) {
                appendByteArrayAsHex(sb, b, off, len);
            }
        });
    }
}
