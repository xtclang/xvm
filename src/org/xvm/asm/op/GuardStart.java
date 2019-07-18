package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.MultiGuard;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * GUARD #handlers:(CONST_CLASS, CONST_STRING, rel_addr)
 * <p/>
 * The GUARD op indicates the beginning of a section of ops for which there are exception handlers.
 * The GUARD op contains the information, in order of precedence, for the types of exceptions that
 * are to be caught, and the address of the CATCH op that is the exception handler for each of those
 * exception types.
 * <p/>
 * The section of guarded ops concludes with a matching GUARD_END op.
 */
public class GuardStart
        extends Op
    {
    /**
     * Construct a GUARD op for multiple exceptions.
     *
     * @param aOpCatch  the first op of the catch handlers
     */
    public GuardStart(CatchStart[] aOpCatch)
        {
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
    public void resolveCode(Code code, Constant[] aconst)
        {
        super.resolveCode(code, aconst);

        int c = m_aofCatch.length;
        m_aOpCatch = new CatchStart[c];
        for (int i = 0; i < c; i++)
            {
            CatchStart op = (CatchStart) code.get(ofThis + m_aofCatch[i]);
            op.setNameId(m_anNameId[i]);
            op.setTypeId(m_anTypeId[i]);
            m_aOpCatch[i] = op;
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aOpCatch != null)
            {
            int   cCatch   = m_aOpCatch.length;
            int[] anTypeId = new int[cCatch];
            int[] anNameId = new int[cCatch];
            for (int i = 0; i < cCatch; ++i)
                {
                CatchStart op = m_aOpCatch[i];
                op.preWrite(registry);
                anTypeId[i] = op.getTypeId();
                anNameId[i] = op.getNameId();
                }
            m_anTypeId = anTypeId;
            m_anNameId = anNameId;
            }

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
    public void resolveAddresses()
        {
        if (m_aOpCatch != null)
            {
            int c = m_aOpCatch.length;
            m_aofCatch = new int[c];

            for (int i = 0; i < c; i++)
                {
                m_aofCatch[i] = calcRelativeAddress(m_aOpCatch[i]);
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
        int iScope = frame.enterScope(m_nNextVar);

        MultiGuard guard = m_guard;
        if (guard == null)
            {
            m_guard = guard = new MultiGuard(iPC, iScope, m_anTypeId, m_anNameId, m_aofCatch);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }

    @Override
    public void markReachable(Op[] aop)
        {
        super.markReachable(aop);
        findCorrespondingOp(aop, OP_GUARD_END).markNecessary();
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter(this);

        m_nNextVar = scope.getCurVars();
        }

    @Override
    public boolean branches(List<Integer> list)
        {
        resolveAddresses();
        for (int i : m_aofCatch)
            {
            list.add(i);
            }
        return true;
        }

    private int[] m_anTypeId;
    private int[] m_anNameId;
    private int[] m_aofCatch;

    private CatchStart[] m_aOpCatch;

    private int m_nNextVar;

    private transient MultiGuard m_guard; // cached struct
    }
