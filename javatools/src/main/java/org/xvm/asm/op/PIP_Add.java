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
import org.xvm.javajit.TextSupport;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

/**
 * PIP_ADD PROPERTY, rvalue-target, rvalue2 ; T += T
 */
public class PIP_Add
        extends OpPropInPlaceAssign
        implements NumberSupport, TextSupport {
    /**
     * Construct a PIP_ADD op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argValue   the value Argument
     */
    public PIP_Add(PropertyConstant idProp, Argument argTarget, Argument argValue) {
        super(idProp, argTarget, argValue);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Add(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_PIP_ADD;
    }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue) {
        return hTarget.getTemplate().invokePropertyAdd(frame, hTarget, idProp, hValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected TypeConstant buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                                RegisterInfo regTarget, int argValue) {
        if (regTarget.type().isA(bctx.pool().typeChar())) {
            return buildAddToChar(bctx, code, regTarget, argValue);
        }
        return super.buildOptimizedBinary(bctx, code, regTarget, argValue);
    }

    @Override
    protected void buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget, RegisterInfo regArg) {
        buildPrimitiveAdd(bctx, code, regTarget);
    }

    @Override
    protected TypeConstant buildOptimizedNumber(BuildContext bctx, CodeBuilder code,
                                                   RegisterInfo regTarget, int argValue) {
        buildXvmPrimitiveAdd(bctx, code, regTarget, argValue);
        return regTarget.type();
    }
}
