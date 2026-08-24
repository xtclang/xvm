package org.xvm.runtime.template.annotations;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Fiber;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template.xException;


/**
 * Native implementation of Lazy.
 */
public class xLazy
        extends xVar {
    public xLazy(Container container, ClassStructure structure) {
        super(container, structure, NativeRole.DERIVED);
    }

    @Override
    public void initNative() {
    }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName) {
        return new LazyHandle(clazz, sName);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        LazyHandle hThis = (LazyHandle) hTarget;

        switch (sPropName) {
        case "assigned":
            if (!hThis.isAssigned() && hThis.isPropertyOnImmutable()) {
                hThis.registerAssign(frame.f_fiber);
            }
            break;
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "set": {
            LazyHandle hLazy = (LazyHandle) hTarget;
            if (hLazy.isPropertyOnImmutable()) {
                return invokeImmutableSet(frame, hLazy, hArg);
            }
            break;
        }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    protected int invokeImmutableSet(Frame frame, LazyHandle hLazy, ObjectHandle hValue) {
        if (!hValue.isPassThrough()) {
            if (hValue.getType().isA(frame.poolContext().typeFreezable())) {
                return Utils.callFreeze(frame, hValue, null, frameCaller ->
                    completeInvokeSet(frameCaller, hLazy, frameCaller.popStack()));
            }

            ObjectHandle hOuter = hLazy.getField(frame, GenericHandle.OUTER);
            return frame.raiseException(
                xException.notFreezableProperty(frame, hLazy.getName(), hOuter.getType()));
        }
        return completeInvokeSet(frame, hLazy, hValue);
    }

    protected int completeInvokeSet(Frame frame, LazyHandle hLazy, ObjectHandle hValue) {
        synchronized (hLazy.f_guard) {
            boolean fAllowDupe = hLazy.unregisterAssign(frame.f_fiber);
            if (hLazy.isAssigned()) {
                return fAllowDupe
                    ? Op.R_NEXT
                    : frame.raiseException(xException.immutableObjectProperty(
                        frame, hLazy.getName(), hLazy.getField(frame, GenericHandle.OUTER).getType()));
            } else {
                hLazy.setReferent(hValue); // this is exactly what the super.invokeNative1() call does
                hLazy.makeImmutable();
                return Op.R_NEXT;
            }
        }
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class LazyHandle
            extends RefHandle {
        /**
         * The initialization guard shared by every cloneAs view of this lazy reference.
         *
         * <p>The old shape synchronized on the handle instance and kept the allowed-to-assign
         * fiber set in a per-view field, but cloneAs views are shallow copies: two views of one
         * lazy property did not exclude each other, so the "compute at most once" value could be
         * computed and assigned twice, and a fiber registered through one view was invisible
         * through another, which turned the legal lazy recompute race into a spurious
         * immutable-property exception. The lazily computed value itself lives in the parent
         * object's shared field storage, so the guard state must be exactly as shared as the
         * value it guards. The final field makes every shallow view copy share this one guard.
         */
        protected final InitGuard f_guard = new InitGuard();

        protected LazyHandle(TypeComposition clazz, String sName) {
            super(clazz, sName);
        }

        /**
         * @return true iff this handle represents a lazy property on an immutable object
         */
        public boolean isPropertyOnImmutable() {
            // absence of the OUTER indicates a static Lazy property
            ObjectHandle hOuter = getField(null, GenericHandle.OUTER);
            return hOuter == null || !hOuter.isMutable();
        }

        /**
         * Register the specified fiber as "allowed to assign".
         */
        protected void registerAssign(Fiber fiber) {
            f_guard.register(fiber);
        }

        /**
         * Unregister the specified fiber from the "allowed to assign" set.
         *
         * @return true iff the specified fiber has been told that this var is unassigned and
         *              therefore is allowed to set it
         */
        protected boolean unregisterAssign(Fiber fiber) {
            return f_guard.unregister(fiber);
        }

        /**
         * The shared initialization guard: the monitor for compute/assign mutual exclusion across
         * all views, and the set of fibers that have observed the reference unassigned and are
         * therefore allowed a duplicate assignment.
         *
         * <p>In theory the fiber set could leak a service reference in a weird scenario, when
         * some code arbitrarily checks the "assigned" property on a lazy property ref but takes
         * no other action; that pre-existing caveat is unchanged.
         */
        protected static class InitGuard {
            private Set<Fiber> setInitFiber;

            synchronized void register(Fiber fiber) {
                Set<Fiber> setInit = setInitFiber;
                if (setInit == null) {
                    setInitFiber = setInit = new HashSet<>();
                }
                setInit.add(fiber);
            }

            synchronized boolean unregister(Fiber fiber) {
                boolean    fAllow  = false;
                Set<Fiber> setInit = setInitFiber;
                if (setInit != null) {
                    fAllow = setInit.remove(fiber);

                    if (setInit.isEmpty()) {
                        setInitFiber = null;
                    }
                }
                return fAllow;
            }
        }
    }
}
