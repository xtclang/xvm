package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.text.String;
import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

/**
 * Native UInt32 wrapper.
 */
public class UInt32 extends xConst {
    /**
     * Construct an Ecstasy UInt32 object.
     *
     * @param value  the 32-bit unsigned integer value
     */
    private UInt32(int value) {
        super(null);
        $value = value;
    }

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt32:" + Integer.toUnsignedString($value);
    }
}
