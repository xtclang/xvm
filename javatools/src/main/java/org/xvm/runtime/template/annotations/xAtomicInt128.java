package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicReference;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.BaseInt128;
import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;


/**
 * Native implementation for @Atomic Int128 and UInt128.
 */
public class xAtomicInt128
        extends xAtomicVar
    {
    public xAtomicInt128(BaseInt128 templateIntBase)
        {
        super(templateIntBase.f_container, xAtomicIntNumber.INSTANCE.getStructure(), false);

        f_templateReferent = templateIntBase;
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // native handle - no further initialization is required
        return new AtomicLongLongHandle(clazz.ensureAccess(Access.PUBLIC), sName);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "exchange":
                {
                AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
                AtomicReference<LongLong> atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                LongLong llNew = ((LongLongHandle) hArg).getValue();
                LongLong llOld = atomic.getAndSet(llNew);

                return frame.assignValue(iReturn, f_templateReferent.makeHandle(llOld));
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
                AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
                AtomicReference<LongLong> atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                LongLong llExpect = ((LongLongHandle) ahArg[0]).getValue();
                LongLong llNew    = ((LongLongHandle) ahArg[1]).getValue();

                LongLong llCur;
                while ((llCur = atomic.get()).equals(llExpect))
                    {
                    if (atomic.compareAndSet(llCur, llNew))
                        {
                        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                        }
                    }
                return frame.assignValues(aiReturn, xBoolean.TRUE,
                    f_templateReferent.makeHandle(llCur));
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
        AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
        AtomicReference<LongLong> atomic = hThis.m_atomicValue;

        return atomic == null
            ? frame.raiseException(xException.unassignedReference(frame))
            : frame.assignValue(iReturn, f_templateReferent.makeHandle(atomic.get()));
        }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue)
        {
        AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
        AtomicReference<LongLong> atomic = hThis.m_atomicValue;

        LongLong llValue = ((LongLongHandle) hValue).getValue();

        if (atomic == null)
            {
            hThis.m_atomicValue = new AtomicReference<>(llValue);
            }
        else
            {
            atomic.set(llValue);
            }
        return Op.R_NEXT;
        }


    // ----- the handle ----------------------------------------------------------------------------

    public static class AtomicLongLongHandle
            extends RefHandle
        {
        protected AtomicReference<LongLong> m_atomicValue;

        protected AtomicLongLongHandle(TypeComposition clazz, String sName)
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
            return "(Atomic " + m_clazz.getTemplate().f_sName + ')' +
                    (m_atomicValue == null ? "unassigned" : m_atomicValue.get());
            }
        }


    // ----- data fields ---------------------------------------------------------------------------

    private final BaseInt128 f_templateReferent;
    }