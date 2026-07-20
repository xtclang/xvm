package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt8 (a.k.a. Byte) wrapper.
 */
public class UInt8 extends IntNumber {
    /**
     * Construct an Ecstasy UInt8 object.
     *
     * @param value  the 8-bit integer value
     */
    private UInt8(int value) {
        $value = value;
    }

    private static final UInt8[] CACHE = new UInt8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString(thi$));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return $estimateUnsignedStringLength(thi$);
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toUnsignedString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toUnsignedString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    /**
     * Obtain an Int8 for an 8-bit "primitive" int (a Java "int" value).
     *
     * @param value  an 8-bit "primitive" int
     *
     * @return an Int8 reference
     */
    public static UInt8 $box(int value) {
        UInt8 ref = CACHE[value = value & 0xFF];
        if (ref == null) {
            CACHE[value] = ref = new UInt8(value);
        }
        return ref;
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    @Override
    protected long[] $longValues() {
        return new long[]{(long) $value << 56};
    }

    @Override
    protected long bitLength$get$p() {
        return 8;
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
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toInt8$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt8 value " + thi$ + " is not a valid Int8 value");
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
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toInt16$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toInt32$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java long
     */
    public static long toInt64$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
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
     * @return this UInt8 value as an Int128
     */
    public static long toInt128$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return thi$ & 0xFFL;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toUInt8$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toUInt16$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java {@code int}
     */
    public static int toUInt32$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt8 value as a Java long
     */
    public static long toUInt64$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
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
     * @return this UInt8 value as a UInt128
     */
    public static long toUInt128$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return thi$ & 0xFFL;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt8:" + Integer.toUnsignedString($value);
    }
}
