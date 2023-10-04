package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xConstrainedInteger;


/**
 * Native implementation for AtomicIntNumber<Referent> for any Referent that uses JavaLong handle.
 */
public class xAtomicIntNumber
        extends xAtomicVar
    {
    public static xAtomicIntNumber INSTANCE;

    public xAtomicIntNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        f_templateReferent = null;
        }

    public xAtomicIntNumber(xConstrainedInteger templateIntNumber)
        {
        super(templateIntNumber.f_container, INSTANCE.f_struct, false);

        f_templateReferent = templateIntNumber;
        }

    @Override
    public void initNative()
        {
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // native handle - no further initialization is required
        return new AtomicJavaLongHandle(clazz.ensureAccess(Access.PUBLIC), sName);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        AtomicJavaLongHandle hThis = (AtomicJavaLongHandle) hTarget;

        switch (method.getName())
            {
            case "exchange":
                {
                AtomicLong atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                long lNew = ((JavaLong) hArg).getValue();
                long lOld = atomic.getAndSet(lNew);

                return frame.assignValue(iReturn, f_templateReferent.makeJavaLong(lOld));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "replaceFailed":
                {
                AtomicJavaLongHandle hThis  = (AtomicJavaLongHandle) hTarget;
                AtomicLong           atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                long lExpect = ((JavaLong) ahArg[0]).getValue();
                long lNew    = ((JavaLong) ahArg[1]).getValue();

                long lCur;
                while ((lCur = atomic.get()) == lExpect)
                    {
                    if (atomic.compareAndSet(lCur, lNew))
                        {
                        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                        }
                    }
                return frame.assignValues(aiReturn, xBoolean.TRUE, f_templateReferent.makeJavaLong(lCur));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hTarget, int iReturn)
        {
        return getReferentImpl(frame, hTarget, false, iReturn);
        }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return setReferentImpl(frame, hTarget, false, hValue);
        }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, int iReturn)
        {
        AtomicJavaLongHandle hAtomic = (AtomicJavaLongHandle) hTarget;
        AtomicLong         atomic  = hAtomic.m_atomicValue;

        return atomic == null
            ? frame.raiseException(xException.unassignedReference(frame))
            : frame.assignValue(iReturn, f_templateReferent.makeJavaLong(atomic.get()));
        }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue)
        {
        AtomicJavaLongHandle hAtomic = (AtomicJavaLongHandle) hTarget;
        AtomicLong         atomic  = hAtomic.m_atomicValue;
        long               lValue  = ((JavaLong) hValue).getValue();

        if (atomic == null)
            {
            hAtomic.m_atomicValue = new AtomicLong(lValue);
            }
        else
            {
            atomic.set(lValue);
            }
        return Op.R_NEXT;
        }


    // ----- the handle ----------------------------------------------------------------------------

    public static class AtomicJavaLongHandle
            extends RefHandle
        {
        protected AtomicLong m_atomicValue;

        protected AtomicJavaLongHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        @Override
        public boolean isAssigned()
            {
            return m_atomicValue != null;
            }

        @Override
        public String toString()
            {
            return "(AtomicIntNumber) " +
                    (m_atomicValue == null ? "unassigned" : m_atomicValue.get());
            }
        }


    // ----- data fields ---------------------------------------------------------------------------

    private final xConstrainedInteger f_templateReferent;
    }