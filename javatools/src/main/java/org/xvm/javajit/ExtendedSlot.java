package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Parameter;
import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static org.xvm.javajit.JitFlavor.Primitive;

/**
 * A register that stores an XVM value in two Java slots, where the second is always a boolean.
 *
 * @param bctx    the {@link BuildContext} associated with this register
 * @param regId   the register id
 * @param slot    the Java slot that stores the primitive value
 * @param extSlot the Java slot that stores an additional boolean flag
 * @param flavor  the {@link JitFlavor} of the value this register represents
 * @param type    the {@link TypeConstant} of the value this register represents
 * @param cd      the {@link ClassDesc} of the value this register represents
 * @param name    the name of the value represented by this register
 */
public record ExtendedSlot(BuildContext bctx, int regId, int slot, int extSlot,
                           JitFlavor flavor, TypeConstant type, ClassDesc cd, String name)
        implements RegisterInfo {

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
        return false;
    }

    @Override
    public RegisterInfo load(CodeBuilder code) {
        switch (flavor) {
        case PrimitiveWithDefault:
            Parameter parameter = bctx.methodStruct.getParam(regId);
            assert parameter.hasDefaultValue();

            Label ifTrue = code.newLabel();
            Label endIf = code.newLabel();

            // if the extension slot is `true`, take the default value
            code.iload(extSlot)
                .ifne(ifTrue)
                .loadLocal(Builder.toTypeKind(cd), slot)
                .goto_(endIf)
                .labelBinding(ifTrue);
            bctx.builder.loadConstant(bctx, code, parameter.getDefaultValue());
            code.labelBinding(endIf);
            return new SingleSlot(type(), Primitive, cd, name());

        case NullablePrimitive:
            // load the "extension" boolean flag last
            Builder.load(code, cd, slot);
            Builder.load(code, CD_boolean, extSlot);
            return this;

        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public RegisterInfo store(BuildContext bctx, CodeBuilder code, TypeConstant type) {
        // store the "extension" boolean flag first
        code.istore(extSlot());

        return RegisterInfo.super.store(bctx, code, type);
    }
}
