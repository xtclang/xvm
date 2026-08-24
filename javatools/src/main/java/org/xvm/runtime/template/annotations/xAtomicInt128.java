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
        extends xAtomic {
    public xAtomicInt128(BaseInt128 templateIntBase, xAtomicIntNumber templateAtomicInt) {
        super(templateIntBase.f_container, templateAtomicInt.getStructure());

        f_templateReferent = templateIntBase;
    }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName) {
        // native handle - no further initialization is required
        return new AtomicLongLongHandle(clazz.ensureAccess(Access.PUBLIC), sName);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "exchange": {
            AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
            AtomicReference<LongLong> atomic = hThis.m_atomicValue;
            if (atomic.get() == null) {
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
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "replaceFailed": {
            AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
            AtomicReference<LongLong> atomic = hThis.m_atomicValue;
            if (atomic.get() == null) {
                return frame.raiseException(xException.unassignedReference(frame));
            }

            LongLong llExpect = ((LongLongHandle) ahArg[0]).getValue();
            LongLong llNew    = ((LongLongHandle) ahArg[1]).getValue();

            LongLong llCur;
            while ((llCur = atomic.get()).equals(llExpect)) {
                if (atomic.compareAndSet(llCur, llNew)) {
                    return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
                }
            }
            return frame.assignValues(aiReturn, xBoolean.trueHandle(frame),
                f_templateReferent.makeHandle(llCur));
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hTarget, int iReturn) {
        return getReferentImpl(frame, hTarget, false, iReturn);
    }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue) {
        return setReferentImpl(frame, hTarget, false, hValue);
    }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, int iReturn) {
        AtomicLongLongHandle      hThis  = (AtomicLongLongHandle) hTarget;
        LongLong                  llCur  = hThis.m_atomicValue.get();

        return llCur == null
            ? frame.raiseException(xException.unassignedReference(frame))
            : frame.assignValue(iReturn, f_templateReferent.makeHandle(llCur));
    }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue) {
        AtomicLongLongHandle hThis = (AtomicLongLongHandle) hTarget;

        hThis.m_atomicValue.set(((LongLongHandle) hValue).getValue());
        return Op.R_NEXT;
    }


    // ----- the handle ----------------------------------------------------------------------------

    public static class AtomicLongLongHandle
            extends RefHandle {
        /**
         * The atomic cell, deliberately eager and final: cloneAs views shallow-copy this field, so
         * every view of one Atomic ref shares one cell (the old lazily installed cell let a
         * pre-assignment view install its own). A null referent inside the cell means unassigned.
         */
        protected final AtomicReference<LongLong> m_atomicValue = new AtomicReference<>();

        protected AtomicLongLongHandle(TypeComposition clazz, String sName) {
            super(clazz, sName);
        }

        @Override
        public boolean isAssigned() {
            return m_atomicValue.get() != null;
        }

        @Override
        public String toString() {
            return "(Atomic " + m_clazz.getTemplate().f_sName + ')' +
                    (isAssigned() ? m_atomicValue.get() : "unassigned");
        }
    }


    // ----- data fields ---------------------------------------------------------------------------

    private final BaseInt128 f_templateReferent;
}
