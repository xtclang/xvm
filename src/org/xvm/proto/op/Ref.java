package org.xvm.proto.op;

import org.xvm.proto.*;
import org.xvm.proto.template.xRef;
import org.xvm.proto.template.xRef.RefHandle;

/**
 * REF lvalue ; next register represents the Variable ref for the specified variable
 *
 * @author gg 2017.03.08
 */
public class Ref extends OpInvocable
    {
    private final int f_nSrcValue;

    public Ref(int nSrc)
        {
        f_nSrcValue = nSrc;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        Frame.VarInfo infoSrc = frame.f_aInfo[f_nSrcValue];

        if (infoSrc.m_fDynamicRef)
            {
            // the "dynamic ref" register must contain a RefHandle itself
            RefHandle hRef = (RefHandle) frame.f_ahVar[f_nSrcValue];

            frame.f_aInfo[nNextVar] = new Frame.VarInfo(infoSrc.f_clazz, false);
            frame.f_ahVar[nNextVar] = hRef;
            }
        else
            {
            TypeComposition clzRef = xRef.INSTANCE.resolve(new TypeComposition[]{infoSrc.f_clazz});

            RefHandle hRef = new RefHandle(clzRef, frame, f_nSrcValue);

            frame.f_aInfo[nNextVar] = new Frame.VarInfo(clzRef, false);
            frame.f_ahVar[nNextVar] = hRef;
            }
        return iPC + 1;
        }
    }
