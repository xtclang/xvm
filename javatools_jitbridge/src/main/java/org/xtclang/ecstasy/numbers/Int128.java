package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.xtclang.ecstasy.Comparable;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int128 wrapper.
 */
public class Int128 extends IntNumber {
    /**
     * Construct an Ecstasy Int128 object.
     */
    Int128(long lowValue, long highValue) {
        $lowValue  = lowValue;
        $highValue = highValue;
    }

    public final long $lowValue;
    public final long $highValue;

    private BigInteger $bigInteger = null;

    private String $toString = null;

    @Override
    public String toString(Ctx ctx) {
        String toString = $toString;
        if (toString == null) {
            toString = $toString = String.of(ctx, $asBigInteger().toString());
        }
        return toString;
    }

    /**
     * Obtain an Int128 for two 64-bit "primitive" long values.
     *
     * @return an Int128 reference
     */
    public static Int128 $box(long lowValue, long highValue) {
        return new Int128(lowValue, highValue);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal($asBigInteger());
    }

    /**
     * A helper method to perform 128-bit integer division.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return  the low 64 bits of the result
     */
    public static long $div(Ctx ctx, long low1, long high1, long low2, long high2) {
        BigInteger n1 = $toBigInteger(low1, high1);
        BigInteger n2 = $toBigInteger(low2, high2);
        return $fromBigInteger(ctx, n1.divide(n2));
    }

    /**
     * A helper method to perform 128-bit integer modulus division.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return  the low 64 bits of the result
     */
    public static long $mod(Ctx ctx, long low1, long high1, long low2, long high2) {
        BigInteger n1 = $toBigInteger(low1, high1);
        BigInteger n2 = $toBigInteger(low2, high2);
        return $fromBigInteger(ctx, n1.mod(n2));
    }


    /**
     * @return this Int128 as a {@link BigInteger}
     */
    private BigInteger $asBigInteger() {
        BigInteger bi = $bigInteger;
        if (bi == null) {
            bi = $bigInteger = $toBigInteger($lowValue, $highValue);
        }
        return bi;
    }

    /**
     * This method is called at the end of a JIT prmitive method to return the value of a
     * {@link BigInteger}.
     * <p>
     * The high 64 bits of the value will be set into the {@link Ctx#i0} field and the low 64
     * bits will be returned as a {@code long}.
     *
     * @param ctx  the current context
     * @param bi   the {@link BigInteger} to return
     *
     * @return  a {@code long} representing the low 64 bits of the {@code BigInteger}
     */
    public static long $fromBigInteger(Ctx ctx, BigInteger bi) {
        ctx.i0 = bi.shiftRight(64).longValue();
        return bi.longValue();
    }

    /**
     * Convert a 128-bit integer represented as two long values into a {@link BigInteger}.
     *
     * @param lowValue   the low 64 bits of the {@code BigInteger}
     * @param highValue  the high 64 bits of the {@code BigInteger}
     *
     * @return a {@link BigInteger} created from the two long values
     */
    private static BigInteger $toBigInteger(long lowValue, long highValue) {
        BigInteger low = BigInteger.valueOf(lowValue & Long.MAX_VALUE);
        if (lowValue < 0) {
            low = low.setBit(63);
        }
        if (highValue == 0) {
            return low;
        }
        return low.or(BigInteger.valueOf(highValue).shiftLeft(64));
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * Implementation of Int8 toInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Byte.MIN_VALUE || $lowValue > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid Int8 value");
        }
        return (byte) $lowValue;
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Short.MIN_VALUE || $lowValue > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid Int16 value");
        }
        return (short) $lowValue;
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid Int32 value");
        }
        return (int) $lowValue;
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid Int32 value");
        }
        return $lowValue;
    }

    /**
     * The primitive implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as an Int128
     */
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = $highValue;
        return $lowValue;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0L || $lowValue > 255L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid UInt8 value");
        }
        return (byte) $lowValue & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0 || $lowValue > 65535L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid UInt16 value");
        }
        return (int) $lowValue & 0xFFFF;
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0L || $lowValue > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid UInt32 value");
        }
        return (int) $lowValue;
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && $lowValue < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid UInt64 value");
        }
        return $lowValue;
    }

    /**
     * The primitive implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a UInt128
     */
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $asBigInteger()
                    + " is not a valid UInt128 value");
        }
        // load the high long value to the context and return the low value
        ctx.i0 = $highValue;
        return $lowValue;
    }

    // ----- internal JIT support ------------------------------------------------------------------

    /**
     * The internal compare method for two Int128 values called by the compare methods generated
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleCompareMethod}
     * and also in {@link TypeConstant#buildCompare}
     *
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return a negative integer if the first Int28 is lower than the second, zero if both
     * Int128 values are equal, or a positive integer if the first Int128 is greater than the
     * second.
     */
    public static int $compare(long low1, long high1, long low2, long high2) {
        return high1 == high2 ? Long.compareUnsigned(low1, low2)
                              : Long.compare(high1, high2);
    }

    /**
     * The internal equals method for two Int128 values called by the equals methods generated
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleEqualsMethod} Method}
     * and also in {@link TypeConstant#buildCompare}
     *
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return {@code true} if the two Int128 values are equal, {@code false} otherwise.
     */
    public static boolean $equals(long low1, long high1, long low2, long high2) {
        return high1 == high2 && low1 == low2;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $asBigInteger().toString();
    }
}
