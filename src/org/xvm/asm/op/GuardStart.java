package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

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
     * @param nClassConstId  the exception class to catch
     * @param nNameConstId   the name of the catch exception variable
     * @param nCatchAddress  the address of the catch handler
     */
    public GuardStart(int nClassConstId, int nNameConstId, int nCatchAddress)
        {
        this(new int[] {nClassConstId}, new int[] {nNameConstId}, new int[] {nCatchAddress});
        }

    /**
     * Construct a GUARD op for multiple exceptions.
     *
     * @param anClassConstId  the exception classes to catch
     * @param anNameConstId   the names of each catch exception variable
     * @param anCatch         the addresses of each catch handler
     */
    public GuardStart(int[] anClassConstId, int[] anNameConstId, int[] anCatch)
        {
        assert anClassConstId.length == anCatch.length;

        f_anClassConstId    = anClassConstId;
        f_anNameConstId     = anNameConstId;
        f_anCatchRelAddress = anCatch;
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

        f_anClassConstId    = new int[c];
        f_anNameConstId     = new int[c];
        f_anCatchRelAddress = new int[c];
        for (int i = 0; i < c; i++)
            {
            f_anClassConstId[i]    = readPackedInt(in);
            f_anNameConstId[i]     = readPackedInt(in);
            f_anCatchRelAddress[i] = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry) throws IOException
        {
        out.writeByte(OP_GUARD);

        int c = f_anClassConstId.length;
        writePackedLong(out, c);

        for (int i = 0; i < c; i++)
            {
            writePackedLong(out, f_anClassConstId[i]);
            writePackedLong(out, f_anNameConstId[i]);
            writePackedLong(out, f_anCatchRelAddress[i]);
            }
        }

    @Override
    public int getOpCode()
        {
        return OP_GUARD;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        MultiGuard guard = m_guard;
        if (guard == null)
            {
            m_guard = guard = new MultiGuard(iPC, iScope,
                    f_anClassConstId, f_anNameConstId, f_anCatchRelAddress);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        }

    private final int[] f_anClassConstId;
    private final int[] f_anNameConstId;
    private final int[] f_anCatchRelAddress;

    private transient MultiGuard m_guard; // cached struct
    }
