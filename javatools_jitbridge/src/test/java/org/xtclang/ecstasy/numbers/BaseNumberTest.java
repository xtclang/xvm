package org.xtclang.ecstasy.numbers;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BaseNumberTest {

    protected int[] ensureIntTestData() {
        return ensureIntTestData(false, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    protected int[] ensureIntTestData(int start, int end) {
        return ensureIntTestData(false, start, end);
    }

    protected int[] ensurePositiveIntTestData() {
        return ensureIntTestData(true, 0, Integer.MAX_VALUE);
    }

    protected int[] ensurePositiveIntTestData(int end) {
        return ensureIntTestData(true, 0, end);
    }

    protected int[] ensureIntTestData(boolean positive, int start, int end) {
        if (positive) {
            assertTrue(start >= 0, "start must be >= 0");
        }
        assertTrue(start <= end, "start must be <= end");

        Random rnd        = new Random();
        int    minInteger = positive ? 0 : Integer.MIN_VALUE;
        int    count      = 5000;
        int[]  testData   = new int[count];
        int    i          = 0;

        testData[i++] = minInteger;
        if (minInteger != start) {
            testData[i++] = start;
        }

        for (; i < 1000 - 1; i++) {
            testData[i] = rnd.nextInt(start, end);
        }

        testData[i++] = end;
        for (; i < count - 1; i++) {
            if (positive) {
                testData[i] = rnd.nextInt(0, Integer.MAX_VALUE);
            } else {
                testData[i] = i % 2 == 0
                        ? rnd.nextInt(0, Integer.MAX_VALUE)
                        : rnd.nextInt(minInteger, 0);
            }
        }
        testData[count - 1] = Integer.MAX_VALUE;
        return testData;
    }

    protected long[] ensureLongTestData() {
        return ensureLongTestData(false, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    protected long[] ensureLongTestData(long start, long end) {
        return ensureLongTestData(false, start, end);
    }

    protected long[] ensurePositiveLongTestData() {
        return ensureLongTestData(true, 0, Long.MAX_VALUE);
    }

    protected long[] ensurePositiveLongTestData(long end) {
        return ensureLongTestData(true, 0, end);
    }

    private long[] ensureLongTestData(boolean positive, long start, long end) {
        if (positive) {
            assertTrue(start >= 0, "start must be >= 0");
        }
        assertTrue(start <= end, "start must be <= end");

        Random rnd      = new Random();
        long   minLong  = positive ? 0 : Long.MIN_VALUE;
        int    count    = 5000;
        long[] testData = new long[count];
        int    i        = 0;

        testData[i++] = minLong;
        if (minLong != start) {
            testData[i++] = start;
        }

        for (; i < 1000 - 1; i++) {
            testData[i] = rnd.nextLong(start, end);
        }

        testData[i++] = end;

        for (; i < count - 1; i++) {
            if (positive) {
                testData[i] = rnd.nextLong(0, Long.MAX_VALUE);
            } else {
                testData[i] = i % 2 == 0
                        ? rnd.nextLong(0, Long.MAX_VALUE)
                        : rnd.nextLong(minLong, 0);
            }
        }
        testData[i] = Long.MAX_VALUE;

        return testData;
    }
}
