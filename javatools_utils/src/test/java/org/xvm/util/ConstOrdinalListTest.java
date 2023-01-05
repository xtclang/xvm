package org.xvm.util;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * A test for ConstOrdinalList.
 */
public class ConstOrdinalListTest
    {
    public static void main(String[] args)
        {
//        validate(new ConstOrdinalList(ConstOrdinalList.decompress(
//              Handy.hexStringToByteArray("0x0B0C0AD9245739010101FD0C01CC3336"))), 0, true);

        // for test reproducers:
        if (args != null && args.length > 0)
            {
            // e.g. "[4, 27, 27, 27, 27, 13, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 4, 4, 4]"
            boolean fDump = false;
            String s = args[0];
            if (args.length > 1 && s.length() > 0 && s.substring(0, 1).equalsIgnoreCase("d"))
                {
                fDump = true;
                s = args[1];
                }

            if (s.startsWith("["))
                {
                s = s.substring(1);
                }
            if (s.endsWith("]"))
                {
                s = s.substring(0, s.length() - 1);
                }
            String[] as = Handy.parseDelimitedString(s, ',');
            List<Integer> list = new ArrayList<>();
            for (String num : as)
                {
                num = num.trim();
                if (num.length() > 0)
                    {
                    list.add(Integer.parseInt(num));
                    }
                }

            validate(list, 0, fDump);
            }
        else
            {
            for (int i = 0; i < 1000; ++i)
                {
                int nMax = 1+s_rnd.nextInt(1+s_rnd.nextInt(Integer.MAX_VALUE));
                int nDft = s_rnd.nextInt(nMax);
                test(i, nMax, nDft);
                }
            }
        }

    static void test(int iTest, int nMax, int nDft)
        {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10; ++i)
            {
            try
                {
                validate(list, iTest*10+i, false);


                if (s_rnd.nextBoolean())
                    {
                    for (int iAdd = 0, cAdd = 1+s_rnd.nextInt(1+s_rnd.nextInt(12)); iAdd < cAdd; ++iAdd)
                        {
                        list.add(nDft);
                        }
                    }
                else if (s_rnd.nextBoolean())
                    {
                    int nVal = s_rnd.nextInt(nMax);
                    for (int iAdd = 0, cAdd = 1+s_rnd.nextInt(1+s_rnd.nextInt(25)); iAdd < cAdd; ++iAdd)
                        {
                        list.add(nVal);
                        }
                    }
                else
                    {
                    for (int iAdd = 0, cAdd = 1+s_rnd.nextInt(1+s_rnd.nextInt(20)); iAdd < cAdd; ++iAdd)
                        {
                        list.add(s_rnd.nextInt(nMax));
                        }
                    }
                }
            catch (RuntimeException | AssertionError e)
                {
                System.out.println("Exception with list: " + list);
                throw e;
                }
            }
        }

    static void validate(List<Integer> list, int iTest, boolean fDump)
        {
        ConstOrdinalList col = new ConstOrdinalList(list);
        validate(list, col, iTest, "", fDump);

        for (int cFast = 5; cFast < 100; cFast *= 2)
            {
            if (cFast > list.size())
                {
                break;
                }

            col = new ConstOrdinalList(ConstOrdinalList.compress(col.toIntArray(), cFast));
            validate(list, col, iTest, "fast=" + cFast, fDump);
            }
        }

    static void validate(List<Integer> list, ConstOrdinalList col, int iTest, String sTest, boolean fDump)
        {
        byte[] ab = col.getBytes();
        int    cb = ab.length;

        System.out.println("Test #" + iTest
            + (sTest == null || sTest.length() == 0 ? "" : " (" + sTest + ")")
            + ", size=" + col.size()
            + ", bytes=" + cb
            + ", compression=" + calcPct(list.size() * 4 + 4, cb));
        if (fDump)
            {
            System.out.println(Handy.byteArrayToHexDump(ab, 80));
            }

        if (list.isEmpty() != col.isEmpty())
            {
            throw new IllegalStateException("empty check");
            }
        if (list.size() != col.size())
            {
            throw new IllegalStateException("size check");
            }
        if (!list.equals(col))
            {
            throw new IllegalStateException("equality check");
            }

        for (int i = 0, c = list.size(); i < c; ++i)
            {
            assert list.get(i).equals(col.get(i));
            }
        }

    static String calcPct(int nFrom, int nTo)
        {
        int nPct = (int) ((((double) nTo) - nFrom) / nFrom * 100);
        return nPct < 0 ? nPct + "%" : "+" + nPct + "%";
        }

    private final static Random s_rnd = new Random();
    }