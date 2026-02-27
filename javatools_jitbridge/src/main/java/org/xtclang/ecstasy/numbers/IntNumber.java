package org.xtclang.ecstasy.numbers;

import org.xvm.javajit.Ctx;

/**
 * Native IntNumber wrapper.
 */
public abstract class IntNumber extends Number {
    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public int toDec32$p(Ctx ctx) {
        return super.toDec32$p(ctx);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toDec64$p(Ctx ctx) {
        return super.toDec64$p(ctx);
    }

    // we must override the method here otherwise the JIT will add its own implementation
    @Override
    public long toDec128$p(Ctx ctx) {
        return super.toDec128$p(ctx);
    }
}
