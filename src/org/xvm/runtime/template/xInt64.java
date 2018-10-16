package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int64 support.
 */
public class xInt64
        extends xConstrainedInteger
    {
    public static xInt64 INSTANCE;

    public xInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    public static JavaLong makeHandle(long lValue)
        {
        return INSTANCE.makeJavaLong(lValue);
        }
    }
