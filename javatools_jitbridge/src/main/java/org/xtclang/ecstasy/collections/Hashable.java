package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.nType;
import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.numbers.Int64;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * The native implementation of the {@code ecstasy.collections.Hashable} interface.
 */
@JitGenerated
public interface Hashable extends Object {
    /**
     * The native implementation of:
     * {@code static <CompileType extends Hashable> Int hashCode(CompileType value);}.
     */
    @JitName
    @JitFromNatural
    default Int64 hashCode(Ctx ctx, nType type, Hashable hashable) {
        // this method will be implemented by the JIT from natural code
        return null;
    }
}
