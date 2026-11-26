package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Object;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * The native implementation of {@code ecstasy.collections.UniformIndexed}.
 */
@JitGenerated
public interface UniformIndexed extends Object {
    /**
     * The native implementation of
     * {@code @Op("[]") Element getElement(Index index);}.
     */
    @JitName
    @JitGenerated
    Object getElement(Ctx ctx, Object index);
}
