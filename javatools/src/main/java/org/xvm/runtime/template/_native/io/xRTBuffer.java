package org.xvm.runtime.template._native.io;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constants;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;


/**
 * Native RTBuffer implementation.
 */
public class xRTBuffer
        extends ClassTemplate {
    public static xRTBuffer INSTANCE;

    public xRTBuffer(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz) {
        assert clazz.getTemplate() == this;

        return new RTBufferHandle(clazz.ensureAccess(Constants.Access.STRUCT));
    }

    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Native RTBuffer handle. It's allowed to cross the service boundaries.
     */
    private static class RTBufferHandle
            extends GenericHandle {
        public RTBufferHandle(TypeComposition clazz) {
            super(clazz);
        }

        @Override
        public boolean isPassThrough(Container container) {
            return true;
        }
    }
}