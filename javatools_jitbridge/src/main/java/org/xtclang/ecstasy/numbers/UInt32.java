package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt32 wrapper.
 */
public class UInt32 extends UIntNumber {
    /**
     * Construct an Ecstasy UInt32 object.
     *
     * @param value  the 32-bit unsigned integer value
     */
    private UInt32(int value) {
        $value = value;
    }

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
     * Obtain a UInt32 for a "primitive" int (a Java "int" value).
     *
     * @param value  an int value
     *
     * @return a UInt32 reference
     */
    public static UInt32 $box(int value) {
        return new UInt32(value);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt32:" + Integer.toUnsignedString($value);
    }
}
