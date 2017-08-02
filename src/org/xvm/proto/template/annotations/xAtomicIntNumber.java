package org.xvm.proto.template.annotations;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xInt64;
import org.xvm.proto.template.xRef;

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

    public xAtomicIntNumber(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
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
        // TODO: all native
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyStructure property, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            frame.m_hException = xException.makeHandle("Unassigned reference");
            return Op.R_EXCEPTION;
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.incrementAndGet()));
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyStructure property, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            frame.m_hException = xException.makeHandle("Unassigned reference");
            return Op.R_EXCEPTION;
            }

        return frame.assignValue(iReturn, xInt64.makeHandle(atomic.getAndIncrement()));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        AtomicLong atomic = ((AtomicIntRefHandle) hTarget).m_atomicValue;

        if (atomic == null)
            {
            frame.m_hException = xException.makeHandle("Unassigned reference");
            return Op.R_EXCEPTION;
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
            return "(x:AtomicIntNumber) " +
                    (m_atomicValue == null ? "unassigned" : m_atomicValue.get());
            }
        }
    }
