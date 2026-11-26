package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native interface for {@code ecstasy.Enum}; its methods are added by JIT augmentation.
 */
@JitGenerated
public interface Enum extends Object {

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
}
