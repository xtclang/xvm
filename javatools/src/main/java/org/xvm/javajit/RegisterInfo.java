package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

/**
 * Represents an information about XTC register.
 */
public interface RegisterInfo {
    /**
     * @return the corresponding register id, which could be a negative value - one of the
     *         Op.A_ constants (e.g. {@link Op#A_THIS})
     */
    int regId();

    /**
     * @return the corresponding Java slot index or -1 if the corresponding value has been placed on
     *         the Java stack
     */
    int slot();

    /**
     * @return the XTC register type
     */
    TypeConstant type();

    /**
     * @return the JitFlavor for the register
     */
    JitFlavor flavor();

    /**
     * @return the jit type for XTC register
     */
    default TypeConstant jitType() {
        return type().getCanonicalJitType();
    }

    /**
     * @return the ClassDesc for the {@link #slot()}
     */
    ClassDesc cd();

    /**
     * @return the XTC register name (optional)
     */
    String name();

    /**
     * @return true iff the XTC register is represented by a single Java slot
     */
    boolean isSingle();

    /**
     * @return the original register, which could be different from "this" for narrowed registers
     */
    default RegisterInfo original() {
        return this;
    }

    /**
     * @return true iff the XTC register represents a value on Java stack
     */
    default boolean isJavaStack() {
        return regId() == JAVA_STACK;
    }

    /**
     * @return true iff the XTC register represents a value to be ignored
     */
    default boolean isIgnore() {
        return regId() == Op.A_IGNORE;
    }

    /**
     * @return true iff the XTC register represents a pre-defined "this" value
     */
    default boolean isThis() {
        return regId() == Op.A_THIS;
    }

    /**
     * Load the value for this register on the Java stack.
     *
     * @param type  (optional) the type of the value; could be wider than a narrowed register type
     */
    default RegisterInfo load(CodeBuilder code) {
        Builder.load(code, cd(), slot());
        return this;
    }

    /**
     * Store the value on the Java stack into the slot for this register.
     *
     * @param type  (optional) the type of the value; could be wider than a narrowed register type
     */
    default RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        if (isIgnore()) {
            Builder.pop(code, cd());
        } else {
            if (type == null) {
                type = type();
            }
            if (type.isPrimitive() && !cd().isPrimitive()) {
                Builder.box(code, type, JitTypeDesc.getPrimitiveClass(type));
            }
            Builder.store(code, cd(), slot());
        }
        return this;
    }

    /**
     * Mark this register as "changed". This may be used by implementations to ensure correct data
     * transfer.
     */
    default void markChanged() {}

    /**
     * Used for the value of the {@link #slot()} to indicates that the value is on the Java stack.
     */
    int JAVA_STACK = -1;
}
