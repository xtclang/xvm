package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_nUtil;
import static org.xvm.javajit.Builder.MD_StringOf;
import static org.xvm.javajit.Builder.md;

/**
 * ASSERT_V rvalue, STRING, #vals(rvalue)
 */
public class AssertV
        extends AssertM {
    /**
     * Construct an ASSERT_V op based on the specified arguments.
     *
     * @param argTest      the test Argument
     * @param constructor  the exception constructor (or null for a breakpoint)
     * @param constMsg     the message StringConstant
     * @param aArgValue    the value Arguments
     */
    public AssertV(Argument argTest, MethodConstant constructor, StringConstant constMsg, Argument[] aArgValue) {
        super(argTest, constructor, constMsg);
        m_aArgValue = aArgValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public AssertV(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
        m_anValue = readIntArray(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgValue != null) {
            m_anValue = encodeArguments(m_aArgValue, registry);
        }

        writeIntArray(out, m_anValue);
    }

    @Override
    public int getOpCode() {
        return OP_ASSERT_V;
    }

    @Override
    protected int evaluate(Frame frame, int iPC, BooleanHandle hTest) {
        if (hTest.get()) {
            return iPC + 1;
        }

        // first, get the unformatted String from the constant pool and split it up into its pieces
        String[] asParts = splitMessage(frame.getString(m_nMsgConstId));

        // get the trace variable and constant values to display; note that some values could
        // be unassigned conditional returns
        int            cArgs = m_anValue.length;
        ObjectHandle[] ahArg = new ObjectHandle[cArgs];
        for (int i = 0; i < cArgs; i++) {
            try {
                ahArg[i] = frame.getArgument(m_anValue[i]);
            } catch (Exception e) {
                ahArg[i] = xString.EMPTY_STRING;
            }
        }

        // build the assertion message and finish by throwing it
        StringBuilder      sb         = new StringBuilder(asParts[0]);
        Frame.Continuation doComplete = (frameCaller) -> complete(frameCaller, iPC, sb.toString());
        return new MessageToString(sb, ahArg, asParts, doComplete).doNext(frame);
    }

    /**
     * Helper class to create a string message.
     */
    private static class MessageToString
            implements Frame.Continuation {
        public MessageToString(
                StringBuilder      sb,
                ObjectHandle[]     ahValue,
                String[]           asLabel,
                Frame.Continuation nextStep) {
            this.sb       = sb;
            this.ahValue  = ahValue;
            this.asLabel  = asLabel;
            this.nextStep = nextStep;
        }

        protected int doNext(Frame frameCaller) {
            loop: while (++index < ahValue.length) {
                switch (Utils.callValueOf(frameCaller, ahValue[index])) {
                case Op.R_NEXT:
                    if (updateResult(frameCaller)) {
                        continue; // loop;
                    } else {
                        break loop;
                    }

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    return Op.R_CALL;

                default:
                    throw new IllegalStateException();
                }
            }

            return nextStep.proceed(frameCaller);
        }

        @Override
        public int proceed(Frame frameCaller) {
            if (updateResult(frameCaller)) {
                return doNext(frameCaller);
            }

            // too much text; enough for an output...
            return nextStep.proceed(frameCaller);
        }

        /**
         * @return false iff the output is too long
         */
        protected boolean updateResult(Frame frameCaller) {
            StringHandle hMsg = (StringHandle) frameCaller.popStack();
            char[]       ach  = hMsg.getValue();

            if (ach.length > MAX_VAL) {
                sb.append(ach, 0, MAX_VAL)
                  .append("...");
            } else {
                sb.append(ach);
            }

            if (sb.length() > MAX_LEN) {
                sb.append("...");
                return false;
            }

            sb.append(asLabel[index+1]);
            return true;
        }

        protected static final int MAX_VAL = 2*1024;
        protected static final int MAX_LEN = 16*1024;

        protected final StringBuilder      sb;
        protected final ObjectHandle[]     ahValue;
        protected final String[]           asLabel;
        protected final Frame.Continuation nextStep;

        protected int index = -1;
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(" (");

        for (int i = 0, c = Math.max(m_anValue   == null ? 0 : m_anValue.length,
                                     m_aArgValue == null ? 0 : m_aArgValue.length); i < c; ++i) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(Argument.toIdString(m_aArgValue == null ? null : m_aArgValue[i],
                                          m_anValue   == null ? Register.UNKNOWN : m_anValue[i]));
        }

        sb.append(')');
        return sb.toString();
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected void buildMessage(BuildContext bctx, CodeBuilder code) {
        String[] asParts = splitMessage(bctx.getString(m_nMsgConstId));

        bctx.loadCtx(code);
        code.new_(CD_StringBuilder)
            .dup()
            .invokespecial(CD_StringBuilder, INIT_NAME, MD_BuilderInit);
        for (int i = 0, c = m_anValue.length; i < c; ++i) {
            appendString(code, asParts[i]);
            appendValue(bctx, code, m_anValue[i]);
        }
        code.invokevirtual(CD_StringBuilder, "toString", MD_JavaToString)
            .invokestatic(CD_String, "of", MD_StringOf);
    }

    /**
     * Append a best-effort diagnostic value. We do not track whether a capture executed at runtime,
     * so a short-circuited primitive expression can incorrectly be reported using an existing or
     * verifier-initialized default value (such as zero or False), rather than being omitted.
     */
    private void appendValue(BuildContext bctx, CodeBuilder code, int argId) {
        RegisterInfo reg = null;
        if (argId >= 0) {
            reg = bctx.getRegisterInfo(argId);
            if (reg == null || !bctx.isAssigned(reg) ||
                    bctx.typeMatrix.needsInitialization(argId, getAddress())) {
                // short-circuiting can skip both the declaration and the store of a capture
                return;
            }
        }

        // keep ctx and buffer on stack after "appendValue" consumes its arguments
        code.dup2();
        if (argId >= 0) {
            // narrowing may initialize primitive slots only on one branch;
            // use the original representation, which is available on every failure path
            reg = reg.original().load(code);
        } else {
            reg = bctx.loadArgument(code, argId);
        }
        appendLoadedRegister(code, reg);
    }

    private void appendLoadedRegister(CodeBuilder code, RegisterInfo reg) {
        JitFlavor flavor = reg.flavor();
        if (flavor.isOptimized) {
            switch (flavor) {
                case Primitive, XvmPrimitive -> {
                    Builder.box(code, reg);
                    appendLoadedValue(code);
                }
                case NullablePrimitive ->
                    appendNullablePrimitive(code, reg);

                case NullableXvmPrimitive ->
                    appendNullableXvmPrimitive(code, reg);

                default ->
                    throw new UnsupportedOperationException("Unsupported flavor: " + flavor);
            }
        }
        else {
            // a verifier-only default can be Java null; Ecstasy Null is a real object
            Label labelNull = code.newLabel();
            Label labelDone = code.newLabel();
            code.dup().ifnull(labelNull);
            appendLoadedValue(code);
            code.goto_(labelDone)
                .labelBinding(labelNull)
                .pop()
                .pop2()
                .labelBinding(labelDone);
        }
    }

    private void appendNullablePrimitive(CodeBuilder code, RegisterInfo reg) {
        // stack: ctx, buffer, ctx, buffer, primitive value, isNull
        Label ifNull = code.newLabel();
        Label endIf  = code.newLabel();
        code.ifne(ifNull);

        TypeConstant type = reg.type().removeNullable();
        Builder.box(code, type);
        appendLoadedValue(code);
        code.goto_(endIf)
            .labelBinding(ifNull);

        // stack: ctx, buffer, ctx, buffer, primitive value
        Builder.pop(code, reg.cd());
        Builder.loadNull(code);
        appendLoadedValue(code);
        code.labelBinding(endIf);
    }

    private void appendNullableXvmPrimitive(CodeBuilder code, RegisterInfo reg) {
        // stack: ctx, buffer, ctx, buffer, xvm primitive slot values, isNull
        Label ifNull = code.newLabel();
        Label endIf  = code.newLabel();
        code.ifne(ifNull);

        TypeConstant type = reg.type().removeNullable();
        Builder.box(code, type);
        appendLoadedValue(code);
        code.goto_(endIf)
            .labelBinding(ifNull);

        // stack: ctx, buffer, ctx, buffer, xvm primitive slot values
        ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(type);
        for (int i = cds.length - 1; i >= 0; --i) {
            Builder.pop(code, cds[i]);
        }
        Builder.loadNull(code);
        appendLoadedValue(code);
        code.labelBinding(endIf);
    }

    private void appendLoadedValue(CodeBuilder code) {
        // stack: ctx, buffer, value
        code.invokestatic(CD_nUtil, "appendValue", MD_AppendValue);
    }

    private void appendString(CodeBuilder code, String sText) {
        // stack: buffer
        if (!sText.isEmpty()) {
            code.dup()
                .ldc(sText)
                .invokestatic(CD_nUtil, "appendText", MD_AppendText);
        }
    }

    private String[] splitMessage(String sMsg) {
        String[] asParts = m_asParts;
        if (asParts == null) {
            int count = m_anValue.length;
            asParts = new String[count + 1];
            for (int i = 0; i < count; ++i) {
                String sTag  = "{" + i + "}";
                int    ofTag = sMsg.indexOf(sTag);
                if (ofTag >= 0) {
                    asParts[i] = sMsg.substring(0, ofTag);
                    sMsg       = sMsg.substring(ofTag + sTag.length());
                } else {
                    asParts[i] = "";
                }
            }
            // the suffix after the last placeholder may be empty, but it must exist
            assert sMsg != null;
            asParts[count] = sMsg;
            m_asParts = asParts;
        }
        return asParts;
    }

    // ----- fields --------------------------------------------------------------------------------

    private static final ClassDesc      CD_StringBuilder = ClassDesc.of(StringBuilder.class.getName());
    private static final MethodTypeDesc MD_BuilderInit   = md(CD_void);
    private static final MethodTypeDesc MD_AppendText    = md(CD_void, CD_StringBuilder, CD_JavaString);
    private static final MethodTypeDesc MD_AppendValue   = md(CD_void, CD_Ctx, CD_StringBuilder, CD_Object);
    private static final MethodTypeDesc MD_JavaToString  = md(CD_JavaString);

    private int[] m_anValue;

    private Argument[] m_aArgValue;

    private transient volatile String[] m_asParts;
}
