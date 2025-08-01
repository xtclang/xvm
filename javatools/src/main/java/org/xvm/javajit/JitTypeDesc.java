package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.IdentityConstant;
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
     *         class; null otherwise
     */
    public static ClassDesc getPrimitiveClass(TypeConstant type) {
        ConstantPool pool = type.getConstantPool();

        if (type.isSingleUnderlyingClass(false)) {
            IdentityConstant id = type.getSingleUnderlyingClass(false);
            if (id.getName().startsWith("Int")) {
                if (type.equals(pool.typeInt64()) || type.equals(pool.typeInt32()) ||
                    type.equals(pool.typeInt16()) || type.equals(pool.typeInt8())) {
                    // TODO: use "int" and "byte" for lower arity types
                    return CD_long;
                }
            } else if (id.getName().startsWith("UInt")) {
                if (type.equals(pool.typeUInt64()) || type.equals(pool.typeUInt32()) ||
                    type.equals(pool.typeUInt16()) || type.equals(pool.typeUInt8())) {

                    return CD_long;
                }
            } else if (type.equals(pool.typeBoolean())) {

                return CD_boolean;
            } else if (type.equals(pool.typeChar())) {

                return CD_int;
            }
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
