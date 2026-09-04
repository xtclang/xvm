package org.xvm.asm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Base class for property in-place assign op codes (PIP_ADD, PIP_SUB, etc.).
 */
public abstract class OpPropInPlaceAssign
        extends OpProperty {
    /**
     * Construct a "property in-place and assign" op for the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argVal     the second Argument
     */
    protected OpPropInPlaceAssign(PropertyConstant idProp, Argument argTarget, Argument argVal) {
        super(idProp);

        m_argTarget = argTarget;
        m_argValue = argVal;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpPropInPlaceAssign(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nTarget = readPackedInt(in);
        m_nValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nValue = encodeArgument(m_argValue,  registry);
        }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nValue);
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nValue}, 2);

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    processProperty(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }
            return processProperty(frame, ahArg[0], ahArg[1]);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    /**
     * Continuation of the processing with resolved arguments.
     */
    protected int processProperty(Frame frame, ObjectHandle hTarget, ObjectHandle hValue) {
        PropertyConstant idProp = frame.getConstant(m_nPropId, PropertyConstant.class);

        return complete(frame, hTarget, idProp, hValue);
    }

    /**
     * The completion of processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argTarget = registerArgument(m_argTarget, registry);
        m_argValue = registerArgument(m_argValue, registry);
    }

    @Override
    public String toString() {
        return super.toString()
                + ", " + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argValue, m_nValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        PropertyConstant prop      = bctx.getConstant(m_nPropId, PropertyConstant.class);
        RegisterInfo     regTarget = bctx.loadArgument(code, m_nTarget);
        regTarget = bctx.storeTempRegister(code, m_nTarget, regTarget);

        RegisterInfo regValue  = bctx.buildGetProperty(code, regTarget.load(code), m_nPropId);
        TypeConstant typeValue = buildInPlaceAssign(bctx, code, regValue, m_nValue);
        bctx.storeValue(code, regValue, typeValue);
        bctx.buildSetProperty(code, regTarget.type(), regTarget::load, prop, regValue::load);
        return -1;
    }

    /**
     * Build the operation on the current property value.
     *
     * @return the type of the result on the Java stack
     */
    protected TypeConstant buildInPlaceAssign(BuildContext bctx, CodeBuilder code,
                                              RegisterInfo regTarget, int argValue) {
        if (regTarget.cd().isPrimitive()) {
            if (!regTarget.isSingle()) {
                throw new UnsupportedOperationException(toName(getOpCode()) + " on multi-slot");
            }
            return buildOptimizedBinary(bctx, code, regTarget, argValue);
        }

        TypeConstant typeTarget = regTarget.type();
        if (typeTarget.isXvmPrimitive()) {
            if (typeTarget.isA(bctx.pool().typeNumber())) {
                return buildOptimizedNumber(bctx, code, regTarget, argValue);
            }

            MethodInfo    method = findOpMethod(bctx, typeTarget, argValue);
            JitMethodDesc jmd    = buildXvmOptimized(bctx, code, regTarget, method,
                    new int[] {argValue});
            for (int i = 1; i < jmd.optimizedReturns.length; i++) {
                Builder.loadFromContext(code,
                        jmd.optimizedReturns[i].cd, jmd.optimizedReturns[i].altIndex);
            }
            return method.getSignature().getRawReturns()[0];
        }

        throw new UnsupportedOperationException(toName(getOpCode()) + " on "
                + typeTarget.getValueString());
    }

    /**
     * Find the natural operator method for a non-numeric XVM primitive property.
     */
    private MethodInfo findOpMethod(BuildContext bctx, TypeConstant typeTarget, int argValue) {
        String name;
        String op;
        switch (getOpCode()) {
            case OP_PIP_ADD:
                name = "add";
                op   = "+";
                break;

            case OP_PIP_SUB:
                name = "sub";
                op   = "-";
                break;

            case OP_PIP_MUL:
                name = "mul";
                op   = "*";
                break;

            case OP_PIP_DIV:
                name = "div";
                op   = "/";
                break;

            case OP_PIP_MOD:
                name = "mod";
                op   = "%";
                break;

            case OP_PIP_SHL:
                name = "shiftLeft";
                op   = "<<";
                break;

            case OP_PIP_SHR:
                name = "shiftRight";
                op   = ">>";
                break;

            case OP_PIP_USHR:
                name = "shiftAllRight";
                op   = ">>>";
                break;

            case OP_PIP_AND:
                name = "and";
                op   = "&";
                break;

            case OP_PIP_OR:
                name = "or";
                op   = "|";
                break;

            case OP_PIP_XOR:
                name = "xor";
                op   = "^";
                break;

            default:
                throw new UnsupportedOperationException(toName(getOpCode()));
        }

        TypeConstant typeArg = bctx.getArgumentType(argValue);
        return bctx.getTypeInfo(typeTarget).findOpMethod(name, op, typeArg);
    }


    // ----- data fields ---------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nValue;

    private Argument m_argTarget;
    private Argument m_argValue;
}
