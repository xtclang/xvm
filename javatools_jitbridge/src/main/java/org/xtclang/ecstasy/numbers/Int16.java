package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int16 wrapper.
 */
public class Int16 extends IntNumber {
    /**
     * Construct an Ecstasy Int16 object.
     *
     * @param value  the 16-bit signed integer value
     */
    private Int16(int value) {
        $value = value;
    }

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
     * Obtain an Int16 for a 16-bit "primitive" int (a Java "int" value).
     *
     * @param value  a 16-bit "primitive" signed int
     *
     * @return an Int16 reference
     */
    public static Int16 $box(int value) {
        return new Int16((short) value);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int16:" + $value;
    }
}
