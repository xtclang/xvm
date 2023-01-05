package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicReference;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.annotations.xAtomicInt128.AtomicLongLongHandle;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xIntBase;


/**
 * Native implementation for @Atomic Int and UInt.
 */
public class xAtomicInt
        extends xAtomicVar
    {
    public xAtomicInt(xIntBase templateIntBase)
        {
        super(templateIntBase.f_container, xAtomicIntNumber.INSTANCE.getStructure(), false);

        f_templateReferent = templateIntBase;
        f_fSigned          = templateIntBase.f_fSigned;
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

                LongLong llNew = hArg instanceof JavaLong hL
                        ? new LongLong(hL.getValue(), f_fSigned)
                        : ((LongLongHandle) hArg).getValue();

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

                LongLong llExpect = ahArg[0] instanceof JavaLong hExpect
                        ? new LongLong(hExpect.getValue(), f_fSigned)
                        : ((LongLongHandle) ahArg[0]).getValue();
                LongLong llNew = ahArg[1] instanceof JavaLong hNew
                        ? new LongLong(hNew.getValue(), f_fSigned)
                        : ((LongLongHandle) ahArg[1]).getValue();

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

        LongLong llValue = hValue instanceof JavaLong hL
                        ? new LongLong(hL.getValue(), f_fSigned)
                        : ((LongLongHandle) hValue).getValue();

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

    // ----- data fields ---------------------------------------------------------------------------

    private final xIntBase f_templateReferent;
    private final boolean  f_fSigned;
    }