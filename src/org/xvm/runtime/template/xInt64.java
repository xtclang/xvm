package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


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
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (((l1 ^ lr) & (l2 ^ lr)) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeInt(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (((l1 ^ l2) & (l1 ^ lr)) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeInt(lr));
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

        return frame.assignValue(iReturn, makeInt(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeInt(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeInt(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Integer.MAX_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeInt(l + 1));
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

    @Override
    protected JavaLong makeInt(long lValue)
        {
        return new JavaLong(INSTANCE.getCanonicalClass(), lValue);
        }

    public static JavaLong makeHandle(long lValue)
        {
        // TODO: use a cache of common values
        return new JavaLong(INSTANCE.getCanonicalClass(), lValue);
        }
    }
