package org.xvm.compiler;


import java.io.File;
import java.util.List;

import org.junit.Test;

import org.xvm.asm.ErrorList;


/**
 * TODO
 */
public class LexerTest
    {
    public static void main(String[] args)
            throws Exception
        {
        if (args.length < 1 || args[0].length() < 1)
            {
            out("file name required");
            return;
            }

        File file = new File(args[0]);
        if (!(file.exists() && file.canRead()))
            {
            out("cannot read file: " + args[0]);
            return;
            }

        Source source = new Source(file, 0);
        lexit(source);
        }

    @Test
    public void testSimple()
        {
        Source source = new Source("module Test {}");
        lexit(source);
        }

    @Test
    public void testNumbers()
        {
        Source source = new Source("{0 1 -1 12345 -12345 +12345 .123 -.123 +.123 0.123 -0.123 +0.123}");
        lexit(source);
        }

    @Test
    public void testBadNumbers()
        {
        Source source = new Source("{+. -. 0. 123.}");
        lexit(source);
        }

    @Test
    public void testFractionsLeadingZeros()
        {
        Source source = new Source("{0.001 .00001230 0.1 0.000009 0.00 +0.001 +.00001230 +0.1 +0.000009 +0.00 -0.001 -.00001230 -0.1 -0.000009 -0.00}");
        lexit(source);
        }

    @Test
    public void testENumbers()
        {
        Source source = new Source("{1e10, .123e43, -2.5E-99}");
        lexit(source);
        }

//    @Test
    public void testUnsupportedForNowNums()
        {
        Source source = new Source("{1p10, .123p43, -2.5P-99 0x123.ABC 0b001.1001 0o713.415}");
        lexit(source);
        }

    @Test
    public void testHexNums()
        {
        Source source = new Source("{0x 0X 0x0 0X0 0x123 0x01 0xAb10 0Xf -0xf -0xE -0X00}");
        lexit(source);
        }

    @Test
    public void testBinNums()
        {
        Source source = new Source("{0b 0B 0b0 0B0 0b101 0b01 0b00_010 0B1 -0b1111 -0b1110 -0B00 -0b1110_ -0b_1110}");
        lexit(source);
        }

    @Test
    public void testOctNums()
        {
        Source source = new Source("{0o 0o0 0o123 0o01 0o7710 0o3 -0o771 -0o77 -0o00}");
        lexit(source);
        }

    @Test
    public void testStrings()
        {
        Source source = new Source("module Test {String s = \"hello world's!\"; Char ch = 'x';}");
        lexit(source);
        }

    static void lexit(Source source)
        {
        ErrorList errlist = new ErrorList(5);
        Lexer lexer = new Lexer(source, errlist);

        out("tokens:");
        while (lexer.hasNext())
            {
            out(lexer.next());
            }

        out("error list (" + errlist.getSeriousErrorCount()
                + " of " + errlist.getSeriousErrorMax() + ", sev="
                + errlist.getSeverity() + "):");
        List<ErrorList.ErrorInfo> list = errlist.getErrors();
        for (ErrorList.ErrorInfo info : list)
            {
            out(info);
            }
        }

    static void out(Object o)
        {
        System.out.println(o);
        }
    }
