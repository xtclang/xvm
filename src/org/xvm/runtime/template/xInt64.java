package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xIntArray;


/**
 * TODO:
 */
public class xInt64
        extends xUncheckedInt64
    {
    public static xInt64 INSTANCE;

    public xInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;

            // TODO: cache some often used numbers
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        return constant instanceof IntConstant ? new JavaLong(getCanonicalClass(),
                (((IntConstant) constant).getValue().getLong())) : null;
        }

    @Override
    public int createArrayStruct(Frame frame, TypeConstant typeEl, long cCapacity, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            return frame.raiseException(xException.makeHandle("Invalid array size: " + cCapacity));
            }

        return frame.assignValue(iReturn, xIntArray.makeIntArrayInstance(cCapacity));
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (((l1 ^ lr) & (l2 ^ lr)) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (((l1 ^ lr) & (l2 ^ lr)) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(lr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 * l2;

        long a1 = Math.abs(l1);
        long a2 = Math.abs(l2);
        if (((a1 | a2) >>> 31 != 0))
            {
            // see Math.multiplyExact()
           if (((l2 != 0) && (lr / l2 != l1)) ||
               (l1 == Long.MIN_VALUE && l2 == -1))
               {
               return overflow(frame);
               }
            }

        return frame.assignValue(iReturn, makeHandle(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MAX_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l+1));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((JavaLong) hValue1).getValue() == ((JavaLong) hValue2).getValue();
        }


    // ----- helpers -----

    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.makeHandle("Int overflow"));
        }

    public static JavaLong makeHandle(long lValue)
        {
        // TODO: use a cache of common values
        return new JavaLong(INSTANCE.getCanonicalClass(), lValue);
        }
    }
