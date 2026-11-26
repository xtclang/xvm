package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native interface for {@code ecstasy.Freezable}.
 */
@JitGenerated
public interface Freezable extends Object {

    @JitName
    @JitGenerated
    Freezable freeze(Ctx ctx, Boolean inPlace);
}
