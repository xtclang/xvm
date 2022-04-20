package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.PackedInteger;


/**
 * Native IntN support.
 */
public class xIntN
        extends xUnconstrainedInteger
    {
    public static xIntN INSTANCE;

    public xIntN(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeProperty("magnitude");

        markNativeMethod("abs", VOID, THIS);

        super.initNative();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "magnitude":
                {
                PackedInteger pi = ((xIntLiteral.IntNHandle) hTarget).m_piValue;
                return frame.assignValue(iReturn, pi.isNegative()
                        ? makeInt(pi.negate())
                        : hTarget);
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }
    }