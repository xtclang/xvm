package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xvm.javajit.Ctx;

/**
 * Native FPConvertible wrapper.
 */
public interface FPConvertible {
    /**
     * @return true iff the value is neither an infinity nor a NaN
     */
    default boolean $isInfinite() {
        return false;
    }

    /**
     * @return true iff the value is a NaN value, regardless of sign
     */
    default boolean $isNaN() {
        return false;
    }

    /**
     * @return true iff the sign bit is set
     */
    default boolean $isSigned() {
        return false;
    }

    /**
     * Implementation of the method
     * <pre>
     * toDec32()
     * </pre>
     *
     * @param ctx  the build context
     *
     * @return this FPConvertible value converted to an Int8 value inside a Java {@code int}
     */
    default int toDec32$p(Ctx ctx) {
        if ($isInfinite()) {
            return $isSigned() ? Dec32.$NEG_INFINITY : Dec32.$POS_INFINITY;
        }
        if ($isNaN()) {
            return Dec32.$NaN;
        }
        return Dec32.$toIntBits(ctx, $toBigDecimal());
    }

    /**
     * Implementation of the method
     * <pre>
     * toDec64()
     * </pre>
     *
     * @param ctx the build context
     *
     * @return this FPConvertible value converted to an Int8 value inside a Java {@code int}
     */
    default long toDec64$p(Ctx ctx) {
        if ($isInfinite()) {
            return $isSigned() ? Dec64.$NEG_INFINITY : Dec64.$POS_INFINITY;
        }
        if ($isNaN()) {
            return Dec64.$NaN;
        }
        return Dec64.$toLongBits(ctx, $toBigDecimal());
    }

    /**
     * Implementation of the method
     * <pre>
     * toDec128()
     * </pre>
     * The high 64-bits of the Dec128 will be set into the {@link Ctx#i0} field, and the low 64-bits
     * will be returned.
     *
     * @param ctx the build context
     *
     * @return this FPConvertible value converted to an Int8 value inside a Java {@code int}
     */
    default long toDec128$p(Ctx ctx) {
        if ($isInfinite()) {
            ctx.i0 = $isSigned() ? Dec128.$NEG_INFINITY_HIGH : Dec128.$POS_INFINITY_HIGH;
            return 0L;
        }
        if ($isNaN()) {
            ctx.i0 = Dec128.$NaN_HIGH;
            return 0L;
        }
        return Dec128.$toLongBits(ctx, $toBigDecimal());
    }

    /**
     * Implementation of the method
     * <pre>
     * toFloat32()
     * </pre>
     *
     * @param ctx the build context
     *
     * @return this FPConvertible value converted to a Float32 value inside a Java {@code float}
     */
    default float toFloat32$p(Ctx ctx) {
        return $toBigDecimal().floatValue();
    }

    /**
     * Implementation of the method
     * <pre>
     * toFloat64()
     * </pre>
     *
     * @param ctx the build context
     *
     * @return this FPConvertible value converted to an Float64 value inside a Java {@code double}
     */
    default double toFloat64$p(Ctx ctx) {
        return $toBigDecimal().doubleValue();
    }

    /**
     * Obtain the decimal value as a Java BigDecimal, iff the decimal is finite.
     *
     * @return a BigDecimal, or null if the decimal is not finite
     */
    default BigDecimal $toBigDecimal() {
        throw new UnsupportedOperationException();
    }
}
