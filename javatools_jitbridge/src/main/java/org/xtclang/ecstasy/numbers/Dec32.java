package org.xtclang.ecstasy.numbers;

/**
 * Native Dec32 wrapper.
 */
public class Dec32 extends DecimalFPNumber {
    /**
     * Construct an Ecstasy Dec32 object.
     *
     * @param value  the 32 bits of the decimal value.
     */
    private Dec32(int value) {
        $value = value;
    }

    public final int $value;

    public static Dec32 $box(int value) {
        return new Dec32(value);
    }
}
