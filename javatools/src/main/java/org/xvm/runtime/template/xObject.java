package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;


/**
 * Native Object functionality implementation.
 */
public class xObject
        extends ClassTemplate {
    public static xObject getInstance(Frame frame) {
        return NativeTemplates.get(frame).object();
    }

    public static xObject getInstance(Container container) {
        return NativeTemplates.get(container).object();
    }

    public xObject(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        if (NativeTemplates.get(this).isObject(this)) {
            markNativeMethod("equals", null, BOOLEAN);
            markNativeMethod("makeImmutable", VOID, null);

            invalidateTypeInfo();
        }
    }
}
