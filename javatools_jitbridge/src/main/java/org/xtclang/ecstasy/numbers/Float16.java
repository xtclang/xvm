package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Float16 wrapper.
 */
public class Float16 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float16 object.
     *
     * @param value  the 32-bit float value
     */
    private Float16(float value) {
        $value = value;
    }

    public final float $value;

    public static Float16 $box(float value) {
        return new Float16(value);
    }

    @Override
    public org.xtclang.ecstasy.text.String toString(Ctx ctx) {
        return String.of(ctx, Float.toString($value));
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal($value);
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     *
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        float l1 = ((Float16) value1).$value;
        float l2 = ((Float16) value2).$value;
        return l1 < l2    ? Ordered.Lesser.$INSTANCE
                : l1 == l2 ? Ordered.Equal.$INSTANCE
                : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     *
     *  static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     */
    public static Boolean equals(Ctx ctx, nType type, org.xtclang.ecstasy.Comparable value1, org.xtclang.ecstasy.Comparable value2) {
        float l1 = ((Float16) value1).$value;
        float l2 = ((Float16) value2).$value;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }
    // ----- conversion ----------------------------------------------------------------------------

    @Override
    public float toFloat32$p(Ctx ctx) {
        return (float) $value;
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
