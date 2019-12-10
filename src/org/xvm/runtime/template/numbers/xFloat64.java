package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.Float64Constant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Float64 support.
 */
public class xFloat64
        extends FPBase
    {
    public static xFloat64 INSTANCE;

    public xFloat64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Float64Constant)
            {
            double dValue = ((Float64Constant) constant).getValue();
            frame.pushStack(makeFloat(dValue));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected int getPrecision()
        {
        // bits 51-0
        return 52;
        }
    }
