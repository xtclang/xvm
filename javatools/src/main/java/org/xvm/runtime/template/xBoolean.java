package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.text.xString;

/**
 * Native Boolean implementation.
 */
public class xBoolean
        extends xEnum
    {
    public static BooleanHandle TRUE;
    public static BooleanHandle FALSE;

    public xBoolean(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        if (getStructure().getFormat() == Format.ENUM)
            {
            super.initNative();

            FALSE = (BooleanHandle) getEnumByOrdinal(0);
            TRUE  = (BooleanHandle) getEnumByOrdinal(1);
            }
        }

    @Override
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal)
        {
        return new BooleanHandle(clz, iOrdinal != 0);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn, makeHandle(((BooleanHandle) hTarget).get() & ((BooleanHandle) hArg).get()));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn, makeHandle(((BooleanHandle) hTarget).get() | ((BooleanHandle) hArg).get()));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn, makeHandle(((BooleanHandle) hTarget).get() ^ ((BooleanHandle) hArg).get()));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, not((BooleanHandle) hTarget));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, not((BooleanHandle) hTarget));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
                xString.makeHandle(hTarget == FALSE ? "False" : "True"));
        }

    public static BooleanHandle makeHandle(boolean f)
        {
        return f ? TRUE : FALSE;
        }

    public static BooleanHandle not(BooleanHandle hValue)
        {
        return hValue == FALSE ? TRUE : FALSE;
        }

    public static class BooleanHandle
                extends EnumHandle
        {
        BooleanHandle(TypeComposition clz, boolean f)
            {
            super(clz, f ? 1 : 0);
            }

        public boolean get()
            {
            return m_index != 0;
            }

        @Override
        public String toString()
            {
            return m_index == 0 ? "False" : "True";
            }
        }
    }