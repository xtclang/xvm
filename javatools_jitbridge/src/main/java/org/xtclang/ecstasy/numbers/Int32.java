package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native Int32 wrapper.
 */
public class Int32 extends IntNumber {
    /**
     * Construct an Ecstasy Int32 object.
     *
     * @param value  the 32-bit signed integer value
     */
    private Int32(int value) {
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

    /**
     * Obtain an Int32 for a "primitive" int (a Java "int" value).
     *
     * @param value  an int value
     *
     * @return an Int32 reference
     */
    public static Int32 $box(int value) {
        return new Int32(value);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int32:" + $value;
    }
}
