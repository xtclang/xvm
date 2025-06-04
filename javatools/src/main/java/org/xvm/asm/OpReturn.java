package org.xvm.asm;

import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;

import static java.lang.constant.ConstantDescs.CD_boolean;

/**
 * Base class for the RETURN_* op-codes.
 */
public abstract class OpReturn
        extends Op {
    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);
    }

    @Override
    public boolean advances() {
        return false;
    }

    @Override
    public void resolveAddresses(Op[] aop) {
        super.resolveAddresses(aop);

        int nGuardAllDepth = getGuardAllDepth();
        if (nGuardAllDepth > 0) {
            Op opFinally = findFirstUnmatchedOp(aop, OP_GUARD_ALL, OP_FINALLY);

            assert opFinally.getGuardAllDepth() == nGuardAllDepth; // GuardAllDepth drops right after OP_FINALLY

            m_ixAllGuard   = opFinally.getGuardDepth() + nGuardAllDepth - 1;
            m_fCallFinally = true;
        }
    }

    // ----- JIT support ---------------------------------------------------------------------------

    public void buildReturn(BuildContext bctx, CodeBuilder code, int[] anRet) {
        int cRets = anRet.length;
        assert cRets > 0;

        JitMethodDesc jmd        = bctx.jmd;
        boolean       fOptimized = bctx.isOptimized;

        for (int i = cRets - 1; i >= 0; i--) {
            int          iOpt  = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
            JitParamDesc pdRet = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
            Slot         slot  = bctx.loadArgument(code, anRet[i], pdRet);
            ClassDesc    cd    = slot.cd();
            if (i == 0) {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert fOptimized;
                    if (cd.isPrimitive()) {
                        // `false` at Ctx.i0 and return the actual primitive value
                        code.iconst_0();
                        Builder.storeToContext(code, CD_boolean, 0);
                        Builder.addReturn(code, cd);
                    } else {
                        assert slot.type().isOnlyNullable();

                        // throw away Null; `true` at Ctx.i0 and return the default value
                        code.pop().iconst_1();
                        Builder.storeToContext(code, CD_boolean, 0);
                        Builder.defaultLoad(code, pdRet.cd);
                        Builder.addReturn(code, pdRet.cd);
                    }
                    break;

                default:
                    Builder.addReturn(code, cd);
                    break;
                }
            } else {
                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert fOptimized;
                    JitParamDesc pdExt = jmd.optimizedReturns[iOpt+1];
                    if (cd.isPrimitive()) {
                        // the actual primitive value and `false` at Ctx
                        Builder.storeToContext(code, cd, pdRet.altIndex);
                        code.iconst_0();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                    } else {
                        assert slot.type().isOnlyNullable();

                        // the default primitive value and `true` at Ctx
                        Builder.defaultLoad(code, pdRet.cd);
                        Builder.storeToContext(code, cd, pdRet.altIndex);
                        code.iconst_1();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                    }
                    break;

                default:
                    Builder.storeToContext(code, cd, pdRet.altIndex);
                    break;
                }
            }
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected boolean m_fCallFinally;
    protected int     m_ixAllGuard;
}
