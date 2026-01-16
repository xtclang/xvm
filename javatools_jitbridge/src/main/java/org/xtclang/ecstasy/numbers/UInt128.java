package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.OutOfBounds;
import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;
import org.xvm.runtime.template.numbers.LongLong;

/**
 * Native UInt128 wrapper.
 */
public class UInt128 extends nConst {
    /**
     * Construct an Ecstasy UInt128 object.
     */
    UInt128(long lowValue, long highValue) {
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 
                && ($lowValue < Byte.MIN_VALUE || $lowValue > Byte.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < Short.MIN_VALUE || $lowValue > Short.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < Integer.MIN_VALUE || $lowValue > Integer.MAX_VALUE)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $lowValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as an Int128
     */
    public Int128 toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
                    + " is not a valid Int128 value");
        }
        return new Int128($lowValue, $highValue);
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
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < 0 || $lowValue > 255)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0
                && ($lowValue < 0 || $lowValue > 65535)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 
                && ($lowValue < 0 || $lowValue > 4294967295L)) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $highValue != 0 && $lowValue < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "UInt128 value " + new LongLong($lowValue, $highValue)
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
     * @return this Int8 value as a UInt128
     */
    public UInt128 toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return this;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return new LongLong($lowValue, $highValue).toString();
    }
}
