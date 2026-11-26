package org.xtclang.ecstasy;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation for "ecstasy.Sequential".
 */
@JitGenerated
public interface Sequential extends Orderable {
    @JitName
    @JitGenerated
    default Boolean next(Ctx ctx) {
        return null;
    }

    @JitName
    @JitGenerated
    default Boolean prev(Ctx ctx) {
        return null;
    }

    @JitName
    @JitGenerated
    default Sequential skip(Ctx ctx, Int64 steps) {
        return null;
    }

    @JitName
    @JitGenerated
    default Int64 stepsTo(Ctx ctx, Sequential other) {
        return null;
    }
}
