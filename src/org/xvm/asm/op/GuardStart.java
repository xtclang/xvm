package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.MultiGuard;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GUARD #handlers:(CONST_CLASS, CONST_STRING, rel_addr)
 */
public class GuardStart
        extends Op
    {
    /**
     * Construct a GUARD op for a single exception.
     *
     * @param typeException  the exception type to catch
     * @param constName      the name constant for the catch exception variable
     * @param opCatch        the first op of the catch handler
     */
    public GuardStart(TypeConstant typeException, StringConstant constName, Op opCatch)
        {
        this(new TypeConstant[] {typeException},
            new StringConstant[] {constName}, new Op[] {opCatch});
        }

    /**
     * Construct a GUARD op for multiple exceptions.
     *
     * @param aTypeException  the exception type to catch
     * @param aConstName      the name constant for the catch exception variable
     * @param aOpCatch         the first op of the catch handler
     */
    public GuardStart(TypeConstant[] aTypeException, StringConstant[] aConstName, Op[] aOpCatch)
        {
        assert aTypeException.length == aConstName.length;
        assert aTypeException.length == aOpCatch.length;

        m_aTypeException = aTypeException;
        m_aConstName = aConstName;
        m_aOpCatch = aOpCatch;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        int c = readPackedInt(in);

        m_anTypeId = new int[c];
        m_anNameId = new int[c];
        m_aofCatch = new int[c];
        for (int i = 0; i < c; i++)
            {
            m_anTypeId[i] = readPackedInt(in);
            m_anNameId[i] = readPackedInt(in);
            m_aofCatch[i] = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_aTypeException != null)
            {
            m_anTypeId = encodeArguments(m_aTypeException, registry);
            m_anNameId = encodeArguments(m_aConstName, registry);
            }
        out.writeByte(OP_GUARD);

        int c = m_anTypeId.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; i++)
            {
            writePackedLong(out, m_anTypeId[i]);
            writePackedLong(out, m_anNameId[i]);
            writePackedLong(out, m_aofCatch[i]);
            }
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_aOpCatch != null && m_aofCatch == null)
            {
            int c = m_aOpCatch.length;
            m_aofCatch = new int[c];

            for (int i = 0; i < c; i++)
                {
                m_aofCatch[i] = code.resolveAddress(iPC, m_aOpCatch[i]);
                }
            }
        }

    @Override
    public int getOpCode()
        {
        return OP_GUARD;
        }

    @Override
    public boolean isEnter()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        MultiGuard guard = m_guard;
        if (guard == null)
            {
            m_guard = guard = new MultiGuard(iPC, iScope,
                m_anTypeId, m_anNameId, m_aofCatch);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aTypeException, registry);
        registerArguments(m_aConstName, registry);
        }

    private int[] m_anTypeId;
    private int[] m_anNameId;
    private int[] m_aofCatch;

    private TypeConstant[] m_aTypeException;
    private StringConstant[] m_aConstName;
    private Op[] m_aOpCatch;

    private transient MultiGuard m_guard; // cached struct
    }
