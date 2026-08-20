package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.OutOfBounds;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int8 wrapper.
 */
public class Int8 extends IntNumber {
    /**
     * Construct an Ecstasy Int8 object.
     *
     * @param value  the 8-bit signed integer value
     */
    private Int8(int value) {
        $value = value;
    }

    private static final Int8[] CACHE = new Int8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toString(thi$));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return Integer.toString(thi$).length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
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

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    @Override
    protected long[] $longValues() {
        return new long[]{(long) $value << 56};
    }

    @Override
    protected long bitLength$get$p() {
        return 8;
    }

    // ----- conversion ----------------------------------------------------------------------------

    /**
     * The primitive implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public static IntN toIntN$p(int thi$, Ctx ctx) {
        return IntN.$box(thi$);
    }

    /**
     * The primitive implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public static UIntN toUIntN$p(int thi$, Ctx ctx) {
        if (thi$ < 0) {
            OutOfBounds oob = new OutOfBounds(ctx);
            throw oob.$init(ctx, "Int8 value " + thi$ + " is not a valid UIntN value");
        }
        return UIntN.$box(((long) thi$) & 0xFFL);
    }

    /**
     * The native implementation of:
     * <pre>
     *     IntN toIntN()
     * </pre>
     */
    public IntN toIntN(Ctx ctx) {
        return toIntN$p($value, ctx);
    }

    /**
     * The native implementation of:
     * <pre>
     *     UIntN toUIntN()
     * </pre>
     */
    public UIntN toUIntN$p(Ctx ctx) {
        return toUIntN$p($value, ctx);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int8:" + $value;
    }
}
