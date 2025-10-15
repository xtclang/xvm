package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_xObj;

/**
 * Representation of an Ecstasy type in Java.
 */
public class JitTypeDesc {

    public JitTypeDesc(TypeConstant type, JitFlavor flavor, ClassDesc cd) {
        this.type   = type;
        this.flavor = flavor;
        this.cd     = cd;
    }

    public final TypeConstant type;
    public final JitFlavor    flavor;
    public final ClassDesc    cd;

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a primitive Java
     *         class; and a corresponding non-primitive ClassDesc otherwise.
     */
    public static ClassDesc getJitClass(TypeSystem ts, TypeConstant type) {
        return type.isPrimitive()
            ? JitParamDesc.getPrimitiveClass(type)
            : type.isSingleUnderlyingClass(true)
                ? type.ensureClassDesc(ts)
                : CD_xObj;
    }

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a primitive Java
     *         class; null otherwise
     */
    public static ClassDesc getPrimitiveClass(TypeConstant type) {
        if (type.isPrimitive()) {
            return switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Char", "Int8", "Int16", "Int32", "UInt8", "UInt16", "UInt32"
                    -> CD_int;
                case "Int64", "UInt64"
                    -> CD_long;
                case "Boolean"
                    -> CD_boolean;
                default
                    -> null;
            };
        }
        return null;
    }

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a multi-slot
     *         primitive Java class; null otherwise
     */
    public static ClassDesc getMultiSlotPrimitiveClass(TypeConstant type) {
        return type.isNullable()
            ? getPrimitiveClass(type.removeNullable())
            : null;
    }

    /**
     * @return the widening ClassDesc if the specified type needs to be widened; null otherwise
     */
    public static ClassDesc getWidenedClass(TypeConstant type) {
        if (!type.isSingleUnderlyingClass(true)) {
            // TODO: this could be more specific
            return CD_xObj;
        }
        return null;
    }
}
