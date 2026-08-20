package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;
import org.xtclang.ecstasy.AppenderᐸCharᐳ;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt64 wrapper.
 */
public class UInt64 extends UIntNumber {
    /**
     * Construct an Ecstasy UInt64 object.
     *
     * @param value  the 64-bit unsigned long value
     */
    private UInt64(long value) {
        $value = value;
    }

    public final long $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Long.toUnsignedString($value));
    }

    public static String toString$p(long thi$, Ctx ctx) {
        return String.of(ctx, Long.toUnsignedString(thi$));
    }

    public static long estimateStringLength$p(long thi$, Ctx ctx) {
        return $estimateUnsignedStringLength(thi$);
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toUnsignedString($value).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    public static AppenderᐸCharᐳ appendTo$p(long thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        for (char c : Long.toUnsignedString(thi$).toCharArray()) {
            appender = appender.add$p(ctx, c);
        }
        return appender;
    }

    /**
     * Obtain a UInt64 for a 64-bit "primitive" long.
     *
     * @param value  a 64-bit "primitive" long
     *
     * @return an UInt64 reference
     */
    public static UInt64 $box(long value) {
        return new UInt64(value);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return new BigDecimal(Long.toUnsignedString($value));
    }

    @Override
    protected long[] $longValues() {
        return new long[]{$value};
    }

    @Override
    protected long bitLength$get$p() {
        return 64;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt64:" + Long.toUnsignedString($value);
    }
}
