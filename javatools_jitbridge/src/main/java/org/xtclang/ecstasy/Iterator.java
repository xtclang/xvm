package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.Iterator`.
 */
@JitGenerated
public interface Iterator extends Object {

    nType Element$get(Ctx ctx);

    @JitName
    boolean next$p(Ctx ctx);
}
