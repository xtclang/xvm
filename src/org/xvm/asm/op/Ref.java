package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * REF lvalue ; next register represents the Variable ref for the specified variable
 */
public class Ref
        extends OpInvocable
    {
    /**
     * Construct a REF op.
     *
     * @param nSrc  the item to obtain a ref of
     */
    public Ref(int nSrc)
        {
        f_nSrcValue = nSrc;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Ref(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nSrcValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_REF);
        writePackedLong(out, f_nSrcValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_REF;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(f_nSrcValue);
        TypeComposition clzReferent = infoSrc.f_clazz;

        if (infoSrc.m_nStyle == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[f_nSrcValue];

            frame.introduceVar(clzReferent, null, Frame.VAR_STANDARD, hRef);
            }
        else
            {
            TypeComposition clzRef = org.xvm.runtime.template.Ref.INSTANCE.ensureClass(
                    Collections.singletonMap("RefType", infoSrc.f_clazz.ensurePublicType()));

            RefHandle hRef = new RefHandle(clzRef, frame, f_nSrcValue);

            frame.introduceVar(clzRef, null, Frame.VAR_STANDARD, hRef);
            }

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    private final int f_nSrcValue;
    }
