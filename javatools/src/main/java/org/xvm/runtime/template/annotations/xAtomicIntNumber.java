package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native implementation for AtomicIntNumber.
 */
public class xAtomicIntNumber
        extends xAtomicVar
    {
    public static xAtomicIntNumber INSTANCE;

    public xAtomicIntNumber(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("increment", VOID, VOID);
        markNativeMethod("decrement", VOID, VOID);
        markNativeMethod("preIncrement", VOID, null);
        markNativeMethod("preDecrement", VOID, null);
        markNativeMethod("postIncrement", VOID, null);
        markNativeMethod("postDecrement", VOID, null);
        markNativeMethod("addAssign", null, VOID);
        markNativeMethod("subAssign", null, VOID);
        markNativeMethod("mulAssign", null, VOID);
        markNativeMethod("divAssign", null, VOID);
        markNativeMethod("modAssign", null, VOID);
        markNativeMethod("andAssign", null, VOID);
        markNativeMethod("orAssign", null, VOID);
        markNativeMethod("xorAssign", null, VOID);
        markNativeMethod("shiftLeftAssign", INT, VOID);
        markNativeMethod("shiftRightAssign", INT, VOID);
        markNativeMethod("shiftAllRightAssign", INT, VOID);

        // TODO: how to implement checked/unchecked optimally?
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // native handle - no further initialization is required
        return new AtomicIntVarHandle(clazz.ensureAccess(Access.PUBLIC), sName);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        AtomicIntVarHandle hThis = (AtomicIntVarHandle) hTarget;

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

                return frame.assignValue(iReturn, xInt64.makeHandle(lOld));
                }

            case "addAssign":
                return invokeVarAdd(frame, hThis, hArg);

            case "subAssign":
                return invokeVarSub(frame, hThis, hArg);

            case "divAssign":
                return invokeVarDiv(frame, hThis, hArg);

            case "modAssign":
                return invokeVarMod(frame, hThis, hArg);

            case "andAssign":
                return invokeVarAnd(frame, hThis, hArg);

            case "orAssign":
                return invokeVarOr(frame, hThis, hArg);

            case "xorAssign":
                return invokeVarXor(frame, hThis, hArg);

            case "shiftLeftAssign":
                return invokeVarShl(frame, hThis, hArg);

            case "shiftRightAssign":
                return invokeVarShr(frame, hThis, hArg);

            case "shiftAllRightAssign":
                return invokeVarShrAll(frame, hThis, hArg);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        AtomicIntVarHandle hThis = (AtomicIntVarHandle) hTarget;

        switch (method.getName())
            {
            case "replace":
                {
                long lExpect = ((JavaLong) ahArg[0]).getValue();
                long lNew    = ((JavaLong) ahArg[1]).getValue();

                AtomicLong atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(atomic.compareAndSet(lExpect, lNew)));
                }

            case "increment":
                {
                AtomicLong atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }
                atomic.getAndIncrement();
                return Op.R_NEXT;
                }

            case "decrement":
                {
                AtomicLong atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }
                atomic.getAndDecrement();
                return Op.R_NEXT;
                }

            case "preIncrement":
                return invokeVarPreInc(frame, hThis, iReturn);

            case "preDecrement":
                return invokeVarPreDec(frame, hThis, iReturn);

            case "postIncrement":
                return invokeVarPostInc(frame, hThis, iReturn);

            case "postDecrement":
                return invokeVarPostDec(frame, hThis, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "replaceFailed":
                {
                AtomicIntVarHandle hThis  = (AtomicIntVarHandle) hTarget;
                AtomicLong         atomic = hThis.m_atomicValue;
                if (atomic == null)
                    {
                    return frame.raiseException(xException.unassignedReference(frame));
                    }

                long lExpect = ((JavaLong) ahArg[0]).getValue();
                long lNew    = ((JavaLong) ahArg[1]).getValue();

                long lOld;
                while ((lOld = atomic.get()) == lExpect)
                    {
                    if (atomic.compareAndSet(lExpect, lNew))
                        {
                        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                        }
                    }
                return frame.assignValues(aiReturn, xBoolean.TRUE, xInt64.makeHandle(lOld));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- VarSupport API ------------------------------------------------------------------------

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.incrementAndGet()));
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndIncrement()));
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.decrementAndGet()));
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndDecrement()));
        }

    @Override
    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.addAndGet(lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.addAndGet(-lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal * lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        if (lArg == 0)
            {
            return frame.raiseException(xException.divisionByZero(frame));
            }

        atomic.updateAndGet(lVal -> lVal / lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal % lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarShl(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal << lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarShr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal >> lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarShrAll(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal >>> lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarAnd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal & lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarOr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal | lArg);
        return Op.R_NEXT;
        }

    @Override
    public int invokeVarXor(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;
        long       lArg   = ((JavaLong) hArg).getValue();

        if (atomic == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        atomic.updateAndGet(lVal -> lVal ^ lArg);
        return Op.R_NEXT;
        }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicIntVarHandle hAtomic = (AtomicIntVarHandle) hTarget;
        AtomicLong         atomic  = hAtomic.m_atomicValue;

        return atomic == null
            ? frame.raiseException(xException.unassignedReference(frame))
            : frame.assignValue(iReturn, xInt64.makeHandle(atomic.get()));
        }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        AtomicIntVarHandle hAtomic = (AtomicIntVarHandle) hTarget;
        AtomicLong         atomic  = hAtomic.m_atomicValue;
        long               lValue  = ((JavaLong) hValue).getValue();

        if (atomic == null)
            {
            hAtomic.m_atomicValue = atomic = new AtomicLong(lValue);
            }
        atomic.set(lValue);
        return Op.R_NEXT;
        }


    // ----- the handle -----

    public static class AtomicIntVarHandle
            extends RefHandle
        {
        protected AtomicLong m_atomicValue;

        protected AtomicIntVarHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        @Override
        public boolean isAssigned(Frame frame)
            {
            return m_atomicValue != null;
            }

        @Override
        public String toString()
            {
            return "(x:AtomicIntNumber) " +
                    (m_atomicValue == null ? "unassigned" : m_atomicValue.get());
            }
        }
    }
