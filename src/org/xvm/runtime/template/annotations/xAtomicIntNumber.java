package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xVar;


/**
 * TODO:
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
    public void initDeclared()
        {
        // TODO: do we need to mark the VarOps as native?
        // TODO: how to implement checked/unchecked optimally?
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new AtomicIntVarHandle(clazz, sName);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 2:
                switch (method.getName())
                    {
                    case "replace":
                        {
                        AtomicIntVarHandle hThis = (AtomicIntVarHandle) hTarget;

                        long lExpect = ((JavaLong) ahArg[0]).getValue();
                        long lNew = ((JavaLong) ahArg[1]).getValue();
                        AtomicLong atomic = hThis.m_atomicValue;

                        return frame.assignValue(iReturn, xBoolean.makeHandle(
                            atomic.compareAndSet(lExpect, lNew)));
                        }
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (ahArg.length)
            {
            case 2:
                switch (method.getName())
                    {
                    case "replaceFailed":
                        {
                        AtomicIntVarHandle hThis = (AtomicIntVarHandle) hTarget;

                        long lExpect = ((JavaLong) ahArg[0]).getValue();
                        long lNew = ((JavaLong) ahArg[1]).getValue();
                        AtomicLong atomic = hThis.m_atomicValue;

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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.incrementAndGet()));
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndIncrement()));
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.decrementAndGet()));
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntVarHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        if (lArg == 0)
            {
            return frame.raiseException(xException.makeHandle("Division by zero"));
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
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        atomic.updateAndGet(lVal -> lVal % lArg);
        return Op.R_NEXT;
        }

    @Override
    protected int getInternal(Frame frame, RefHandle hTarget, int iReturn)
        {
        AtomicIntVarHandle hAtomic = (AtomicIntVarHandle) hTarget;
        AtomicLong atomicValue = hAtomic.m_atomicValue;

        return atomicValue == null
            ? frame.raiseException(xException.makeHandle("Unassigned reference"))
            : frame.assignValue(iReturn, xInt64.makeHandle(atomicValue.get()));
        }

    @Override
    protected int setInternal(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        AtomicIntVarHandle hAtomic = (AtomicIntVarHandle) hTarget;
        AtomicLong atomicValue = hAtomic.m_atomicValue;
        long lValue = ((JavaLong) hValue).getValue();

        if (atomicValue == null)
            {
            hAtomic.m_atomicValue = atomicValue = new AtomicLong(lValue);
            }
        atomicValue.set(lValue);
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
