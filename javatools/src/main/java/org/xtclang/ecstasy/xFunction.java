package org.xtclang.ecstasy;

import org.xtclang.ecstasy.collections.Tuple;

import org.xtclang.ecstasy.reflect.Function;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy "function" types must extend this class.
 */
public abstract class xFunction implements Function {

    @Override
    public Tuple invoke(Ctx $ctx, Tuple args) {
        // almost *never* called - reflection based
        // TODO
        return null;
    }

    /**
     * This class is registered as a bridge between the Signature of "void()" and the Function type
     */
    public abstract static class $0 extends xFunction {
        /**
         * This name is registered as a bridge and needs to be implemented by all native or
         * auto-generated Function classes.
         */
        abstract void $call(Ctx ctx);

        @Override
        public Tuple invoke(Ctx $ctx, Tuple args) {
            $call($ctx);
            return xTuple.Empty;
        }
    }
}
