package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import java.util.concurrent.atomic.AtomicLong;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xAtomicIntNumber
        extends xRef
    {
    public static xAtomicIntNumber INSTANCE;

    public xAtomicIntNumber(TypeSet types)
        {
        super(types, "x:AtomicIntNumber<RefType>", "x:AtomicRef", Shape.Mixin);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    @op Void increment()
        //    @op Void decrement()
        //    @op RefType preIncrement()
        //    @op RefType preDecrement()
        //    @op RefType postIncrement()
        //    @op RefType postDecrement()
        //    @op Void addAssign(RefType n)
        //    @op Void subAssign(RefType n)
        //    @op Void mulAssign(RefType n)
        //    @op Void divAssign(RefType n)
        //    @op Void modAssign(RefType n)
        //    @op Void andAssign(RefType n)
        //    @op Void orAssign(RefType n)
        //    @op Void xorAssign(RefType n)
        //    @op Void shiftLeftAssign(Int count)
        //    @op Void shiftRightAssign(Int count)
        //    @op Void shiftAllRightAssign(Int count)
        }

    @Override
    public ExceptionHandle invokePreInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        return atomic == null ? xException.makeHandle("Unassigned reference") :
                frame.assignValue(iReturn, xInt64.makeHandle(atomic.incrementAndGet()));
        }

    @Override
    public ExceptionHandle invokePostInc(Frame frame, ObjectHandle hTarget, PropertyTemplate property, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        return atomic == null ? xException.makeHandle("Unassigned reference") :
                frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndIncrement()));
        }

    @Override
    public ExceptionHandle invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            return xException.makeHandle("Unassigned reference");
            }

        long lValue = atomic.get();
        while (!atomic.compareAndSet(lValue, -lValue))
            {
            lValue = atomic.get();
            }
        return frame.assignValue(iReturn, xInt64.makeHandle(lValue));
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new AtomicIntRefHandle(clazz);
        }

    public static class AtomicIntRefHandle
            extends RefHandle
        {
        protected AtomicLong m_atomicValue;

        protected AtomicIntRefHandle(TypeComposition clazz)
            {
            super(clazz, null);
            }

        @Override
        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            if (m_atomicValue == null)
                {
                throw xException.makeHandle("Unassigned reference").getException();
                }
            return xInt64.makeHandle(m_atomicValue.get());
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
            return "x:AtomicIntNumber " +
                    (m_atomicValue == null ? "unassigned" : "-> " + m_atomicValue.get());
            }
        }
    }
