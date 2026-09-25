package org.xvm.javajit.registers;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitFlavor;

/**
 * A register that stores an XVM value in a single Java slot.
 */
public class SingleSlot
        extends AbstractRegisterInfo {

    /**
     * The canonical constructor.
     *
     * @param regId   the register id
     * @param slot    the Java slot that stores the underlying value
     * @param flavor  the {@link JitFlavor} of the value this register represents
     * @param type    the {@link TypeConstant} of the value this register represents
     * @param cd      the {@link ClassDesc} of the value this register represents
     * @param name    the name of the value represented by this register
     */
    public SingleSlot(int regId, int slot, JitFlavor flavor, TypeConstant type, ClassDesc cd, String name) {
        super(regId, flavor, type.removeAutoNarrowing(), cd, name);
        this.slot = slot;
    }

    /**
     * Construct the SingleSlot representing a value placed on the Java stack.
     */
    public SingleSlot(TypeConstant type, JitFlavor flavor, ClassDesc cd, String name) {
        this(Op.A_STACK, JAVA_STACK, flavor, type, cd, name);
    }

    @Override
    public int slot() {
        return slot;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + ", slot=" + slot;
    }

    private final int slot;
}
