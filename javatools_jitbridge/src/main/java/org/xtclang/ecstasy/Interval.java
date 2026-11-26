package org.xtclang.ecstasy;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native interface for {@code ecstasy.Interval}.
 */
@JitGenerated
public interface Interval extends Object {
    /**
     * The native implementation of:
     * {@code @Op("[]") Element getElement(Int index)}.
     */
    @JitName
    @JitGenerated
    default Sequential getElement(Ctx ctx, Int64 index) {
        return null;
    }
}
