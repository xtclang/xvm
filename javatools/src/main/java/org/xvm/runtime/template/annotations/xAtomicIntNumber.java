package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicBoolean;
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
        extends xAtomic {
    public xAtomicIntNumber(Container container, ClassStructure structure) {
        super(container, structure);

        f_templateReferent = null;
    }

    public xAtomicIntNumber(xConstrainedInteger templateIntNumber,
                            xAtomicIntNumber templateAtomicInt) {
        super(templateIntNumber.f_container, templateAtomicInt.getStructure());

        f_templateReferent = templateIntNumber;
    }

    @Override
    public void initNative() {
    }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName) {
        // native handle - no further initialization is required
        return new AtomicJavaLongHandle(clazz.ensureAccess(Access.PUBLIC), sName);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        AtomicJavaLongHandle hThis = (AtomicJavaLongHandle) hTarget;

        switch (method.getName()) {
        case "exchange": {
            if (!hThis.m_fAssigned.get()) {
                return frame.raiseException(xException.unassignedReference(frame));
            }
            AtomicLong atomic = hThis.m_atomicValue;

            long lNew = ((JavaLong) hArg).getValue();
            long lOld = atomic.getAndSet(lNew);

            return frame.assignValue(iReturn, f_templateReferent.makeJavaLong(lOld));
        }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "replaceFailed": {
            AtomicJavaLongHandle hThis  = (AtomicJavaLongHandle) hTarget;
            if (!hThis.m_fAssigned.get()) {
                return frame.raiseException(xException.unassignedReference(frame));
            }
            AtomicLong atomic = hThis.m_atomicValue;

            long lExpect = ((JavaLong) ahArg[0]).getValue();
            long lNew    = ((JavaLong) ahArg[1]).getValue();

            long lCur;
            while ((lCur = atomic.get()) == lExpect) {
                if (atomic.compareAndSet(lCur, lNew)) {
                    return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
                }
            }
            return frame.assignValues(aiReturn, xBoolean.trueHandle(frame), f_templateReferent.makeJavaLong(lCur));
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
        AtomicJavaLongHandle hAtomic = (AtomicJavaLongHandle) hTarget;

        return hAtomic.m_fAssigned.get()
            ? frame.assignValue(iReturn, f_templateReferent.makeJavaLong(hAtomic.m_atomicValue.get()))
            : frame.raiseException(xException.unassignedReference(frame));
    }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue) {
        AtomicJavaLongHandle hAtomic = (AtomicJavaLongHandle) hTarget;

        // value first, flag second: a reader that observes the flag as true is guaranteed to see
        // the value through the two volatile operations
        hAtomic.m_atomicValue.set(((JavaLong) hValue).getValue());
        hAtomic.m_fAssigned.set(true);
        return Op.R_NEXT;
    }


    // ----- the handle ----------------------------------------------------------------------------

    public static class AtomicJavaLongHandle
            extends RefHandle {
        /**
         * The atomic cell, deliberately eager and final: cloneAs views shallow-copy this field, so
         * every view of one Atomic ref shares one cell. The old lazily installed cell let a view
         * cloned before the first assignment install its own cell, silently splitting one Atomic
         * into two independent ones between views.
         */
        protected final AtomicLong m_atomicValue = new AtomicLong();

        /**
         * The monotonic assigned flag, final and shared for the same reason. Unassigned state can
         * no longer ride on cell-null, because the cell must exist before any view is cloned.
         */
        protected final AtomicBoolean m_fAssigned = new AtomicBoolean();

        protected AtomicJavaLongHandle(TypeComposition clazz, String sName) {
            super(clazz, sName);
        }

        @Override
        public boolean isAssigned() {
            return m_fAssigned.get();
        }

        @Override
        public String toString() {
            return "(AtomicIntNumber) " +
                    (m_fAssigned.get() ? m_atomicValue.get() : "unassigned");
        }
    }


    // ----- data fields ---------------------------------------------------------------------------

    private final xConstrainedInteger f_templateReferent;
}
