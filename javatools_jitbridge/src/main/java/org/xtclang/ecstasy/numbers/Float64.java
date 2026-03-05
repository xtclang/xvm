package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.MathContext;

import org.xtclang.ecstasy.Comparable;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Float64 wrapper.
 */
public class Float64 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float64 object.
     *
     * @param value  the 64-bit double value
     */
    private Float64(double value) {
        $value = value;
    }

    public final double $value;

    public static Float64 $box(double value) {
        return new Float64(value);
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Double.toString($value));
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal(Double.toString($value), MathContext.DECIMAL64);
    }

    @Override
    public boolean $isInfinite() {
        return Double.isInfinite($value);
    }

    @Override
    public boolean $isNaN() {
        return Double.isNaN($value);
    }

    @Override
    public boolean $isSigned() {
        return Double.doubleToRawLongBits($value) < 0L;
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     *
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        double l1 = ((Float64) value1).$value;
        double l2 = ((Float64) value2).$value;
        return l1 < l2    ? Ordered.Lesser.$INSTANCE
                : l1 == l2 ? Ordered.Equal.$INSTANCE
                : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     *
     *  static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     */
    public static Boolean equals(Ctx ctx, nType type, Comparable value1, Comparable value2) {
        double l1 = ((Float64) value1).$value;
        double l2 = ((Float64) value2).$value;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- conversion ----------------------------------------------------------------------------

    @Override
    public float toFloat32$p(Ctx ctx) {
        return super.toFloat32$p(ctx);
    }

    @Override
    public double toFloat64$p(Ctx ctx) {
        return $value;
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toDec32$p(Ctx ctx) {
        return super.toDec32$p(ctx);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toDec64$p(Ctx ctx) {
        return super.toDec64$p(ctx);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toDec128$p(Ctx ctx) {
        return super.toDec128$p(ctx);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toInt8$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds,
                         Rounding direction) {
        return super.toInt16$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds,
                         Rounding direction) {
        return super.toInt32$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds,
                         Rounding direction) {
        return super.toInt64$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toInt128$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toUInt8$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toUInt8$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toUInt16$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toUInt16$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toUInt32$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toUInt32$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toUInt64$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toUInt64$p(ctx, checkBounds, dfltCheckBounds, direction);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toUInt128$p(Ctx ctx, boolean checkBounds, boolean dfltCheckBounds, Rounding direction) {
        return super.toUInt128$p(ctx, checkBounds, dfltCheckBounds, direction);
    }
}
