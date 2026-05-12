package org.xtclang.ecstasy.numbers;

/**
 * TODO:
 */
public class Float8e4 extends BinaryFPNumber {
    private Float8e4() {}

    @Override
    protected long[] $longValues() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    protected long bitLength$get$p() {
        return 8;
    }
}
