package org.xvm.util;


import java.io.IOException;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;

import static org.xvm.util.Handy.hexStringToByteArray;

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
        testSer("100123456789ABCDEF0123456789ABCDEF");
        testSer("100023456789ABCDEF0123456789ABCDEF");
        testSer("100000456789ABCDEF0123456789ABCDEF");
        testSer("140123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("140023456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("140000456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("140000006789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("140000000089ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("140000000000ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        testSer("14000000000000CDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        }

    private void testSer(String s)
            throws IOException
        {
        byte[] ab = hexStringToByteArray(s.substring(2));
        BigInteger bigint = new BigInteger(ab);
        PackedInteger pint = new PackedInteger(dis(s));
        Assert.assertEquals(bigint, pint.getBigInteger());
        StringBuilder sb = new StringBuilder();
        pint.writeObject(dos(sb));
        Assert.assertEquals(s, sb.toString());
        }
    }
