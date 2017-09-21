package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Frame.Guard;
import org.xvm.proto.Frame.MultiGuard;
import org.xvm.asm.Op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * GUARD #handlers:(CONST_CLASS, CONST_STRING, rel_addr)
 *
 * @author gg 2017.03.08
 */
public class GuardStart extends Op
    {
    private final int[] f_anClassConstId;
    private final int[] f_anNameConstId;
    private final int[] f_anCatchRelAddress;

    private MultiGuard m_guard; // cached struct

    public GuardStart(int nClassConstId, int nNameConstId, int nCatchAddress)
        {
        f_anClassConstId = new int[] {nClassConstId};
        f_anNameConstId = new int[] {nNameConstId};
        f_anCatchRelAddress = new int[] {nCatchAddress};
        }

    public GuardStart(int[] anClassConstId, int[] anNameConstId, int[] anCatch)
        {
        assert anClassConstId.length == anCatch.length;

        f_anClassConstId = anClassConstId;
        f_anNameConstId   = anNameConstId;
        f_anCatchRelAddress = anCatch;
        }

    public GuardStart(DataInput in)
            throws IOException
        {
        int c = in.readUnsignedByte();

        f_anClassConstId = new int[c];
        f_anNameConstId = new int[c];
        f_anCatchRelAddress = new int[c];
        for (int i = 0; i < c; i++)
            {
            f_anClassConstId[i]    = in.readInt();
            f_anNameConstId[i]     = in.readInt();
            f_anCatchRelAddress[i] = in.readInt();
            }
        }

    @Override
    public void write(DataOutput out) throws IOException
        {
        out.write(OP_GUARD);

        int c = f_anClassConstId.length;
        out.write(c);

        for (int i = 0; i < c; i++)
            {
            out.writeInt(f_anClassConstId[i]);
            out.writeInt(f_anNameConstId[i]);
            out.writeInt(f_anCatchRelAddress[i]);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.enterScope();

        Guard guard = m_guard;
        if (guard == null)
            {
            guard = m_guard = new MultiGuard(iPC, iScope,
                    f_anClassConstId, f_anNameConstId, f_anCatchRelAddress);
            }
        frame.pushGuard(guard);

        return iPC + 1;
        }
    }
