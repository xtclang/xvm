package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt64 support.
 */
public class xUInt64
        extends xUnsignedConstrainedInt
    {
    public static xUInt64 INSTANCE;

    public xUInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, -1, 64,  true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((ObjectHandle.JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(xUnsignedConstrainedInt.unsignedLongToString(l)));
        }
    }
