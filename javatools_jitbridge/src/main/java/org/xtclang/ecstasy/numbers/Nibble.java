package org.xtclang.ecstasy.numbers;

import org.bouncycastle.util.Exceptions;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Nibble wrapper.
 */
public class Nibble extends UIntNumber {
    /**
     * Construct an Ecstasy Nibble object.
     *
     * @param value  the 4-bit value (0-15)
     */
    private Nibble(int value) {
        $value = value;
    }

    private static final Nibble[] CACHE = new Nibble[16];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString(thi$));
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

    /**
     * The primitive implementation of:
     * <pre>
     *     static Nibble of(Int n)
     * </pre>
     */
    public static int of$p(Ctx ctx, long n) {
        if (n < 0 || n > 15) {
            throw Exceptions.illegalArgumentException("Int value " + n + " must be in the range 0..15", null);
        }
        return (int) n;
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     static Nibble of(Char ch)
     * </pre>
     */
    public static int of$1$p(Ctx ctx, int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 0xA;
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 0xA;
        }
        java.lang.String msg = "Illegal character \"" + ch + "; the character value must be in " +
                               "the range \"0..9\", \"A..F\", or \"a..f\"";
        throw Exceptions.illegalArgumentException(msg, null);
    }

    @Override
    protected long[] $longValues() {
        return new long[]{(long) $value << 60};
    }

    @Override
    protected long bitLength$get$p() {
        return 4;
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
