package org.xtclang.ecstasy;

import java.lang.invoke.MethodHandle;

import org.xtclang.ecstasy.collections.Tuple;

import org.xtclang.ecstasy.reflect.Function;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy "function" types must extend this class.
 */
public abstract class nFunction extends nObj implements Function {
    public nFunction(Ctx ctx, MethodHandle stdMethod, MethodHandle optMethod, boolean immmutable) {
        super(ctx);

        this.stdMethod = stdMethod;
        this.optMethod = optMethod;
        this.immutable = immmutable;
    }

    public final MethodHandle stdMethod;
    public final MethodHandle optMethod;
    public final boolean      immutable;

    @Override
    public Tuple invoke(Ctx ctx, Tuple args) {
        // almost *never* called - reflection based
        // TODO
        return null;
    }

    @Override
    public boolean $isImmut() {
        return immutable;
    }

    /**
     * This class is registered as a bridge between the Signature of "void()" and the Function type
     */
    public static class ꖛ0 extends nFunction {
        public ꖛ0(Ctx ctx, MethodHandle stdMethod, MethodHandle optMethod, boolean immmutable) {
            super(ctx, stdMethod, optMethod, immmutable);
        }

        @Override
        public TypeConstant $xvmType(Ctx ctx) {
            return $xvm().ecstasyPool.buildFunctionType(TypeConstant.NO_TYPES);
        }

        /**
         * Well known name for a standard method call.
         */
        void $invoke(Ctx ctx) {
            try {
                stdMethod.invokeExact();
            } catch (nException nEx) {
                throw nEx;
            } catch (Throwable e) {
                // documentation for invokeExact() says it can throw WrongMethodTypeException; any
                // other exception should have originated in Ecstasy code as an nException
                throw Exception.$typeMismatch(ctx, e.getMessage());
            }
        }

        @Override
        public Tuple invoke(Ctx ctx, Tuple args) {
            $invoke(ctx);
            return nTuple.Empty;
        }
    }
}
