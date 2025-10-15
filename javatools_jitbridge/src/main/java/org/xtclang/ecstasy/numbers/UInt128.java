package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.xConst;

import org.xvm.runtime.template.numbers.LongLong;

/**
 * Native UInt128 wrapper.
 */
public class UInt128 extends xConst {
    /**
     * Construct an Ecstasy UInt128 object.
     */
    private UInt128(long lowValue, long highValue) {
        super(null);
        $lowValue  = lowValue;
        $highValue = highValue;
    }

    public final long $lowValue;
    public final long $highValue;

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return "UInt128:" + new LongLong($lowValue, $highValue);
    }
}
