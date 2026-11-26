package org.xtclang.ecstasy;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation for "ecstasy.Sequential".
 */
@JitGenerated
public interface Sequential extends Orderable {
    @JitName
    @JitFromNatural
    default Boolean next(Ctx ctx) {
        return null;
    }

    @JitName
    @JitFromNatural
    default Boolean prev(Ctx ctx) {
        return null;
    }

    @JitName
    @JitFromNatural
    default Sequential skip(Ctx ctx, Int64 steps) {
        return null;
    }

    @JitName
    @JitFromNatural
    default Int64 stepsTo(Ctx ctx, Sequential other) {
        return null;
    }
}
