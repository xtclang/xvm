package org.xvm.javajit.registers;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import java.util.Arrays;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.RegisterInfo;

import static org.xvm.javajit.JitFlavor.AlwaysNull;

/**
 * A register holding a narrowed value.
 *
 * @param regId       the register id
 * @param slots       the identifiers of the slots that store the value represented by this register
 * @param type        the {@link TypeConstant} of the value this register represents
 * @param flavor      the {@link JitFlavor} of the value this register represents
 * @param cd          the {@link ClassDesc} of the value this register represents
 * @param slotCds     the {@link ClassDesc} instances for each slot
 * @param name        the name of the value represented by this register
 * @param scopeDepth  the scope depth this narrowed register was introduced at
 * @param castOnLoad  if true, the register {@link #load} operation requires a cast
 * @param origReg     the original register info
 */
public record Narrowed(int regId, int[] slots, TypeConstant type, JitFlavor flavor,
                       ClassDesc cd, ClassDesc[] slotCds, String name, int scopeDepth,
                       boolean castOnLoad, RegisterInfo origReg)
    implements RegisterInfo {

    @Override
    public int slot() {
        return slots[0];
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public RegisterInfo original() {
        return origReg;
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        if (flavor == AlwaysNull) {
            Builder.loadNull(code);
        }
        else {
            for (int i = 0; i < slotCds.length; i++) {
                Builder.load(code, slotCds[i], slots[i]);
            }
            if (castOnLoad) {
                code.checkcast(cd);
            }
        }
        return this;
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        assert regId() > Op.CONSTANT_OFFSET; // cannot store a property register

        if (type == null) {
            RegisterInfo origReg = bctx.resetRegister(this);

            if (!this.sharesOriginalSlot()) {
                if (cd().isPrimitive()) {
                    if (origReg.cd().isPrimitive()) {
                        assert origReg instanceof ExtendedSlot;
                        code.iconst_0() // false
                            .istore(((ExtendedSlot) origReg).extSlot());
                    }
                    else {
                        Builder.box(code, type());
                    }
                }
                else if (type().isXvmPrimitive()) {
                    if (origReg.type().isXvmPrimitive()) {
                        assert origReg instanceof MultiSlot;
                        code.iconst_0() // false
                            .istore(((MultiSlot) origReg).extSlot());
                    }
                    else {
                        Builder.box(code, type());
                    }
                }
            }

            int[] slots = origReg.slots();
            ClassDesc[] cds = origReg.slotCds();
            // store slots in reverse order
            for (int i = slots.length - 1; i >= 0; i--) {
                Builder.store(code, cds[i], slots[i]);
            }
            return origReg;
        }

        if (type.isA(this.type())) {
            return RegisterInfo.super.store(bctx, code, type);
        }
        else {
            assert type.isA(original().type());
            RegisterInfo origReg = bctx.resetRegister(this).store(bctx, code, type);
            return bctx.narrowRegister(code, origReg, type);
        }
    }

    /**
     * @return {@code true} if this register shares the same slot as the original register
     */
    boolean sharesOriginalSlot() {
        return Arrays.equals(slots, origReg.slots());
    }

    /**
     * Widen this register to the specified type. This may require data transfer between Java
     * slots.
     */
    public RegisterInfo widen(BuildContext bctx, CodeBuilder code, TypeConstant wideType) {
        TypeConstant prevType = type();
        RegisterInfo origReg  = original();
        TypeConstant origType = origReg.type();

        assert !prevType.equals(wideType) && wideType.isA(origType);

        if (this.slot() != origReg.slot()) {
            load(code);

            if (cd().isPrimitive()) {
                if (origReg.cd().isPrimitive()) {
                    assert origReg instanceof ExtendedSlot;
                    code.iconst_0() // false
                        .istore(((ExtendedSlot) origReg).extSlot());
                }
                else {
                    Builder.box(code, prevType);
                }
            }
            int[] origSlots = origReg.slots();
            ClassDesc[] origCds = origReg.slotCds();
            for (int i = 0; i < origSlots.length; i++) {
                Builder.store(code, origCds[i], origSlots[i]);
            }
        }

        return wideType.equals(origType)
            ? origReg
            : bctx.narrowRegister(code, origReg, wideType).store(bctx, code, wideType);
    }

    @Override
    public String toString() {
        return "regId=" + regId
            + ", slots=" + Arrays.toString(slots)
            + ", flavor=" + flavor
            + ", type=" + type.getValueString()
            + ", slotCds=" + Arrays.toString(slotCds);
    }
}
