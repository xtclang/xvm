package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIntArray
        extends xArray
    {
    public static xIntArray INSTANCE;

    public xIntArray(TypeSet types)
        {
        super(types, "x:collections.IntArray", "x:collections.Array", Shape.Class);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        }

    public static class IntArrayHandle
            extends ArrayHandle
        {
        public long[] m_alValue;
        public int m_cSize;

        protected IntArrayHandle(TypeComposition clzArray, long cCapacity, boolean fFixed)
            {
            super(clzArray, fFixed);

            m_alValue = new long[(int) cCapacity];
            m_cSize = 0;
            }
        }

    @Override
    public ExceptionHandle getArrayValue(Frame frame, ArrayHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return outOfRange(lIndex, hArray.m_cSize);
            }
        return frame.assignValue(iReturn,
                xInt64.makeHandle(hArray.m_alValue[(int) lIndex]));
        }

    @Override
    public ExceptionHandle setArrayValue(Frame frame, ArrayHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;
        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex >= cSize)
            {
            if (hArray.m_fFixed || lIndex != cSize)
                {
                outOfRange(lIndex, cSize);
                }

            // check the capacity
            int cCapacity = hArray.m_alValue.length;
            if (cCapacity <= cSize)
                {
                // resize (TODO: we should be much smarter here)
                cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

                long[] alNew = new long[cCapacity];
                System.arraycopy(hArray.m_alValue, 0, alNew, 0, cSize);
                hArray.m_alValue = alNew;
                }
            hArray.m_cSize++;
            }

        long lValue = ((JavaLong) hValue).getValue();

        hArray.m_alValue[(int) lIndex] = lValue;
        return null;
        }

    public static IntArrayHandle makeIntArrayInstance(long cCapacity, boolean fFixed)
        {
        return new IntArrayHandle(INSTANCE.f_clazzCanonical, cCapacity, fFixed);
        }
    }
