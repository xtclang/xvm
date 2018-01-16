package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.Ref;


/**
 * TODO:
 */
public class xAtomicIntNumber
        extends Ref
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
        //    @Op Void increment()
        //    @Op Void decrement()
        //    @Op RefType preIncrement()
        //    @Op RefType preDecrement()
        //    @Op RefType postIncrement()
        //    @Op RefType postDecrement()
        //    @Op Void addAssign(RefType n)
        //    @Op Void subAssign(RefType n)
        //    @Op Void mulAssign(RefType n)
        //    @Op Void divAssign(RefType n)
        //    @Op Void modAssign(RefType n)
        //    @Op Void andAssign(RefType n)
        //    @Op Void orAssign(RefType n)
        //    @Op Void xorAssign(RefType n)
        //    @Op Void shiftLeftAssign(Int count)
        //    @Op Void shiftRightAssign(Int count)
        //    @Op Void shiftAllRightAssign(Int count)
        // TODO: all native
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.incrementAndGet()));
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndIncrement()));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        long lValue = atomic.get();
        while (!atomic.compareAndSet(lValue, -lValue))
            {
            lValue = atomic.get();
            }
        return frame.assignValue(iReturn, xInt64.makeHandle(lValue));
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new AtomicIntRefHandle(clazz, sName);
        }

    public static class AtomicIntRefHandle
            extends RefHandle
        {
        protected AtomicLong m_atomicValue;

        protected AtomicIntRefHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        @Override
        public boolean isAssigned()
            {
            return m_atomicValue != null;
            }

        @Override
        protected int getInternal(Frame frame, int iReturn)
            {
            return m_atomicValue == null
                ? frame.raiseException(xException.makeHandle("Unassigned reference"))
                : frame.assignValue(iReturn, xInt64.makeHandle(m_atomicValue.get()));
            }

        @Override
        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            long lValue = ((JavaLong) handle).getValue();
            if (m_atomicValue == null)
                {
                m_atomicValue = new AtomicLong(lValue);
                }
            m_atomicValue.set(lValue);
            return null;
            }

        @Override
        public String toString()
            {
            return "(x:AtomicIntNumber) " +
                    (m_atomicValue == null ? "unassigned" : m_atomicValue.get());
            }
        }
    }
