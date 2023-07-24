package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xOrdered;


/**
 * Base class for unsigned integer classes.
 */
public abstract class xUnsignedConstrainedInt
        extends xConstrainedInteger
    {
    protected xUnsignedConstrainedInt(Container container, ClassStructure structure,
                                      long cMinValue, long cMaxValue,
                                      int cNumBits, boolean fChecked)
        {
        super(container, structure, cMinValue, cMaxValue, cNumBits, true, fChecked);
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        long lQuo = divUnsigned(l1, l2);
        long lRem = modUnsigned(l1, l2);

        return frame.assignValues(aiReturn, makeJavaLong(lQuo), makeJavaLong(lRem));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(
            Long.compareUnsigned(h1.getValue(), h2.getValue())));
        }
    }