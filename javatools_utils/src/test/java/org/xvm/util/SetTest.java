package org.xvm.util;


import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.junit.Assert;


/**
 * Tests of the Handy class.
 */
public class SetTest
    {
    public static void main(String[] args)
        {
        int    cSteps = 1000;
        int    cIters = 1;
        String sFile  = null;
        if (args != null && args.length > 0)
            {
            try
                {
                cSteps = Math.max(1, Integer.parseInt(args[0]));
                try
                    {
                    cIters = Math.max(1, Integer.parseInt(args[1]));
                    }
                catch (Exception e) {}
                }
            catch (Exception e)
                {
                sFile = args[0];
                }
            }

        String sTest = null;
        if (sFile != null)
            {
            if (sFile.equals("speed"))
                {
                speedTest();
                return;
                }

            try
                {
                File file = new File(sFile);
                if (!file.isFile() || !file.canRead())
                    {
                    throw new IOException("no such file: " + sFile);
                    }

                int    cb = (int) Math.min(file.length(), 1_000_000_000);
                byte[] ab = new byte[cb];

                new DataInputStream(new FileInputStream(sFile)).readFully(ab);
                sTest = new String(ab, 0);
                }
            catch (IOException e)
                {
                out("Error reading file: " + sFile);
                return;
                }
            }

        if (sTest == null)
            {
            TestLoop: for (int i = 1; i <= cIters; ++i)
                {
                out("Running test# " + i);
                if (!randomTest(cSteps))
                    {
                    break;
                    }
                }
            }
        else
            {
            replayTest(sTest);
            }
        }

//    @Test
    public void sizeTestHashSet()
        {
        sizeTest(new HashSet<Integer>());
        }
//    @Test
    public void sizeTestListSet()
        {
        sizeTest(new ListSet<Integer>());
        }
    static void sizeTest(Set<Integer> set)
        {
        int  c     = 0;
        long start = System.currentTimeMillis();
        while (true)
            {
            set.add(++c);
            if (c % 1000000 == 0)
                {
                out(""+(c/1000000)+"m in " + (System.currentTimeMillis()-start) + "ms");
                }
            }
        }
    static void speedTest()
        {
        for (int i = 0, p=990, c = 1000; i < c; ++i)
            {
            speedTest(i>p);
            }
        }
    static void speedTest(boolean fPrint)
        {
        int cIters = fPrint ? 1000 : 100;
        long l0 = System.currentTimeMillis();
        for (int i = 0; i < cIters; ++i)
            {
            speedTest(new HashSet<Integer>());
            }
        long l1 = System.currentTimeMillis();
        for (int i = 0; i < cIters; ++i)
            {
            speedTest(new ListSet<Integer>());
            }
        long l2 = System.currentTimeMillis();
        if (fPrint)
            {
            out("HashSet @" + (l1-l0) + "ms vs. ListSet @" + (l2-l1) + "ms");
            }
        }
    static void speedTest(Set<Integer> set)
        {
        for (int i = 0, c = 1000; i <= c; ++i)
            {
            set.add(i);
            }
        }

    static boolean randomTest(int cSteps)
        {
        ArrayList<Op> listOps = new ArrayList<>(cSteps);
        Data data = new StringData(1+rnd.nextInt(rnd.nextInt(100000)));
        Set   setControl = createControlSet();
        Set[] aTestSets  = createTestSets();
        for (int iStep = 0; iStep < cSteps; ++iStep)
            {
            Op op = randomOp(setControl, data);
            listOps.add(op);
            op.init(setControl);
            for (int iSet = 0, cSets = aTestSets.length; iSet < cSets; ++iSet)
                {
                Set set = aTestSets[iSet];
                try
                    {
                    op.test(set);
                    }
                catch (Exception | AssertionError e)
                    {
                    displayErr(listOps, iSet, iStep, e);
                    return false;
                    }
                }
            }

        return true;
        }

    static boolean fReplay;
    static void replayTest(String sTest)
        {
        fReplay = true;

        String[]      asLine  = Handy.parseDelimitedString(sTest, '\n');
        int           cLines  = asLine.length;
        ArrayList<Op> listOps = new ArrayList<>(cLines);
        for (int i = 0; i < cLines; ++i)
            {
            String sLine = asLine[i];
            if (sLine.length() > 0 && sLine.charAt(0) == '[')
                {
                int of = sLine.indexOf(']');
                assert of > 0;
                String sOp = sLine.substring(of+1).trim();
                Op     op  = parseOp(sOp);
                listOps.add(op);
                }
            }

        Set[] aSet = createTestSets();
        for (int i = 0, cOps = listOps.size(); i < cOps; ++i)
            {
            Op op = listOps.get(i);
            for (int iSet = 0, cSets = aSet.length; iSet < cSets; ++iSet)
                {
                Set set = aSet[iSet];
                try
                    {
                    op.test(set);
                    }
                catch (Exception | AssertionError e)
                    {
                    displayErr(listOps, iSet, i, e);
                    return;
                    }
                }
            }
        }

    static void displayErr(List<Op> listOps, int iSet, int iOp, Throwable e)
        {
        out();
        out("Exception on set# " + iSet + " in step# " + iOp + ": " + e.getMessage());
        e.printStackTrace(System.out);
        out();
        out("Test steps:");
        for (int i = 0, c = listOps.size(); i < c; ++i)
            {
            out("[" + i + "] " + listOps.get(i));
            }
        }

    static Op parseOp(String sLine)
        {
        int of = sLine.indexOf(' ');
        assert of > 0;
        String   sOp     = sLine.substring(0, of);
        String   sParams = sLine.substring(of+1).trim();
        String[] asParams = Handy.parseDelimitedString(sParams, ' ');

        Op op;
        switch (sOp)
            {
            case "add":
                op = new OpAdd();
                break;

            case "size":
                op = new OpSize();
                break;

            case "remove":
                op = new OpRemove();
                break;

            case "clear":
                op = new OpClear();
                break;

            case "iterate":
                op = new OpIter();
                break;

            case "iterate-partial":
                op = new OpIterPart();
                break;

            default:
                throw new IllegalStateException("unknown op: " + sOp);
            }

        op.parse(asParams);
        return op;
        }

    static Op randomOp(Set set, Data data)
        {
        while (true)
            {
            switch (rnd.nextInt(10))
                {
                default:
                case 0:         // add random
                    return new OpAdd(data.rnd());

                case 1:         // add existing
                    if (set.isEmpty()) break;
                    return new OpAdd(randomElement(set));

                case 2:         // remove random
                    return new OpRemove(data.rnd());

                case 3:         // remove existing
                    if (set.isEmpty()) break;
                    return new OpRemove(randomElement(set));

                case 4:         // check the size
                    return new OpSize();

                case 5:
                    return new OpIter();

                case 6:
                    return new OpIterPart(set.isEmpty() ? 0 : rnd.nextInt(set.size()));

                case 7:
                    if (rnd.nextInt(10000) != 7) break;
                    return new OpClear();
                }
            }
        }

    static Object randomElement(Set set)
        {
        return set.toArray()[rnd.nextInt(set.size())];
        }

    static Set createControlSet()
        {
        return new HashSet<>();
        }

    static Set[] createTestSets()
        {
        return fReplay
                ? new Set[]
                        {
                        new ListSet(),
                        new ListSet().disableHashIndex(),
                        }
                : new Set[]
                        {
                        new ListSet(),
                        new ListSet().disableHashIndex(),
                        new ListSet().useIdentityEquality(),
                        new ListSet().disableHashIndex().useIdentityEquality(),
                        };
        }

    public interface Data
        {
        Object rnd();
        }

    public static class StringData
            implements Data
        {
        StringData(int count)
            {
            assert count > 0;
            HashSet<String> set = new HashSet<>(count);
            for (int i = 0; i < count; ++i)
                {
                StringBuilder sb = new StringBuilder();
                do
                    {
                    sb.append((char) ('A' + rnd.nextInt(26)));
                    }
                while (!set.add(sb.toString()));
                }
            strings = set.toArray(new String[count]);

            // Inexplicably this exception occurred once in the rnd() method below, which could only
            // occur if the "strings" array were of zero length, which should be impossible (given
            // the assertion above and the algorith in use here in the constructor.) To try to catch
            // the exception, an assertion was placed here, and then this never showed up again,
            // even with many thousand and thousands of tests run.
            //
            // Exception in thread "main" java.lang.IllegalArgumentException: bound must be positive
            //    at java.util.Random.nextInt(Random.java:388)
            //    at org.xvm.util.SetTest$StringData.rnd(SetTest.java:290)
            //    at org.xvm.util.SetTest$StringData.rnd(SetTest.java:269)
            //    at org.xvm.util.SetTest.randomOp(SetTest.java:215)
            //    at org.xvm.util.SetTest.randomTest(SetTest.java:96)
            //    at org.xvm.util.SetTest.main(SetTest.java:76)
            assert strings.length > 0;
            }

        public String rnd()
            {
            return strings[rnd.nextInt(strings.length)];
            }

        String[] strings;
        }

    public interface Op
        {
        void parse(String[] sParams);
        void init(Set set);
        void test(Set set);

        @Override
        String toString();
        }

    public static class OpAdd
            implements Op
        {
        OpAdd() {}

        public OpAdd(Object oAdd)
            {
            this.oAdd = oAdd;
            }

        @Override
        public void parse(String[] asParams)
            {
            oAdd = asParams[0];
            assert asParams[1].equals("->");
            fExpect = Boolean.valueOf(asParams[2]);
            }

        @Override
        public void init(Set set)
            {
            fExpect = set.add(oAdd);
            }

        @Override
        public void test(Set set)
            {
            Assert.assertEquals(fExpect, set.add(oAdd));
            }

        @Override
        public String toString()
            {
            return "add " + oAdd + " -> " + fExpect;
            }

        Object  oAdd;
        boolean fExpect;
        }

    public static class OpRemove
            implements Op
        {
        OpRemove() {}

        public OpRemove(Object oRemove)
            {
            this.oRemove = oRemove;
            }

        @Override
        public void parse(String[] asParams)
            {
            oRemove = asParams[0];
            assert asParams[1].equals("->");
            fExpect = Boolean.valueOf(asParams[2]);
            }

        @Override
        public void init(Set set)
            {
            fExpect = set.remove(oRemove);
            }

        @Override
        public void test(Set set)
            {
            Assert.assertEquals(fExpect, set.remove(oRemove));
            }

        @Override
        public String toString()
            {
            return "remove " + oRemove + " -> " + fExpect;
            }

        Object  oRemove;
        boolean fExpect;
        }

    public static class OpSize
            implements Op
        {
        OpSize() {}

        @Override
        public void parse(String[] asParams)
            {
            assert asParams[0].equals("->");
            nExpect = Integer.valueOf(asParams[1]);
            }

        @Override
        public void init(Set set)
            {
            nExpect = set.size();
            }

        @Override
        public void test(Set set)
            {
            Assert.assertEquals(nExpect, set.size());
            }

        @Override
        public String toString()
            {
            return "size -> " + nExpect;
            }

        int nExpect;
        }

    public static class OpClear
            implements Op
        {
        OpClear() {}

        @Override
        public void parse(String[] asParams)
            {
            assert asParams[0].equals("->");
            }

        @Override
        public void init(Set set)
            {
            set.clear();
            }

        @Override
        public void test(Set set)
            {
            set.clear();
            }

        @Override
        public String toString()
            {
            return "clear ->";
            }
        }

    public static class OpIter
            implements Op
        {
        OpIter() {}

        @Override
        public void parse(String[] asParams)
            {
            assert asParams[0].equals("->");
            nExpect = Integer.valueOf(asParams[1]);
            }

        @Override
        public void init(Set set)
            {
            nExpect = iter(set);
            }

        @Override
        public void test(Set set)
            {
            Assert.assertEquals(nExpect, iter(set));
            }

        int iter(Set set)
            {
            int c = 0;
            for (Iterator iter = set.iterator(); iter.hasNext(); )
                {
                iter.next();
                ++c;
                }
            return c;
            }

        @Override
        public String toString()
            {
            return "iterate -> " + nExpect;
            }

        int nExpect;
        }

    public static class OpIterPart
            implements Op
        {
        OpIterPart() {}

        OpIterPart(int cIter)
            {
            this.cIter = cIter;
            }

        @Override
        public void parse(String[] asParams)
            {
            cIter = Integer.valueOf(asParams[0]);
            assert asParams[1].equals("->");
            }

        @Override
        public void init(Set set)
            {
            iter(set);
            }

        @Override
        public void test(Set set)
            {
            iter(set);
            }

        void iter(Set set)
            {
            Iterator iter = set.iterator();
            for (int c = 0; c < cIter; ++c)
                {
                Assert.assertTrue(iter.hasNext());
                iter.next();
                }
            }

        @Override
        public String toString()
            {
            return "iterate-partial " + cIter + " ->";
            }

        int cIter;
        }

    static void out()
        {
        out("");
        }

    static void out(String s)
        {
        System.out.println(s);
        }

    static final Random rnd = new Random();


    // ----- unit tests ----------------------------------------------------------------------------

    @Test
    public void testSimple()
        {
        ListSet<String> set = new ListSet<>();

        Assert.assertTrue(set.isEmpty());
        Assert.assertEquals(0, set.size());
        Assert.assertFalse(set.contains("a"));
        Assert.assertFalse(set.contains("m"));
        Assert.assertFalse(set.contains("z"));
        Assert.assertFalse(set.contains("Z"));

        for (char ch = 'a'; ch <= 'z'; ++ch)
            {
            set.add(String.valueOf(ch));
            }

        Assert.assertFalse(set.isEmpty());
        Assert.assertEquals(26, set.size());
        Assert.assertTrue(set.contains("a"));
        Assert.assertTrue(set.contains("m"));
        Assert.assertTrue(set.contains("z"));
        Assert.assertFalse(set.contains("Z"));
        }

    @Test
    public void testIterator()
        {
        ListSet<String> set = new ListSet<>();
        for (char ch = 'a'; ch <= 'z'; ++ch)
            {
            set.add(String.valueOf(ch));
            }

        Iterator<String> iter = set.iterator();
        for (char ch = 'a'; ch <= 'z'; ++ch)
            {
            Assert.assertTrue(iter.hasNext());
            Assert.assertEquals(String.valueOf(ch), iter.next());
            }
        Assert.assertFalse(iter.hasNext());
        }

    @Test
    public void repro1()
        {
        // Exception on set# 0 in step# 49: null
        // java.lang.NullPointerException
        // 	at org.xvm.util.ListSet.indexSearch(ListSet.java:770)
        // 	at org.xvm.util.ListSet.verifyIterator(ListSet.java:499)
        // 	at org.xvm.util.ListSet.access$800(ListSet.java:18)
        // 	at org.xvm.util.ListSet$SafeIterator.synced(ListSet.java:1000)
        // 	at org.xvm.util.ListSet$SafeIterator.<init>(ListSet.java:882)
        // 	at org.xvm.util.ListSet.iterator(ListSet.java:163)
        // 	at org.xvm.util.SetTest$OpIter.iter(SetTest.java:519)
        // 	at org.xvm.util.SetTest$OpIter.test(SetTest.java:513)
        // 	at org.xvm.util.SetTest.randomTest(SetTest.java:147)
        // 	at org.xvm.util.SetTest.main(SetTest.java:119)

        StringBuilder sb = new StringBuilder();
        sb.append("[0] remove HTM -> false\n")
          .append("[1] add FQP -> true\n")
          .append("[2] add FQP -> false\n")
          .append("[3] remove DMTJ -> false\n")
          .append("[4] add FQP -> false\n")
          .append("[5] add IZGG -> true\n")
          .append("[6] add FQP -> false\n")
          .append("[7] add IZGG -> false\n")
          .append("[8] add EVWM -> true\n")
          .append("[9] remove EVWM -> true\n")
          .append("[10] add YZKK -> true\n")
          .append("[11] remove NLES -> false\n")
          .append("[12] add IZGG -> false\n")
          .append("[13] iterate -> 3\n")
          .append("[14] size -> 3\n")
          .append("[15] add FQP -> false\n")
          .append("[16] remove FQP -> true\n")
          .append("[17] add YZKK -> false\n")
          .append("[18] add UHV -> true\n")
          .append("[19] add PGX -> true\n")
          .append("[20] iterate -> 4\n")
          .append("[21] add WGP -> true\n")
          .append("[22] add WGP -> false\n")
          .append("[23] add UHV -> false\n")
          .append("[24] size -> 5\n")
          .append("[25] remove YZKK -> true\n")
          .append("[26] add ZIWT -> true\n")
          .append("[27] add NMOQ -> true\n")
          .append("[28] add NZD -> true\n")
          .append("[29] iterate -> 7\n")
          .append("[30] size -> 7\n")
          .append("[31] remove NMOQ -> true\n")
          .append("[32] iterate -> 6\n")
          .append("[33] add AUH -> true\n")
          .append("[34] add CJCD -> true\n")
          .append("[35] add OLX -> true\n")
          .append("[36] add DLG -> true\n")
          .append("[37] remove JKQ -> false\n")
          .append("[38] add HFF -> true\n")
          .append("[39] remove AUH -> true\n")
          .append("[40] iterate -> 10\n")
          .append("[41] add EXN -> true\n")
          .append("[42] add LAFL -> true\n")
          .append("[43] add IZGG -> false\n")
          .append("[44] add QZI -> true\n")
          .append("[45] size -> 13\n")
          .append("[46] size -> 13\n")
          .append("[47] add BZX -> true\n")
          .append("[48] iterate -> 14\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro2()
        {
        // Exception on set# 0 in step# 31: expected:<false> but was:<true>

        // Exception on set# 0 in step# 27: null
        // java.lang.AssertionError
        // 	at org.xvm.util.ListSet.indexAdjust(ListSet.java:825)
        // 	at org.xvm.util.ListSet.compact(ListSet.java:377)
        // 	at org.xvm.util.ListSet.ensureSpace(ListSet.java:311)
        // 	at org.xvm.util.ListSet.addInternal(ListSet.java:255)
        // 	at org.xvm.util.ListSet.add(ListSet.java:120)
        // 	at org.xvm.util.SetTest$OpAdd.test(SetTest.java:485)
        // 	at org.xvm.util.SetTest.replayTest(SetTest.java:296)
        // 	at org.xvm.util.SetTest.repro2(SetTest.java:174)

        StringBuilder sb = new StringBuilder();
        sb.append("[0] add UAY -> true\n")
          .append("[1] add NBT -> true\n")
          .append("[2] size -> 2\n")
          .append("[3] size -> 2\n")
          .append("[4] remove MRP -> false\n")
          .append("[5] add ZIW -> true\n")
          .append("[6] add GFW -> true\n")
          .append("[7] add SZMV -> true\n")
          .append("[8] add SZMV -> false\n")
          .append("[9] add ADX -> true\n")
          .append("[10] add ADX -> false\n")
          .append("[11] add BWK -> true\n")
          .append("[12] add SZMV -> false\n")
          .append("[13] remove SZMV -> true\n")
          .append("[14] add NBT -> false\n")
          .append("[15] add NKDK -> true\n")
          .append("[16] add RHV -> true\n")
          .append("[17] iterate -> 8\n")
          .append("[18] iterate -> 8\n")
          .append("[19] add DHH -> true\n")
          .append("[20] add TLT -> true\n")
          .append("[21] add TRB -> true\n")
          .append("[22] add WYCV -> true\n")
          .append("[23] iterate -> 12\n")
          .append("[24] add SFJ -> true\n")
          .append("[25] remove NKDK -> true\n")
          .append("[26] size -> 12\n")
          .append("[27] add MTW -> true\n")
          .append("[28] remove ZIW -> true\n")
          .append("[29] iterate -> 12\n")
          .append("[30] add ADX -> false\n")
          .append("[31] add UAY -> false\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro3()
        {
        // Exception on set# 0 in step# 71: expected:<true> but was:<false>

        // Exception on set# 0 in step# 59: null
        // java.lang.AssertionError
        //     at org.xvm.util.ListSet.indexAdjust(ListSet.java:828)
        //     at org.xvm.util.ListSet.compact(ListSet.java:377)
        //     at org.xvm.util.ListSet.ensureSpace(ListSet.java:311)
        //     at org.xvm.util.ListSet.addInternal(ListSet.java:255)
        //     at org.xvm.util.ListSet.add(ListSet.java:120)
        //     at org.xvm.util.SetTest$OpAdd.test(SetTest.java:577)
        //     at org.xvm.util.SetTest.replayTest(SetTest.java:388)
        //     at org.xvm.util.SetTest.repro3(SetTest.java:266)

        StringBuilder sb = new StringBuilder();
        sb.append("[0] size -> 0\n")
          .append("[1] add LA -> true\n")
          .append("[2] add HJZ -> true\n")
          .append("[3] remove LA -> true\n")
          .append("[4] add VB -> true\n")
          .append("[5] add WED -> true\n")
          .append("[6] iterate -> 3\n")
          .append("[7] iterate -> 3\n")
          .append("[8] add ER -> true\n")
          .append("[9] remove VB -> true\n")
          .append("[10] add JDE -> true\n")
          .append("[11] add KIM -> true\n")
          .append("[12] iterate -> 5\n")
          .append("[13] add NE -> true\n")
          .append("[14] add YZ -> true\n")
          .append("[15] iterate -> 7\n")
          .append("[16] size -> 7\n")
          .append("[17] add YZ -> false\n")
          .append("[18] remove N -> false\n")
          .append("[19] add NE -> false\n")
          .append("[20] add WED -> false\n")
          .append("[21] size -> 7\n")
          .append("[22] add JE -> true\n")
          .append("[23] iterate -> 8\n")
          .append("[24] add NE -> false\n")
          .append("[25] add WED -> false\n")
          .append("[26] remove NE -> true\n")
          .append("[27] remove YZ -> true\n")
          .append("[28] add CZ -> true\n")
          .append("[29] add KIM -> false\n")
          .append("[30] size -> 7\n")
          .append("[31] add AG -> true\n")
          .append("[32] remove ZXO -> false\n")
          .append("[33] size -> 8\n")
          .append("[34] add HJZ -> false\n")
          .append("[35] add JDE -> false\n")
          .append("[36] add ER -> false\n")
          .append("[37] add KIM -> false\n")
          .append("[38] add AF -> true\n")
          .append("[39] add U -> true\n")
          .append("[40] iterate -> 10\n")
          .append("[41] add PG -> true\n")
          .append("[42] remove SRO -> false\n")
          .append("[43] add XZ -> true\n")
          .append("[44] remove KIM -> true\n")
          .append("[45] iterate -> 11\n")
          .append("[46] add CVO -> true\n")
          .append("[47] remove U -> true\n")
          .append("[48] iterate -> 11\n")
          .append("[49] add JA -> true\n")
          .append("[50] remove CVO -> true\n")
          .append("[51] iterate -> 11\n")
          .append("[52] add EXF -> true\n")
          .append("[53] remove CZ -> true\n")
          .append("[54] iterate -> 11\n")
          .append("[55] remove LG -> false\n")
          .append("[56] add EXF -> false\n")
          .append("[57] add OS -> true\n")
          .append("[58] remove UO -> false\n")
          .append("[59] add S -> true\n")
          .append("[60] size -> 13\n")
          .append("[61] add WU -> true\n")
          .append("[62] add MKL -> true\n")
          .append("[63] add NRF -> true\n")
          .append("[64] remove AA -> false\n")
          .append("[65] add KFM -> true\n")
          .append("[66] size -> 17\n")
          .append("[67] add ST -> true\n")
          .append("[68] add PG -> false\n")
          .append("[69] add AI -> true\n")
          .append("[70] add RN -> true\n")
          .append("[71] remove EXF -> true\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro4()
        {
        // Exception on set# 0 in step# 116: null
        // java.lang.NullPointerException
        //     at org.xvm.util.ListSet.indexSearch(ListSet.java:785)
        //     at org.xvm.util.ListSet.indexOf(ListSet.java:190)
        //     at org.xvm.util.ListSet.contains(ListSet.java:102)
        //     at org.xvm.util.ListSet.add(ListSet.java:114)
        //     at org.xvm.util.SetTest$OpAdd.test(SetTest.java:589)
        //     at org.xvm.util.SetTest.randomTest(SetTest.java:360)

        StringBuilder sb = new StringBuilder();
        sb.append("[0] add IL -> true\n")
          .append("[1] add IL -> false\n")
          .append("[2] add JP -> true\n")
          .append("[3] add WQ -> true\n")
          .append("[4] add IL -> false\n")
          .append("[5] remove WQ -> true\n")
          .append("[6] iterate -> 2\n")
          .append("[7] remove JP -> true\n")
          .append("[8] size -> 1\n")
          .append("[9] add IL -> false\n")
          .append("[10] add LB -> true\n")
          .append("[11] add MSS -> true\n")
          .append("[12] size -> 3\n")
          .append("[13] add MS -> true\n")
          .append("[14] add OC -> true\n")
          .append("[15] add JP -> true\n")
          .append("[16] add IL -> false\n")
          .append("[17] add CG -> true\n")
          .append("[18] add V -> true\n")
          .append("[19] remove JP -> true\n")
          .append("[20] add I -> true\n")
          .append("[21] add IL -> false\n")
          .append("[22] remove ZB -> false\n")
          .append("[23] add JPQ -> true\n")
          .append("[24] add JPQ -> false\n")
          .append("[25] iterate -> 9\n")
          .append("[26] add NL -> true\n")
          .append("[27] add Z -> true\n")
          .append("[28] size -> 11\n")
          .append("[29] add AU -> true\n")
          .append("[30] remove Y -> false\n")
          .append("[31] remove IL -> true\n")
          .append("[32] remove AU -> true\n")
          .append("[33] remove MSS -> true\n")
          .append("[34] iterate -> 9\n")
          .append("[35] add AC -> true\n")
          .append("[36] add ZB -> true\n")
          .append("[37] add Z -> false\n")
          .append("[38] add V -> false\n")
          .append("[39] add B -> true\n")
          .append("[40] add G -> true\n")
          .append("[41] remove N -> false\n")
          .append("[42] add Z -> false\n")
          .append("[43] add PN -> true\n")
          .append("[44] remove LB -> true\n")
          .append("[45] add V -> false\n")
          .append("[46] add RC -> true\n")
          .append("[47] add TK -> true\n")
          .append("[48] remove B -> true\n")
          .append("[49] add N -> true\n")
          .append("[50] size -> 15\n")
          .append("[51] remove OC -> true\n")
          .append("[52] iterate -> 14\n")
          .append("[53] size -> 14\n")
          .append("[54] add MS -> false\n")
          .append("[55] add C -> true\n")
          .append("[56] add ME -> true\n")
          .append("[57] size -> 16\n")
          .append("[58] remove RC -> true\n")
          .append("[59] size -> 15\n")
          .append("[60] iterate -> 15\n")
          .append("[61] add W -> true\n")
          .append("[62] remove NL -> true\n")
          .append("[63] add K -> true\n")
          .append("[64] add CG -> false\n")
          .append("[65] remove Z -> true\n")
          .append("[66] add XD -> true\n")
          .append("[67] add I -> false\n")
          .append("[68] add TK -> false\n")
          .append("[69] add O -> true\n")
          .append("[70] add JPQ -> false\n")
          .append("[71] add P -> true\n")
          .append("[72] add XD -> false\n")
          .append("[73] size -> 18\n")
          .append("[74] iterate -> 18\n")
          .append("[75] remove C -> true\n")
          .append("[76] size -> 17\n")
          .append("[77] add M -> true\n")
          .append("[78] add N -> false\n")
          .append("[79] iterate -> 18\n")
          .append("[80] add T -> true\n")
          .append("[81] size -> 19\n")
          .append("[82] remove P -> true\n")
          .append("[83] add CG -> false\n")
          .append("[84] add MK -> true\n")
          .append("[85] iterate -> 19\n")
          .append("[86] add UE -> true\n")
          .append("[87] add R -> true\n")
          .append("[88] remove M -> true\n")
          .append("[89] iterate -> 20\n")
          .append("[90] add AK -> true\n")
          .append("[91] remove CG -> true\n")
          .append("[92] add P -> true\n")
          .append("[93] add MSS -> true\n")
          .append("[94] add AU -> true\n")
          .append("[95] size -> 23\n")
          .append("[96] add TK -> false\n")
          .append("[97] add J -> true\n")
          .append("[98] add T -> false\n")
          .append("[99] iterate -> 24\n")
          .append("[100] remove JP -> false\n")
          .append("[101] add XT -> true\n")
          .append("[102] remove AU -> true\n")
          .append("[103] remove MSS -> true\n")
          .append("[104] iterate -> 23\n")
          .append("[105] add KL -> true\n")
          .append("[106] iterate -> 24\n")
          .append("[107] remove ZB -> true\n")
          .append("[108] iterate -> 23\n")
          .append("[109] add RM -> true\n")
          .append("[110] add N -> false\n")
          .append("[111] remove ME -> true\n")
          .append("[112] add WM -> true\n")
          .append("[113] remove K -> true\n")
          .append("[114] add MS -> false\n")
          .append("[115] remove PN -> true\n")
          .append("[116] add AU -> true\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro5()
        {
        // Exception on set# 0 in step# 41: expected:<false> but was:<true>
        StringBuilder sb = new StringBuilder();
        sb.append("[0] size -> 0\n")
          .append("[1] iterate -> 0\n")
          .append("[2] iterate -> 0\n")
          .append("[3] add POP -> true\n")
          .append("[4] add SMPV -> true\n")
          .append("[5] add HYW -> true\n")
          .append("[6] add RXYN -> true\n")
          .append("[7] add LRSY -> true\n")
          .append("[8] add WLD -> true\n")
          .append("[9] remove DDKH -> false\n")
          .append("[10] add EHN -> true\n")
          .append("[11] add ZCK -> true\n")
          .append("[12] remove RXYN -> true\n")
          .append("[13] add UAPK -> true\n")
          .append("[14] iterate -> 8\n")
          .append("[15] add EHN -> false\n")
          .append("[16] add HYW -> false\n")
          .append("[17] add POP -> false\n")
          .append("[18] add SMPV -> false\n")
          .append("[19] add TRFI -> true\n")
          .append("[20] remove DMWB -> false\n")
          .append("[21] size -> 9\n")
          .append("[22] add RXU -> true\n")
          .append("[23] remove YEPT -> false\n")
          .append("[24] add XGU -> true\n")
          .append("[25] add ACJY -> true\n")
          .append("[26] add ACJY -> false\n")
          .append("[27] add FXK -> true\n")
          .append("[28] add FUY -> true\n")
          .append("[29] iterate -> 14\n")
          .append("[30] iterate -> 14\n")
          .append("[31] add FUY -> false\n")
          .append("[32] add ZSQ -> true\n")
          .append("[33] add IMOO -> true\n")
          .append("[34] iterate -> 16\n")
          .append("[35] add BXR -> true\n")
          .append("[36] size -> 17\n")
          .append("[37] add TRFI -> false\n")
          .append("[38] remove ACJY -> true\n")
          .append("[39] add SMPV -> false\n")
          .append("[40] add XGU -> false\n")
          .append("[41] add LRSY -> false\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro6()
        {
        // Exception on set# 0 in step# 34: expected:<true> but was:<false>
        StringBuilder sb = new StringBuilder();
        sb.append("[0] remove SD -> false\n")
          .append("[1] add DUM -> true\n")
          .append("[2] add IZE -> true\n")
          .append("[3] add YNE -> true\n")
          .append("[4] add YNE -> false\n")
          .append("[5] add DUM -> false\n")
          .append("[6] add PRB -> true\n")
          .append("[7] add WA -> true\n")
          .append("[8] add TJ -> true\n")
          .append("[9] add JHD -> true\n")
          .append("[10] iterate -> 7\n")
          .append("[11] add MFN -> true\n")
          .append("[12] remove MFN -> true\n")
          .append("[13] add BV -> true\n")
          .append("[14] remove XM -> false\n")
          .append("[15] add YP -> true\n")
          .append("[16] add GTE -> true\n")
          .append("[17] size -> 10\n")
          .append("[18] add DGM -> true\n")
          .append("[19] add TIX -> true\n")
          .append("[20] remove WA -> true\n")
          .append("[21] add ARQ -> true\n")
          .append("[22] add DUM -> false\n")
          .append("[23] iterate -> 12\n")
          .append("[24] add BV -> false\n")
          .append("[25] size -> 12\n")
          .append("[26] iterate -> 12\n")
          .append("[27] add PRB -> false\n")
          .append("[28] add EOG -> true\n")
          .append("[29] add NBX -> true\n")
          .append("[30] add EOG -> false\n")
          .append("[31] remove TIX -> true\n")
          .append("[32] add VW -> true\n")
          .append("[33] add WK -> true\n")
          .append("[34] remove ARQ -> true\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro7()
        {
        // Exception on set# 0 in step# 55: expected:<true> but was:<false>
        StringBuilder sb = new StringBuilder();
        sb.append("[0] add SAN -> true\n")
          .append("[1] iterate -> 1\n")
          .append("[2] add SAN -> false\n")
          .append("[3] add SAN -> false\n")
          .append("[4] add SAN -> false\n")
          .append("[5] size -> 1\n")
          .append("[6] add TA -> true\n")
          .append("[7] add OWB -> true\n")
          .append("[8] add NQQ -> true\n")
          .append("[9] remove LDL -> false\n")
          .append("[10] remove DHJ -> false\n")
          .append("[11] remove LCX -> false\n")
          .append("[12] add CJJ -> true\n")
          .append("[13] add TA -> false\n")
          .append("[14] add NWI -> true\n")
          .append("[15] add OWB -> false\n")
          .append("[16] add CYU -> true\n")
          .append("[17] iterate -> 7\n")
          .append("[18] add KIT -> true\n")
          .append("[19] add CL -> true\n")
          .append("[20] size -> 9\n")
          .append("[21] add IGC -> true\n")
          .append("[22] add TA -> false\n")
          .append("[23] iterate -> 10\n")
          .append("[24] add TUK -> true\n")
          .append("[25] remove ATP -> false\n")
          .append("[26] iterate -> 11\n")
          .append("[27] remove JDB -> false\n")
          .append("[28] add LWA -> true\n")
          .append("[29] add DUS -> true\n")
          .append("[30] remove DUS -> true\n")
          .append("[31] add TBI -> true\n")
          .append("[32] remove TD -> false\n")
          .append("[33] add WEJ -> true\n")
          .append("[34] size -> 14\n")
          .append("[35] add XRX -> true\n")
          .append("[36] add VAZ -> true\n")
          .append("[37] add NXP -> true\n")
          .append("[38] add JDM -> true\n")
          .append("[39] remove TUK -> true\n")
          .append("[40] iterate -> 17\n")
          .append("[41] add OXM -> true\n")
          .append("[42] size -> 18\n")
          .append("[43] remove TBI -> true\n")
          .append("[44] add ULL -> true\n")
          .append("[45] add ZCGV -> true\n")
          .append("[46] remove WEJ -> true\n")
          .append("[47] remove XXG -> false\n")
          .append("[48] remove ULL -> true\n")
          .append("[49] size -> 17\n")
          .append("[50] add UP -> true\n")
          .append("[51] size -> 18\n")
          .append("[52] remove LWA -> true\n")
          .append("[53] remove SAN -> true\n")
          .append("[54] add UP -> false\n")
          .append("[55] remove NQQ -> true\n");
        replayTest(sb.toString());
        }

    @Test
    public void repro8()
        {
        // Exception on set# 0 in step# 39: expected:<true> but was:<false>
        StringBuilder sb = new StringBuilder();
        sb.append("[0] add JWD -> true\n")
          .append("[1] add JWD -> false\n")
          .append("[2] iterate -> 1\n")
          .append("[3] add XMH -> true\n")
          .append("[4] remove JWD -> true\n")
          .append("[5] remove ALQ -> false\n")
          .append("[6] add RMD -> true\n")
          .append("[7] add RMD -> false\n")
          .append("[8] remove RMD -> true\n")
          .append("[9] iterate -> 1\n")
          .append("[10] add SOYW -> true\n")
          .append("[11] add CRT -> true\n")
          .append("[12] add CRT -> false\n")
          .append("[13] remove SOYW -> true\n")
          .append("[14] add JDYH -> true\n")
          .append("[15] add SBKA -> true\n")
          .append("[16] iterate -> 4\n")
          .append("[17] size -> 4\n")
          .append("[18] add UKBB -> true\n")
          .append("[19] add DHG -> true\n")
          .append("[20] add ZCK -> true\n")
          .append("[21] remove SBKA -> true\n")
          .append("[22] add AQDD -> true\n")
          .append("[23] size -> 7\n")
          .append("[24] size -> 7\n")
          .append("[25] remove KDL -> false\n")
          .append("[26] add GOD -> true\n")
          .append("[27] add FNP -> true\n")
          .append("[28] add ZPZZ -> true\n")
          .append("[29] remove DUOZ -> false\n")
          .append("[30] add SGN -> true\n")
          .append("[31] add ZOCZ -> true\n")
          .append("[32] add MCOA -> true\n")
          .append("[33] remove AQDD -> true\n")
          .append("[34] iterate -> 12\n")
          .append("[35] add HHQZ -> true\n")
          .append("[36] remove XMH -> true\n")
          .append("[37] remove APUG -> false\n")
          .append("[38] add ZPZZ -> false\n")
          .append("[39] remove GOD -> true\n");          // sacrilege?
        replayTest(sb.toString());
        }

    @Test
    public void repro9()
        {
        // Exception on set# 0 in step# 13: expected:<7> but was:<6>
        StringBuilder sb = new StringBuilder();
        sb.append("[0] iterate -> 0\n")
          .append("[1] add TV -> true\n")
          .append("[2] add BQK -> true\n")
          .append("[3] add KZ -> true\n")
          .append("[4] add BQK -> false\n")
          .append("[5] add EGP -> true\n")
          .append("[6] size -> 4\n")
          .append("[7] add RY -> true\n")
          .append("[8] iterate -> 5\n")
          .append("[9] add RTS -> true\n")
          .append("[10] add QOP -> true\n")
          .append("[11] remove RTS -> true\n")
          .append("[12] add ZVN -> true\n")
          .append("[13] size -> 7\n");
        replayTest(sb.toString());
        }
    }
