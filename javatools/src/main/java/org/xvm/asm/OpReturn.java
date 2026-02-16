package org.xvm.asm;

import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.op.GuardAll;

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

    @Override
    public void computeTypes(BuildContext bctx) {
        // only propagate onto the "finally" block
        if (m_fCallFinally) {
            bctx.typeMatrix.follow(m_nFinallyAddr);
        }
    }

    /**
     * Customization of the {@link #build} method. See {@link org.xvm.asm.op.GuardAll#build} for
     * the return values allocations.
     */
    public int buildReturn(BuildContext bctx, CodeBuilder code, int[] anRet) {
        int cRets = anRet.length;
        assert cRets > 0;

        JitMethodDesc jmd        = bctx.methodDesc;
        boolean       fOptimized = bctx.isOptimized;

        if (m_fCallFinally) {
            // $retN = true;
            int slotRet = bctx.scope.getSynthetic(GuardAll.DO_RETURN_SLOT_NAME, true);
            assert slotRet != -1;
            code.iconst_1()
                .istore(slotRet);

            // $retN = ...
            for (int i = 0; i < cRets; i++) {
                int[]        optIndexes = jmd.getAllOptimizedReturnIndexes(i);
                int          iOpt       = fOptimized ? optIndexes[0] : -1;
                JitParamDesc pdRet      = fOptimized ? jmd.optimizedReturns[iOpt]
                                                     : jmd.standardReturns[i];
                int          iExt       = fOptimized ? optIndexes[optIndexes.length - 1] : -1;
                JitParamDesc pdExt      = fOptimized ? jmd.optimizedReturns[iExt] : null;
                RegisterInfo regRet     = bctx.loadArgument(code, anRet[i]);
                ClassDesc    cd         = regRet.cd();
                String       slotName   = GuardAll.returnSlotName(pdRet);
                int          slotR      = bctx.scope.getSynthetic(slotName, true);
                String       extName    = fOptimized ? GuardAll.returnSlotName(pdExt) : "";
                int          slotValEx  = fOptimized ? bctx.scope.getSynthetic(extName, true) : -1;
                JitParamDesc retDesc;

                switch (pdRet.flavor) {
                case NullablePrimitive:
                    assert fOptimized;
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

                case XvmPrimitive:
                    // iSynth - the primitive values into slots in reverse order
                    for (int j = optIndexes.length - 1; j >= 0; j--) {
                        retDesc = jmd.optimizedReturns[optIndexes[j]];
                        slotName   = GuardAll.returnSlotName(retDesc);
                        slotR  = bctx.scope.getSynthetic(slotName, true);
                        Builder.store(code, retDesc.cd, slotR);
                    }
                    break;

                case NullableXvmPrimitive:
                    assert fOptimized;
                    if (regRet.type().removeNullable().isXvmPrimitive()) {
                        // iSynth - `false` at iSynth+n and the primitive values in reverse
                        for (int j = optIndexes.length - 1; j >= 0; j--) {
                            retDesc  = jmd.optimizedReturns[optIndexes[j]];
                            slotName = GuardAll.returnSlotName(retDesc);
                            slotR    = bctx.scope.getSynthetic(slotName, true);
                            Builder.store(code, retDesc.cd, slotR);
                        }
                    } else { // return is Null
                        assert regRet.type().isOnlyNullable();
                        // iSynth - `true` at iSynth+n and the default primitive values in reverse
                        int j = optIndexes.length - 1;
                        code.iconst_1();
                        Builder.store(code, CD_boolean, slotValEx);
                        for (; j >= 0; j--) {
                            retDesc  = jmd.optimizedReturns[optIndexes[j]];
                            slotName = GuardAll.returnSlotName(retDesc);
                            slotR    = bctx.scope.getSynthetic(slotName, true);
                            Builder.defaultLoad(code, retDesc.cd);
                            Builder.store(code, retDesc.cd, slotR);
                        }
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
                int[]        optIndexes = fOptimized ? jmd.getAllOptimizedReturnIndexes(i) : null;
                int          iOpt       = fOptimized ? optIndexes[0] : -1;
                JitParamDesc pdRet      = fOptimized ? jmd.optimizedReturns[iOpt]
                                                     : jmd.standardReturns[i];
                int          iExt       = fOptimized ? optIndexes[optIndexes.length - 1] : -1;
                JitParamDesc pdExt      = fOptimized ? jmd.optimizedReturns[iExt] : null;
                RegisterInfo regRet     = bctx.loadArgument(code, anRet[i]);
                ClassDesc    cd         = regRet.cd();
                boolean      fValid     = true;

                switch (regRet.flavor()) {
                case NullablePrimitive:
                    switch (pdRet.flavor) {
                    case NullablePrimitive:
                        assert fOptimized;
                        // e.g.: Int? f(Int? i) = i;
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        break;

                    case Primitive:
                        // e.g.: Int f(Int? i) = i ?: -1;
                        code.pop();
                        break;

                    default:
                        fValid = false;
                        break;
                    }
                    break;

                case Primitive: {
                    assert fOptimized;
                    switch (pdRet.flavor) {
                    case NullablePrimitive:
                        // e.g.: Int? f() = 42;

                        // pass `false` at Ctx
                        code.iconst_0();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        break;

                    case Primitive:
                        break;

                    default:
                        fValid = false;
                        break;
                    }
                    break;
                }

                case Specific:
                    switch (pdRet.flavor) {
                    case NullablePrimitive:
                        // e.g.: Int? f() = Null;
                        assert fOptimized && regRet.type().isOnlyNullable();

                        // throw away Null; `true` at Ctx and return the default value
                        code.pop().iconst_1();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        cd = pdRet.cd;
                        Builder.defaultLoad(code, cd);
                        break;

                    case NullableXvmPrimitive:
                        // e.g.: Int128? f() = Null;
                        assert fOptimized && regRet.type().isOnlyNullable();

                        // throw away Null; `true` at Ctx and return the default value
                        // since Null is being returned, there is no need to load default values
                        // to the context
                        code.pop().iconst_1();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        cd = pdRet.cd;
                        Builder.defaultLoad(code, cd);
                        break;

                    case Specific:
                        break;

                    default:
                        fValid = false;
                        break;
                    }
                    break;

                case NullableXvmPrimitive:
                    switch (pdRet.flavor) {
                    case NullableXvmPrimitive:
                        assert fOptimized;
                        // e.g.: Int128? f(Int128? i) = i;
                        for (int j = optIndexes.length - 1; j >= 1 ; j--) {
                            JitParamDesc retDesc = jmd.optimizedReturns[optIndexes[j]];
                            Builder.storeToContext(code, retDesc.cd, retDesc.altIndex);
                        }
                        cd = pdRet.cd;
                        break;

                    case XvmPrimitive:
                        // e.g.: Int f(Int? i) = i ?: -1;
                        assert fOptimized;
                        // pop the boolean nullable flag
                        code.pop();
                        // store the remaining primitives to the context
                        for (int j = optIndexes.length - 1; j >= 1 ; j--) {
                            JitParamDesc retDesc = jmd.optimizedReturns[optIndexes[j]];
                            Builder.storeToContext(code, retDesc.cd, retDesc.altIndex);
                        }
                        cd = pdRet.cd;
                        break;

                    default:
                        fValid = false;
                        break;
                    }
                    break;

                case XvmPrimitive:
                    assert fOptimized;
                    switch (pdRet.flavor) {
                    case XvmPrimitive:
                        for (int j = optIndexes.length - 1; j >= 1 ; j--) {
                            JitParamDesc retDesc = jmd.optimizedReturns[optIndexes[j]];
                            Builder.storeToContext(code, retDesc.cd, retDesc.altIndex);
                        }
                        cd = pdRet.cd;
                        break;

                    case NullableXvmPrimitive:
                        // e.g.: Int? f() = 42;
                        // store the remaining primitives to the context
                        for (int j = optIndexes.length - 2; j >= 1 ; j--) {
                            JitParamDesc retDesc = jmd.optimizedReturns[optIndexes[j]];
                            Builder.storeToContext(code, retDesc.cd, retDesc.altIndex);
                        }
                        // pass `false` in the Ctx slot for the boolean nullable flag
                        code.iconst_0();
                        Builder.storeToContext(code, CD_boolean, pdExt.altIndex);
                        cd = pdRet.cd;
                        break;

                    default:
                        fValid = false;
                        break;

                    }
                    break;
                }

                if (fValid) {
                    if (i == 0) {
                        // return the actual primitive value
                        Builder.addReturn(code, cd);
                    } else {
                        // pass the actual primitive value at Ctx
                        Builder.storeToContext(code, pdRet.cd, pdRet.altIndex);
                    }
                } else {
                    throw new UnsupportedOperationException(
                        "Not implemented: src=" + regRet.flavor() + "; dst=" + pdRet.flavor);
                }
            }
        }
        return -1;
    }

    // ----- fields --------------------------------------------------------------------------------

    protected transient int     m_ixAllGuard;
    protected transient boolean m_fCallFinally;
    protected transient int     m_nFinallyAddr;
}
