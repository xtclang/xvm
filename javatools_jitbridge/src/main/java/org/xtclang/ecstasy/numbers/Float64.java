package org.xtclang.ecstasy.numbers;

/**
 * Native Float64 wrapper.
 */
public class Float64 extends BinaryFPNumber {
    /**
     * Construct an Ecstasy Float64 object.
     *
     * @param value  the 64-bit double value
     */
    private Float64(double value) {
        $value = value;
    }

    public final double $value;

    public static Float64 $box(double value) {
        return new Float64(value);
    }
}
