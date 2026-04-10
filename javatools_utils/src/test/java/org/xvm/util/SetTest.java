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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the Handy class.
 */
public class SetTest {
    static void main(final String[] args) {
        int    cSteps = 1000;
        int    cIters = 1;
        String sFile  = null;
        if (args != null && args.length > 0) {
            try {
                cSteps = Math.max(1, Integer.parseInt(args[0]));
                try {
                    cIters = Math.max(1, Integer.parseInt(args[1]));
                } catch (final Exception ignore) {}
            } catch (final Exception e) {
                sFile = args[0];
            }
        }

        String sTest = null;
        if (sFile != null) {
            if ("speed".equals(sFile)) {
                speedTest();
                return;
            }

            try {
                File file = new File(sFile);
                if (!file.isFile() || !file.canRead()) {
                    throw new IOException("no such file: " + sFile);
                }

                int    cb = (int) Math.min(file.length(), 1_000_000_000);
                byte[] ab = new byte[cb];

                new DataInputStream(new FileInputStream(sFile)).readFully(ab);
                sTest = new String(ab);
            } catch (final IOException e) {
                out("Error reading file: " + sFile);
                return;
            }
        }

        if (sTest == null) {
            for (int i = 1; i <= cIters; ++i) {
                out("Running test# " + i);
                if (!randomTest(cSteps)) {
                    break;
                }
            }
        } else {
            replayTest(sTest);
        }
    }

    @Test @Disabled
    public void sizeTestHashSet() {
        sizeTest(new HashSet<>());
    }

    @Test @Disabled
    public void sizeTestListSet() {
        sizeTest(new ListSet<>());
    }

    static void sizeTest(final Set<Integer> set) {
        int  c     = 0;
        long start = System.currentTimeMillis();
        while (true) {
            set.add(++c);
            if (c % 1000000 == 0) {
                out((c/1000000) + "m in " + (System.currentTimeMillis()-start) + "ms");
                break;
            }
        }
    }

    static void speedTest() {
        for (int i = 0, p=990, c = 1000; i < c; ++i) {
            speedTest(i>p);
        }
    }

    static void speedTest(final boolean fPrint) {
        int cIters = fPrint ? 1000 : 100;
        long l0 = System.currentTimeMillis();
        for (int i = 0; i < cIters; ++i) {
            speedTest(new HashSet<>());
        }
        long l1 = System.currentTimeMillis();
        for (int i = 0; i < cIters; ++i) {
            speedTest(new ListSet<>());
        }
        long l2 = System.currentTimeMillis();
        if (fPrint) {
            out("HashSet @" + (l1-l0) + "ms vs. ListSet @" + (l2-l1) + "ms");
        }
    }

    static void speedTest(final Set<Integer> set) {
        for (int i = 0, c = 1000; i <= c; ++i) {
            set.add(i);
        }
    }

    static boolean randomTest(final int cSteps) {
        ArrayList<Op> listOps = new ArrayList<>(cSteps);
        Data data = new StringData(1+rnd.nextInt(1+rnd.nextInt(100000)));
        var setControl = createControlSet();
        var testSets   = createTestSets();
        for (int iStep = 0; iStep < cSteps; ++iStep) {
            Op op = randomOp(setControl, data);
            listOps.add(op);
            op.init(setControl);
            for (int iSet = 0, cSets = testSets.size(); iSet < cSets; ++iSet) {
                var set = testSets.get(iSet);
                try {
                    op.test(set);
                } catch (final Exception | AssertionError e) {
                    displayErr(listOps, iSet, iStep, e);
                    return false;
                }
            }
        }

        return true;
    }

    static boolean fReplay;

    static void replayTest(final String sTest) {
        fReplay = true;

        String[]      asLine  = Handy.parseDelimitedString(sTest, '\n');
        int           cLines  = asLine.length;
        ArrayList<Op> listOps = new ArrayList<>(cLines);
        for (final String sLine : asLine) {
            if (!sLine.isEmpty() && sLine.charAt(0) == '[') {
                int of = sLine.indexOf(']');
                assert of > 0;
                String sOp = sLine.substring(of + 1).trim();
                Op op = parseOp(sOp);
                listOps.add(op);
            }
        }

        var testSets = createTestSets();
        for (int i = 0, cOps = listOps.size(); i < cOps; ++i) {
            Op op = listOps.get(i);
            for (int iSet = 0, cSets = testSets.size(); iSet < cSets; ++iSet) {
                var set = testSets.get(iSet);
                try {
                    op.test(set);
                } catch (final Exception | AssertionError e) {
                    displayErr(listOps, iSet, i, e);
                    return;
                }
            }
        }
    }

    static void displayErr(final List<Op> listOps, final int iSet, final int iOp, final Throwable e) {
        out();
        out("Exception on set# " + iSet + " in step# " + iOp + ": " + e.getMessage());
        e.printStackTrace(System.err);
        out();
        out("Test steps:");
        for (int i = 0, c = listOps.size(); i < c; ++i) {
            out("[" + i + "] " + listOps.get(i));
        }
    }

    static Op parseOp(final String sLine) {
        int of = sLine.indexOf(' ');
        assert of > 0;
        String   sOp     = sLine.substring(0, of);
        String   sParams = sLine.substring(of+1).trim();
        String[] asParams = Handy.parseDelimitedString(sParams, ' ');

        Op op = switch (sOp) {
            case "add" -> new OpAdd();
            case "size" -> new OpSize();
            case "remove" -> new OpRemove();
            case "clear" -> new OpClear();
            case "iterate" -> new OpIter();
            case "iterate-partial" -> new OpIterPart();
            default -> throw new IllegalStateException("unknown op: " + sOp);
        };

        op.parse(asParams);
        return op;
    }

    static Op randomOp(final Set<Object> set, final Data data) {
        while (true) {
            switch (rnd.nextInt(10)) {
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

            case 0:         // add random
            default:
                return new OpAdd(data.rnd());
            }
        }
    }

    static Object randomElement(final Set<Object> set) {
        return set.toArray()[rnd.nextInt(set.size())];
    }

    static Set<Object> createControlSet() {
        return new HashSet<>();
    }

    static List<Set<Object>> createTestSets() {
        return fReplay
                ? List.of(
                        new ListSet<>(),
                        new ListSet<>().disableHashIndex()
                    )
                : List.of(
                        new ListSet<>(),
                        new ListSet<>().disableHashIndex(),
                        new ListSet<>().useIdentityEquality(),
                        new ListSet<>().disableHashIndex().useIdentityEquality()
                    );
    }

    @FunctionalInterface
    public interface Data {
        Object rnd();
    }

    @Test
    public void testSimple() {
        ListSet<String> set = new ListSet<>();

        assertTrue(set.isEmpty());
        //noinspection ConstantValue
        assertEquals(0, set.size());
        assertFalse(set.contains("a"));
        assertFalse(set.contains("m"));
        assertFalse(set.contains("z"));
        assertFalse(set.contains("Z"));

        for (char ch = 'a'; ch <= 'z'; ++ch) {
            set.add(String.valueOf(ch));
        }

        assertFalse(set.isEmpty());
        assertEquals(26, set.size());
        assertTrue(set.contains("a"));
        assertTrue(set.contains("m"));
        assertTrue(set.contains("z"));
        assertFalse(set.contains("Z"));

        assertEquals("b", set.get(1));
        assertEquals("y", set.get(24));
    }

    public interface Op {
        void parse(String[] sParams);
        void init(Set<Object> set);
        void test(Set<Object> set);

        @Override
        String toString();
    }

    public static class OpAdd
            implements Op {
        OpAdd() {}

        public OpAdd(final Object oAdd) {
            this.oAdd = oAdd;
        }

        @Override
        public void parse(final String[] asParams) {
            oAdd = asParams[0];
            assert "->".equals(asParams[1]);
            fExpect = Boolean.parseBoolean(asParams[2]);
        }

        @Override
        public void init(final Set<Object> set) {
            fExpect = set.add(oAdd);
        }

        @Override
        public void test(final Set<Object> set) {
            assertEquals(fExpect, set.add(oAdd));
        }

        @Override
        public String toString() {
            return "add " + oAdd + " -> " + fExpect;
        }

        Object  oAdd;
        boolean fExpect;
    }

    public static class OpRemove
            implements Op {
        OpRemove() {}

        public OpRemove(final Object oRemove) {
            this.oRemove = oRemove;
        }

        @Override
        public void parse(final String[] asParams) {
            oRemove = asParams[0];
            assert "->".equals(asParams[1]);
            fExpect = Boolean.parseBoolean(asParams[2]);
        }

        @Override
        public void init(final Set<Object> set) {
            fExpect = set.remove(oRemove);
        }

        @Override
        public void test(final Set<Object> set) {
            assertEquals(fExpect, set.remove(oRemove));
        }

        @Override
        public String toString() {
            return "remove " + oRemove + " -> " + fExpect;
        }

        Object  oRemove;
        boolean fExpect;
    }

    public static class OpSize
            implements Op {
        OpSize() {}

        @Override
        public void parse(final String[] asParams) {
            assert "->".equals(asParams[0]);
            nExpect = Integer.parseInt(asParams[1]);
        }

        @Override
        public void init(final Set<Object> set) {
            nExpect = set.size();
        }

        @Override
        public void test(final Set<Object> set) {
            assertEquals(nExpect, set.size());
        }

        @Override
        public String toString() {
            return "size -> " + nExpect;
        }

        int nExpect;
    }

    public static class OpClear
            implements Op {
        OpClear() {}

        @Override
        public void parse(final String[] asParams) {
            assert "->".equals(asParams[0]);
        }

        @Override
        public void init(final Set<Object> set) {
            set.clear();
        }

        @Override
        public void test(final Set<Object> set) {
            set.clear();
        }

        @Override
        public String toString() {
            return "clear ->";
        }
    }

    public static class OpIter
            implements Op {
        OpIter() {}

        @Override
        public void parse(final String[] asParams) {
            assert "->".equals(asParams[0]);
            nExpect = Integer.parseInt(asParams[1]);
        }

        @Override
        public void init(final Set<Object> set) {
            nExpect = iter(set);
        }

        @Override
        public void test(final Set<Object> set) {
            assertEquals(nExpect, iter(set));
        }

        static int iter(final Set<Object> set) {
            int c = 0;
            //noinspection ForLoopReplaceableByForEach
            for (Iterator<Object> iter = set.iterator(); iter.hasNext(); ) {
                iter.next();
                ++c;
            }
            return c;
        }

        @Override
        public String toString() {
            return "iterate -> " + nExpect;
        }

        int nExpect;
    }

    public static class OpIterPart
            implements Op {
        OpIterPart() {}

        OpIterPart(final int cIter) {
            this.cIter = cIter;
        }

        @Override
        public void parse(final String[] asParams) {
            cIter = Integer.parseInt(asParams[0]);
            assert "->".equals(asParams[1]);
        }

        @Override
        public void init(final Set<Object> set) {
            iter(set);
        }

        @Override
        public void test(final Set<Object> set) {
            iter(set);
        }

        void iter(final Set<Object> set) {
            Iterator<Object> iter = set.iterator();
            for (int c = 0; c < cIter; ++c) {
                assertTrue(iter.hasNext());
                iter.next();
            }
        }

        @Override
        public String toString() {
            return "iterate-partial " + cIter + " ->";
        }

        int cIter;
    }

    static void out() {
        out("");
    }

    static void out(final String s) {
        System.out.println(s);
    }

    static final Random rnd = new Random();


    // ----- unit tests ----------------------------------------------------------------------------

    public static class StringData
            implements Data {
        StringData(final int count) {
            assert count > 0;
            HashSet<String> set = new HashSet<>(count);
            for (int i = 0; i < count; ++i) {
                StringBuilder sb = new StringBuilder();
                do {
                    sb.append((char) ('A' + rnd.nextInt(26)));
                } while (!set.add(sb.toString()));
            }
            strings = set.toArray(new String[count]);

            // Inexplicably this exception occurred once in the rnd() method below, which could only
            // occur if the "strings" array were of zero length, which should be impossible (given
            // the assertion above and the algorithm in use here in the constructor.) To try to catch
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
            //noinspection ConstantValue
            assert strings.length > 0;
        }

        public String rnd() {
            return strings[rnd.nextInt(strings.length)];
        }

        String[] strings;
    }

    @Test
    public void testIterator() {
        ListSet<String> set = new ListSet<>();
        for (char ch = 'a'; ch <= 'z'; ++ch) {
            set.add(String.valueOf(ch));
        }

        Iterator<String> iter = set.iterator();
        assertEquals("z", set.last());
        for (char ch = 'a'; ch <= 'z'; ++ch) {
            assertTrue(iter.hasNext());
            assertEquals(String.valueOf(ch), iter.next());
        }
        assertFalse(iter.hasNext());
    }

    @Test
    public void repro1() {
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

        String sb = """
                [0] remove HTM -> false
                [1] add FQP -> true
                [2] add FQP -> false
                [3] remove DMTJ -> false
                [4] add FQP -> false
                [5] add IZGG -> true
                [6] add FQP -> false
                [7] add IZGG -> false
                [8] add EVWM -> true
                [9] remove EVWM -> true
                [10] add YZKK -> true
                [11] remove NLES -> false
                [12] add IZGG -> false
                [13] iterate -> 3
                [14] size -> 3
                [15] add FQP -> false
                [16] remove FQP -> true
                [17] add YZKK -> false
                [18] add UHV -> true
                [19] add PGX -> true
                [20] iterate -> 4
                [21] add WGP -> true
                [22] add WGP -> false
                [23] add UHV -> false
                [24] size -> 5
                [25] remove YZKK -> true
                [26] add ZIWT -> true
                [27] add NMOQ -> true
                [28] add NZD -> true
                [29] iterate -> 7
                [30] size -> 7
                [31] remove NMOQ -> true
                [32] iterate -> 6
                [33] add AUH -> true
                [34] add CJCD -> true
                [35] add OLX -> true
                [36] add DLG -> true
                [37] remove JKQ -> false
                [38] add HFF -> true
                [39] remove AUH -> true
                [40] iterate -> 10
                [41] add EXN -> true
                [42] add LAFL -> true
                [43] add IZGG -> false
                [44] add QZI -> true
                [45] size -> 13
                [46] size -> 13
                [47] add BZX -> true
                [48] iterate -> 14
                """;
        replayTest(sb);
    }

    @Test
    public void repro2() {
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

        String sb = """
                [0] add UAY -> true
                [1] add NBT -> true
                [2] size -> 2
                [3] size -> 2
                [4] remove MRP -> false
                [5] add ZIW -> true
                [6] add GFW -> true
                [7] add SZMV -> true
                [8] add SZMV -> false
                [9] add ADX -> true
                [10] add ADX -> false
                [11] add BWK -> true
                [12] add SZMV -> false
                [13] remove SZMV -> true
                [14] add NBT -> false
                [15] add NKDK -> true
                [16] add RHV -> true
                [17] iterate -> 8
                [18] iterate -> 8
                [19] add DHH -> true
                [20] add TLT -> true
                [21] add TRB -> true
                [22] add WYCV -> true
                [23] iterate -> 12
                [24] add SFJ -> true
                [25] remove NKDK -> true
                [26] size -> 12
                [27] add MTW -> true
                [28] remove ZIW -> true
                [29] iterate -> 12
                [30] add ADX -> false
                [31] add UAY -> false
                """;
        replayTest(sb);
    }

    @Test
    public void repro3() {
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

        String sb = """
                [0] size -> 0
                [1] add LA -> true
                [2] add HJZ -> true
                [3] remove LA -> true
                [4] add VB -> true
                [5] add WED -> true
                [6] iterate -> 3
                [7] iterate -> 3
                [8] add ER -> true
                [9] remove VB -> true
                [10] add JDE -> true
                [11] add KIM -> true
                [12] iterate -> 5
                [13] add NE -> true
                [14] add YZ -> true
                [15] iterate -> 7
                [16] size -> 7
                [17] add YZ -> false
                [18] remove N -> false
                [19] add NE -> false
                [20] add WED -> false
                [21] size -> 7
                [22] add JE -> true
                [23] iterate -> 8
                [24] add NE -> false
                [25] add WED -> false
                [26] remove NE -> true
                [27] remove YZ -> true
                [28] add CZ -> true
                [29] add KIM -> false
                [30] size -> 7
                [31] add AG -> true
                [32] remove ZXO -> false
                [33] size -> 8
                [34] add HJZ -> false
                [35] add JDE -> false
                [36] add ER -> false
                [37] add KIM -> false
                [38] add AF -> true
                [39] add U -> true
                [40] iterate -> 10
                [41] add PG -> true
                [42] remove SRO -> false
                [43] add XZ -> true
                [44] remove KIM -> true
                [45] iterate -> 11
                [46] add CVO -> true
                [47] remove U -> true
                [48] iterate -> 11
                [49] add JA -> true
                [50] remove CVO -> true
                [51] iterate -> 11
                [52] add EXF -> true
                [53] remove CZ -> true
                [54] iterate -> 11
                [55] remove LG -> false
                [56] add EXF -> false
                [57] add OS -> true
                [58] remove UO -> false
                [59] add S -> true
                [60] size -> 13
                [61] add WU -> true
                [62] add MKL -> true
                [63] add NRF -> true
                [64] remove AA -> false
                [65] add KFM -> true
                [66] size -> 17
                [67] add ST -> true
                [68] add PG -> false
                [69] add AI -> true
                [70] add RN -> true
                [71] remove EXF -> true
                """;
        replayTest(sb);
    }

    @Test
    public void repro4() {
        // Exception on set# 0 in step# 116: null
        // java.lang.NullPointerException
        //     at org.xvm.util.ListSet.indexSearch(ListSet.java:785)
        //     at org.xvm.util.ListSet.indexOf(ListSet.java:190)
        //     at org.xvm.util.ListSet.contains(ListSet.java:102)
        //     at org.xvm.util.ListSet.add(ListSet.java:114)
        //     at org.xvm.util.SetTest$OpAdd.test(SetTest.java:589)
        //     at org.xvm.util.SetTest.randomTest(SetTest.java:360)

        String sb = """
                [0] add IL -> true
                [1] add IL -> false
                [2] add JP -> true
                [3] add WQ -> true
                [4] add IL -> false
                [5] remove WQ -> true
                [6] iterate -> 2
                [7] remove JP -> true
                [8] size -> 1
                [9] add IL -> false
                [10] add LB -> true
                [11] add MSS -> true
                [12] size -> 3
                [13] add MS -> true
                [14] add OC -> true
                [15] add JP -> true
                [16] add IL -> false
                [17] add CG -> true
                [18] add V -> true
                [19] remove JP -> true
                [20] add I -> true
                [21] add IL -> false
                [22] remove ZB -> false
                [23] add JPQ -> true
                [24] add JPQ -> false
                [25] iterate -> 9
                [26] add NL -> true
                [27] add Z -> true
                [28] size -> 11
                [29] add AU -> true
                [30] remove Y -> false
                [31] remove IL -> true
                [32] remove AU -> true
                [33] remove MSS -> true
                [34] iterate -> 9
                [35] add AC -> true
                [36] add ZB -> true
                [37] add Z -> false
                [38] add V -> false
                [39] add B -> true
                [40] add G -> true
                [41] remove N -> false
                [42] add Z -> false
                [43] add PN -> true
                [44] remove LB -> true
                [45] add V -> false
                [46] add RC -> true
                [47] add TK -> true
                [48] remove B -> true
                [49] add N -> true
                [50] size -> 15
                [51] remove OC -> true
                [52] iterate -> 14
                [53] size -> 14
                [54] add MS -> false
                [55] add C -> true
                [56] add ME -> true
                [57] size -> 16
                [58] remove RC -> true
                [59] size -> 15
                [60] iterate -> 15
                [61] add W -> true
                [62] remove NL -> true
                [63] add K -> true
                [64] add CG -> false
                [65] remove Z -> true
                [66] add XD -> true
                [67] add I -> false
                [68] add TK -> false
                [69] add O -> true
                [70] add JPQ -> false
                [71] add P -> true
                [72] add XD -> false
                [73] size -> 18
                [74] iterate -> 18
                [75] remove C -> true
                [76] size -> 17
                [77] add M -> true
                [78] add N -> false
                [79] iterate -> 18
                [80] add T -> true
                [81] size -> 19
                [82] remove P -> true
                [83] add CG -> false
                [84] add MK -> true
                [85] iterate -> 19
                [86] add UE -> true
                [87] add R -> true
                [88] remove M -> true
                [89] iterate -> 20
                [90] add AK -> true
                [91] remove CG -> true
                [92] add P -> true
                [93] add MSS -> true
                [94] add AU -> true
                [95] size -> 23
                [96] add TK -> false
                [97] add J -> true
                [98] add T -> false
                [99] iterate -> 24
                [100] remove JP -> false
                [101] add XT -> true
                [102] remove AU -> true
                [103] remove MSS -> true
                [104] iterate -> 23
                [105] add KL -> true
                [106] iterate -> 24
                [107] remove ZB -> true
                [108] iterate -> 23
                [109] add RM -> true
                [110] add N -> false
                [111] remove ME -> true
                [112] add WM -> true
                [113] remove K -> true
                [114] add MS -> false
                [115] remove PN -> true
                [116] add AU -> true
                """;
        replayTest(sb);
    }

    @Test
    public void repro5() {
        // Exception on set# 0 in step# 41: expected:<false> but was:<true>
        String sb = """
                [0] size -> 0
                [1] iterate -> 0
                [2] iterate -> 0
                [3] add POP -> true
                [4] add SMPV -> true
                [5] add HYW -> true
                [6] add RXYN -> true
                [7] add LRSY -> true
                [8] add WLD -> true
                [9] remove DDKH -> false
                [10] add EHN -> true
                [11] add ZCK -> true
                [12] remove RXYN -> true
                [13] add UAPK -> true
                [14] iterate -> 8
                [15] add EHN -> false
                [16] add HYW -> false
                [17] add POP -> false
                [18] add SMPV -> false
                [19] add TRFI -> true
                [20] remove DMWB -> false
                [21] size -> 9
                [22] add RXU -> true
                [23] remove YEPT -> false
                [24] add XGU -> true
                [25] add ACJY -> true
                [26] add ACJY -> false
                [27] add FXK -> true
                [28] add FUY -> true
                [29] iterate -> 14
                [30] iterate -> 14
                [31] add FUY -> false
                [32] add ZSQ -> true
                [33] add IMOO -> true
                [34] iterate -> 16
                [35] add BXR -> true
                [36] size -> 17
                [37] add TRFI -> false
                [38] remove ACJY -> true
                [39] add SMPV -> false
                [40] add XGU -> false
                [41] add LRSY -> false
                """;
        replayTest(sb);
    }

    @Test
    public void repro6() {
        // Exception on set# 0 in step# 34: expected:<true> but was:<false>
        String sb = """
                [0] remove SD -> false
                [1] add DUM -> true
                [2] add IZE -> true
                [3] add YNE -> true
                [4] add YNE -> false
                [5] add DUM -> false
                [6] add PRB -> true
                [7] add WA -> true
                [8] add TJ -> true
                [9] add JHD -> true
                [10] iterate -> 7
                [11] add MFN -> true
                [12] remove MFN -> true
                [13] add BV -> true
                [14] remove XM -> false
                [15] add YP -> true
                [16] add GTE -> true
                [17] size -> 10
                [18] add DGM -> true
                [19] add TIX -> true
                [20] remove WA -> true
                [21] add ARQ -> true
                [22] add DUM -> false
                [23] iterate -> 12
                [24] add BV -> false
                [25] size -> 12
                [26] iterate -> 12
                [27] add PRB -> false
                [28] add EOG -> true
                [29] add NBX -> true
                [30] add EOG -> false
                [31] remove TIX -> true
                [32] add VW -> true
                [33] add WK -> true
                [34] remove ARQ -> true
                """;
        replayTest(sb);
    }

    @Test
    public void repro7() {
        // Exception on set# 0 in step# 55: expected:<true> but was:<false>
        String sb = """
                [0] add SAN -> true
                [1] iterate -> 1
                [2] add SAN -> false
                [3] add SAN -> false
                [4] add SAN -> false
                [5] size -> 1
                [6] add TA -> true
                [7] add OWB -> true
                [8] add NQQ -> true
                [9] remove LDL -> false
                [10] remove DHJ -> false
                [11] remove LCX -> false
                [12] add CJJ -> true
                [13] add TA -> false
                [14] add NWI -> true
                [15] add OWB -> false
                [16] add CYU -> true
                [17] iterate -> 7
                [18] add KIT -> true
                [19] add CL -> true
                [20] size -> 9
                [21] add IGC -> true
                [22] add TA -> false
                [23] iterate -> 10
                [24] add TUK -> true
                [25] remove ATP -> false
                [26] iterate -> 11
                [27] remove JDB -> false
                [28] add LWA -> true
                [29] add DUS -> true
                [30] remove DUS -> true
                [31] add TBI -> true
                [32] remove TD -> false
                [33] add WEJ -> true
                [34] size -> 14
                [35] add XRX -> true
                [36] add VAZ -> true
                [37] add NXP -> true
                [38] add JDM -> true
                [39] remove TUK -> true
                [40] iterate -> 17
                [41] add OXM -> true
                [42] size -> 18
                [43] remove TBI -> true
                [44] add ULL -> true
                [45] add ZCGV -> true
                [46] remove WEJ -> true
                [47] remove XXG -> false
                [48] remove ULL -> true
                [49] size -> 17
                [50] add UP -> true
                [51] size -> 18
                [52] remove LWA -> true
                [53] remove SAN -> true
                [54] add UP -> false
                [55] remove NQQ -> true
                """;
        replayTest(sb);
    }

    @Test
    public void repro8() {
        // Exception on set# 0 in step# 39: expected:<true> but was:<false>
        String sb = """
                [0] add JWD -> true
                [1] add JWD -> false
                [2] iterate -> 1
                [3] add XMH -> true
                [4] remove JWD -> true
                [5] remove ALQ -> false
                [6] add RMD -> true
                [7] add RMD -> false
                [8] remove RMD -> true
                [9] iterate -> 1
                [10] add SOYW -> true
                [11] add CRT -> true
                [12] add CRT -> false
                [13] remove SOYW -> true
                [14] add JDYH -> true
                [15] add SBKA -> true
                [16] iterate -> 4
                [17] size -> 4
                [18] add UKBB -> true
                [19] add DHG -> true
                [20] add ZCK -> true
                [21] remove SBKA -> true
                [22] add AQDD -> true
                [23] size -> 7
                [24] size -> 7
                [25] remove KDL -> false
                [26] add GOD -> true
                [27] add FNP -> true
                [28] add ZPZZ -> true
                [29] remove DUOZ -> false
                [30] add SGN -> true
                [31] add ZOCZ -> true
                [32] add MCOA -> true
                [33] remove AQDD -> true
                [34] iterate -> 12
                [35] add HHQZ -> true
                [36] remove XMH -> true
                [37] remove APUG -> false
                [38] add ZPZZ -> false
                [39] remove GOD -> true
                """;          // sacrilege?
        replayTest(sb);
    }

    @Test
    public void repro9() {
        // Exception on set# 0 in step# 13: expected:<7> but was:<6>
        String sb = """
                [0] iterate -> 0
                [1] add TV -> true
                [2] add BQK -> true
                [3] add KZ -> true
                [4] add BQK -> false
                [5] add EGP -> true
                [6] size -> 4
                [7] add RY -> true
                [8] iterate -> 5
                [9] add RTS -> true
                [10] add QOP -> true
                [11] remove RTS -> true
                [12] add ZVN -> true
                [13] size -> 7
                """;
        replayTest(sb);
    }
}
