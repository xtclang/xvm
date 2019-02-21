package org.xvm.runtime.template;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Base class for unsigned integer classes.
 */
public abstract class xUnsignedConstrainedInt
        extends xConstrainedInteger
    {
    protected xUnsignedConstrainedInt(TemplateRegistry templates, ClassStructure structure,
                                      long cMinValue, long cMaxValue,
                                      int cNumBits, boolean fChecked)
        {
        super(templates, structure, cMinValue, cMaxValue, cNumBits, true, fChecked);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (f_fChecked && (((l1 & l2) | ((l1 | l2) & ~lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((ObjectHandle.JavaLong) hTarget).getValue();
        long l2 = ((ObjectHandle.JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (f_fChecked && (((~l1 & l2) | ((~l1 | l2) & lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle.JavaLong h1 = (ObjectHandle.JavaLong) hValue1;
        ObjectHandle.JavaLong h2 = (ObjectHandle.JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(
            Long.compareUnsigned(h1.getValue(), h2.getValue())));
        }
    }
