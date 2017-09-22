package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref.RefHandle;

import org.xvm.runtime.template.annotations.xInjectedRef;


/**
 * DNVAR CONST_REF_CLASS, CONST_STRING ; next register is a named "dynamic reference" variable
 *
 * @author gg 2017.03.08
 */
public class DNVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nNameConstId;

    // cached InjectedRef
    // NOTE: the injected ref must be named, so this caching is not needed at DVAR op
    transient private RefHandle m_ref;

    public DNVar(int nClassConstId, int nNameConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNameConstId;
        }

    public DNVar(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        f_nNameConstId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_DNVAR);
        out.writeInt(f_nClassConstId);
        out.writeInt(f_nNameConstId);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        StringConstant constName =
                (StringConstant) context.f_pool.getConstant(f_nNameConstId);
        String sName = constName.getValue();

        RefHandle hRef = m_ref;
        if (hRef == null)
            {
            TypeComposition clz = context.f_types.ensureComposition(
                    f_nClassConstId, frame.getActualTypes());

            hRef = clz.f_template.createRefHandle(clz, sName);

            if (hRef instanceof xInjectedRef.InjectedHandle)
                {
                // prime the injection (fail fast)
                try
                    {
                    hRef.get();
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return frame.raiseException(e);
                    }

                m_ref = hRef;
                }
            }

        frame.introduceVar(hRef.f_clazz, sName, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }
    }
