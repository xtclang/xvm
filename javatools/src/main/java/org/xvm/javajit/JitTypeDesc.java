package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Dec128;
import static org.xvm.javajit.Builder.CD_Dec32;
import static org.xvm.javajit.Builder.CD_Dec64;
import static org.xvm.javajit.Builder.CD_Int128;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_UInt128;
import static org.xvm.javajit.Builder.CDs_Int;
import static org.xvm.javajit.Builder.CDs_Long;
import static org.xvm.javajit.Builder.CDs_LongLong;

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
    public static ClassDesc getJitClass(Builder builder, TypeConstant type) {
        return type.isJavaPrimitive()
            ? JitParamDesc.getPrimitiveClass(type)
            : type.isSingleUnderlyingClass(true)
                ? builder.ensureClassDesc(type)
                : CD_Object;
    }

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a primitive Java
     *         class; null otherwise
     */
    public static ClassDesc getPrimitiveClass(TypeConstant type) {
        if (type.isJavaPrimitive()) {
            return switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Bit", "Nibble", "Char", "Byte",
                     "Int8", "Int16", "Int32", "UInt8", "UInt16", "UInt32"
                    -> CD_int;
                case "Int64", "UInt64"
                    -> CD_long;
                case "Float16", "Float32"
                     -> CD_float;
                case "Float64"
                     -> CD_double;
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
    public static ClassDesc getNullablePrimitiveClass(TypeConstant type) {
        return type.isNullable()
            ? getPrimitiveClass(type.removeNullable())
            : null;
    }

    /**
     * @return the widening ClassDesc if the specified type needs to be widened; null otherwise
     */
    public static ClassDesc getWidenedClass(Builder builder, TypeConstant type) {
        return type.isSingleUnderlyingClass(true)
                ? null
                : ClassDesc.of(type.getCanonicalJitType().ensureJitClassName(builder.typeSystem));
    }

    public static ClassDesc getNullableXvmPrimitiveClass(TypeConstant type) {
        return type.isNullable()
                ? getXvmPrimitiveClass(type.removeNullable())
                : null;
    }

    public static ClassDesc getXvmPrimitiveClass(TypeConstant type) {
        if (type.isSingleUnderlyingClass(false)) {
            return switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Dec32"   -> CD_Dec32;
                case "Dec64"   -> CD_Dec64;
                case "Dec128"  -> CD_Dec128;
                case "Int128"  -> CD_Int128;
                case "UInt128" -> CD_UInt128;
                default        -> null;
            };
        }
        return null;
    }

    public static int getXvmPrimitiveSlotCount(TypeConstant type) {
        return getXvmPrimitiveClasses(type).length;
    }

    public static ClassDesc[] getXvmPrimitiveClasses(TypeConstant type) {
        TypeConstant baseType = type.removeNullable();
        if (baseType.isSingleUnderlyingClass(false)) {
            return switch (baseType.getSingleUnderlyingClass(false).getName()) {
                case "Dec32" -> CDs_Int;
                case "Dec64" -> CDs_Long;
                case "Dec128", "Int128", "UInt128" -> CDs_LongLong;
                default        -> {
                    ClassDesc cd = getPrimitiveClass(baseType);
                    if (cd == null) {
                        throw new IllegalArgumentException("Unsupported primitive: " + baseType);
                    }
                    yield new ClassDesc[]{cd};
                }
            };
        }
        throw new IllegalArgumentException("Unsupported primitive: " + baseType);
    }
}
