package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
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
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedInt8(f_templates, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt8.INSTANCE;
        }

    @Override
    protected xConstrainedInteger getUncheckedTemplate()
        {
        return xUncheckedInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Int8Constant)
            {
            return frame.pushStack(
                    makeJavaLong(((Int8Constant) constant).getValue().longValue()));
            }

        return super.createConstHandle(frame, constant);
        }
    }