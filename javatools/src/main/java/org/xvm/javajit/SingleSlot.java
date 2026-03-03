package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;
import org.xvm.asm.constants.TypeConstant;

/**
 * A register that stores an XVM value in a single Java slot.
 *
 * @param regId   the register id
 * @param slot    the Java slot that stores the primitive value
 * @param flavor  the {@link JitFlavor} of the value this register represents
 * @param type    the {@link TypeConstant} of the value this register represents
 * @param cd      the {@link ClassDesc} of the value this register represents
 * @param name    the name of the value represented by this register
 */
public record SingleSlot(int regId, int slot, JitFlavor flavor, TypeConstant type, ClassDesc cd,
                         String name)
        implements RegisterInfo {

    /**
     * Construct the SingleSlot representing a value placed on the Java stack.
     */
    public SingleSlot(TypeConstant type, JitFlavor flavor, ClassDesc cd, String name) {
        this(Op.A_STACK, JAVA_STACK, flavor, type, cd, name);
    }

    @Override
    public int[] slots() {
        return new int[]{slot};
    }

    @Override
    public ClassDesc[] slotCds() {
        return new ClassDesc[]{cd};
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
