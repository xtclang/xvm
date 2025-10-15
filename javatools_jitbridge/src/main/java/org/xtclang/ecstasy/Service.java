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

    /**
     * {@code <Value> SharedContext<Value>.Token? findContextToken(SharedContext<Value> ctx)}
     */
    xObj findContextToken(Ctx ctx, xType t$Value, SharedContext sharedContext);

    /**
     * {@code void registerContextToken(SharedContext.Token token)}
     */
    void registerContextToken(Ctx ctx, SharedContext.Token token);

    /**
     * {@code void unregisterContextToken(SharedContext.Token token)}
     */
    void unregisterContextToken(Ctx ctx, SharedContext.Token token);

    /**
     * {@code void registerTimeout(Timeout? timeout)}
     */
    void registerTimeout(Ctx ctx, xObj timeout);
}
