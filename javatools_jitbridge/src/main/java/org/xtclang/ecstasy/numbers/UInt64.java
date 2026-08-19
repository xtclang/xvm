package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt64 wrapper.
 */
public class UInt64 extends UIntNumber {
    /**
     * Construct an Ecstasy UInt64 object.
     *
     * @param value  the 64-bit unsigned long value
     */
    private UInt64(long value) {
        $value = value;
    }

    public final long $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Long.toUnsignedString($value));
    }

    public static String toString$p(long thi$, Ctx ctx) {
        return String.of(ctx, Long.toUnsignedString(thi$));
    }

    public static long estimateStringLength$p(long thi$, Ctx ctx) {
        return $estimateUnsignedStringLength(thi$);
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toUnsignedString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(long thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toUnsignedString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    /**
     * Obtain a UInt64 for a 64-bit "primitive" long.
     *
     * @param value  a 64-bit "primitive" long
     *
     * @return an UInt64 reference
     */
    public static UInt64 $box(long value) {
        return new UInt64(value);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal(Long.toUnsignedString($value));
    }

    @Override
    protected long[] $longValues() {
        return new long[]{$value};
    }

    @Override
    protected long bitLength$get$p() {
        return 64;
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
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toInt8$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid Int8 value");
        }
        return (byte) thi$;
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toInt16$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid Int16 value");
        }
        return (short) thi$;
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toInt32$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid Int32 value");
        }
        return (int) thi$;
    }

    /**
     * Native implementation of: "Int toInt(Boolean checkBounds = False)"
     *
     * This bridge is required because a primitive invocation bypasses the natural toInt()
     * implementation that delegates to toInt64().
     */
    public static long toInt$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toInt64$p(thi$, ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java long
     */
    public static long toInt64$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$ < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid Int32 value");
        }
        return thi$;
    }

    /**
     * The primitive implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as an Int128
     */
    public static long toInt128$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return thi$;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public static IntN toIntN$p(long thi$, Ctx ctx) {
        return IntN.$box(new BigInteger(Long.toUnsignedString(thi$)));
    }

    /**
     * The primitive implementation of Nibble toNibble(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this value as the least significant four bits of a Java {@code int}
     */
    public static int toNibble$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > 15)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt64 value " + thi$ + " is not a valid Nibble value");
        }
        return thi$ & 0x0F;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toUInt8$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > 255)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid UInt8 value");
        }
        return (int) thi$ & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toUInt16$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > 65535)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid UInt16 value");
        }
        return (int) thi$ & 0xFFFF;
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java {@code int}
     */
    public static int toUInt32$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + thi$ + " is not a valid UInt32 value");
        }
        return (int) (thi$ & 0xFFFFFFFFL);
    }

    /**
     * Native implementation of: "UInt toUInt(Boolean checkBounds = False)"
     *
     * This bridge is required because a primitive invocation bypasses the natural toUInt()
     * implementation that delegates to toUInt64().
     */
    public static long toUInt$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toUInt64$p(thi$, ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a Java long
     */
    public static long toUInt64$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt64 value as a UInt128
     */
    public static long toUInt128$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return thi$;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public static UIntN toUIntN$p(long thi$, Ctx ctx) {
        return UIntN.$box(new BigInteger(Long.toUnsignedString(thi$)));
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public IntN toIntN(Ctx ctx) {
        return toIntN$p($value, ctx);
    }

    /**
     * The native implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public UIntN toUIntN$p(Ctx ctx) {
        return toUIntN$p($value, ctx);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt64:" + Long.toUnsignedString($value);
    }
}
