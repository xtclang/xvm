package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.math.BigInteger;

import java.util.Random;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.xvm.util.Handy.appendByteArrayAsHex;
import static org.xvm.util.Handy.appendByteAsHex;
import static org.xvm.util.Handy.hexStringToByteArray;
import static org.xvm.util.PackedInteger.readLong;
import static org.xvm.util.PackedInteger.writeLong;

/**
 * Tests of the PackedIntegerTest class.
 */
public class PackedIntegerTest
    {
    @Test
    public void testWritePackedLong()
            throws IOException
        {
        // small
        for (int i = -64; i <= 127; ++i)
            {
            StringBuilder sb = new StringBuilder();
            writeLong(dos(sb), i);
            assertEquals(2, sb.length(), "i=" + i);
            }

        // medium
        for (int i = -4096; i <= 4095; ++i)
            {
            if (i < -64 || i > 127)
                {
                StringBuilder sb = new StringBuilder();
                writeLong(dos(sb), i);
                assertEquals(4, sb.length(), "i=" + i);
                }
            }

        // large 2-byte
        for (int i = -32768; i <= 32767; ++i)
            {
            if (i < -4096 || i > 4095)
                {
                StringBuilder sb = new StringBuilder();
                writeLong(dos(sb), i);
                assertEquals(6, sb.length(), "i=" + i);
                }
            }

        // large 3-byte
        for (int i = -1048576; i <= 1048575; ++i)
            {
            if (i < -32768 || i > 32767)
                {
                StringBuilder sb = new StringBuilder();
                writeLong(dos(sb), i);
                assertEquals(8, sb.length(), "i=" + i);
                }
            }
        }

    @Test
    public void testReadAndWritePackedLong()
            throws IOException
        {
        ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
        DataOutputStream out    = new DataOutputStream(outRaw);
        for (long i = -17000000; i < 17000000; ++i) // cover +/- 2^24 ...
            {
            writeLong(out, i);
            }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(outRaw.toByteArray()));
        for (long i = -17000000; i < 17000000; ++i)
            {
            assertEquals(i, readLong(in));
            }

        try
            {
            in.readByte();
            throw new IllegalStateException("oops .. bytes left over");
            }
        catch (IOException ignore) {}
        }

    @Test
    public void testReadAndWritePackedLongRnd()
            throws IOException
        {
        Random rnd    = ThreadLocalRandom.current();
        long   lStart = System.currentTimeMillis();
        long   lStop  = lStart + 1000;           // TODO move to "slow" tests (and up the seconds)
        do
            {
            long lOrig = rnd.nextLong();

            ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
            DataOutputStream      out    = new DataOutputStream(outRaw);
            for (int i = 0; i < 40; ++i)
                {
                writeLong(out, lOrig >> i);
                }

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(outRaw.toByteArray()));
            for (int i = 0; i < 40; ++i)
                {
                long lCheck = readLong(in);
                assertEquals(lOrig >> i, lCheck);
                }

            try
                {
                in.readByte();
                throw new IllegalStateException("oops .. bytes left over");
                }
            catch (IOException ignore) {}
            }
        while (System.currentTimeMillis() < lStop);
        }

    @Test
    public void testSer()
            throws IOException
        {
        testSer("00");
        testSer("01");
        testSer("80");
        testSer("7F");
        testSer("0100");
        testSer("0180");
        testSer("017F");
        testSer("01FF");
        testSer("8000");
        testSer("8080");
        testSer("807F");
        testSer("80FF");
        testSer("7F00");
        testSer("7F80");
        testSer("7F7F");
        testSer("7FFF");
        testSer("FF00");
        testSer("FF80");
        testSer("FF7F");
        testSer("FF");
        testSer("FFFF");
        testSer("FFFFFFFF");
        testSer("FFFFFFFFFFFFFFFF");
        testSer("80");
        testSer("8080");
        testSer("80808080");
        testSer("8080808080808080");
        testSer("01");
        testSer("0101");
        testSer("01010101");
        testSer("0101010101010101");
        testSer("7F");
        testSer("7F7F");
        testSer("7F7F7F7F");
        testSer("7F7F7F7F7F7F7F7F");
        testSer("CD");
        testSer("CDCD");
        testSer("CDCDCDCD");
        testSer("CDCDCDCDCDCDCDCD");
        testSer("23456789ABCDEF0123456789ABCDEF");
        testSer("23456789ABCDEF0123456789ABCDEF");
        testSer("00456789ABCDEF0123456789ABCDEF");
        testSer("23456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("23456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("00456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("00006789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("00000089ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("00000000ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0000000000CDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0100456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0100006789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0100000089ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("0100000000ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("010000000000CDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF23456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF23456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF00456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF00006789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF00000089ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF00000000ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("FF0000000000CDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");

        // TODO move to "slow" tests
//        for (int i = -10000000; i < 10000000; ++i)
//            {
//            testSer(intToHexString(i).substring(3));
//            }
//
//        Random rnd = new Random();
//        long lStop = System.currentTimeMillis() + 30000;
//        for (int i = 0; i < 100000000; ++i)
//            {
//            int cb = rnd.nextInt(rnd.nextInt(64)+1)+1;
//            byte[] ab = new byte[cb];
//            rnd.nextBytes(ab);
//            testSer(ab);
//            if (i % 100000 == 0 && System.currentTimeMillis() > lStop)
//                {
//                break;
//                }
//            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    private static void testSer(String s)
            throws IOException
        {
        byte[] ab = hexStringToByteArray(s);
        testSer(ab);
        }

    private static void testSer(byte[] ab)
            throws IOException
        {
        BigInteger bigint = new BigInteger(ab);
        PackedInteger pint = new PackedInteger(bigint);
        StringBuilder sb = new StringBuilder();
        pint.writeObject(dos(sb));
        PackedInteger pint2 = new PackedInteger(dis(sb.toString()));
        assertEquals(pint, pint2);
        BigInteger bigint2 = pint2.getBigInteger();
        assertEquals(bigint, bigint2);
        }

    private static DataInput dis(String s)
        {
        return new DataInputStream(new ByteArrayInputStream(hexStringToByteArray(s)));
        }

    private static DataOutput dos(StringBuilder sb)
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