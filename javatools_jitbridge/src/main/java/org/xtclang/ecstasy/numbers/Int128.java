package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int128 wrapper.
 */
public class Int128 extends IntNumber {
    /**
     * Construct an Ecstasy Int128 object.
     */
    private Int128(long lowValue, long highValue) {
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

    public static String toString$p(long thi$Lo, long thi$Hi, Ctx ctx) {
        return String.of(ctx, $toBigInteger(thi$Lo, thi$Hi).toString());
    }

    public static long estimateStringLength$p(long thi$Lo, long thi$Hi, Ctx ctx) {
        return $toBigInteger(thi$Lo, thi$Hi).toString().length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : $asBigInteger().toString().toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(long thi$Lo, long thi$Hi, Ctx ctx,
                                             AppenderᐸCharᐳ appender) {
        for (char c : $toBigInteger(thi$Lo, thi$Hi).toString().toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
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

    @Override
    protected long[] $longValues() {
        return new long[]{$highValue, $lowValue};
    }

    @Override
    protected long bitLength$get$p() {
        return 128;
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
     * Native implementation of: "Int8 toInt8(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toInt8$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < Byte.MIN_VALUE || thi$Lo > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid Int8 value");
        }
        return (byte) thi$Lo;
    }

    /**
     * Native implementation of: "Int16 toInt16(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toInt16$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < Short.MIN_VALUE || thi$Lo > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid Int16 value");
        }
        return (short) thi$Lo;
    }

    /**
     * Native implementation of: "Int32 toInt32(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toInt32$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < Integer.MIN_VALUE || thi$Lo > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid Int32 value");
        }
        return (int) thi$Lo;
    }

    /**
     * Native implementation of: "Int toInt(Boolean checkBounds = False)"
     *
     * Although Int and Int64 have the same JIT representation, "toInt()" and "toInt64()" are
     * distinct Ecstasy methods.
     *
     * This bridge is required because a primitive invocation bypasses the natural toInt()
     * implementation that delegates to toInt64().
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java long
     */
    public static long toInt$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toInt64$p(thi$Lo, thi$Hi, ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * Native implementation of: "Int64 toInt64(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java long
     */
    public static long toInt64$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < Integer.MIN_VALUE || thi$Lo > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid Int32 value");
        }
        return thi$Lo;
    }

    /**
     * Native implementation of: "Int128 toInt128(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as an Int128
     */
    public static long toInt128$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = thi$Hi;
        return thi$Lo;
    }

    /**
     * Native implementation of: "IntN toIntN()"
     */
    public static IntN toIntN$p(long thi$Lo, long thi$Hi, Ctx ctx) {
        return IntN.$box(thi$Lo, thi$Hi);
    }

    /**
     * Native implementation of: "UInt8 toUInt8(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toUInt8$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < 0L || thi$Lo > 255L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UInt8 value");
        }
        return (byte) thi$Lo & 0xFF;
    }

    /**
     * Native implementation of: "UInt16 toUInt16(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toUInt16$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < 0 || thi$Lo > 65535L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UInt16 value");
        }
        return (int) thi$Lo & 0xFFFF;
    }

    /**
     * Native implementation of: "UInt32 toUInt32(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java {@code int}
     */
    public static int toUInt32$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && (thi$Lo < 0L || thi$Lo > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UInt32 value");
        }
        return (int) thi$Lo;
    }

    /**
     * Native implementation of: "UInt toUInt(Boolean checkBounds = False)"
     *
     * Although UInt and UInt64 have the same JIT representation, "toUInt()" and "toUInt64()" are
     * distinct Ecstasy methods.
     *
     * This bridge is required because a primitive invocation bypasses the natural toUInt()
     * implementation that delegates to toUInt64().
     */
    public static long toUInt$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toUInt64$p(thi$Lo, thi$Hi, ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * Native implementation of: "UInt64 toUInt64(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a Java long
     */
    public static long toUInt64$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi != 0 && thi$Hi != -1
                && thi$Lo < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UInt64 value");
        }
        return thi$Lo;
    }

    /**
     * Native implementation of: "UInt128 toUInt128(Boolean checkBounds = False)"
     *
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int128 value as a UInt128
     */
    public static long toUInt128$p(long thi$Lo, long thi$Hi, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$Hi < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UInt128 value");
        }
        // load the high long value to the context and return the low value
        ctx.i0 = thi$Hi;
        return thi$Lo;
    }

    /**
     * Native implementation of: "UIntN toUIntN()"
     */
    public static UIntN toUIntN$p(long thi$Lo, long thi$Hi, Ctx ctx) {
        if (thi$Hi < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + $toBigInteger(thi$Lo, thi$Hi)
                    + " is not a valid UIntN value");
        }
        return UIntN.$box(thi$Lo, thi$Hi);
    }

    // ----- internal JIT support ------------------------------------------------------------------

    /**
     * The internal compare method for two Int128 values called by the compare methods generated
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleConstCompare}
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
     * in {@link org.xvm.javajit.builders.CommonBuilder#assembleConstEquals} Method}
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
