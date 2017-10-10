package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref.RefHandle;

import org.xvm.runtime.template.annotations.xInjectedRef;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_DN TYPE, STRING ; next register is a named "dynamic reference" variable
 */
public class Var_DN
        extends Op
    {
    /**
     * Construct a VAR_DN.
     *
     * @param nTypeConstId   the index of the constant containing the type of the variable
     * @param nNameConstId   the index of the constant containing the name of the variable
     */
    public Var_DN(int nTypeConstId, int nNameConstId)
        {
        f_nTypeConstId = nTypeConstId;
        f_nNameConstId = nNameConstId;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_DN(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTypeConstId = readPackedInt(in);
        f_nNameConstId  = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_VAR_DN);
        writePackedLong(out, f_nTypeConstId);
        writePackedLong(out, f_nNameConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_DN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        String sName = frame.getString(f_nNameConstId);

        RefHandle hRef = m_ref;
        if (hRef == null)
            {
            TypeComposition clz = frame.f_context.f_types.ensureComposition(
                    f_nTypeConstId, frame.getActualTypes());

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

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    final private int f_nTypeConstId;
    final private int f_nNameConstId;

    /**
     * cached InjectedRef.
     * NOTE: the injected ref must be named, so this caching is not needed on the VAR_D op
     */
    transient private RefHandle m_ref;
    }
