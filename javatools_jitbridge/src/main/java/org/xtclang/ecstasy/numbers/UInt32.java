package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt32 wrapper.
 */
public class UInt32 extends IntNumber {
    /**
     * Construct an Ecstasy UInt32 object.
     *
     * @param value  the 32-bit unsigned integer value
     */
    private UInt32(int value) {
        $value = value;
    }

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    /**
     * Obtain a UInt32 for a "primitive" int (a Java "int" value).
     *
     * @param value  an int value
     *
     * @return a UInt32 reference
     */
    public static UInt32 $box(int value) {
        return new UInt32(value);
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
     * @return this UInt32 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && ($value < 0 || $value > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + $value + " is not a valid Int8 value");
        }
        return (byte) $value;
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && ($value < 0 || $value > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + $value + " is not a valid Int16 value");
        }
        return (short) $value;
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + $value + " is not a valid Int32 value");
        }
        return $value;
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return ((long) $value) & 0xFFFFFFFFL;
    }

    /**
     * The primitive implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as an Int128
     */
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return ((long) $value) & 0xFFFFFFFFL;
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && ($value < 0 || $value > 255)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + $value + " is not a valid UInt8 value");
        }
        return $value & 0xFF;
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && ($value < 0 || $value > 65535)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt32 value " + $value + " is not a valid UInt16 value");
        }
        return $value & 0xFFFF;
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return $value;
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return $value & 0xFFFFFFFFL;
    }

    /**
     * The primitive implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this UInt32 value as a UInt128
     */
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        // load the high long value to the context and return the low value
        ctx.i0 = 0L;
        return $value & 0xFFFFFFFFL;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt32:" + Integer.toUnsignedString($value);
    }
}
