package org.xvm.runtime.template.annotations;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.SignatureConstant;

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
 * Native implementation of LazyVar.
 */
public class xLazyVar
        extends xVar
    {
    public xLazyVar(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        return new LazyVarHandle(clazz, sName);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        LazyVarHandle hThis = (LazyVarHandle) hTarget;

        switch (sPropName)
            {
            case "assigned":
                if (!hThis.isAssigned(frame) && hThis.isPropertyOnImmutable())
                    {
                    hThis.registerAssign(frame.f_fiber);
                    }
                break;
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "set":
                {
                LazyVarHandle hLazy = (LazyVarHandle) hTarget;
                if (hLazy.isPropertyOnImmutable())
                    {
                    return invokeImmutableSet(frame, hLazy, hArg);
                    }
                break;
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    protected int invokeImmutableSet(Frame frame, LazyVarHandle hLazy, ObjectHandle hValue)
        {
        if (!hValue.isPassThrough())
            {
            if (hValue.getType().isA(frame.poolContext().typeFreezable()))
                {
                return Utils.callFreeze(frame, hValue, frameCaller ->
                    completeInvokeSet(frameCaller, hLazy, frameCaller.popStack()));
                }

            ObjectHandle hOuter = hLazy.getField(frame, GenericHandle.OUTER);
            return frame.raiseException(
                xException.notFreezableProperty(frame, hLazy.getName(), hOuter.getType()));
            }
        return completeInvokeSet(frame, hLazy, hValue);
        }

    protected int completeInvokeSet(Frame frame, LazyVarHandle hLazy, ObjectHandle hValue)
        {
        synchronized (hLazy)
            {
            boolean fAllowDupe = hLazy.unregisterAssign(frame.f_fiber);
            if (hLazy.isAssigned(frame))
                {
                return fAllowDupe
                    ? Op.R_NEXT
                    : frame.raiseException(xException.immutableObjectProperty(
                        frame, hLazy.getName(), hLazy.getField(frame, GenericHandle.OUTER).getType()));
                }
            else
                {
                hLazy.setReferent(hValue); // this is exactly what the super.invokeNative1() call does
                return Op.R_NEXT;
                }
            }
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    protected static class LazyVarHandle
            extends RefHandle
        {
        /**
         * A set of services that have seen this LazyVar unassigned. Only used by lazy properties
         * on immutable objects that could be shared across services.
         *
         * In theory, this could leak a service reference in a weird scenario, when some code
         * arbitrarily checks the "assigned" property on a lazy property ref, but takes no other
         * action.
         */
        protected Set<Fiber> m_setInitFiber;

        protected LazyVarHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        /**
         * @return true iff this handle represents a lazy property on an immutable object
         */
        public boolean isPropertyOnImmutable()
            {
            ObjectHandle hOuter = getField(null, GenericHandle.OUTER);
            return hOuter != null && !hOuter.isMutable();
            }

        /**
         * Register the specified fiber as "allowed to assign".
         */
        synchronized protected void registerAssign(Fiber fiber)
            {
            Set<Fiber> setInit = m_setInitFiber;
            if (setInit == null)
                {
                m_setInitFiber = setInit = new HashSet<>();
                }
            setInit.add(fiber);
            }

        /**
         * Unregister the specified fiber from "allowed to assign" set.
         *
         * This method must be called while holding synchronization on the var.
         *
         * @return true iff the specified service has been told that this var is unassigned and
         *              therefore is allowed to set it
         */
        protected boolean unregisterAssign(Fiber fiber)
            {
            boolean    fAllow  = false;
            Set<Fiber> setInit = m_setInitFiber;
            if (setInit != null)
                {
                fAllow = setInit.remove(fiber);

                if (setInit.isEmpty())
                    {
                    m_setInitFiber = null;
                    }
                }
            return fAllow;
            }
        }

    private static SignatureConstant SIG_FREEZE;
    }