package org.xtclang.ecstasy.numbers;

/**
 * TODO:
 */
public class BFloat16 extends BinaryFPNumber {
    private BFloat16() {}

    @Override
    protected long[] $longValues() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    protected long bitLength$get$p() {
        return 16;
    }
}
