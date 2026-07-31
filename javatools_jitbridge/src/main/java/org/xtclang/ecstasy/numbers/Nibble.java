package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.nConst;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Nibble wrapper.
 */
public class Nibble extends nConst {
    /**
     * Construct an Ecstasy Nibble object.
     *
     * @param value  the 4-bit value (0-15)
     */
    private Nibble(int value) {
        super(null);
        $value = value;
    }

    private static final Nibble[] CACHE = new Nibble[16];

    public final int $value;

    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString(thi$));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return (int) Math.log10(thi$) + 1;
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
     * Obtain a Nibble for a 4-bit "primitive" int (a Java "int" value).
     *
     * @param value  a 4-bit "primitive" int
     *
     * @return a Nibble reference
     */
    public static Nibble $box(int value) {
        Nibble ref = CACHE[value = value & 0xF];
        if (ref == null) {
            CACHE[value] = ref = new Nibble(value);
        }
        return ref;
    }

    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * The primitive implementation of Nibble toNibble()
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java {@code int}
     */
    public static int toNibble$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of Int8 toInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java {@code int}
     */
    public static int toInt8$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java {@code int}
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
     * @return this Nibble value as a Java {@code int}
     */
    public static int toInt32$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java long
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
     * @return this Nibble value as an Int128
     */
    public static long toInt128$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        ctx.i0 = 0L;
        return thi$ & 0xFL;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java {@code int}
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
     * @return this Nibble value as a Java {@code int}
     */
    public static int toUInt16$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return thi$;
    }

    /**
     * The primitive implementation of UInt32 toUInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Nibble value as a Java {@code int}
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
     * @return this Nibble value as a Java long
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
     * @return this Nibble value as a UInt128
     */
    public static long toUInt128$p(int thi$, Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        ctx.i0 = 0L;
        return thi$ & 0xFL;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Nibble:" + Integer.toUnsignedString($value);
    }
}
