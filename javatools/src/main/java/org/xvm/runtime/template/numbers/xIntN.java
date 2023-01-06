package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;

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
        markNativeMethod("abs", VOID, THIS);

        super.initNative();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "abs":
                {
                PackedInteger pi = ((IntNHandle) hTarget).getValue();
                return frame.assignValue(iReturn, pi.isNegative() ? makeInt(pi.negate()) : hTarget);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }
    }