package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nType;

import org.xvm.javajit.Ctx;

/**
 * Native UInt128 wrapper.
 */
public class UInt128 extends IntNumber {
    /**
     * Construct an Ecstasy UInt128 object.
     */
    UInt128(long lowValue, long highValue) {
        $lowValue  = lowValue;
        $highValue = highValue;
    }

    public final long $lowValue;
    public final long $highValue;

    private BigInteger $bigInteger = null;

    private org.xtclang.ecstasy.text.String $toString = null;

    @Override
    public org.xtclang.ecstasy.text.String toString(Ctx ctx) {
        org.xtclang.ecstasy.text.String toString = $toString;
        if (toString == null) {
            toString = $toString = org.xtclang.ecstasy.text.String.of(ctx,
                    $asBigInteger().toString());
        }
        return toString;
    }

    /**
     * Obtain a UInt128 for two 64-bit "primitive" long values.
     *
     * @return a UInt128 reference
     */
    public static UInt128 $box(long lowValue, long highValue) {
        return new UInt128(lowValue, highValue);
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
     * @return this Int128 as an unsigned {@link BigInteger}
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
     * Convert a 128-bit integer represented as two long values into an unsigned {@link BigInteger}.
     *
     * @param lowValue   the low 64 bits of the {@code BigInteger}
     * @param highValue  the high 64 bits of the {@code BigInteger}
     *
     * @return an unsigned {@link BigInteger} created from the two long values
     */
    private static BigInteger $toBigInteger(long lowValue, long highValue) {
        return toUnsignedBigInteger(lowValue).
                or(toUnsignedBigInteger(highValue).shiftLeft(64));
    }

    /**
     * @return  a {@code long} value converted to an unsigned {@link BigInteger}
     */
    public static BigInteger toUnsignedBigInteger(long value) {
        if (value >= 0L) {
            return BigInteger.valueOf(value);
        }
        int nHigh = (int) (value >>> 32);
        int nLow  = (int) value;
        return (BigInteger.valueOf(Integer.toUnsignedLong(nHigh))).shiftLeft(32).
                add(BigInteger.valueOf(Integer.toUnsignedLong(nLow)));
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
     * @return this UInt128 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < Byte.MIN_VALUE || $lowValue > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < Short.MIN_VALUE || $lowValue > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $lowValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as an Int128
     */
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
                    + " is not a valid Int128 value");
        }
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
     * @return this UInt128 value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < 0 || $lowValue > 255)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
                    + " is not a valid UInt8 value");
        }
        return (int) $lowValue & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt128 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < 0 || $lowValue > 65535)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < 0 || $lowValue > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $lowValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + $asBigInteger()
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
     * @return this UInt128 value as a UInt128
     */
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = $highValue;
        return $lowValue;
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     * <p>
     * {@code static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);}
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        UInt128 i1        = (UInt128) value1;
        UInt128 i2        = (UInt128) value2;
        long    unsigned1 = i1.$highValue + Long.MIN_VALUE;
        long    unsigned2 = i2.$highValue + Long.MIN_VALUE;
        if (unsigned1 != unsigned2) {
            return (unsigned1 < unsigned2)
                    ? Ordered.Lesser.$INSTANCE
                    : Ordered.Greater.$INSTANCE;
        }
        unsigned1 = i1.$lowValue + Long.MIN_VALUE;
        unsigned2 = i2.$lowValue + Long.MIN_VALUE;
        return (unsigned1 < unsigned2)
                ? Ordered.Lesser.$INSTANCE
                : unsigned1 == unsigned2 ? Ordered.Equal.$INSTANCE : Ordered.Greater.$INSTANCE;
    }

    /**
     * Compare two Int128 primitives.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return a negative integer if the first Int28 is lower than the second, zero if both
     * Int128 values are equal, or a positive integer if the first Int128 is greater than the
     * second.
     */
    public static int compare$p(Ctx ctx, long low1, long high1, long low2, long high2) {
        if (high1 != high2) {
            return Long.compareUnsigned(high1, high2);
        }
        return Long.compareUnsigned(low1, low2);
    }

    /**
     * The primitive implementation of:
     * <p>
     * {@code static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);}
     */
    public static Boolean equals(Ctx ctx, nType type, org.xtclang.ecstasy.Comparable value1, org.xtclang.ecstasy.Comparable value2) {
        Int128 i1 = (Int128) value1;
        Int128 i2 = (Int128) value2;
        return i1.$lowValue == i2.$lowValue && i1.$highValue == i2.$highValue
                ? Boolean.TRUE
                : Boolean.FALSE;
    }

    /**
     * Determine whether two Int128 primitives are equal.
     *
     * @param ctx    the context
     * @param low1   the low 64 bits of the first Int128
     * @param high1  the high 64 bits of the first Int128
     * @param low2   the low 64 bits of the second Int128
     * @param high2  the high 64 bits of the second Int128
     *
     * @return {@code true} iff the two Int128 primitives are equal
     */
    public static boolean equals$p(Ctx ctx, long low1, long high1, long low2, long high2) {
        return low1 == low2 && high1 == high2;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $asBigInteger().toString();
    }
}
