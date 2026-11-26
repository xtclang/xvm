package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Object;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * A readable/writable reference in Ecstasy.
 */
@JitGenerated
public interface Var extends Ref {
    /**
     * @param referent  the referent to store in this reference
     */
    @JitName
    void set(Ctx ctx, Object referent);
}
