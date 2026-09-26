package org.xvm.javajit.registers;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.XvmPrimitive;

/**
 * A register that stores an XVM value in multiple Java slots.
 */
public class MultiSlot
        extends AbstractRegisterInfo {

    /**
     * An {@code int} value to indicate that the extSlot is not used.
     */
    public static final int NO_EXT = Integer.MIN_VALUE;

    /**
     * Create a {@link MultiSlot} representing a value stored on the Java stack.
     *
     * @param flavor   the {@link JitFlavor} of the value this register represents
     * @param type     the {@link TypeConstant} of the value this register represents
     * @param cd       the {@link ClassDesc} of the value this register represents
     * @param cdSlots  the {@link ClassDesc} instances for each slot
     */
    public MultiSlot(JitFlavor flavor, TypeConstant type, ClassDesc cd, ClassDesc[] cdSlots) {
        this(Op.A_STACK, null, NO_EXT, flavor, type, cd, cdSlots, "");
    }

    /**
     * Create a {@link MultiSlot} representing a value stored on the Java stack.
     *
     * @param regId    the identifier of the register
     * @param slots    the identifiers of the slots that store the value represented by this register
     * @param extSlot  the identifier of the slot that stores an additional boolean flag
     * @param flavor   the {@link JitFlavor} of the value this register represents
     * @param type     the {@link TypeConstant} of the value this register represents
     * @param cd       the {@link ClassDesc} of the value this register represents
     * @param slotCds  the {@link ClassDesc} instances for each slot
     * @param name     the name of the value represented by this register
     */
    public MultiSlot(int regId, int[] slots, int extSlot, JitFlavor flavor, TypeConstant type,
                     ClassDesc cd, ClassDesc[] slotCds, String name) {
        super(regId, Objects.requireNonNull(flavor), Objects.requireNonNull(type),
                Objects.requireNonNull(cd), name);
        assert flavor == XvmPrimitive || flavor == NullableXvmPrimitive;

        this.extSlot = extSlot;
        this.slotCds = Objects.requireNonNull(slotCds);

        if (slots == null) {
            this.slots = new int[slotCds.length];
            Arrays.fill(this.slots, JAVA_STACK);
        } else {
            assert slots.length == slotCds.length;
            this.slots = slots;
        }
    }

    /**
     * @return the number of slots used by this register
     */
    public int slotCount() {
        return slots.length;
    }

    /**
     * @return the slot at the specified index
     */
    public int slot(int index) {
        return slots[index];
    }

    /**
     * @return the slot for the additional boolean flag, or {@link #NO_EXT} if none
     */
    public int extSlot() {
        return extSlot;
    }

    /**
     * @return the class descriptor at the specified index
     */
    public ClassDesc cd(int index) {
        return slotCds[index];
    }

    @Override
    public int[] slots() {
        return slots;
    }

    @Override
    public ClassDesc[] slotCds() {
        return slotCds;
    }

    @Override
    public boolean isSingle() {
        return slots.length == 1;
    }

    @Override
    public int slot() {
        return slots[0];
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        assert flavor == XvmPrimitive || flavor == NullableXvmPrimitive;

        for (int i = 0; i < slotCds.length; i++) {
            Builder.load(code, slotCds[i], slots[i]);
        }

        if (extSlot != NO_EXT) {
            // load the "extension" boolean flag last
            Builder.load(code, CD_boolean, extSlot);
        }
        return this;
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        assert regId() > Op.CONSTANT_OFFSET; // cannot store a property register

        if (extSlot != NO_EXT) {
            if (type != null && type.isXvmPrimitive()) {
                // a non-null result has only its primitive components on the stack
                code.iconst_0();
            }
            // store the "extension" boolean flag first
            code.istore(extSlot);
        }

        if (isIgnore()) {
            // pop stack in reverse CD order
            for (int i = slotCds.length - 1; i >= 0; i--) {
                Builder.pop(code, slotCds[i]);
            }
        } else {
            // store slots in reverse order
            for (int i = slotCds.length - 1; i >= 0; i--) {
                Builder.store(code, slotCds[i], slots[i]);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return super.toString()
            + ", slots="   + Arrays.toString(slots)
            + ", slotCds=" + Arrays.toString(slotCds)
            + ", extSlot=" + extSlot;
    }

    private final int[]       slots;
    private final int         extSlot;
    private final ClassDesc[] slotCds;
}
