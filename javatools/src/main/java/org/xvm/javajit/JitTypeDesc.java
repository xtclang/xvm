package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_byte;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_short;

import static org.xvm.javajit.Builder.CD_Date;
import static org.xvm.javajit.Builder.CD_Dec128;
import static org.xvm.javajit.Builder.CD_Dec32;
import static org.xvm.javajit.Builder.CD_Dec64;
import static org.xvm.javajit.Builder.CD_Int128;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_UInt128;
import static org.xvm.javajit.Builder.CD_Duration;
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
            ? requireJavaPrimitive(type)
            : type.isSingleUnderlyingClass(true)
                ? builder.ensureClassDesc(type)
                : CD_Object;
    }

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a primitive Java
     *         class; null otherwise
     */
    public static ClassDesc getJavaPrimitive(TypeConstant type) {
        if (type.isJavaPrimitive()) {
            String name = type.getSingleUnderlyingClass(false).getName();
            return switch (name) {
                case "Bit", "Nibble", "Char", "Byte",
                     "Int8", "Int16", "Int32", "UInt8", "UInt16", "UInt32",
                     "Float8e4", "Float8e5"
                    -> CD_int;
                case "Int64", "UInt64"
                    -> CD_long;
                case "BFloat16", "Float16", "Float32"
                     -> CD_float;
                case "Float64"
                     -> CD_double;
                case "Boolean"
                    -> CD_boolean;
                // isJavaPrimitive() and this switch must list the same names
                default
                    -> throw new IllegalStateException("No Java primitive carrier for: " + name);
            };
        }
        return null;
    }

    /**
     * The same carrier as {@link #getJavaPrimitive}, for a type already known to have one.
     * Callers that have checked {@link TypeConstant#isJavaPrimitive} should use this, so they do
     * not have to answer for a null that cannot occur.
     *
     * @param type  a type optimizable to a primitive Java class
     *
     * @return the carrier, never null
     *
     * @throws IllegalArgumentException  if the type has no primitive Java carrier
     */
    public static ClassDesc requireJavaPrimitive(TypeConstant type) {
        ClassDesc cd = getJavaPrimitive(type);
        if (cd == null) {
            throw new IllegalArgumentException("Not a Java primitive type: " + type);
        }
        return cd;
    }

    /**
     * @return the primitive ClassDesc if the specified type is optimizable to a multi-slot
     *         primitive Java class; null otherwise
     */
    public static ClassDesc getNullablePrimitiveClass(TypeConstant type) {
        return type.isNullable()
            ? getJavaPrimitive(type.removeNullable())
            : null;
    }

    /**
     * @return the widening ClassDesc if the specified type needs to be widened; null otherwise
     */
    public static ClassDesc getWidenedClass(Builder builder, TypeConstant type) {
        return type.isSingleUnderlyingClass(true)
                ? null
                : ClassDesc.of(type.getCallableJitType().ensureJitClassName(builder.typeSystem));
    }

    public static ClassDesc getNullableXvmPrimitiveClass(TypeConstant type) {
        return type.isNullable()
                ? getXvmPrimitiveClass(type.removeNullable())
                : null;
    }

    public static ClassDesc getXvmPrimitiveClass(TypeConstant type) {
        if (type.isSingleUnderlyingClass(false)) {
            return switch (type.getSingleUnderlyingClass(false).getName()) {
                case "Dec32"    -> CD_Dec32;
                case "Dec64"    -> CD_Dec64;
                case "Dec128"   -> CD_Dec128;
                case "Int128"   -> CD_Int128;
                case "UInt128"  -> CD_UInt128;
                case "Date"     -> CD_Date;
                case "Duration" -> CD_Duration;
                default         -> null;
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
                case "Dec32", "Date" -> CDs_Int;
                case "Dec64" -> CDs_Long;
                case "Dec128", "Int128", "UInt128", "Duration" -> CDs_LongLong;
                default        -> {
                    ClassDesc cd = getJavaPrimitive(baseType);
                    if (cd == null) {
                        throw new IllegalArgumentException("Unsupported primitive: " + baseType);
                    }
                    yield new ClassDesc[]{cd};
                }
            };
        }
        throw new IllegalArgumentException("Unsupported primitive: " + baseType);
    }

    /**
     * @return the ClassDesc to use for a primitive field if the specified type is optimizable to a
     * single Java primitive ClassDesc; null otherwise
     */
    public static ClassDesc getPrimitiveFieldClass(TypeConstant type) {
        TypeConstant sansNullable = type.removeNullable();
        if (sansNullable.isJavaPrimitive()) {
            String name = sansNullable.getSingleUnderlyingClass(false).getName();
            // NOTE: isJavaPrimitive() and this switch must list the same names
            return switch (name) {
                case "Byte", "Nibble", "Int8", "UInt8", "Float8e4", "Float8e5" -> CD_byte;
                case "Int16", "UInt16"                                         -> CD_short;
                case "Char", "Int32", "UInt32"                                 -> CD_int;
                case "Int64", "UInt64"                                         -> CD_long;
                case "BFloat16", "Float16", "Float32"                          -> CD_float;
                case "Float64"                                                 -> CD_double;
                case "Boolean", "Bit"                                          -> CD_boolean;
                default -> throw new IllegalStateException("No field carrier for: " + name);
            };
        }
        return null;
    }

    /**
     * The same carrier as {@link #getPrimitiveFieldClass}, for a type already known to have one.
     * Callers that have checked {@link TypeConstant#isJavaPrimitive} should use this, so they do
     * not have to answer for a null that cannot occur.
     *
     * @param type  a type with a Java primitive carrier; a nullable form is accepted
     *
     * @return the carrier, never null
     *
     * @throws IllegalArgumentException  if the type has no Java primitive carrier
     */
    public static ClassDesc requirePrimitiveFieldClass(TypeConstant type) {
        ClassDesc cd = getPrimitiveFieldClass(type);
        if (cd == null) {
            throw new IllegalArgumentException("Not a Java primitive type: " + type);
        }
        return cd;
    }
}
