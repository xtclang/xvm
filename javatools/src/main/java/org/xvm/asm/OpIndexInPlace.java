package org.xvm.asm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Base class for I_ (index based) and IIP_ (index based in-place) op codes.
 */
public abstract class OpIndexInPlace
        extends OpIndex {
    /**
     * Construct an "index based" op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    protected OpIndexInPlace(Argument argTarget, Argument argIndex, Argument argValue) {
        super(argTarget, argIndex);

        m_argValue = argValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpIndexInPlace(DataInput in, Constant[] aconst)
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
    protected boolean isAssignOp() {
        return false;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nIndex, m_nValue}, 3);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], (JavaLong) ahArg[1], ahArg[2]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

            return complete(frame, ahArg[0], (JavaLong) ahArg[1], ahArg[2]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
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

    @Override
    protected void buildPrimitiveArrayOp(BuildContext bctx, CodeBuilder code, RegisterInfo regArray,
                                         TypeConstant typeEl) {

        RegisterInfo regElement = loadArrayElement(bctx, code, regArray);

        if (typeEl.isJavaPrimitive()) {
            buildOptimizedBinary(bctx, code, regElement, m_nValue);
        } else if (typeEl.isXvmPrimitive() && typeEl.isA(bctx.pool().typeNumber())) {
            buildOptimizedNumber(bctx, code, regElement, m_nValue);
        } else {
            MethodInfo    method = findOpMethod(bctx, typeEl);
            JitMethodDesc jmd    = buildXvmOptimized(bctx, code, regElement, method,
                    new int[] {m_nValue});

            for (int i = 1; i < jmd.optimizedReturns.length; i++) {
                Builder.loadFromContext(code,
                        jmd.optimizedReturns[i].cd, jmd.optimizedReturns[i].altIndex);
            }
        }

        regElement.store(bctx, code, typeEl);
        storeArrayElement(bctx, code, regArray, regElement);
    }

    /**
     * Find the natural operator method for a non-numeric primitive array element.
     */
    private MethodInfo findOpMethod(BuildContext bctx, TypeConstant typeElement) {
        String name;
        String op;
        switch (getOpCode()) {
            case OP_IIP_ADD  -> {name = "add";           op = "+";  }
            case OP_IIP_SUB  -> {name = "sub";           op = "-";  }
            case OP_IIP_MUL  -> {name = "mul";           op = "*";  }
            case OP_IIP_DIV  -> {name = "div";           op = "/";  }
            case OP_IIP_MOD  -> {name = "mod";           op = "%";  }
            case OP_IIP_SHL  -> {name = "shiftLeft";     op = "<<"; }
            case OP_IIP_SHR  -> {name = "shiftRight";    op = ">>"; }
            case OP_IIP_USHR -> {name = "shiftAllRight"; op = ">>>"; }
            case OP_IIP_AND  -> {name = "and";           op = "&";  }
            case OP_IIP_OR   -> {name = "or";            op = "|";  }
            case OP_IIP_XOR  -> {name = "xor";           op = "^";  }

            default -> throw new UnsupportedOperationException(toName(getOpCode()));
        }

        TypeConstant typeArg = bctx.getArgumentType(m_nValue);
        return bctx.getTypeInfo(typeElement).findOpMethod(name, op, typeArg);
    }

    // ----- fields --------------------------------------------------------------------------------

    protected int m_nValue;

    private Argument m_argValue;
}
