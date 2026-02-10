package org.xtclang.ecstasy.numbers;

/**
 * Native Dec64 wrapper.
 */
public class Dec64 extends DecimalFPNumber {
    /**
     * Construct an Ecstasy Dec64 object.
     *
     * @param value  the 64 bits of the decimal value.
     */
    private Dec64(long value) {
        $value = value;
    }

    public final long $value;

    public static Dec64 $box(long value) {
        return new Dec64(value);
    }
}
