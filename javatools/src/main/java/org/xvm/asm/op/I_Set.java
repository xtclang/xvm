package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpIndex;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;

import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_nObj;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * I_SET rvalue-target, rvalue-ix, rvalue ; T[ix] = T
 */
public class I_Set
        extends OpIndex {
    /**
     * Construct an I_SET op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    public I_Set(Argument argTarget, Argument argIndex, Argument argValue) {
        super(argTarget, argIndex);

        m_argValue = argValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Set(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argValue != null) {
            m_nValue = encodeArgument(m_argValue, registry);
        }

        writePackedLong(out, m_nValue);
    }

    @Override
    public int getOpCode() {
        return OP_I_SET;
    }

    @Override
    protected boolean isAssignOp() {
        return false;
    }

    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nIndex, m_nValue}, 3);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], ahArg[1], ahArg[2]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return complete(frame, ahArg[0], ahArg[1], ahArg[2]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex, ObjectHandle hValue) {
        ClassTemplate template = hTarget.getTemplate();
        if (template instanceof IndexSupport) {
            return ((IndexSupport) template).
                assignArrayValue(frame, hTarget, ((JavaLong) hIndex).getValue(), hValue);
        }

        CallChain chain = getOpChain(frame, hTarget.getType());
        if (chain == null) {
            chain = template.findOpChain(hTarget, "[]=", new ObjectHandle[] {hIndex, hValue});
            if (chain == null) {
                return frame.raiseException("Invalid op: \"[]=\"");
            }
            saveOpChain(frame, hTarget.getType(), chain);
        }

        ObjectHandle[] ahVar = new ObjectHandle[Math.max(chain.getMaxVars(), 2)];
        ahVar[0] = hIndex;
        ahVar[1] = hValue;

        return chain.invoke(frame, hTarget, ahVar, Op.A_IGNORE);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected int getValueId() {
        return m_nValue;
    }

    /**
     * Build the operation to execute on an array element.
     * <p>
     * The array reference is already loaded onto the stack.
     *
     * @param bctx      the current {@link BuildContext}
     * @param code      the {@link CodeBuilder} to use to generate byte codes
     * @param regArray  the {@link RegisterInfo} for the array reference
     * @param typeEl    the {@link TypeConstant} of the array element
     */
    protected void buildPrimitiveArrayOp(BuildContext bctx, CodeBuilder code, RegisterInfo regArray,
                                         TypeConstant typeEl) {

        boolean javaPrimitive = typeEl.isJavaPrimitive();
        boolean xvmPrimitive  = typeEl.isXvmPrimitive();

        ClassDesc[] cdArgs;
        ClassDesc   cdEl;
        if (javaPrimitive) {
            cdEl   = JitTypeDesc.getPrimitiveClass(typeEl);
            cdArgs = new ClassDesc[]{CD_Ctx, CD_long, cdEl};
        } else if (xvmPrimitive) {
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(typeEl);
            cdEl   = cds[0];
            cdArgs = prependArgs(cds, CD_Ctx, CD_long);
        } else {
            cdEl = CD_nObj;
            cdArgs = new ClassDesc[]{CD_Ctx, CD_long, cdEl};
        }
        assert cdEl != null;

        bctx.loadCtx(code);
        bctx.loadArgument(code, m_nIndex);
        bctx.loadArgument(code, getValueId());
        code.invokevirtual(regArray.cd(), "setElement$pi", MethodTypeDesc.of(CD_void, cdArgs));
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nValue;

    private Argument m_argValue;
}