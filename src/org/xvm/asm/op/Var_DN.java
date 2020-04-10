package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.VarSupport;

import org.xvm.runtime.template.annotations.xInjectedRef.InjectedHandle;

import org.xvm.runtime.template.xRef.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_DN TYPE, STRING ; next register is a named "dynamic reference" variable
 */
public class Var_DN
        extends OpVar
    {
    /**
     * Construct a VAR_DN op for the specified register and name.
     *
     * @param reg        the register
     * @param constName  the name constant
     */
    public Var_DN(Register reg, StringConstant constName)
        {
        super(reg);

        if (constName == null)
            {
            throw new IllegalArgumentException("name required");
            }
        m_constName = constName;
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
        super(in, aconst);

        m_nNameId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId = encodeArgument(m_constName, registry);
            }

        writePackedLong(out, m_nNameId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_DN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        String sName = frame.getString(m_nNameId);

        RefHandle hRef = m_ref;
        int       iRet = iPC + 1;
        if (hRef == null)
            {
            ClassComposition clz = frame.resolveClass(m_nType);

            iRet = ((VarSupport) clz.getSupport()).introduceRef(frame, clz, sName, m_nVar);

            if (iRet == Op.R_NEXT && frame.f_ahVar[m_nVar] instanceof InjectedHandle)
                {
                m_ref = hRef;
                iRet  = iPC + 1;
                }
            }
        else
            {
            frame.introduceResolvedVar(m_nVar, hRef.getType(), sName, Frame.VAR_DYNAMIC_REF, hRef);
            }

        return iRet;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return Argument.toIdString(m_constName, m_nNameId, null);
        }

    private int m_nNameId;

    private StringConstant m_constName;

    /**
     * cached InjectedRef.
     * NOTE: the injected ref must be named, so this caching is not needed on the VAR_D op
     */
    transient private RefHandle m_ref;
    }
