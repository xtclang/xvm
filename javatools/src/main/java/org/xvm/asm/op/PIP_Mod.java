package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlaceAssign;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

/**
 * PIP_MOD PROPERTY, rvalue-target, rvalue2 ; T %= T
 */
public class PIP_Mod
        extends OpPropInPlaceAssign
        implements NumberSupport {
    /**
     * Construct a PIP_MOD op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argValue   the value Argument
     */
    public PIP_Mod(PropertyConstant idProp, Argument argTarget, Argument argValue) {
        super(idProp, argTarget, argValue);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Mod(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_PIP_MOD;
    }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue) {
        return hTarget.getTemplate().invokePropertyMod(frame, hTarget, idProp, hValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected void buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget, RegisterInfo regArg) {
        buildPrimitiveMod(bctx, code, regTarget);
    }

    @Override
    protected TypeConstant buildOptimizedNumber(BuildContext bctx, CodeBuilder code,
                                                   RegisterInfo regTarget, int argValue) {
        buildXvmPrimitiveMod(bctx, code, regTarget, argValue);
        return regTarget.type();
    }
}
