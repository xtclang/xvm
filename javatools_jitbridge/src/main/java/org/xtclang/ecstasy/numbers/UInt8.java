package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt8 (a.k.a. Byte) wrapper.
 */
public class UInt8 extends UIntNumber {
    /**
     * Construct an Ecstasy UInt8 object.
     *
     * @param value  the 8-bit integer value
     */
    private UInt8(int value) {
        $value = value;
    }

    private static final UInt8[] CACHE = new UInt8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
    }

    public static String toString$p(int thi$, Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString(thi$));
    }

    public static long estimateStringLength$p(int thi$, Ctx ctx) {
        return $estimateUnsignedStringLength(thi$);
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toUnsignedString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(int thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Integer.toUnsignedString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    /**
     * Obtain an Int8 for an 8-bit "primitive" int (a Java "int" value).
     *
     * @param value  an 8-bit "primitive" int
     *
     * @return an Int8 reference
     */
    public static UInt8 $box(int value) {
        UInt8 ref = CACHE[value = value & 0xFF];
        if (ref == null) {
            CACHE[value] = ref = new UInt8(value);
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt8:" + Integer.toUnsignedString($value);
    }
}
