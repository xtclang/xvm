package org.xvm.asm.op;


import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.javajit.BuildContext;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.DeferredGuardAction;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xNullable;

import static java.lang.constant.ConstantDescs.CD_boolean;


/**
 * FINALLY_END ; finish a "finally" handler (Implicit EXIT)
 * <p/>
 * Each FINALLY_END op must match up with a previous GUARD_ALL and FINALLY op.
 * <p/>
 * The FINALLY_END op either re-throws the exception that occurred within the GUARD_ALL block, or
 * if no exception had occurred, it exits the scope and proceeds to the next instruction.
 */
public class FinallyEnd
        extends Op {
    /**
     * Construct an FINALLY_END op.
     */
    public FinallyEnd() {
    }

    @Override
    public int getOpCode() {
        return OP_FINALLY_END;
    }

    @Override
    public boolean isExit() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        // a possible exception sits in the first variable of this scope,
        // which is the same as the "next" variable in the previous scope
        // (see Frame.findGuard and FinallyStart.process)
        int nException = frame.f_anNextVar[frame.m_iScope - 1];

        ObjectHandle hException = frame.f_ahVar[nException];
        if (hException == xNullable.NULL) {
            DeferredGuardAction deferred = frame.m_deferred;
            if (deferred == null) {
                // the "finally" scope was entered naturally and exits naturally
                frame.exitScope();
                return iPC + 1;
            }

            // the "finally" scope was jumped to from a RETURN_* or JUMP op
            return frame.processAllGuard(deferred);
        }

        // re-throw
        return frame.raiseException((ExceptionHandle) hException);
    }

    @Override
    public void simulate(Scope scope) {
        scope.exit(this);
    }

    @Override
    public boolean advances() {
        return m_fAdvances;
    }

    /**
     * @param fCompletes  if true, indicates Whether this FinallyEnd op-code can proceed in a normal
     *                    fashion (to the next op); otherwise, only via a throw or jump
     */
    public void setCompletes(boolean fCompletes) {
        m_fAdvances = fCompletes;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        org.xvm.javajit.Scope scopeFin   = bctx.exitScope(code);
        org.xvm.javajit.Scope scopeGuard = scopeFin.parent;

        // the "$rethrow" is an optional exception that we are supposed to re-throw;
        // see FinallyStart
        int slotRethrow = scopeGuard.getRethrow();
        assert slotRethrow >= 0;

        Label labelNormal = code.newLabel();
        code.aload(slotRethrow)
            .dup()
            .ifnull(labelNormal)
            .athrow()
            .labelBinding(labelNormal)
            .pop();

        // the "jumps" list contains optional jump addresses; see Jump
        for (Integer jump : scopeGuard.jumps) {
            int   slotJump  = scopeGuard.getSynthetic("$jump" + jump, false);
            Label labelJump = bctx.ensureLabel(code, jump);

            Label labelSkip = code.newLabel();
            code.iload(slotJump) // boolean: if true, jump is needed
                .ifeq(labelSkip)
                .goto_(labelJump)
                .labelBinding(labelSkip);
        }

        int slotRet = scopeGuard.getSynthetic("$doReturn", false);
        if (slotRet >= 0) {
            // this is the topmost FinallyEnd; generate the return code
            JitMethodDesc jmd        = bctx.jmd;
            boolean       fOptimized = bctx.isOptimized;
            int           cRets      = jmd.standardReturns.length;
            String        sRetVal    = "$ret";

            Label labelSkip = null;
            if (advances()) {
                labelSkip = code.newLabel();
                code.iload(slotRet);  // boolean: if true, return is needed
                code.ifeq(labelSkip);
            } else {
                // $doReturn must be "true" here
            }

            for (int i = cRets - 1; i >= 0; i--) {
                int          iOpt    = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
                JitParamDesc pdRet   = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
                ClassDesc    cd      = pdRet.cd;
                int          slotVal = scopeGuard.getSynthetic(sRetVal + i, false);

                Builder.load(code, cd, slotVal);

                // the code below is complementary to the code in OpReturn
                if (i == 0) {
                    switch (pdRet.flavor) {
                    case MultiSlotPrimitive:
                        assert fOptimized;
                        int slotEx = scopeGuard.getSynthetic(sRetVal + (i + 1), false);
                        code.iload(slotEx);
                        Builder.storeToContext(code, CD_boolean, 0);
                        Builder.addReturn(code, cd);
                        break;

                    default:
                        Builder.addReturn(code, cd);
                        break;
                    }
                } else {
                    switch (pdRet.flavor) {
                    case MultiSlotPrimitive:
                        assert fOptimized;
                        int slotEx = scopeGuard.getSynthetic(sRetVal + (i + 1), false);
                        code.iload(slotEx);

                        JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];
                        Builder.storeToContext(code, cd, pdRet.altIndex);
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        break;

                    default:
                        Builder.storeToContext(code, cd, pdRet.altIndex);
                        break;
                    }
                }
            }
            if (labelSkip != null) {
                code.labelBinding(labelSkip);
            }
        }
        bctx.exitScope(code);
    }

    // ----- fields --------------------------------------------------------------------------------

    private transient boolean m_fAdvances = true;
}