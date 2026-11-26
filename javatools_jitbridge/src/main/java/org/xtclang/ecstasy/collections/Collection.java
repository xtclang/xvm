package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Object;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * The native implementation of {@code ecstasy.collections.Collection}.
 */
@JitGenerated
public interface Collection extends Object {
    /**
     * Native implementation of:
     *
     * <pre>{@code
     *     Collection! reify()
     * }</pre>
     */
    @JitName
    @JitFromNatural
    Collection reify(Ctx ctx);
}
