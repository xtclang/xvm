package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.UInt8Constant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt8 (Byte) support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt
    {
    public static xUInt8 INSTANCE;

    public xUInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 255, 8, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof UInt8Constant)
            {
            frame.pushStack(new ObjectHandle.JavaLong(getCanonicalClass(),
                    (((UInt8Constant) constant).getValue().longValue())));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    public static JavaLong makeHandle(long chValue)
        {
        assert chValue >= -128 & chValue <= 127;

        // TODO: cache?
        return new ObjectHandle.JavaLong(INSTANCE.getCanonicalClass(), chValue);
        }
    }
