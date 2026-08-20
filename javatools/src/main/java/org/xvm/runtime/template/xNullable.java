package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;


/**
 * Native Nullable.
 */
public class xNullable
        extends xEnum {
    public static xNullable getInstance(Frame frame) {
        return NativeTemplates.get(frame).nullable();
    }

    public static xNullable getInstance(Container container) {
        return NativeTemplates.get(container).nullable();
    }

    public xNullable(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        if (getStructure().getFormat() == Format.ENUM) {
            super.initNative();

            // Preserve the old eager native-enum cache warmup, but keep the handle owned by this
            // template's container instead of publishing it in a mutable process global.
            getEnumByOrdinal(0);
        }
    }

    @Override
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal) {
        return new EnumHandle(clz, 0);
    }

    public static EnumHandle makeHandle(Frame frame) {
        return makeHandle(frame.container());
    }

    public static EnumHandle makeHandle(Container container) {
        return getInstance(container).getEnumByOrdinal(0);
    }

    public static boolean isNull(ObjectHandle hValue) {
        return hValue instanceof EnumHandle hEnum &&
                hEnum.getOrdinal() == 0 &&
                hEnum.getTemplate() instanceof xNullable;
    }
}
