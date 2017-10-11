package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref;
import org.xvm.runtime.template.Ref.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * REF rvalue-src, lvalue-dest ; move reference-to-source to destination
 */
public class MoveRef
        extends OpInvocable
    {
    /**
     * Construct a MOV_REF op.
     *
     * @param nSource  the source location
     * @param nDest    the destination location
     *
     * @deprecated
     */
    public MoveRef(int nSource, int nDest)
        {
        m_nSrcValue = nSource;
        m_nDestValue = nDest;
        }

    /**
     * Construct a REF op for the passed Registers.
     *
     * @param regSrc   the source Register
     * @param regDest  the destination Register
     */
    public MoveRef(Register regSrc, Register regDest)
        {
        if (regSrc == null || regDest == null)
            {
            throw new IllegalArgumentException("registers required");
            }
        m_regSrc  = regSrc;
        m_regDest = regDest;
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
        m_nSrcValue = readPackedInt(in);
        m_nDestValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_regSrc != null)
            {
            m_nSrcValue  = encodeArgument(m_regSrc.getType(), registry);
            m_nDestValue = encodeArgument(m_regDest.getType(), registry);
            }

        out.writeByte(OP_REF);
        writePackedLong(out, m_nSrcValue);
        writePackedLong(out, m_nDestValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(m_nSrcValue);

        if (infoSrc.getStyle() == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[m_nSrcValue];

            if (frame.isNextRegister(m_nDestValue))
                {
                frame.introduceVar(infoSrc.getType(), null, Frame.VAR_STANDARD, hRef);
                }
            else
                {
                frame.f_ahVar[m_nDestValue] = hRef;
                }
            }
        else
            {
            TypeComposition clzRef = Ref.INSTANCE.ensureClass(
                    Collections.singletonMap("RefType", infoSrc.getType()));

            RefHandle hRef = new RefHandle(clzRef, frame, m_nSrcValue);

            if (frame.isNextRegister(m_nDestValue))
                {
                frame.introduceVar(clzRef.ensurePublicType(), null, Frame.VAR_STANDARD, hRef);
                }
            else
                {
                frame.f_ahVar[m_nDestValue] = hRef;
                }
            }

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        if (scope.isNextRegister(m_nDestValue))
            {
            scope.allocVar();
            }
        }

    private int m_nSrcValue;
    private int m_nDestValue;

    private Register m_regSrc;
    private Register m_regDest;
    }
