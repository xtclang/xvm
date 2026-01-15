package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nConst;
import org.xvm.javajit.Ctx;
import org.xvm.runtime.template.numbers.LongLong;

/**
 * Native Int128 wrapper.
 */
public class Int128 extends nConst {
    /**
     * Construct an Ecstasy Int128 object.
     */
    Int128(long lowValue, long highValue) {
        super(null);
        $lowValue  = lowValue;
        $highValue = highValue;
    }

    public final long $lowValue;
    public final long $highValue;

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * Implementation of Int8 toInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as an Int8
     */
    public Int8 toInt8(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return Int8.$box(toInt8$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * Implementation of Int8 toInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Byte.MIN_VALUE || $lowValue > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid Int8 value");
        }
        return (byte) $lowValue;
    }

    /**
     * Implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public Int16 toInt16(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return Int16.$box(toInt16$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of Int16 toInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Short.MIN_VALUE || $lowValue > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid Int16 value");
        }
        return (short) $lowValue;
    }

    /**
     * Implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public Int32 toInt32(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return Int32.$box(toInt32$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of Int32 toInt32(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java long
     */
    public Int64 toInt64(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return Int64.$box(toInt64$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * Implementation of Int64 toInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid Int32 value");
        }
        return $lowValue;
    }

    /**
     * Implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as an Int128
     */
    public Int128 toInt128(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return this;
    }

    /**
     * The primitive implementation of Int128 toInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as an Int128
     */
    public Int128 toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return this;
    }

    /**
     * Implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public UInt8 toUInt8(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return UInt8.$box(toInt8$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of UInt8 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0L || $lowValue > 255L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid UInt8 value");
        }
        return (byte) $lowValue & 0xFF;
    }

    /**
     * Implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public UInt16 toUInt16(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return UInt16.$box(toInt16$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of UInt16 toUInt16(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0 || $lowValue > 65535L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid UInt16 value");
        }
        return (int) $lowValue & 0xFFFF;
    }

    /**
     * Implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public UInt32 toUInt32(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return UInt32.$box(toInt32$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of UInt32 toUInt8(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && ($lowValue < 0L || $lowValue > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid UInt32 value");
        }
        return (int) $lowValue;
    }

    /**
     * Implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java long
     */
    public UInt64 toUInt64(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return UInt64.$box(toUInt64$p(ctx, checkBounds, dfltCheckBounds));
    }

    /**
     * The primitive implementation of UInt64 toUInt64(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $highValue != -1
                && $lowValue < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid UInt64 value");
        }
        return $lowValue;
    }

    /**
     * Implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a UInt128
     */
    public UInt128 toUInt128(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return toUInt128$p(ctx, checkBounds, dfltCheckBounds);
    }

    /**
     * The primitive implementation of UInt128 toUInt128(Boolean checkBounds = False)
     *
     * @param ctx              the build context
     * @param checkBounds      the check bounds flag
     * @param dfltCheckBounds  if {@code true} ignore the checkBounds parameter and use the
     *                         default value (in this case False)
     *
     * @return this Int8 value as a UInt128
     */
    public UInt128 toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue < 0L) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid UInt128 value");
        }
        return new UInt128($lowValue, $highValue);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString() {
        return new LongLong($lowValue, $highValue).toString();
    }
}
