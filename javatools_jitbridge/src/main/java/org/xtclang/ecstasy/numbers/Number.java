package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Exception;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native Number wrapper.
 */
public abstract class Number
        extends nConst
        implements FPConvertible {

    protected Number() {
        super(null);
    }

    /**
     * Estimate the length of the number as a string representation.
     *
     * @param value the numeric value as a double
     *
     * @return the estimated string length
     */
    public int $estimateStringLength(double value) {
        if (value == 0) {
            return 1;
        }
        int neg = value < 0 ? 1 : 0;
        return (int) Math.log10(Math.abs(value)) + 1 + neg;
    }

    /**
     * An unsigned Java long can store values up to 20 digits long (specifically up to
     * 18,446,744,073,709,551,615). Because standard mathematical methods and casting to double
     * fail at these extreme boundaries due to precision loss, we use an array lookup.
     * <p>
     * Because an unsigned long has a fixed maximum size, you can use a lookup array filled with
     * the unsigned power-of-10 thresholds. By using Long.compareUnsigned(), you avoid any object
     * creation.
     */
    public static int $estimateUnsignedStringLength(long number) {
        for (int i = 0; i < UNSIGNED_TENS.length; i++) {
            if (Long.compareUnsigned(number, UNSIGNED_TENS[i]) < 0) {
                return i;
            }
        }
        return 20; // If it is greater than or equal to 10^19, it has 20 digits.
    }

    private static final long[] UNSIGNED_TENS = {
            1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L, 1000000000L,
            10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L,
            1000000000000000L, 10000000000000000L, 100000000000000000L, 1000000000000000000L,
            Long.parseUnsignedLong("10000000000000000000")
    };

    /**
     * Native support of IllegalMath exception.
     */
    public static class IllegalMath extends Exception {
        public IllegalMath(Ctx ctx) {
            super(ctx);
        }
    }

    /**
     * Native support of DivisionByZero exception.
     */
    public static class DivisionByZero extends IllegalMath {
        public DivisionByZero(Ctx ctx) {
            super(ctx);
        }
    }
}
