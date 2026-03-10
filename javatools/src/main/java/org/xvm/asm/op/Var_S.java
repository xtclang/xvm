package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nArrayObj;
import static org.xvm.javajit.Builder.CD_nObj;


/**
 * VAR_S TYPE, #values:(rvalue-src) ; next register is an initialized anonymous Array variable
 */
public class Var_S
        extends OpVar {
    /**
     * Construct a VAR_S op for the specified register and arguments.
     *
     * @param reg        the register
     * @param aArgValue  the value argument
     */
    public Var_S(Register reg, Argument[] aArgValue) {
        super(reg);

        if (aArgValue == null) {
            throw new IllegalArgumentException("values required");
        }

        m_aArgValue = aArgValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_S(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgValue != null) {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
        }

        writeIntArray(out, m_anArgValue);
    }

    @Override
    public int getOpCode() {
        return OP_VAR_S;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, m_anArgValue.length);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, iPC, ahArg);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return complete(frame, iPC, ahArg);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, int iPC, ObjectHandle[] ahArg) {
        boolean fImmutable = true;
        for (ObjectHandle hValue : ahArg) {
            if (!hValue.isPassThrough()) {
                fImmutable = false;
                break;
            }
        }

        TypeConstant    typeList = frame.resolveType(m_nType);
        TypeComposition clzArray = getArrayClass(frame, typeList);

        ArrayHandle hArray = xArray.makeArrayHandle(clzArray, ahArg.length, ahArg,
                fImmutable ? Mutability.Constant : Mutability.Persistent);

        if (typeList.isA(frame.poolContext().typeSet())) {
            frame.introduceResolvedVar(m_nVar, typeList, null, Frame.VAR_STANDARD, null);
            return xArray.createListSet(frame, hArray, m_nVar);
        }

        frame.introduceResolvedVar(m_nVar, typeList, null, Frame.VAR_STANDARD, hArray);
        return iPC + 1;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        buildArray(bctx, code, m_anArgValue, "");
        return -1;
    }

    private static final MethodTypeDesc MD_newArray
            = MethodTypeDesc.of(CD_nArrayObj, CD_Ctx, CD_TypeConstant, CD_long, CD_boolean);

    private static final MethodTypeDesc MD_add = MethodTypeDesc.of(CD_nArrayObj, CD_Ctx, CD_nObj);

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
}