package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xRef.RefHandle;
import org.xvm.runtime.template.xVar;


/**
 * MOV_VAR rvalue-src, lvalue-dest ; move Var-to-source to destination
 */
public class MoveVar
        extends OpMove
    {
    /**
     * Construct a MOV_VAR op.
     *
     * @param nSource  the source location
     * @param nDest    the destination location
     *
     * @deprecated
     */
    public MoveVar(int nSource, int nDest)
        {
        super((Argument) null, null);

        m_nFromValue = nSource;
        m_nToValue   = nDest;
        }

    /**
     * Construct a MOV_VAR op for the passed arguments.
     *
     * @param regSrc   the source Register
     * @param regDest  the destination Register
     */
    public MoveVar(Register regSrc, Register regDest)
        {
        super(regSrc, regDest);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MoveVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV_VAR;
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
            frame.f_ahVar[m_nToValue] = hRef;
            }
        else
            {
            TypeComposition clzRef = xVar.INSTANCE.ensureParameterizedClass(infoSrc.getType());

            RefHandle hRef = new RefHandle(clzRef, frame, m_nFromValue);

            if (frame.isNextRegister(m_nToValue))
                {
                frame.introduceResolvedVar(m_nToValue, clzRef.getType());
                }

            // the destination type must be the same as the source
            frame.f_ahVar[m_nToValue] = hRef;
            }

        return iPC + 1;
        }
    }
