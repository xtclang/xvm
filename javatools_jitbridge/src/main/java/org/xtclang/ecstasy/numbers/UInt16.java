package org.xtclang.ecstasy.numbers;

import java.math.BigDecimal;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt16 wrapper.
 */
public class UInt16 extends UIntNumber {
    /**
     * Construct an Ecstasy UInt16 object.
     *
     * @param value  the 16-bit unsigned integer value
     */
    private UInt16(int value) {
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
        return $estimateUnsignedStringLength(thi$ & 0xFFFFL);
    }

    /**
     * Obtain an UInt16 for a 16-bit "primitive" unsigned int (a Java "int" value).
     *
     * @param value  an 16-bit "primitive" unsigned int
     *
     * @return an UInt16 reference
     */
    public static UInt16 $box(int value) {
        return new UInt16(value & 0xFFFF);
    }

    @Override
    public BigDecimal $toBigDecimal() {
        return BigDecimal.valueOf($value);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt16:" + Integer.toUnsignedString($value);
    }
}
