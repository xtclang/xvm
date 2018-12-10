package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xRef;
import org.xvm.runtime.template.xRef.RefHandle;


/**
 * MOV_REF rvalue-src, lvalue-dest ; move Ref-to-source to destination (read-only)
 */
public class MoveRef
        extends OpMove
    {
    /**
     * Construct a REF op for the passed arguments.
     *
     * @param regSrc   the source Register
     * @param regDest  the destination Register
     */
    public MoveRef(Register regSrc, Register regDest)
        {
        super(regSrc, regDest);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveRef(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(m_nFromValue);

        if (infoSrc.isDynamic())
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[m_nFromValue];

            if (frame.isNextRegister(m_nToValue))
                {
                frame.introduceResolvedVar(m_nToValue, infoSrc.getType());
                }

            // the destination type must be the same as the source
            frame.assignValue(m_nToValue, hRef, false);
            }
        else
            {
            TypeComposition clzRef = xRef.INSTANCE.ensureParameterizedClass(
                frame.poolContext(), infoSrc.getType());

            RefHandle hRef = new RefHandle(clzRef, frame, m_nFromValue);

            if (frame.isNextRegister(m_nToValue))
                {
                frame.introduceResolvedVar(m_nToValue, clzRef.getType());
                }

            // the destination type must be the same as the source
            frame.assignValue(m_nToValue, hRef, false);
            }

        return iPC + 1;
        }
    }
