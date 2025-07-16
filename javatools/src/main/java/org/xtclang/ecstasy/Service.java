package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Service`.
 */
public interface Service extends Object {

    /**
     * {@code Boolean hasFutureArrived()}
     */
    boolean hasFutureArrived$p(Ctx ctx);

    /**
     * {@code void callLater(function void doLater())}
     */
    void callLater(Ctx ctx, xFunction.$0 doLater);
}
