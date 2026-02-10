package org.xtclang.ecstasy.numbers;

/**
 * Native Float32 wrapper.
 */
public class Float32 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float32 object.
     *
     * @param value  the 32-bit float value
     */
    private Float32(float value) {
        $value = value;
    }

    public final float $value;

    public static Float32 $box(float value) {
        return new Float32(value);
    }
}
