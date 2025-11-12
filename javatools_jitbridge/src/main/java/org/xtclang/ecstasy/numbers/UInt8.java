package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.nConst;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native UInt8 (a.k.a. Byte) wrapper.
 */
public class UInt8 extends nConst {
    /**
     * Construct an Ecstasy UInt8 object.
     *
     * @param value  the 8-bit integer value
     */
    private UInt8(int value) {
        super(null);
        $value = value;
    }

    private static final UInt8[] CACHE = new UInt8[256];

    public final int $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Integer.toUnsignedString($value));
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

    public UInt128 toUInt128$p(Ctx ctx, boolean checkBounds, boolean $checkBounds) {
        throw new UnsupportedOperationException();
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt8:" + Integer.toUnsignedString($value);
    }
}
