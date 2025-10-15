package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.text.String;
import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

/**
 * Native Int16 wrapper.
 */
public class Int16 extends xConst {
    /**
     * Construct an Ecstasy Int16 object.
     *
     * @param value  the 16-bit signed integer value
     */
    private Int16(int value) {
        super(null);
        $value = value;
    }

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toString($value));
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int16:" + $value;
    }
}
