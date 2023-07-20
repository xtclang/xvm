package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


/**
 * Native Int8 support.
 */
public class xInt8
        extends xConstrainedInteger
    {
    public static xInt8 INSTANCE;

    public xInt8(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedInt8(f_container, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt8.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ByteConstant constByte)
            {
            return frame.pushStack(makeJavaLong(constByte.getValue().longValue()));
            }

        return super.createConstHandle(frame, constant);
        }
    }