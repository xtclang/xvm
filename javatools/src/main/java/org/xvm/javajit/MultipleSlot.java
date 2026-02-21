package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.XvmPrimitive;

/**
 * A register that stores an XVM value in multiple Java slots.
 *
 * @param bctx     the {@link BuildContext} associated with this register
 * @param regId    the register id
 * @param slots    the identifiers of the slots that store the value represented by this register
 * @param extSlot  the identifier of the slot that stores an additional boolean flag
 * @param flavor   the {@link JitFlavor} of the value this register represents
 * @param type     the {@link TypeConstant} of the value this register represents
 * @param cd       the {@link ClassDesc} of the value this register represents
 * @param slotCds  the {@link ClassDesc} instances for each slot
 * @param name     the name of the value represented by this register
 */
public record MultipleSlot(BuildContext bctx, int regId, int[] slots, int extSlot,
                           JitFlavor flavor, TypeConstant type, ClassDesc cd,
                           ClassDesc[] slotCds, String name)
        implements RegisterInfo {

    /**
     * An {@code int} value to indicate that the extSlot is not used.
     */
    public static final int NO_SLOT = Integer.MIN_VALUE;

    /**
     * Create a {@link MultipleSlot} representing a value stored on the Java stack.
     *
     * @param bctx     the {@link BuildContext} associated with this register
     * @param flavor   the {@link JitFlavor} of the value this register represents
     * @param type     the {@link TypeConstant} of the value this register represents
     * @param cd       the {@link ClassDesc} of the value this register represents
     * @param cdSlots  the {@link ClassDesc} instances for each slot
     */
    public MultipleSlot(BuildContext bctx, JitFlavor flavor, TypeConstant type, ClassDesc cd,
                        ClassDesc[] cdSlots) {
        this(bctx, Op.A_STACK, null, NO_SLOT, flavor, type, cd, cdSlots, "");
    }

    /**
     * Create a {@link MultipleSlot} representing a value stored on the Java stack.
     *
     * @param bctx     the {@link BuildContext} associated with this register
     * @param regId    the identifier of the register
     * @param slots    the identifiers of the slots that store the value represented by this register
     * @param flavor   the {@link JitFlavor} of the value this register represents
     * @param type     the {@link TypeConstant} of the value this register represents
     * @param cd       the {@link ClassDesc} of the value this register represents
     * @param cdSlots  the {@link ClassDesc} instances for each slot
     * @param name     the name of the value represented by this register
     */
    public MultipleSlot(BuildContext bctx, int regId, int[] slots, JitFlavor flavor,
                        TypeConstant type, ClassDesc cd, ClassDesc[] cdSlots, String name) {
        this(bctx, regId, slots, NO_SLOT, flavor, type, cd, cdSlots, name);
    }

    /**
     * Create a {@link MultipleSlot} representing a value stored on the Java stack.
     *
     * @param bctx     the {@link BuildContext} associated with this register
     * @param regId    the identifier of the register
     * @param slots    the identifiers of the slots that store the value represented by this register
     * @param extSlot  the identifier of the slot that stores an additional boolean flag
     * @param flavor   the {@link JitFlavor} of the value this register represents
     * @param type     the {@link TypeConstant} of the value this register represents
     * @param cd       the {@link ClassDesc} of the value this register represents
     * @param slotCds  the {@link ClassDesc} instances for each slot
     * @param name     the name of the value represented by this register
     */
    public MultipleSlot(BuildContext bctx, int regId, int[] slots, int extSlot,
                        JitFlavor flavor, TypeConstant type, ClassDesc cd,
                        ClassDesc[] slotCds, String name) {
        assert flavor == XvmPrimitive || flavor == NullableXvmPrimitive;

        this.bctx    = bctx;
        this.regId   = regId;
        this.extSlot = extSlot;
        this.flavor  = Objects.requireNonNull(flavor);
        this.type    = Objects.requireNonNull(type.getCanonicalJitType());
        this.cd      = Objects.requireNonNull(cd);
        this.slotCds = Objects.requireNonNull(slotCds);
        this.name    = name;

        if (slots == null) {
            this.slots = new int[slotCds.length];
            Arrays.fill(this.slots, JAVA_STACK);
        } else {
            assert slots.length == slotCds.length;
            this.slots = slots;
        }
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public int slot() {
        return slots[0];
    }

    /**
     * @return the number of slots used by this register
     */
    public int slotCount() {
        return slots.length;
    }

    /**
     * Obtain the slot at the specified index.
     *
     * @param index the index of the slot to return
     * @return the slot at the specified index
     */
    public int slot(int index) {
        return slots[index];
    }

    /**
     * Obtain the class descriptor at the specified index.
     *
     * @param index the index of the class descriptor to return
     * @return the class descriptor at the specified index
     */
    public ClassDesc cd(int index) {
        return slotCds[index];
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        assert flavor == XvmPrimitive || flavor == NullableXvmPrimitive;

        for (int i = 0; i < slotCds.length; i++) {
            Builder.load(code, slotCds[i], slots[i]);
        }

        if (extSlot != NO_SLOT) {
            // load the "extension" boolean flag last
            Builder.load(code, CD_boolean, extSlot);
        }
        return this;
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        if (extSlot != NO_SLOT) {
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
    public RegisterInfo storeTempValue(BuildContext bctx, CodeBuilder code, int regId) {
        assert isJavaStack(); // constant

        boolean hasExtSlot = extSlot != NO_SLOT;
        int     slotCount  = hasExtSlot ? slots.length + 1 : slots.length;
        int[]   javaSlots  = new int[slotCount];
        int     lastIx     = slotCount - 1;

        if (hasExtSlot) {
            javaSlots[lastIx] = bctx.scope.allocateJavaSlot(slotCds[lastIx]);
            Builder.store(code, slotCds[lastIx], javaSlots[lastIx]);
            lastIx--;
        }

        // the stack will contain the value in multiple slots, they need to be stored in reverse
        for (; lastIx >= 0; lastIx--) {
            javaSlots[lastIx] = bctx.scope.allocateJavaSlot(slotCds[lastIx]);
            Builder.store(code, slotCds[lastIx], javaSlots[lastIx]);
        }
        return new MultipleSlot(bctx, regId, javaSlots, extSlot, flavor, type, cd, slotCds, name);
    }
}
