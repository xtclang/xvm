package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.constants.Int8Constant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int8 support.
 */
public class xInt8
        extends xConstrainedInteger
    {
    public static xInt8 INSTANCE;

    public xInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false, true);

        if (fInstance)
            {
            INSTANCE = this;

            // create unchecked template
            new xUncheckedInt8(templates, structure, true);
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt8.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Int8Constant)
            {
            frame.pushStack(
                makeJavaLong(((Int8Constant) constant).getValue().longValue()));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }
    }