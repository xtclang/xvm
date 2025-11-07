package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

/**
 * Represents an information about XTC register.
 */
public interface RegisterInfo {
    /**
     * @return the corresponding Java slot index
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
     * @return true iff the XTC register represents a value to be ignored
     */
    default boolean isIgnore() {
        return slot() == Op.A_IGNORE;
    }

    /**
     * @return true iff the XTC register represents a pre-defined "this" value
     */
    default boolean isThis() {
        return slot() == Op.A_THIS;
    }
}
