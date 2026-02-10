package org.xtclang.ecstasy.numbers;

/**
 * Native Float16 wrapper.
 */
public class Float16 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float16 object.
     *
     * @param value  the 32-bit float value
     */
    private Float16(float value) {
        $value = value;
    }

    public final float $value;

    public static Float16 $box(float value) {
        return new Float16(value);
    }
}
