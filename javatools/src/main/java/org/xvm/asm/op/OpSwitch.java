package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Base class for switch (JmpVal_*) op-codes.
 */
public abstract class OpSwitch
        extends Op
    {
    /**
     * Construct an op.
     *
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    protected OpSwitch(Constant[] aConstCase, Op[] aOpCase, Op opDefault)
        {
        assert aOpCase != null;

        m_aConstCase = aConstCase;
        m_aOpCase    = aOpCase;
        m_opDefault  = opDefault;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpSwitch(DataInput in, Constant[] aconst)
            throws IOException
        {
        int   cCases    = readMagnitude(in);
        int[] anArgCase = new int[cCases];
        int[] aofCase   = new int[cCases];
        for (int i = 0; i < cCases; ++i)
            {
            anArgCase[i] = readPackedInt(in);
            aofCase  [i] = readPackedInt(in);
            }
        m_anConstCase = anArgCase;
        m_aofCase     = aofCase;

        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aConstCase != null)
            {
            m_anConstCase = encodeArguments(m_aConstCase, registry);
            }

        int[] anArgCase = m_anConstCase;
        int[] aofCase   = m_aofCase;
        int   c         = anArgCase.length;

        writePackedLong(out, c);
        for (int i = 0; i < c; ++i)
            {
            writePackedLong(out, anArgCase[i]);
            writePackedLong(out, aofCase  [i]);
            }

        writePackedLong(out, m_ofDefault);
        }

    @Override
    public void resolveAddresses(Op[] aop)
        {
        if (m_aOpCase == null)
            {
            int c = m_aofCase.length;
            m_aOpCase = new Op[c];
            for (int i = 0; i < c; i++)
                {
                m_aOpCase[i] = calcRelativeOp(aop, m_aofCase[i]);
                }
            m_opDefault = calcRelativeOp(aop, m_ofDefault);
            }
        else
            {
            int c = m_aOpCase.length;

            m_aofCase = new int[c];
            for (int i = 0; i < c; i++)
                {
                m_aofCase[i] = calcRelativeAddress(m_aOpCase[i]);
                }
            m_ofDefault = calcRelativeAddress(m_opDefault);
            }
        }

    @Override
    public void markReachable(Op[] aop)
        {
        super.markReachable(aop);

        Op[]  aOpCase = m_aOpCase;
        int[] aofCase = m_aofCase;
        for (int i = 0, c = aofCase.length; i < c; ++i)
            {
            aOpCase[i] = findDestinationOp(aop, aofCase[i]);
            aofCase[i] = calcRelativeAddress(aOpCase[i]);
            }

        m_opDefault = findDestinationOp(aop, m_ofDefault);
        m_ofDefault = calcRelativeAddress(m_opDefault);
        }

    @Override
    public boolean branches(Op[] aop, List<Integer> list)
        {
        resolveAddresses(aop);
        for (int i : m_aofCase)
            {
            list.add(i);
            }
        list.add(m_ofDefault);
        return true;
        }

    @Override
    public boolean advances()
        {
        return false;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aConstCase, registry);
        }

    /**
     * Make natural calls to determine if the specified value sits in the range (inclusive) between
     * the specified low and high values. The resulting Boolean value should be placed on the
     * caller's stack.
     *
     * @param fLow  if true, check the low value, otherwise the high value
     *
     * @return one of Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION values
     */
    protected int checkRange(Frame frame, TypeConstant typeCompare, ObjectHandle hValue,
                             ObjectHandle hLo, ObjectHandle hHi,
                             boolean fLoEx,    boolean      fHiEx,
                             boolean fLow, Frame.Continuation continuation)
        {
        switch (typeCompare.callCompare(frame, hValue, fLow ? hLo : hHi, Op.A_STACK))
            {
            case Op.R_NEXT:
                ObjectHandle hResult = frame.popStack();
                if (fLow)
                    {
                    boolean fMatch = fLoEx
                                        ? hResult == xOrdered.GREATER
                                        : hResult != xOrdered.LESSER;   // GREATER or EQUAL
                    if (!fMatch)
                        {
                        // we're done; no match
                        frame.pushStack(xBoolean.FALSE);
                        return continuation.proceed(frame);
                        }

                    return checkRange(frame, typeCompare, hValue,
                            hLo, hHi, fLoEx, fHiEx, false, continuation);
                    }
                else
                    {
                    boolean fMatch = fHiEx
                                        ? hResult == xOrdered.LESSER
                                        : hResult != xOrdered.GREATER;  // LESSER or EQUAL
                    frame.pushStack(xBoolean.makeHandle(fMatch));
                    return continuation.proceed(frame);
                    }

            case Op.R_CALL:
                Frame.Continuation stepNext = frameCaller ->
                    {
                    ObjectHandle hR = frameCaller.popStack();
                    if (fLow)
                        {
                        boolean fMatch = fLoEx
                                            ? hR == xOrdered.GREATER
                                            : hR != xOrdered.LESSER;   // GREATER or EQUAL
                        if (!fMatch)
                            {
                            // we're done; no match
                            frameCaller.pushStack(xBoolean.FALSE);
                            return continuation.proceed(frameCaller);
                            }

                        return checkRange(frameCaller, typeCompare, hValue,
                                hLo, hHi, fLoEx, fHiEx, false, continuation);
                        }
                    else
                        {
                        boolean fMatch = fHiEx
                                            ? hR == xOrdered.LESSER
                                            : hR != xOrdered.GREATER;  // LESSER or EQUAL
                        frameCaller.pushStack(xBoolean.makeHandle(fMatch));
                        return continuation.proceed(frameCaller);
                        }
                    };
                frame.m_frameNext.addContinuation(stepNext);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(' ');

        appendArgDescription(sb);

        int cOps     = m_aOpCase == null ? 0 : m_aOpCase.length;
        int cOffsets = m_aofCase == null ? 0 : m_aofCase.length;
        int cLabels  = Math.max(cOps, cOffsets);

        sb.append(cLabels)
          .append(":\n");

        int cConstCases  = m_aConstCase  == null ? 0 : m_aConstCase.length;
        int cNConstCases = m_anConstCase == null ? 0 : m_anConstCase.length;
        assert Math.max(cConstCases, cNConstCases) == cLabels;

        for (int i = 0; i < cLabels; ++i)
            {
            Constant arg  = i < cConstCases  ? m_aConstCase [i] : null;
            int      nArg = i < cNConstCases ? m_anConstCase[i] : Register.UNKNOWN;
            Op       op   = i < cOps         ? m_aOpCase    [i] : null;
            int      of   = i < cOffsets     ? m_aofCase    [i] : 0;

            if (i > 0)
                {
                sb.append(",\n");
                }

            sb.append(Argument.toIdString(arg, nArg))
                    .append(": ")
                    .append(OpJump.getLabelDesc(op, of));
            }

        sb.append("\ndefault: ")
                .append(OpJump.getLabelDesc(m_opDefault, m_ofDefault));

        return sb.toString();
        }

    protected abstract void appendArgDescription(StringBuilder sb);


    // ----- fields and enums ----------------------------------------------------------------------

    protected int[] m_anConstCase;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Constant[] m_aConstCase;
    private Op[]       m_aOpCase;
    private Op         m_opDefault;

    enum Algorithm
        {
        NativeSimple, NativeRange, NaturalSimple, NaturalRange;

        boolean isNative()
            {
            switch (this)
                {
                case NativeSimple:
                case NativeRange:
                    return true;

                default:
                    return false;
                }
            }

        Algorithm worstOf(Algorithm that)
            {
            return this.compareTo(that) <= 0 ? that : this;
            }
        }
    }