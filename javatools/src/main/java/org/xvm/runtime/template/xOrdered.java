package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.text.xString;


/**
 * Native Ordered.
 */
public class xOrdered
        extends xEnum {
    public static xOrdered getInstance(Frame frame) {
        return NativeTemplates.get(frame).ordered();
    }

    public static xOrdered getInstance(Container container) {
        return NativeTemplates.get(container).ordered();
    }

    public xOrdered(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        if (getStructure().getFormat() == Format.ENUM) {
            super.initNative();

            // Initialize the owner-local handles and their cyclic fields without publishing them
            // through mutable process globals.
            EnumHandle hLesser  = getEnumByOrdinal(0);
            EnumHandle hEqual   = getEnumByOrdinal(1);
            EnumHandle hGreater = getEnumByOrdinal(2);

            hLesser .setField(null, "symbol", xString.makeHandle(f_container, "<"));
            hEqual  .setField(null, "symbol", xString.makeHandle(f_container, "="));
            hGreater.setField(null, "symbol", xString.makeHandle(f_container, ">"));

            hLesser .setField(null, "reversed", hGreater);
            hEqual  .setField(null, "reversed", hEqual);
            hGreater.setField(null, "reversed", hLesser);
        }
    }

    @Override
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal) {
        return new EnumHandle(clz, iOrdinal);
    }

    public static EnumHandle makeHandle(Frame frame, long i) {
        return makeHandle(frame.container(), i);
    }

    public static EnumHandle makeHandle(Container container, long i) {
        return getInstance(container).getEnumByOrdinal(i < 0 ? 0 : i > 0 ? 2 : 1);
    }

    public static EnumHandle lesserHandle(Frame frame) {
        return makeHandle(frame, -1);
    }

    public static EnumHandle equalHandle(Frame frame) {
        return makeHandle(frame, 0);
    }

    public static EnumHandle equalHandle(Container container) {
        return makeHandle(container, 0);
    }

    public static EnumHandle greaterHandle(Frame frame) {
        return makeHandle(frame, 1);
    }

    public static boolean isLesser(ObjectHandle hValue) {
        return isOrdinal(hValue, 0);
    }

    public static boolean isEqual(ObjectHandle hValue) {
        return isOrdinal(hValue, 1);
    }

    public static boolean isGreater(ObjectHandle hValue) {
        return isOrdinal(hValue, 2);
    }

    private static boolean isOrdinal(ObjectHandle hValue, int iOrdinal) {
        return hValue instanceof EnumHandle hEnum &&
                hEnum.getOrdinal() == iOrdinal &&
                hEnum.getTemplate() instanceof xOrdered;
    }
}
