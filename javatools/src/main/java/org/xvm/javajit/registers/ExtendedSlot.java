package org.xvm.javajit.registers;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.JitFlavor.NullablePrimitive;

/**
 * A register that stores an XVM value in two Java slots, where the second is always a boolean.
 */
public class ExtendedSlot
        extends AbstractRegisterInfo {

    /**
     * Construct an ExtendedSlot.
     *
     * @param regId   the register id
     * @param slot    the Java slot that stores the primitive value
     * @param extSlot the Java slot that stores an additional boolean flag
     * @param flavor  the {@link JitFlavor} of the value this register represents
     * @param type    the {@link TypeConstant} of the value this register represents
     * @param cd      the {@link ClassDesc} of the value this register represents
     * @param name    the name of the value represented by this register
     */
    public ExtendedSlot(int regId, int slot, int extSlot, JitFlavor flavor, TypeConstant type,
                        ClassDesc cd, String name) {
        super(regId, flavor, type, cd, name);
        this.slot    = slot;
        this.extSlot = extSlot;
    }

    /**
     * @return the slot for the additional boolean flag
     */
    public int extSlot() {
        return extSlot;
    }

    @Override
    public int slot() {
        return slot;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        assert flavor == NullablePrimitive;

        // load the "extension" boolean flag last
        Builder.load(code, cd, slot);
        Builder.load(code, CD_boolean, extSlot);
        return this;
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        assert regId() > Op.CONSTANT_OFFSET; // cannot store a property register
        if (type != null && type.isJavaPrimitive()) {
            // a non-null result (e.g. Int? n = new Int("42")) has no null flag on the stack
            code.iconst_0();
        }
        // store the "extension" boolean flag first
        code.istore(extSlot());
        return super.store(bctx, code, type);
    }

    @Override
    public String toString() {
        return super.toString()
            + ", slot="    + slot
            + ", extSlot=" + extSlot;
    }

    private final int slot;
    private final int extSlot;
}
