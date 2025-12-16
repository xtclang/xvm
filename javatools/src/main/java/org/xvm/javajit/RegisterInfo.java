package org.xvm.javajit;

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
     * @return the Java slot ClassDesc
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
     * Used for the value of the {@link #slot()} to indicates that the value is on the Java stack.
     */
    int JAVA_STACK = -1;

    /**
     * Used for the value of the {@link #slot()} to indicates that there is no slot for the value.
     */
    int NONE = -2;
}
