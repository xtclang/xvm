package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import java.math.MathContext;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Object;
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
    public String toString(Ctx ctx, boolean async) {
        return String.of(ctx, false, Double.toString($value));
    }

    public static String toString$p(double thi$, Ctx ctx, boolean async) {
        return String.of(ctx, false, Double.toString(thi$));
    }

    public static long estimateStringLength$p(double thi$, Ctx ctx, boolean async) {
        return Double.toString(thi$).length();
    }

    public static AppenderᐸCharᐳ appendTo$p(double thi$, Ctx ctx, boolean async,
            AppenderᐸCharᐳ appender) {
        for (char c : Double.toString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, false, c);
        }
        return appender;
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
     * <pre>{@code
     *     static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     * }</pre>
     */
    public static Ordered compare(Ctx ctx, boolean async, nType type, Orderable value1,
            Orderable value2) {
        double l1 = ((Float64) value1).$value;
        double l2 = ((Float64) value2).$value;
        return l1 < l2    ? Ordered.Lesser.$INSTANCE
                : l1 == l2 ? Ordered.Equal.$INSTANCE
                : Ordered.Greater.$INSTANCE;
    }

    /**
     * The primitive implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Orderable> Boolean equals(CompileType value1, CompileType value2);
     * }</pre>
     */
    public static Boolean equals(Ctx ctx, boolean async, nType type, Object value1, Object value2) {
        double l1 = ((Float64) value1).$value;
        double l2 = ((Float64) value2).$value;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }

    // ----- conversion ----------------------------------------------------------------------------

    public static int toInt8$FP$p(double thi$, Ctx ctx, boolean async,
                                  boolean checkBounds, boolean dfltCheckBounds,
                                  Rounding direction) {
        return $box(thi$).toInt8$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toInt16$FP$p(double thi$, Ctx ctx, boolean async,
                                   boolean checkBounds, boolean dfltCheckBounds,
                                   Rounding direction) {
        return $box(thi$).toInt16$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toInt32$FP$p(double thi$, Ctx ctx, boolean async,
                                   boolean checkBounds, boolean dfltCheckBounds,
                                   Rounding direction) {
        return $box(thi$).toInt32$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static long toInt64$FP$p(double thi$, Ctx ctx, boolean async,
                                    boolean checkBounds, boolean dfltCheckBounds,
                                    Rounding direction) {
        return $box(thi$).toInt64$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static long toInt128$FP$p(double thi$, Ctx ctx, boolean async,
                                     boolean checkBounds, boolean dfltCheckBounds,
                                     Rounding direction) {
        return $box(thi$).toInt128$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toNibble$FP$p(double thi$, Ctx ctx, boolean async,
                                    boolean checkBounds, boolean dfltCheckBounds,
                                   Rounding direction) {
        return $box(thi$).toNibble$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toUInt8$FP$p(double thi$, Ctx ctx, boolean async,
                                   boolean checkBounds, boolean dfltCheckBounds,
                                   Rounding direction) {
        return $box(thi$).toUInt8$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toUInt16$FP$p(double thi$, Ctx ctx, boolean async,
                                    boolean checkBounds, boolean dfltCheckBounds,
                                    Rounding direction) {
        return $box(thi$).toUInt16$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static int toUInt32$FP$p(double thi$, Ctx ctx, boolean async,
                                    boolean checkBounds, boolean dfltCheckBounds,
                                    Rounding direction) {
        return $box(thi$).toUInt32$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static long toUInt64$FP$p(double thi$, Ctx ctx, boolean async,
                                     boolean checkBounds, boolean dfltCheckBounds,
                                     Rounding direction) {
        return $box(thi$).toUInt64$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static long toUInt128$FP$p(double thi$, Ctx ctx, boolean async,
                                      boolean checkBounds, boolean dfltCheckBounds,
                                      Rounding direction) {
        return $box(thi$).toUInt128$FP$p(ctx, false, checkBounds, dfltCheckBounds, direction);
    }

    public static IntN toIntN$FP$p(double thi$, Ctx ctx, boolean async, Rounding direction) {
        return $box(thi$).toIntN$FP$p(ctx, false, direction);
    }

    public static UIntN toUIntN$FP$p(double thi$, Ctx ctx, boolean async, Rounding direction) {
        return $box(thi$).toUIntN$FP$p(ctx, false, direction);
    }

    public static int toDec32$p(double thi$, Ctx ctx, boolean async) {
        if (Double.isFinite(thi$)) {
            return Dec32.$toIntBits(ctx, false, $box(thi$).$toBigDecimal());
        }
        return Double.isInfinite(thi$)
                ? Double.doubleToRawLongBits(thi$) < 0 ? Dec32.$NEG_INFINITY : Dec32.$POS_INFINITY
                : Dec32.$NaN;
    }

    public static long toDec64$p(double thi$, Ctx ctx, boolean async) {
        if (Double.isFinite(thi$)) {
            return Dec64.$toLongBits(ctx, false, $box(thi$).$toBigDecimal());
        }
        return Double.isInfinite(thi$)
                ? Double.doubleToRawLongBits(thi$) < 0 ? Dec64.$NEG_INFINITY : Dec64.$POS_INFINITY
                : Dec64.$NaN;
    }

    public static long toDec128$p(double thi$, Ctx ctx, boolean async) {
        if (Double.isFinite(thi$)) {
            return Dec128.$toLongBits(ctx, false, $box(thi$).$toBigDecimal());
        }
        ctx.i0 = Double.isInfinite(thi$)
                ? Double.doubleToRawLongBits(thi$) < 0
                        ? Dec128.$NEG_INFINITY_HIGH
                        : Dec128.$POS_INFINITY_HIGH
                : Dec128.$NaN_HIGH;
        return 0L;
    }

    public static float toFloat32$p(double thi$, Ctx ctx, boolean async) {
        return (float) thi$;
    }

    public static double toFloat64$p(double thi$, Ctx ctx, boolean async) {
        return thi$;
    }
}
