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
 * PIP_AND PROPERTY, rvalue-target, rvalue2 ; T &= T
 */
public class PIP_And
        extends OpPropInPlaceAssign
        implements NumberSupport {
    /**
     * Construct a PIP_AND op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argValue   the value Argument
     */
    public PIP_And(PropertyConstant idProp, Argument argTarget, Argument argValue) {
        super(idProp, argTarget, argValue);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_And(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_PIP_AND;
    }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue) {
        return hTarget.getTemplate().invokePropertyAnd(frame, hTarget, idProp, hValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected void buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget, RegisterInfo regArg) {
        buildPrimitiveAnd(bctx, code, regTarget);
    }

    @Override
    protected TypeConstant buildXvmOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                                   RegisterInfo regTarget, int argValue) {
        buildXvmPrimitiveAnd(bctx, code, regTarget, argValue);
        return regTarget.type();
    }
}
