package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Sequential;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native IntNumber wrapper.
 */
@JitGenerated
public abstract class IntNumber
        extends Number
        implements Sequential {

    @JitName
    @JitFromNatural
    public IntNumber add(Ctx ctx, IntNumber that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber and(Ctx ctx, IntNumber that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber not(Ctx ctx) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber or(Ctx ctx, IntNumber that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber nextValue(Ctx ctx) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber prevValue(Ctx ctx) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber shiftAllRight(Ctx ctx, Int64 count) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber shiftLeft(Ctx ctx, Int64 count) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber shiftRight(Ctx ctx, Int64 count) {
        return null;
    }

    @JitName
    @JitFromNatural
    public Sequential skip$p(Ctx ctx, long steps) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber skip(Ctx ctx, Int64 steps) {
        return null;
    }

    @JitName
    @JitFromNatural
    public Int64 stepsTo(Ctx ctx, Sequential that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public Int64 stepsTo(Ctx ctx, IntNumber that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber sub(Ctx ctx, IntNumber that) {
        return null;
    }

    @JitName
    @JitFromNatural
    public IntNumber xor(Ctx ctx, IntNumber that) {
        return null;
    }
}
