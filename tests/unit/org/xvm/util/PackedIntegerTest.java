package org.xvm.util;


import java.io.IOException;

import java.math.BigInteger;

import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

import static org.xvm.util.Handy.hexStringToByteArray;

import static org.xvm.util.Handy.intToHexString;
import static org.xvm.util.HandyTest.dis;
import static org.xvm.util.HandyTest.dos;


/**
 * Tests of the PackedIntegerTest class.
 */
public class PackedIntegerTest
    {
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

        for (int i = -10000000; i < 10000000; ++i)
            {
            testSer(intToHexString(i).substring(3));
            }

        Random rnd = new Random();
        long lStop = System.currentTimeMillis() + 30000;
        for (int i = 0; i < 100000000; ++i)
            {
            int cb = rnd.nextInt(rnd.nextInt(70)+1)+1;
            byte[] ab = new byte[cb];
            rnd.nextBytes(ab);
            testSer(ab);
            if (i % 100000 == 0 && System.currentTimeMillis() > lStop)
                {
                break;
                }
            }
        }

    private void testSer(String s)
            throws IOException
        {
        byte[] ab = hexStringToByteArray(s);
        testSer(ab);
        }

    private void testSer(byte[] ab)
            throws IOException
        {
        BigInteger bigint = new BigInteger(ab);
        PackedInteger pint = new PackedInteger(bigint);
        StringBuilder sb = new StringBuilder();
        pint.writeObject(dos(sb));
        PackedInteger pint2 = new PackedInteger(dis(sb.toString()));
        Assert.assertEquals(pint, pint2);
        BigInteger bigint2 = pint2.getBigInteger();
        Assert.assertEquals(bigint, bigint2);
        }
    }
