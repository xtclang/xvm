package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.text.String;
import org.xtclang.ecstasy.xConst;

import org.xvm.javajit.Ctx;

/**
 * Native UInt64 wrapper.
 */
public class UInt64 extends xConst {
    /**
     * Construct an Ecstasy UInt64 object.
     *
     * @param value  the 64-bit unsigned long value
     */
    private UInt64(long value) {
        super(null);
        $value = value;
    }

    public final long $value;

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, Long.toUnsignedString($value));
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

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt64:" + Long.toUnsignedString($value);
    }
}
