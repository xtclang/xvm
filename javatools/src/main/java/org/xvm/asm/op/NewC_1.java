package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NEWC_1 CONSTRUCT, rvalue-parent, rvalue-param, lvalue ; virtual "new" for child classes
 */
public class NewC_1
        extends OpCallable {
    /**
     * Construct a NEWC_1 op based on the passed arguments.
     *
     * @param constMethod  the constructor method
     * @param argParent    the parent Argument
     * @param argValue     the value Argument
     * @param argReturn    the return Argument
     */
    public NewC_1(MethodConstant constMethod, Argument argParent, Argument argValue, Argument argReturn) {
        super(constMethod);

        m_argParent = argParent;
        m_argValue = argValue;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NewC_1(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nParentValue = readPackedInt(in);
        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argParent != null) {
            m_nParentValue = encodeArgument(m_argParent, registry);
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        writePackedLong(out, m_nParentValue);
        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_NEWC_1;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hParent = frame.getArgument(m_nParentValue);

            MethodStructure constructor = getChildConstructor(frame, hParent);
            if (constructor == null) {
                return reportMissingConstructor(frame, hParent);
            }

            ObjectHandle[] ahVar = frame.getArguments(
                    new int[]{m_nArgValue}, constructor.getMaxVars());

            return isDeferred(hParent)
                    ? hParent.proceed(frame, frameCaller ->
                        collectArgs(frameCaller, constructor, frameCaller.popStack(), ahVar))
                    : constructChild(frame, constructor, hParent, ahVar);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int collectArgs(Frame frame, MethodStructure constructor, ObjectHandle hParent, ObjectHandle[] ahVar) {
        if (anyDeferred(ahVar)) {
            Frame.Continuation stepNext = frameCaller ->
                constructChild(frameCaller, constructor, hParent, ahVar);

            return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
        }

        return constructChild(frame, constructor, hParent, ahVar);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argParent = registerArgument(m_argParent, registry);
        m_argValue = registerArgument(m_argValue, registry);
    }

    @Override
    protected String getParamsString() {
        return Argument.toIdString(m_argValue, m_nArgValue);
    }

    private int m_nParentValue;
    private int m_nArgValue;

    private Argument m_argParent;
    private Argument m_argValue;
}