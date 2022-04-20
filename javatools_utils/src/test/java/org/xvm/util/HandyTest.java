package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import static org.xvm.util.Handy.hexStringToByteArray;
import static org.xvm.util.Handy.nibbleToChar;
import static org.xvm.util.Handy.appendByteAsHex;
import static org.xvm.util.Handy.byteToHexString;
import static org.xvm.util.Handy.appendByteArrayAsHex;
import static org.xvm.util.Handy.byteArrayToHexString;
import static org.xvm.util.Handy.byteArrayToHexDump;
import static org.xvm.util.Handy.countHexDigits;
import static org.xvm.util.Handy.appendIntAsHex;
import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.Handy.appendLongAsHex;
import static org.xvm.util.Handy.longToHexString;
import static org.xvm.util.Handy.readUtf8String;
import static org.xvm.util.Handy.renderByteToHex;
import static org.xvm.util.Handy.renderIntToHex;
import static org.xvm.util.Handy.renderLongToHex;
import static org.xvm.util.Handy.countChar;
import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.isDigit;
import static org.xvm.util.Handy.digitValue;
import static org.xvm.util.Handy.isHexit;
import static org.xvm.util.Handy.hexitValue;
import static org.xvm.util.Handy.isCharEscaped;
import static org.xvm.util.Handy.appendChar;
import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.quotedString;
import static org.xvm.util.Handy.writeUtf8String;


/**
 * Tests of the Handy class.
 */
public class HandyTest
    {
    @Test
    public void testNibbleToChar()
        {
        Assert.assertEquals(nibbleToChar(0x00), '0');
        Assert.assertEquals(nibbleToChar(0x01), '1');
        Assert.assertEquals(nibbleToChar(0x02), '2');
        Assert.assertEquals(nibbleToChar(0x03), '3');
        Assert.assertEquals(nibbleToChar(0x04), '4');
        Assert.assertEquals(nibbleToChar(0x05), '5');
        Assert.assertEquals(nibbleToChar(0x06), '6');
        Assert.assertEquals(nibbleToChar(0x07), '7');
        Assert.assertEquals(nibbleToChar(0x08), '8');
        Assert.assertEquals(nibbleToChar(0x09), '9');
        Assert.assertEquals(nibbleToChar(0x0A), 'A');
        Assert.assertEquals(nibbleToChar(0x0B), 'B');
        Assert.assertEquals(nibbleToChar(0x0C), 'C');
        Assert.assertEquals(nibbleToChar(0x0D), 'D');
        Assert.assertEquals(nibbleToChar(0x0E), 'E');
        Assert.assertEquals(nibbleToChar(0x0F), 'F');
        Assert.assertEquals(nibbleToChar(0x10), '0');
        Assert.assertEquals(nibbleToChar(0x21), '1');
        Assert.assertEquals(nibbleToChar(0x32), '2');
        Assert.assertEquals(nibbleToChar(0x43), '3');
        Assert.assertEquals(nibbleToChar(0x54), '4');
        Assert.assertEquals(nibbleToChar(0x65), '5');
        Assert.assertEquals(nibbleToChar(0x76), '6');
        Assert.assertEquals(nibbleToChar(0x87), '7');
        Assert.assertEquals(nibbleToChar(0x98), '8');
        Assert.assertEquals(nibbleToChar(0xA9), '9');
        Assert.assertEquals(nibbleToChar(0xBA), 'A');
        Assert.assertEquals(nibbleToChar(0xCB), 'B');
        Assert.assertEquals(nibbleToChar(0xDC), 'C');
        Assert.assertEquals(nibbleToChar(0xED), 'D');
        Assert.assertEquals(nibbleToChar(0xFE), 'E');
        Assert.assertEquals(nibbleToChar(0xFF), 'F');
        }

    @Test
    public void testAppendByteAsHex()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("0x");
        Assert.assertEquals(sb, appendByteAsHex(sb, 0x12345678));
        Assert.assertEquals(sb.toString(), "0x78");
        }

    @Test
    public void testByteToHexString()
        {
        Assert.assertEquals(byteToHexString(0), "0x00");
        Assert.assertEquals(byteToHexString(0x12345678), "0x78");
        Assert.assertEquals(byteToHexString(0xFF), "0xFF");
        }

    @Test
    public void testAppendByteArrayAsHex()
        {
        StringBuilder sb = new StringBuilder();
        Assert.assertEquals(sb, appendByteArrayAsHex(sb,
                new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}));
        Assert.assertEquals(sb.toString(), "01FF785A");
        }

    @Test
    public void testAppendByteArrayAsHex2()
        {
        StringBuilder sb = new StringBuilder();
        Assert.assertEquals(sb, appendByteArrayAsHex(sb, new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}, 1, 2));
        Assert.assertEquals(sb.toString(), "FF78");
        }

    @Test
    public void testByteArrayToHexString()
        {
        Assert.assertEquals(byteArrayToHexString(new byte[] {}), "0x");
        Assert.assertEquals(byteArrayToHexString(new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}), "0x01FF785A");
        Assert.assertEquals(byteArrayToHexString(new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}, 1, 2), "0xFF78");
        }

    @Test
    public void testHexStringToByteArray()
        {
        Assert.assertTrue(Arrays.equals(hexStringToByteArray("01FF785A"),
                new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}));
        Assert.assertTrue(Arrays.equals(hexStringToByteArray("0x01FF785A"),
                new byte[] {0x01, (byte) 0xFF, 0x78, 0x5A}));
        }

    @Test
    public void testByteArrayToHexDump()
            throws IOException
        {
        Assert.assertEquals("00: 41 42 AB\n02: 43    C ", byteArrayToHexDump("ABC".getBytes(), 2));
        }

    @Test
    public void testByteArrayToHexDump2()
            throws IOException
        {
        byte[] ab = new byte[0x700];
        ab[0x500] = (byte) 'A';
        ab[0x501] = (byte) 'B';
        ab[0x502] = (byte) 'C';
        Assert.assertEquals("0500: 41 42 43 00 ABC.\n0504: 00 00 00    ... ", byteArrayToHexDump(ab, 0x500, 7, 4));
        }

    @Test
    public void testCountHexDigits()
        {
        Assert.assertEquals(1, countHexDigits(0x00000000));
        Assert.assertEquals(1, countHexDigits(0x00000007));
        Assert.assertEquals(2, countHexDigits(0x000000FF));
        Assert.assertEquals(3, countHexDigits(0x00000A18));
        Assert.assertEquals(4, countHexDigits(0x00001000));
        Assert.assertEquals(4, countHexDigits(0x00002000));
        Assert.assertEquals(4, countHexDigits(0x00004000));
        Assert.assertEquals(4, countHexDigits(0x00008000));
        Assert.assertEquals(4, countHexDigits(0x0000A000));
        Assert.assertEquals(4, countHexDigits(0x0000B000));
        Assert.assertEquals(4, countHexDigits(0x0000F000));
        Assert.assertEquals(8, countHexDigits(0x12345678));
        }

    @Test
    public void testAppendIntAsHex()
        {
        StringBuilder sb = new StringBuilder();

        Assert.assertEquals(sb, appendIntAsHex(sb, 0x12345678));
        Assert.assertEquals(sb.toString(), "12345678");
        }

    @Test
    public void testAppendIntAsHex2()
        {
        StringBuilder sb = new StringBuilder();

        Assert.assertEquals(sb, appendIntAsHex(sb, 0x12345678, 4));
        Assert.assertEquals(sb.toString(), "5678");
        }

    @Test
    public void testIntToHexString()
        {
        Assert.assertEquals(intToHexString(0), "0x00000000");
        Assert.assertEquals(intToHexString(0x12345678), "0x12345678");
        Assert.assertEquals(intToHexString(-1), "0xFFFFFFFF");
        }

    @Test
    public void testCountHexDigits2()
        {
        Assert.assertEquals(1, countHexDigits(0x00000000L));
        Assert.assertEquals(1, countHexDigits(0x00000007L));
        Assert.assertEquals(2, countHexDigits(0x000000FFL));
        Assert.assertEquals(3, countHexDigits(0x00000A18L));
        Assert.assertEquals(4, countHexDigits(0x00001000L));
        Assert.assertEquals(4, countHexDigits(0x00002000L));
        Assert.assertEquals(4, countHexDigits(0x00004000L));
        Assert.assertEquals(4, countHexDigits(0x00008000L));
        Assert.assertEquals(4, countHexDigits(0x0000A000L));
        Assert.assertEquals(4, countHexDigits(0x0000B000L));
        Assert.assertEquals(4, countHexDigits(0x0000F000L));
        Assert.assertEquals(8, countHexDigits(0x12345678L));
        Assert.assertEquals(15, countHexDigits(0x01FFFFFFFFFFFFFFL));
        Assert.assertEquals(15, countHexDigits(0x0FFFFFFFFFFFFFFFL));
        Assert.assertEquals(16, countHexDigits(0x7FFFFFFFFFFFFFFFL));
        Assert.assertEquals(16, countHexDigits(0xFFFFFFFFFFFFFFFFL));
        }

    @Test
    public void testAppendLongAsHex()
        {
        StringBuilder sb = new StringBuilder();

        Assert.assertEquals(sb, appendLongAsHex(sb, 0x123456789ABCDEF0L));
        Assert.assertEquals(sb.toString(), "123456789ABCDEF0");
        }

    @Test
    public void testAppendLongAsHex2()
        {
        StringBuilder sb = new StringBuilder();

        Assert.assertEquals(sb, appendLongAsHex(sb, 0x123456789ABCDEF0L, 9));
        Assert.assertEquals(sb.toString(), "89ABCDEF0");
        }

    @Test
    public void testLongToHexString()
        {
        Assert.assertEquals("0x0000000000000000", longToHexString(0));
        Assert.assertEquals("0x123456789ABCDEF0", longToHexString(0x123456789ABCDEF0L));
        Assert.assertEquals("0xFFFFFFFFFFFFFFFF", longToHexString(-1));
        }

    @Test
    public void testRenderByteToHex()
        {
        char[] ach = new char[2];
        Assert.assertEquals(2, renderByteToHex(0x1F, ach, 0));
        Assert.assertEquals("1F", new String(ach));
        }

    @Test
    public void testRenderIntToHex()
        {
        char[] ach = new char[4];
        Assert.assertEquals(4, renderIntToHex(0x1F2EABCD, ach, 0, 4));
        Assert.assertEquals("ABCD", new String(ach));

        ach = new char[10];
        Arrays.fill(ach, ' ');
        Assert.assertEquals(9, renderIntToHex(0x1F2EABCD, ach, 1, 8));
        Assert.assertEquals(" 1F2EABCD ", new String(ach));
        }

    @Test
    public void testRenderLongToHex()
        {
        char[] ach = new char[4];
        Assert.assertEquals(4, renderLongToHex(0x1F2EABCDL, ach, 0, 4));
        Assert.assertEquals("ABCD", new String(ach));

        ach = new char[18];
        Arrays.fill(ach, ' ');
        Assert.assertEquals(17, renderLongToHex(0x1CEDCAFE1F2EABCDL, ach, 1, 16));
        Assert.assertEquals(" 1CEDCAFE1F2EABCD ", new String(ach));
        }

    @Test
    public void testCountChar()
        {
        Assert.assertEquals(0, countChar("", '.'));
        Assert.assertEquals(1, countChar(".", '.'));
        Assert.assertEquals(1, countChar("x.", '.'));
        Assert.assertEquals(1, countChar(".x", '.'));
        Assert.assertEquals(2, countChar(".x.", '.'));
        Assert.assertEquals(2, countChar("x..x", '.'));
        Assert.assertEquals(3, countChar("...", '.'));
        Assert.assertEquals(3, countChar("x...x", '.'));
        Assert.assertEquals(3, countChar("x.x.x.x", '.'));
        }

    @Test
    public void testIndentLines()
        {
        Assert.assertEquals("", indentLines("", "   "));
        Assert.assertEquals("  x", indentLines("x", "  "));
        Assert.assertEquals("..x\n..y", indentLines("x\ny", ".."));
        }

    @Test
    public void testIsDigit()
        {
        Assert.assertTrue(isDigit('0'));
        Assert.assertTrue(isDigit('1'));
        Assert.assertTrue(isDigit('2'));
        Assert.assertTrue(isDigit('3'));
        Assert.assertTrue(isDigit('4'));
        Assert.assertTrue(isDigit('5'));
        Assert.assertTrue(isDigit('6'));
        Assert.assertTrue(isDigit('7'));
        Assert.assertTrue(isDigit('8'));
        Assert.assertTrue(isDigit('9'));
        Assert.assertFalse(isDigit('.'));
        Assert.assertFalse(isDigit('/'));
        Assert.assertFalse(isDigit(':'));
        Assert.assertFalse(isDigit(';'));
        Assert.assertFalse(isDigit((char) 0));
        Assert.assertFalse(isDigit(' '));
        Assert.assertFalse(isDigit('~'));
        Assert.assertFalse(isDigit('A'));
        Assert.assertFalse(isDigit('a'));
        Assert.assertFalse(isDigit('F'));
        Assert.assertFalse(isDigit('f'));
        }

    @Test
    public void testDigitValue()
        {
        Assert.assertEquals(0, digitValue('0'));
        Assert.assertEquals(1, digitValue('1'));
        Assert.assertEquals(2, digitValue('2'));
        Assert.assertEquals(3, digitValue('3'));
        Assert.assertEquals(4, digitValue('4'));
        Assert.assertEquals(5, digitValue('5'));
        Assert.assertEquals(6, digitValue('6'));
        Assert.assertEquals(7, digitValue('7'));
        Assert.assertEquals(8, digitValue('8'));
        Assert.assertEquals(9, digitValue('9'));
        }

    @Test
    public void testIsHexit()
        {
        Assert.assertTrue(isHexit('0'));
        Assert.assertTrue(isHexit('1'));
        Assert.assertTrue(isHexit('2'));
        Assert.assertTrue(isHexit('3'));
        Assert.assertTrue(isHexit('4'));
        Assert.assertTrue(isHexit('5'));
        Assert.assertTrue(isHexit('6'));
        Assert.assertTrue(isHexit('7'));
        Assert.assertTrue(isHexit('8'));
        Assert.assertTrue(isHexit('9'));
        Assert.assertTrue(isHexit('A'));
        Assert.assertTrue(isHexit('B'));
        Assert.assertTrue(isHexit('C'));
        Assert.assertTrue(isHexit('D'));
        Assert.assertTrue(isHexit('E'));
        Assert.assertTrue(isHexit('F'));
        Assert.assertTrue(isHexit('a'));
        Assert.assertTrue(isHexit('b'));
        Assert.assertTrue(isHexit('c'));
        Assert.assertTrue(isHexit('d'));
        Assert.assertTrue(isHexit('e'));
        Assert.assertTrue(isHexit('f'));
        Assert.assertFalse(isHexit('.'));
        Assert.assertFalse(isHexit('/'));
        Assert.assertFalse(isHexit(':'));
        Assert.assertFalse(isHexit(';'));
        Assert.assertFalse(isHexit((char) 0));
        Assert.assertFalse(isHexit(' '));
        Assert.assertFalse(isHexit('~'));
        Assert.assertFalse(isHexit('@'));
        Assert.assertFalse(isHexit('`'));
        Assert.assertFalse(isHexit('G'));
        Assert.assertFalse(isHexit('g'));
        }

    @Test
    public void testHexitValue()
        {
        Assert.assertEquals(0, hexitValue('0'));
        Assert.assertEquals(1, hexitValue('1'));
        Assert.assertEquals(2, hexitValue('2'));
        Assert.assertEquals(3, hexitValue('3'));
        Assert.assertEquals(4, hexitValue('4'));
        Assert.assertEquals(5, hexitValue('5'));
        Assert.assertEquals(6, hexitValue('6'));
        Assert.assertEquals(7, hexitValue('7'));
        Assert.assertEquals(8, hexitValue('8'));
        Assert.assertEquals(9, hexitValue('9'));

        Assert.assertEquals(0x0A, hexitValue('A'));
        Assert.assertEquals(0x0B, hexitValue('B'));
        Assert.assertEquals(0x0C, hexitValue('C'));
        Assert.assertEquals(0x0D, hexitValue('D'));
        Assert.assertEquals(0x0E, hexitValue('E'));
        Assert.assertEquals(0x0F, hexitValue('F'));

        Assert.assertEquals(0x0A, hexitValue('a'));
        Assert.assertEquals(0x0B, hexitValue('b'));
        Assert.assertEquals(0x0C, hexitValue('c'));
        Assert.assertEquals(0x0D, hexitValue('d'));
        Assert.assertEquals(0x0E, hexitValue('e'));
        Assert.assertEquals(0x0F, hexitValue('f'));
        }

    @Test
    public void testIsCharEscaped()
        {
        // the automatically-escaped portion of the ascii range
        for (int i = 0; i < 0x20; ++i)
            {
            Assert.assertTrue(isCharEscaped((char) i));
            }

        // the rest of the ascii range
        for (int i = 0x20; i <= 0x7F; ++i)
            {
            Assert.assertEquals(i == '\\' || i == '\'' || i == '\"' || i == 0x7F, isCharEscaped((char) i));
            }

        // other chars that are used as line separators
        Assert.assertTrue(isCharEscaped((char) 0x0085));  // NEL
        Assert.assertTrue(isCharEscaped((char) 0x2028));  // line sep
        Assert.assertTrue(isCharEscaped((char) 0x2029));  // para sep

        // some random non-escaped chars outside of ASCII range
        for (int i = 0x20A0; i <= 0x20BE; ++i)
            {
            Assert.assertFalse(isCharEscaped((char) i));
            }
        }

    @Test
    public void testAppendChar()
        {
        StringBuilder sb = new StringBuilder();
        Assert.assertEquals(sb, appendChar(sb, 'x'));
        Assert.assertEquals(sb, appendChar(sb, '\t'));
        Assert.assertEquals(sb, appendChar(sb, '\n'));
        Assert.assertEquals(sb, appendChar(sb, (char) 0x7f));
        Assert.assertEquals("x\\t\\n\\d", sb.toString());
        }

    @Test
    public void testQuotedChar()
        {
        Assert.assertEquals("'x'", quotedChar('x'));
        Assert.assertEquals("'\\n'", quotedChar('\n'));
        Assert.assertEquals("'\\".concat("u0000'"), quotedChar((char) 0));
        }

    @Test
    public void testAppendString()
        {
        StringBuilder sb = new StringBuilder();
        Assert.assertEquals(sb, appendString(sb, "hello\nworld"));
        Assert.assertEquals("hello\\nworld", sb.toString());
        }

    @Test
    public void testQuotedString()
        {
        Assert.assertEquals("\"x\\ny\"", quotedString("x\ny"));
        }

    @Test
    public void testReadAndWriteUtf8String()
            throws IOException
        {
        String[] as = new String[]
            {
            "",
            "hello world!",
            "this\nis\ta test of the \nemergency broadcasting system! eèêeėēeęįœøō",
            };

        for (int i = 0, c = as.length; i < c; ++i)
            {
            String s = as[i];
            StringBuilder sb = new StringBuilder();
            writeUtf8String(dos(sb), s);
            Assert.assertEquals(s, readUtf8String(dis(sb.toString())));
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    static DataInput dis(String s)
        {
        return new DataInputStream(new ByteArrayInputStream(hexStringToByteArray(s)));
        }

    static DataOutput dos(StringBuilder sb)
        {
        return new DataOutputStream(new OutputStream()
            {
            @Override
            public void write(int b)
                {
                appendByteAsHex(sb, b);
                }

            @Override
            public void write(byte[] b)
                {
                appendByteArrayAsHex(sb, b);
                }

            @Override
            public void write(byte[] b, int off, int len)
                {
                appendByteArrayAsHex(sb, b, off, len);
                }
            });
        }
    }