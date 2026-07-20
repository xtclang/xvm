package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int64 wrapper.
 */
public class Int64 extends IntNumber {
    /**
     * Construct an Ecstasy Int64 object.
     *
     * @param value  the 64-bit signed integer value
     */
    private Int64(long value) {
        $value = value;
    }

    public final long $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Long.toString($value));
    }

    public static String toString$p(long thi$, Ctx ctx) {
        return String.of(ctx, Long.toString(thi$));
    }

    public static long estimateStringLength$p(long thi$, Ctx ctx) {
        return Long.toString(thi$).length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(long thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    private static final int     SMALL_CACHE_OFFSET = 512;  // number of cached negative values
    private static final int     SMALL_CACHE_SIZE   = 8192; // must be power of 2
    private static final Int64[] SMALL_CACHE        = new Int64[SMALL_CACHE_SIZE];

    public static final Int64 ZERO    = $box(0);
    public static final Int64 ONE     = $box(1);
    public static final Int64 NEG_ONE = $box(-1);
    public static final Int64 MIN     = $box(Long.MIN_VALUE);
    public static final Int64 MAX     = $box(Long.MAX_VALUE);

    /**
     * Obtain an Int64 for a 64-bit "primitive" int (a Java "long" value).
     *
     * @param value  a 64-bit "primitive" int
     *
     * @return an Int64 reference
     */
    public static Int64 $box(long value) {
        long key = value + SMALL_CACHE_OFFSET;
        if ((key & -SMALL_CACHE_SIZE) == 0) {
            Int64 ref = SMALL_CACHE[(int) key];
            if (ref == null) {
                SMALL_CACHE[(int) key] = ref = new Int64(value);
            }
            return ref;
        }
        // TODO consider using a cache on the context (to avoid CPU cache line collisions)
        return new Int64(value);
    }

    /**
     * Native implementation of:
     *
     *   construct(String text)
     */
    public static Int64 $new(Ctx ctx, String text) {
        return $box(Long.parseLong(text.toString()));
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    @Override
    protected long[] $longValues() {
        return new long[]{$value};
    }

    @Override
    protected long bitLength$get$p() {
        return 64;
    }

    /**
     * Int64 add(Int64! n)
     */
    public static long addꖛ0$p(long thi$, Ctx ctx, long n) {
        return thi$ + n;
    }

    /**
     * Int64 mul(Int64! n)
     */
    public static long mul$p(long thi$, Ctx ctx, long n) {
        return thi$*n;
    }

    // ----- primitive helpers ---------------------------------------------------------------------

    public static long $next(Ctx ctx, long n) {
        if (n == Long.MAX_VALUE) {
            throw Exception.$oob(ctx, "64-bit max value exceeded");
        }
        return n + 1;
    }

    public static long $prev(Ctx ctx, long n) {
        if (n == Long.MIN_VALUE) {
            throw Exception.$oob(ctx, "64-bit min value exceeded");
        }
        return n - 1;
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
     * @return this Int64 value as a Java {@code int}
     */
    public static int toInt8$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds
                && (thi$ < Byte.MIN_VALUE || thi$ > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid Int8 value");
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
     * @return this Int64 value as a Java {@code int}
     */
    public static int toInt16$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds
                && (thi$ < Short.MIN_VALUE || thi$ > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid Int16 value");
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
     * @return this Int64 value as a Java {@code int}
     */
    public static int toInt32$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds
                && (thi$ < Integer.MIN_VALUE || thi$ > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid Int32 value");
        }
        return (int) thi$;
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int64 value as a Java long
     */
    public static long toInt64$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
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
     * @return this Int64 value as an Int128
     */
    public static long toInt128$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = thi$ < 0 ? -1L : 0L;
        return thi$;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int64 value as a Java {@code int}
     */
    public static int toUInt8$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0L || thi$ > 255L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid UInt8 value");
        }
        return ((int) thi$) & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int64 value as a Java {@code int}
     */
    public static int toUInt16$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0 || thi$ > 65535L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid UInt16 value");
        }
        return (int) (thi$ & 0xFFFFL);
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int64 value as a Java {@code int}
     */
    public static int toUInt32$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && (thi$ < 0L || thi$ > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid UInt32 value");
        }
        return (int) (thi$ & 0xFFFFFFFFL);
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int64 value as a Java long
     */
    public static long toUInt64$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$ < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid UInt64 value");
        }
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
     * @return this Int64 value as a UInt128
     */
    public static long toUInt128$p(long thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && thi$ < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int64 value " + thi$ + " is not a valid UInt128 value");
        }
        // load the high long value to the context and return the low value
        ctx.i0 = thi$ >= 0 ? 0L : -1L;
        return thi$;
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     *
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        long l1 = ((Int64) value1).$value;
        long l2 = ((Int64) value2).$value;
        return l1 < l2    ? Ordered.Lesser.$INSTANCE
               : l1 == l2 ? Ordered.Equal.$INSTANCE
                          : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     *
     *  static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     */
    public static Boolean equals(Ctx ctx, nType type, Object value1, Object value2) {
        long l1 = ((Int64) value1).$value;
        long l2 = ((Int64) value2).$value;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int64:" + $value;
    }
}
