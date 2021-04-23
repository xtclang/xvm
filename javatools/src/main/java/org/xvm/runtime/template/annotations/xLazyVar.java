package org.xvm.runtime.template.annotations;


import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.reflect.xVar;
import org.xvm.runtime.template.xException;


/**
 * Native implementation of LazyVar.
 */
public class xLazyVar
        extends xVar
    {
    public xLazyVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
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
                if (!hThis.isAssigned() && hThis.isPropertyOnImmutable())
                    {
                    hThis.registerAssign(frame.f_context);
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
                LazyVarHandle hThis = (LazyVarHandle) hTarget;

                if (hThis.isPropertyOnImmutable())
                    {
                    synchronized (hThis)
                        {
                        boolean fAllowDupe = hThis.unregisterAssign(frame.f_context);
                        if (hThis.isAssigned())
                            // TODO GG: prevent mutable state: !hArg.isPassThrough(null))
                            {
                            return fAllowDupe
                                ? Op.R_NEXT
                                : frame.raiseException(xException.immutableObject(frame));
                            }
                        else
                            {
                            hThis.setReferent(hArg); // this is exactly what the super call does
                            return Op.R_NEXT;
                            }
                        }
                    }
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
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
        protected Set<ServiceContext> m_setInitContext;

        protected LazyVarHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        /**
         * @return true iff this handle represents a lazy property on an immutable object
         */
        public boolean isPropertyOnImmutable()
            {
            ObjectHandle hOuter = getField(GenericHandle.OUTER);
            return hOuter != null && !hOuter.isMutable();
            }

        /**
         * Register the specified service as "allowed to assign".
         */
        synchronized protected void registerAssign(ServiceContext ctx)
            {
            Set<ServiceContext> setInit = m_setInitContext;
            if (setInit == null)
                {
                m_setInitContext = setInit = new HashSet<>();
                }
            setInit.add(ctx);
            }

        /**
         * Unregister the specified service from "allowed to assign" set.
         *
         * This method must be called while holding synchronization on the var.
         *
         * @return true iff the specified service has been told that this var is unassigned and
         *              therefore is allowed to set it
         */
        protected boolean unregisterAssign(ServiceContext ctx)
            {
            boolean             fAllow  = false;
            Set<ServiceContext> setInit = m_setInitContext;
            if (setInit != null)
                {
                fAllow = setInit.remove(ctx);

                if (setInit.isEmpty())
                    {
                    m_setInitContext = null;
                    }
                }
            return fAllow;
            }
        }
    }
