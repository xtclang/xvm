package org.xtclang.ecstasy.numbers;

/**
 * Native Dec16 wrapper.
 */
public class Dec16 extends DecimalFPNumber {
    /**
     * Construct an Ecstasy Dec16 object.
     *
     * @param value  the 16 bits of the decimal value.
     */
    private Dec16(int value) {
        $value = value;
    }

    public final int $value;

    public static Dec16 $box(int value) {
        return new Dec16(value);
    }
}
