package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.nConst;
import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int8 wrapper.
 */
public class Int8 extends nConst {
    /**
     * Construct an Ecstasy Int8 object.
     *
     * @param value  the 8-bit signed integer value
     */
    private Int8(int value) {
        super(null);
        $value = value;
    }

    private static final Int8[] CACHE = new Int8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toString($value));
    }

    /**
     * Obtain an Int8 for an 8-bit "primitive" signed int (a Java "int" value).
     *
     * @param value  an 8-bit "primitive" signed int
     *
     * @return an Int8 reference
     */
    public static Int8 $box(int value) {
        int  key = 128 + (value = (byte) value);
        Int8 ref = CACHE[key];
        if (ref == null) {
            CACHE[key] = ref = new Int8(value);
        }
        return ref;
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return $value;
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
        return $value;
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
     * @return this Int8 value as a Java long
     */
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        return $value;
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
        return new Int128($value, $value < 0 ? -1L : 0L);
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
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + $value + " is not a valid UInt8 value");
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + $value + " is not a valid UInt16 value");
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
     * @return this Int8 value as a Java {@code int}
     */
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + $value + " is not a valid UInt32 value");
        }
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
     * @return this Int8 value as a Java long
     */
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds) {
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + $value + " is not a valid UInt64 value");
        }
        return $value;
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
        if (!dfltCheckBounds && checkBounds && $value < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + $value + " is not a valid UInt128 value");
        }
        return new UInt128($value, $value >= 0 ? 0L : -1L);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int8:" + $value;
    }
}
