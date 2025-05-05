package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for IP_ (in-place) op codes.
 *
 * Note: "property in-place" ops derive from {@link OpPropInPlace} and
 *       "property in-place assign" from {@link OpPropInPlaceAssign}.
 */
public abstract class OpInPlace
        extends Op {
    /**
     * Construct an "in-place" op for the passed target.
     *
     * @param argTarget  the target Argument
     */
    protected OpInPlace(Argument argTarget) {
        assert(!isAssignOp());

        m_argTarget = argTarget;
    }

    /**
     * Construct an "in-place and assign" op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    protected OpInPlace(Argument argTarget, Argument argReturn) {
        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpInPlace(DataInput in, Constant[] aconst)
            throws IOException {
        m_nTarget = readPackedInt(in);
        if (isAssignOp()) {
            m_nRetValue = readPackedInt(in);
        }
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argTarget != null) {
            m_nTarget = encodeArgument(m_argTarget, registry);
            if (isAssignOp()) {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
            }
        }

        writePackedLong(out, m_nTarget);
        if (isAssignOp()) {
            writePackedLong(out, m_nRetValue);
        }
    }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp() {
        // majority of the ops are assigning; let's default to that
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            int nTarget = m_nTarget;
            if (nTarget >= 0) {
                // operation on a register
                if (frame.isDynamicVar(nTarget)) {
                    RefHandle hVar = frame.getDynamicVar(nTarget);
                    if (hVar == null) {
                        return R_REPEAT;
                    }

                    if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                        frame.introduceRefTypeVar(nTarget);
                    }

                    return completeWithVar(frame, hVar);
                } else {
                    ObjectHandle hTarget = frame.getArgument(nTarget);

                    if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                        frame.introduceVarCopy(m_nRetValue, nTarget);
                    }

                    return isDeferred(hTarget)
                            ? hTarget.proceed(frame, frameCaller ->
                                completeWithRegister(frameCaller, frameCaller.popStack()))
                            : completeWithRegister(frame, hTarget);
                }
            } else {
                // operation on a local property
                if (isAssignOp() && frame.isNextRegister(m_nRetValue)) {
                    frame.introduceVarCopy(m_nRetValue, nTarget);
                }

                PropertyConstant idProp = (PropertyConstant) frame.getConstant(nTarget);

                return completeWithProperty(frame, idProp);
            }
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int completeWithRegister(Frame frame, ObjectHandle hTarget) {
        throw new UnsupportedOperationException();
    }

    protected int completeWithVar(Frame frame, RefHandle hTarget) {
        throw new UnsupportedOperationException();
    }

    protected int completeWithProperty(Frame frame, PropertyConstant idProp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetSimulation() {
        if (isAssignOp()) {
            resetRegister(m_argReturn);
        }
    }

    @Override
    public void simulate(Scope scope) {
        if (isAssignOp()) {
            checkNextRegister(scope, m_argReturn, m_nRetValue);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        m_argTarget = registerArgument(m_argTarget, registry);
        if (isAssignOp()) {
            m_argReturn = registerArgument(m_argReturn, registry);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(' ')
          .append(getTargetString());

        if (isAssignOp()) {
            sb.append(", ")
              .append(getReturnString());
        }

        return sb.toString();
    }

    protected String getTargetString() {
        return Argument.toIdString(m_argTarget, m_nTarget);
    }

    protected String getReturnString() {
        return Argument.toIdString(m_argReturn, m_nRetValue);
    }

    protected int m_nTarget;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
}
