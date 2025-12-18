package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;

import java.util.Arrays;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.Ctx;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_nException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT rvalue
 */
public class Assert
        extends Op {
    /**
     * Construct an ASSERT op based on the specified arguments.
     *
     * @param argTest      the test Argument
     * @param idConstruct  the exception constructor
     */
    public Assert(Argument argTest, MethodConstant idConstruct) {
        m_argTest     = argTest;
        m_idConstruct = idConstruct;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Assert(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTest        = readPackedInt(in);
        m_nConstructor = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTest != null) {
            m_nTest = encodeArgument(m_argTest, registry);
        }
        if (m_idConstruct != null) {
            m_nConstructor = encodeArgument(m_idConstruct, registry);
        }

        writePackedLong(out, m_nTest);
        writePackedLong(out, m_nConstructor);
    }

    @Override
    public int getOpCode() {
        return OP_ASSERT;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hValue = frame.getArgument(m_nTest);

            return isDeferred(hValue)
                    ? hValue.proceed(frame, frameCaller ->
                        evaluate(frameCaller, iPC, (BooleanHandle) frameCaller.popStack()))
                    : evaluate(frame, iPC, (BooleanHandle) hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int evaluate(Frame frame, int iPC, BooleanHandle hTest) {
        if (hTest.get()) {
            return iPC + 1;
        }

        String sMsg = buildMessage(frame);
        return complete(frame, iPC, sMsg);
    }

    protected int complete(Frame frame, int iPC, String sMsg) {
        if (m_nConstructor == A_IGNORE) {
            // debugger break-point
            return frame.f_context.getDebugger().activate(frame, iPC);
        }

        MethodConstant   idConstruct = frame.getConstant(m_nConstructor, MethodConstant.class);
        MethodStructure  construct   = (MethodStructure) idConstruct.getComponent();
        ClassConstant    constClz    = (ClassConstant) idConstruct.getNamespace();
        ClassTemplate    template    = frame.ensureTemplate(constClz);
        ClassComposition clzTarget   = template.getCanonicalClass(frame.f_context.f_container);
        StringHandle     hMsg        = xString.makeHandle(sMsg);
        ObjectHandle[]   ahArg       = new ObjectHandle[construct.getMaxVars()];

        ahArg[0] = hMsg;
        switch (template.construct(frame, construct, clzTarget, null, ahArg, A_STACK)) {
        case Op.R_NEXT:
            return frame.raiseException((ExceptionHandle) frame.popStack());

        case Op.R_CALL:
            frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.raiseException((ExceptionHandle) frameCaller.popStack()));
            return Op.R_CALL;

        case Op.R_EXCEPTION:
            return Op.R_EXCEPTION;

        default:
            throw new IllegalStateException();
        }
    }

    protected String buildMessage(Frame frame) {
        return "Assertion failed";
    }

    @Override
    public boolean advances() {
        // this assumes that we never call "advance()" when we read ops from disk
        return !(m_argTest instanceof Constant &&
                 m_argTest.equals(((Constant) m_argTest).getConstantPool().valFalse()));
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);
        m_argTest     = registerArgument(m_argTest, registry);
        m_idConstruct = (MethodConstant) registerArgument(m_idConstruct, registry);
    }

    @Override
    public String toString() {
        return super.toString() + ' ' + Argument.toIdString(m_argTest, m_nTest);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Label labelEnd = code.newLabel();

        boolean fAlwaysFalse = m_nTest <= Op.CONSTANT_OFFSET
            && bctx.getConstant(m_nTest).equals(bctx.pool().valFalse());
        if (!fAlwaysFalse) {
            bctx.loadArgument(code, m_nTest);
            code.ifne(labelEnd);
        }
        bctx.loadCtx(code);
        if (m_nConstructor == A_IGNORE) {
            code.loadConstant( "Debugger support for jit is not yet implemented");
            code.invokevirtual(CD_Ctx, "log", Ctx.MD_log);
        } else {
            MethodConstant idCtor  = bctx.getConstant(m_nConstructor, MethodConstant.class);
            TypeConstant   typeEx  = idCtor.getNamespace().getType();
            int[]          anArgs  = new int[idCtor.getSignature().getParamCount()];

            RegisterInfo regMsg = bctx.pushTempRegister(bctx.pool().typeString(), CD_String);
            buildMessage(bctx, code);
            code.astore(regMsg.slot());

            anArgs[0] = A_STACK;
            Arrays.fill(anArgs, 1, anArgs.length, Op.A_DEFAULT);

            ClassDesc cdEx = bctx.buildNew(code, typeEx, idCtor, anArgs);
            code.getfield(cdEx, "$exception", CD_nException)
                .athrow();
        }
        if (!fAlwaysFalse) {
            code.labelBinding(labelEnd);
        }
    }

    /**
     * Build the assert message and place it onto the Java stack.
     */
    protected void buildMessage(BuildContext bctx, CodeBuilder code) {
        Builder.loadString(code, "Assertion failed");
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nTest;
    private int m_nConstructor = A_IGNORE;   // important: no constructor means A_IGNORE means DEBUG

    private Argument       m_argTest;
    private MethodConstant m_idConstruct;
}