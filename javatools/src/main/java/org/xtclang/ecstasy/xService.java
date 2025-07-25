package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Type;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `service` types must extend this class.
 */
public abstract class xService extends xObj implements Service {

    public xService(long containerId) {
        super(containerId);
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    @Override
    public void $makeImmut() {
        throw new Exception("Unsupported"); // TODO: new Unsupported
    }

    // ----- Service interface ---------------------------------------------------------------------

    @Override
    public void callLater(Ctx ctx, xFunction.$0 doLater) {
        doLater.$call(ctx); // TODO: create a new fiber that calls "doLater.$call(ctx)"
    }

    @Override
    public boolean hasFutureArrived$p(Ctx ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public xObj findContextToken(Ctx ctx, Type t$Value, SharedContext sharedContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerContextToken(Ctx ctx, SharedContext.Token token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterContextToken(Ctx ctx, SharedContext.Token token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerTimeout(Ctx ctx, xObj timeout) {
        throw new UnsupportedOperationException();
    }
}
