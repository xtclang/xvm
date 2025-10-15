package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.text.String;
import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

/**
 * Native Int8 wrapper.
 */
public class Int8 extends xConst {
    /**
     * Construct an Ecstasy Int8 object.
     *
     * @param value  the 8-bit signed integer value
     */
    private Int8(int value) {
        super(null);
        $value = value;
    }

    private static final Int8[] CACHE = new Int8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toString($value));
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "Int8:" + $value;
    }
}
