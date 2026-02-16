package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;

import org.xvm.runtime.template.xNullable;

import static java.lang.constant.ConstantDescs.CD_Throwable;

import static org.xvm.javajit.Builder.CD_Exception;
import static org.xvm.javajit.Builder.CD_nException;


/**
 * FINALLY ; begin a "finally" handler (implicit EXIT/ENTER and VAR_I of type "Exception?")
 * <p/>
 * The FINALLY op indicates the beginning of the "finally" block. If the block is executed at the
 * normal conclusion of the "try" block, then the variable is null; if the block is executed due
 * to an exception within the "try" block, the the variable holds that exception. The finally block
 * concludes with a matching FINALLY_END op.
 */
public class FinallyStart
        extends OpVar {
    /**
     * Construct a FINALLY op.
     */
    public FinallyStart(Register reg) {
        super(reg);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public FinallyStart(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    protected boolean isTypeAware() {
        return false;
    }

    @Override
    public int getOpCode() {
        return OP_FINALLY;
    }

    @Override
    public boolean isEnter() {
        return true;
    }

    @Override
    public boolean isExit() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        frame.exitScope();
        frame.popGuard();

        frame.enterScope(m_nVar);

        // this op-code can only be reached by the normal flow of execution,
        // while upon an exception, the GuardAll would jump to the very next op
        // (called from Frame.findGuard) with an exception handle at anNextVar[iScope] + 1,
        // so we need to initialize the exception slot (to Null) when coming in normally;
        // presence or absence of the exception will be checked by the FinallyEnd
        frame.introduceResolvedVar(m_nVar, frame.poolContext().typeException१(), null,
                Frame.VAR_STANDARD, xNullable.NULL);

        return iPC + 1;
    }

    @Override
    public void markReachable(Op[] aop) {
        super.markReachable(aop);
        findCorrespondingOp(aop, OP_FINALLY_END).markNecessary();
    }

    @Override
    public void simulate(Scope scope) {
        scope.exit(this);
        scope.exitGuardAll();
        scope.enter(this);

        // super call allocates a var for the exception
        super.simulate(scope);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        bctx.exitScope(null);
        bctx.enterScope(null);

        // we could be called for dead code to correctly compute the scopes, but should not
        // compute types further
        if (bctx.typeMatrix.isReached(getAddress())) {
            bctx.typeMatrix.assign(getAddress(), m_nVar, bctx.pool().typeException१());
        }
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        org.xvm.javajit.Scope scopeGuarded = bctx.exitScope(code);
        assert scopeGuarded.parent == bctx.scope;

        // this op can only be reached normally
        java.lang.classfile.Label labelFin = code.newLabel();
        code.goto_(labelFin);

        // add to the exception table
        java.lang.classfile.Label labelCatch = code.newLabel();
        code.exceptionCatch(scopeGuarded.startLabel, scopeGuarded.endLabel, labelCatch, CD_Throwable);

        int    slotRethrow = bctx.scope.getRethrow();
        assert slotRethrow >= 0;
        code.labelBinding(labelCatch)
            .astore(slotRethrow)
            .labelBinding(labelFin);

        // enter the "finally {}" scope
        bctx.enterScope(code);

        // initialize "try.exception" synthetic variable
        // (TODO: we only need it if the m_nVar variable is used)
        RegisterInfo regEx = bctx.introduceVar(code, m_nVar, bctx.pool().typeException१(), "");

        java.lang.classfile.Label labelNull = code.newLabel();
        java.lang.classfile.Label labelEnd  = code.newLabel();
        code.aload(slotRethrow)
            .dup()
            .ifnull(labelNull)
            .checkcast(CD_nException)
            .getfield(CD_nException, "exception", CD_Exception);
        bctx.storeValue(code, regEx);
        code.goto_(labelEnd)
            .labelBinding(labelNull)
            .pop();
        Builder.loadNull(code);
        bctx.storeValue(code, regEx);
        code.labelBinding(labelEnd);
        return -1;
    }
}
