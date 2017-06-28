package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.ConstantPoolAdapter;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

/**
 * CONSTR_N CONST-CONSTRUCT, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Construct_N extends OpCallable
    {
    private final int f_nConstructId;
    private final int[] f_anArgValue;

    public Construct_N(int nConstructorId, int[] anArg)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue = anArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            MethodStructure constructor = getMethodStructure(frame, f_nConstructId);

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, ConstantPoolAdapter.getVarCount(constructor), 1);
            if (ahVar == null)
                {
                return R_REPEAT;
                }
            ahVar[0] = frame.getArgument(0); // struct

            frame.chainFinalizer(ConstantPoolAdapter.makeFinalizer(constructor, ahVar));

            return frame.call1(constructor, null, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }