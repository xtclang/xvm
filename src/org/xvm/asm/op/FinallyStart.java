package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;

import org.xvm.runtime.template.xNullable;


/**
 * FINALLY ; begin a "finally" handler (implicit EXIT/ENTER and VAR_I of type "Exception?")
 * <p/>
 * The FINALLY op indicates the beginning of the "finally" block. If the block is executed at the
 * normal conclusion of the "try" block, then the variable is null; if the block is executed due
 * to an exception within the "try" block, the the variable holds that exception. The finally block
 * concludes with a matching FINALLY_END op.
 */
public class FinallyStart
        extends OpVar
    {
    /**
     * Construct a FINALLY op.
     */
    public FinallyStart(Register reg)
        {
        super(reg);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FinallyStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    protected boolean isTypeAware()
        {
        return false;
        }

    @Override
    public int getOpCode()
        {
        return OP_FINALLY;
        }

    @Override
    public boolean isEnter()
        {
        return true;
        }

    @Override
    public boolean isExit()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.exitScope();

        frame.enterScope(m_nVar);

        // this op-code can only be reached by the normal flow of execution,
        // while upon an exception, the GuardAll would jump to the very next op
        // (called from Frame.findGuard) with an exception at anNextVar[iScope] + 1,
        // so we need to initialize the exception slot (to Null) when coming in normally;
        // presence or absence of the exception will be checked by the FinallyEnd
        frame.introduceResolvedVar(m_nVar, frame.poolContext().typeException१(), null,
                Frame.VAR_STANDARD, xNullable.NULL);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.exit();
        scope.enter();
        super.simulate(scope);
        }
    }
