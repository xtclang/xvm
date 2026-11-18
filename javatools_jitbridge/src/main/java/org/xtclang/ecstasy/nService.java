package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `service` types must extend this class.
 */
public abstract class nService
        extends nObject
        implements Service {

    public nService(Ctx ctx) {
        super(ctx);
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    @Override
    public void $makeImmut(Ctx ctx, boolean async) {
        throw new Unsupported(ctx).$init(ctx, false, null, null);
    }

    // ----- Service interface ---------------------------------------------------------------------

    @Override
    public void callLater(Ctx ctx, boolean async, nFunction doLater) {
        try {
            doLater.stdMethod.invokeExact(ctx, false);
        } catch (nException nEx) {
            throw nEx;
        } catch (Throwable e) {
            // documentation for invokeExact() says it can throw WrongMethodTypeException; any
            // other exception should have originated in Ecstasy code as an nException
            throw Exception.$typeMismatch(ctx, e.getMessage());
        }
    }

    @Override
    public boolean hasFutureArrived$p(Ctx ctx, boolean async) {
        throw new UnsupportedOperationException();
    }

    @Override
    public nObject findContextToken(Ctx ctx, boolean async, nType t$Value,
            SharedContext sharedContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerContextToken(Ctx ctx, boolean async, SharedContext.Token token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterContextToken(Ctx ctx, boolean async, SharedContext.Token token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerTimeout(Ctx ctx, boolean async, Object timeout) {
        throw new UnsupportedOperationException();
    }
}
