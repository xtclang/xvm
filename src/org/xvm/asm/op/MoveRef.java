package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;

import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref;
import org.xvm.runtime.template.Ref.RefHandle;


/**
 * REF rvalue-src, lvalue-dest ; move reference-to-source to destination
 */
public class MoveRef
        extends OpMove
    {
    /**
     * Construct a REF op.
     *
     * @param nSource  the source location
     * @param nDest    the destination location
     *
     * @deprecated
     */
    public MoveRef(int nSource, int nDest)
        {
        super((Argument) null, null);

        m_nFromValue = nSource;
        m_nToValue = nDest;
        }

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
        return OP_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(m_nFromValue);

        if (infoSrc.getStyle() == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[m_nFromValue];

            if (frame.isNextRegister(m_nToValue))
                {
                frame.introduceVar(infoSrc.getType(), null, Frame.VAR_STANDARD, hRef);
                }
            else
                {
                // the destination type must be the same as the source
                frame.f_ahVar[m_nToValue] = hRef;
                }
            }
        else
            {
            TypeComposition clzRef = Ref.INSTANCE.ensureClass(
                    Collections.singletonMap("RefType", infoSrc.getType()));

            RefHandle hRef = new RefHandle(clzRef, frame, m_nFromValue);

            if (frame.isNextRegister(m_nToValue))
                {
                frame.introduceVar(clzRef.ensurePublicType(), null, Frame.VAR_STANDARD, hRef);
                }
            else
                {
                // the destination type must be the same as the source
                frame.f_ahVar[m_nToValue] = hRef;
                }
            }

        return iPC + 1;
        }
    }
