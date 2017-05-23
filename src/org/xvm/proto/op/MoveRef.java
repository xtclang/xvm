package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.template.xRef;
import org.xvm.proto.template.xRef.RefHandle;

/**
 * MOV_REF lvalue-src, lvalue-dest ; move reference-to-source to destination
 *
 * @author gg 2017.03.08
 */
public class MoveRef extends OpInvocable
    {
    private final int f_nSrcValue;
    private final int f_nDestValue;

    public MoveRef(int nSource, int nDest)
        {
        f_nSrcValue = nSource;
        f_nDestValue = nDest;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame.VarInfo infoSrc = frame.getVarInfo(f_nSrcValue);
        RefHandle hRef;

        if (infoSrc.m_nStyle == Frame.VAR_DYNAMIC_REF)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            hRef = (RefHandle) frame.f_ahVar[f_nSrcValue];
            }
        else
            {
            TypeComposition clzRef = xRef.INSTANCE.resolve(new TypeComposition[]{infoSrc.f_clazz});

            hRef = new RefHandle(clzRef, frame, f_nSrcValue);
            }

        frame.f_ahVar[f_nDestValue] = hRef;

        return iPC + 1;
        }
    }
