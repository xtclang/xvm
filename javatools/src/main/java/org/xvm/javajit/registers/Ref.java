package org.xvm.javajit.registers;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;

import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_nRef;

/**
 * A register that holds a Ref to an underlying value.
 *
 * @param bctx            the {@link BuildContext} associated with this register
 * @param regId           the register id
 * @param slot            the Java slot that stores the primitive value
 * @param name            the register name
 * @param isVar           true for a Var; false for a Ref
 * @param referentType    the {@link TypeConstant} for the referent
 * @param referentFlavor  the flavor of the referent type
 * @param origRef         if not null, the original Ref this Ref is narrowing
 */
public record Ref(BuildContext bctx, int regId, int slot, String name, boolean isVar,
                  TypeConstant referentType, JitFlavor referentFlavor, Ref origRef)
        implements RegisterInfo {

    /**
     * Create an initial Ref.
     */
    public Ref(BuildContext bctx, int regId, int slot, String name, boolean isVar,
                  TypeConstant referentType, JitFlavor referentFlavor) {
        this(bctx, regId, slot, name, isVar, referentType, referentFlavor, null);
    }

    /**
     * Create a narrowing Ref.
     */
    private Ref(Ref origRef, TypeConstant narrowedType) {
        this(origRef.bctx, origRef.regId, origRef.slot, origRef.name, origRef.isVar,
            narrowedType, origRef.referentFlavor, origRef);
    }

    /**
     * Narrow this Ref to the specified referent type.
     */
    public Ref narrow(TypeConstant narrowedType) {
        return new Ref(this, narrowedType);
    }

    /**
     * @return the Ref type
     */
    public TypeConstant refType() {
        ConstantPool pool = bctx.pool();
        return pool.ensureParameterizedTypeConstant(
                isVar ? pool.typeVar() : pool.typeRef(), referentType);
    }

    @Override
    public TypeConstant type() {
        return referentType;
    }

    @Override
    public JitFlavor flavor() {
        return JitFlavor.Ref;
    }

    @Override
    public ClassDesc cd() {
        return CD_nRef;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        ClassDesc referentCd = bctx.builder.ensureClassDesc(referentType);
        String    refName    = "⅋" + name;

        RegisterInfo.super.load(code);
        bctx.loadCtx(code);
        code.invokevirtual(CD_nRef, "get", MethodTypeDesc.of(CD_Object, CD_Ctx));
        code.checkcast(referentCd);
        return switch (referentFlavor) {
            case Specific, Widened ->
                new SingleSlot(referentType, referentFlavor, referentCd, refName);

            case Primitive -> {
                Builder.unbox(code, referentType);
                yield new SingleSlot(referentType, referentFlavor,
                    JitTypeDesc.getPrimitiveClass(referentType), refName);
            }

            default -> throw new UnsupportedOperationException("flavor: " + referentFlavor);
        };
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        // the actual value is on the Java stack
        switch (referentFlavor) {
            case Specific, Widened -> {}

            case Primitive
                -> Builder.box(code, referentType);

            default
                -> throw new UnsupportedOperationException("flavor: " + referentFlavor);
        }

        int tempSlot = bctx.storeTempValue(code, bctx.builder.ensureClassDesc(type));
        if (bctx.isAssigned(this)) {
            RegisterInfo.super.load(code); // nRef
            bctx.loadCtx(code)
                .aload(tempSlot)
                .invokevirtual(CD_nRef, "set", MethodTypeDesc.of(CD_void, CD_Ctx, CD_Object));

            if (!type.isA(referentType)) {
                return bctx.narrowRegister(code, origRef, type);
            }
        }
        else {
            // the Ref has not been created yet
            bctx.buildCreateRef(code, type, isVar, () -> code.aload(tempSlot));
            code.astore(slot());
        }
        return this;
    }

    @Override
    public String toString() {
        return "regId="      + regId
            + ", slot="      + slot
            + ", flavor="    + flavor()
            + ", cd="        + cd()
            + ", name="      + name
            + ", refFlavor=" + referentFlavor
            + ", refType="   + referentType.getValueString()
            + (origRef == null ? "" : ", origRefType=" + origRef.referentType.getValueString()
        );
    }
}
