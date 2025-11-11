package org.xvm.asm;

import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.RegisterInfo;

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

    /**
     * @return true iff this Jump op needs to go first to the finally block
     */
    public boolean shouldCallFinally() {
        return m_fCallFinally;
    }

    /**
     * Save the address of the "finally" block to jump to.
     */
    public void registerJump(int nFinallyAddr) {
        assert m_fCallFinally;

        m_nFinallyAddr = nFinallyAddr;
    }

    /**
     * Customization of the {@link #build} method. See {@link org.xvm.asm.op.GuardAll#build} for
     * the return values allocations.
     */
    public void buildReturn(BuildContext bctx, CodeBuilder code, int[] anRet) {
        int cRets = anRet.length;
        assert cRets > 0;

        JitMethodDesc jmd        = bctx.methodDesc;
        boolean       fOptimized = bctx.isOptimized;

        if (m_fCallFinally) {
            String sRet    = "$doReturn";
            String sRetVal = "$ret";

            // $retN = true;
            int slotRet = bctx.scope.getSynthetic(sRet, true);
            assert slotRet != -1;
            code.iconst_1()
                .istore(slotRet);

            // $retN = ...
            for (int i = 0; i < cRets; i++) {
                int          iOpt   = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
                JitParamDesc pdRet  = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
                RegisterInfo regRet = bctx.loadArgument(code, anRet[i], pdRet);
                ClassDesc    cd     = regRet.cd();
                int          slotR  = bctx.scope.getSynthetic(sRetVal + i, true);

                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    assert fOptimized;
                    int slotValEx = bctx.scope.getSynthetic(sRetVal + (i + 1), true);
                    if (cd.isPrimitive()) {
                        // iSynth - the actual primitive value; and `false` at iSynth+1
                        Builder.store(code, cd, slotR);
                        code.iconst_0();
                        Builder.store(code, CD_boolean, slotValEx);
                    } else {
                        assert regRet.type().isOnlyNullable();

                        // iSynth - the default primitive value and `true` at iSynth+1
                        Builder.defaultLoad(code, pdRet.cd);
                        Builder.store(code, cd, slotR);
                        code.iconst_1();
                        Builder.store(code, CD_boolean, slotValEx);
                    }
                    break;

                default:
                    Builder.store(code, cd, slotR);
                    break;
                }
            }

            assert m_nFinallyAddr > getAddress();
            code.goto_(bctx.ensureLabel(code, m_nFinallyAddr));
        } else {
            for (int i = cRets - 1; i >= 0; i--) {
                int          iOpt   = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
                JitParamDesc pdRet  = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
                RegisterInfo regRet = bctx.loadArgument(code, anRet[i], pdRet);
                ClassDesc    cd     = regRet.cd();
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
                            assert regRet.type().isOnlyNullable();

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
                            assert regRet.type().isOnlyNullable();

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
    }

    // ----- fields --------------------------------------------------------------------------------

    protected transient int     m_ixAllGuard;
    protected transient boolean m_fCallFinally;
    protected transient int     m_nFinallyAddr;
}
