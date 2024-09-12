package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.maps.xListMap;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_M TYPE, #entries:(rvalue, rvalue) ; next register is an initialized anonymous Map variable
 */
public class Var_M
        extends OpVar
    {
    /**
     * Construct a VAR_M op for the specified register and arguments.
     *
     * @param reg      the register
     * @param aArgVal  the key arguments
     * @param aArgVal  the value arguments
     */
    public Var_M(Register reg, Argument[] aArgKey, Argument[] aArgVal)
        {
        super(reg);

        if (aArgKey == null || aArgVal == null)
            {
            throw new IllegalArgumentException("name, keys and values required");
            }

        assert aArgKey.length == aArgVal.length;

        m_aArgKey = aArgKey;
        m_aArgVal = aArgVal;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_M(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        int c = readMagnitude(in);

        int[] aiKey = new int[c];
        for (int i = 0; i < c; ++i)
            {
            aiKey[i] = readPackedInt(in);
            }
        m_anArgKey = aiKey;

        int[] aiVal = new int[c];
        for (int i = 0; i < c; ++i)
            {
            aiVal[i] = readPackedInt(in);
            }
        m_anArgVal = aiVal;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgKey != null)
            {
            m_anArgKey = encodeArguments(m_aArgKey, registry);
            m_anArgVal = encodeArguments(m_aArgVal, registry);
            }

        int c = m_anArgKey.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, m_anArgKey[i]);
            }
        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, m_anArgVal[i]);
            }
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_M;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            TypeConstant   typeMap = frame.resolveType(m_nType);
            ObjectHandle[] ahKey   = frame.getArguments(m_anArgKey, m_anArgKey.length);
            ObjectHandle[] ahValue = frame.getArguments(m_anArgVal, m_anArgVal.length);

            frame.introduceResolvedVar(m_nVar, typeMap, null, Frame.VAR_STANDARD, null);

            return xListMap.INSTANCE.constructMap(frame, typeMap, ahKey, ahValue,
                    anyDeferred(ahKey), anyDeferred(ahValue), m_nVar);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgKey, registry);
        registerArguments(m_aArgVal, registry);
        }

    private int[] m_anArgKey;
    private int[] m_anArgVal;

    private Argument[] m_aArgKey;
    private Argument[] m_aArgVal;
    }