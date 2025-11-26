package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `service` types must extend this class.
 */
public abstract class nService
    extends nObj
    implements Service {

    public nService(Ctx ctx) {
        super(ctx);
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    @Override
    public void $makeImmut(Ctx ctx) {
        throw new Unsupported(ctx).$init(ctx, null, null);
    }

    // ----- Service interface ---------------------------------------------------------------------

    @Override
    public void callLater(Ctx ctx, nFunction doLater) {
        try {
            doLater.stdMethod.invokeExact();
        } catch (nException nEx) {
            throw nEx;
        } catch (Throwable e) {
            // documentation for invokeExact() says it can throw WrongMethodTypeException; any
            // other exception should have originated in Ecstasy code as an nException
            throw Exception.$typeMismatch(ctx, e.getMessage());
        }
    }

    @Override
    public boolean hasFutureArrived$p(Ctx ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public nObj findContextToken(Ctx ctx, nType t$Value, SharedContext sharedContext) {
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
    public void registerTimeout(Ctx ctx, nObj timeout) {
        throw new UnsupportedOperationException();
    }
}
