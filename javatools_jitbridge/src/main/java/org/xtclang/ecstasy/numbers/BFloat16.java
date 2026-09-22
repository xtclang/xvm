package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.Orderable;
import org.xtclang.ecstasy.Ordered;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.BFloat16Constant;

import org.xvm.javajit.Ctx;

/**
 * Native BFloat16 wrapper.
 */
public class BFloat16 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy BFloat16 object.
     *
     * @param value  the 32-bit float value
     */
    private BFloat16(float value) {
        $value = $narrow(value);
    }

    public final float $value;

    public static BFloat16 $box(float value) {
        return new BFloat16(value);
    }

    public static float $narrow(float value) {
        return BFloat16Constant.toFloat(BFloat16Constant.toHalf(value));
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Float.toString($value));
    }

    public static String toString$p(float thi$, Ctx ctx) {
        return String.of(ctx, Float.toString(thi$));
    }

    public static long estimateStringLength$p(float thi$, Ctx ctx) {
        return Float.toString(thi$).length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return appendTo$p($value, ctx, appender);
    }

    public static AppenderᐸCharᐳ appendTo$p(float thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Float.toString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal($value);
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The primitive implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     * }</pre>
     */
    public static Ordered compare(Ctx ctx, nType type, Orderable value1, Orderable value2) {
        float l1 = ((BFloat16) value1).$value;
        float l2 = ((BFloat16) value2).$value;
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
    public static Boolean equals(Ctx ctx, nType type, Object value1, Object value2) {
        float l1 = ((BFloat16) value1).$value;
        float l2 = ((BFloat16) value2).$value;
        return l1 == l2 ? Boolean.TRUE : Boolean.FALSE;
    }
    // ----- conversion ----------------------------------------------------------------------------

    public static float toFloat32$p(float thi$, Ctx ctx) {
        return thi$;
    }

    public static double toFloat64$p(float thi$, Ctx ctx) {
        return thi$;
    }
}
