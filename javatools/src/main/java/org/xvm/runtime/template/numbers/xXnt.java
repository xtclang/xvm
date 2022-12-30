package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;


/**
 * Native Int support.
 */
public class xXnt
        extends xIntBase
    {
    public static xXnt INSTANCE;

    public xXnt(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, /*signed*/ true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        if (this == INSTANCE)
            {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i)
                {
                cache[i] = new JavaLong(clz, i);
                }
            }
        }

    @Override
    protected xIntBase getComplimentaryTemplate()
        {
        return xUInt.INSTANCE;
        }

    @Override
    protected int invokeAbs(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong hLong)
            {
            long l = hLong.getValue();
            return frame.assignValue(iReturn, l >= 0 ? hTarget : makeLong(-l));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            if (ll.signum() >= 0)
                {
                return frame.assignValue(iReturn, hTarget);
                }
            ll = ll.negate();
            if (ll == LongLong.OVERFLOW)
                {
                return overflow(frame);
                }
            return frame.assignValue(iReturn, makeLongLong(ll));
            }
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l = h1.getValue();
            if (l == Long.MIN_VALUE)
                {
                // long overflow
                return frame.assignValue(iReturn, makeLongLong(MAX64_VALUE_NEXT));
                }

            return frame.assignValue(iReturn, makeLong(-l));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            return frame.assignValue(iReturn, makeLongLong(ll.negate()));
            }
        }

    @Override
    protected boolean checkAddOverflow(long l1, long l2, long lr)
        {
        return ((l1 ^ lr) & (l2 ^ lr)) < 0;
        }

    @Override
    protected boolean checkSubOverflow(long l1, long l2, long lr)
        {
        return (((l1 ^ l2) & (l1 ^ lr))) < 0;
        }

    @Override
    protected boolean checkMulOverflow(long l1, long l2, long lr)
        {
        long a1 = Math.abs(l1);
        long a2 = Math.abs(l2);

        // see Math.multiplyExact()
        return (a1 | a2) >>> 31 != 0 &&
               ((l2 != 0) && (lr / l2 != l1) || (l1 == Long.MIN_VALUE && l2 == -1));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l = h1.getValue();
            if (l == Long.MIN_VALUE)
                {
                // long overflow
                return frame.assignValue(iReturn, makeLongLong(MIN64_VALUE_PREV));
                }

            return frame.assignValue(iReturn, makeLong(l-1));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            LongLong lr = ll.prev(true);
            return lr == LongLong.OVERFLOW
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeHandle(lr));
            }
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l = h1.getValue();
            if (l == Long.MAX_VALUE)
                {
                // long overflow
                return frame.assignValue(iReturn, makeLongLong(MAX64_VALUE_NEXT));
                }

            return frame.assignValue(iReturn, makeLong(l+1));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            LongLong lr = ll.next(true);
            return lr == LongLong.OVERFLOW
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeHandle(lr));
            }
        }

    @Override
    public JavaLong makeLong(long lValue)
        {
        if (lValue == (lValue & 0x7F))
            {
            // TODO: cache some negative values as well
            return cache[(int) lValue];
            }
        return new JavaLong(getCanonicalClass(), lValue);
        }

    private final JavaLong[] cache = new JavaLong[128];

    public static final LongLong MAX64_VALUE_NEXT = new LongLong(Long.MIN_VALUE, 0);
    public static final LongLong MIN64_VALUE_PREV = new LongLong(Long.MAX_VALUE, -1);
    }