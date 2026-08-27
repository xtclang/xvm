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

import org.xvm.javajit.Builder.Loader;

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
        int      cRets   = anRet.length;
        Loader[] loaders = new Loader[cRets];
        for (int i = 0; i < cRets; i++) {
            int retId = anRet[i];
            loaders[i] = code_ -> bctx.loadArgument(code_, retId);
        }
        return buildReturn(bctx, code, loaders);
    }

    /**
     * Build a return using the specified logical return-value loaders.
     */
    public int buildReturn(BuildContext bctx, CodeBuilder code, Loader[] retLoaders) {
        int cRets = retLoaders.length;
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
                int[]        retIndexes;
                JitParamDesc pdRet;
                int          slotValEx;

                if (fOptimized) {
                    retIndexes = jmd.getAllOptimizedReturnIndexes(i);

                    int          iExt    = retIndexes[retIndexes.length - 1];
                    JitParamDesc pdExt   = jmd.optimizedReturns[iExt];
                    String       extName = GuardAll.returnSlotName(pdExt);
                    slotValEx = bctx.scope.getSynthetic(extName, true);
                    pdRet     = jmd.optimizedReturns[retIndexes[0]];
                } else {
                    retIndexes = new int[]{jmd.standardReturns[i].index};
                    slotValEx  = -1;
                    pdRet      = jmd.standardReturns[i];
                }

                String       slotName = GuardAll.returnSlotName(pdRet);
                int          slotR    = bctx.scope.getSynthetic(slotName, true);
                RegisterInfo regRet   = retLoaders[i].load(code);
                ClassDesc    cd       = regRet.cd();

                String sTransform = regRet.flavor().name() + "->" + pdRet.flavor.name();
                switch (sTransform) {
                case "Primitive->Primitive",
                     "Specific->Specific",
                     "Specific->Widened",
                     "Widened->Specific",
                     "Widened->Widened":
                    Builder.store(code, cd, slotR);
                    break;

                case "Specific->Primitive",
                     "Widened->Primitive":
                    assert fOptimized;
                    Builder.unbox(code, pdRet.type);
                    Builder.store(code, pdRet.cd, slotR);
                    break;

                case "Primitive->NullablePrimitive":
                    assert fOptimized;
                    // iSynth - the actual primitive value; and `false` at iSynth+1
                    Builder.store(code, cd, slotR);
                    code.iconst_0();
                    Builder.store(code, CD_boolean, slotValEx);
                    break;

                case "NullablePrimitive->Primitive":
                    assert fOptimized;
                    code.pop();
                    Builder.store(code, cd, slotR);
                    break;

                case "NullablePrimitive->NullablePrimitive":
                    assert fOptimized;
                    // iSynth - the primitive value and its null indicator
                    Builder.store(code, CD_boolean, slotValEx);
                    Builder.store(code, cd, slotR);
                    break;

                case "Specific->NullablePrimitive",
                     "Widened->NullablePrimitive":
                    assert fOptimized;
                    // e.g.: Int? f() = Null; load the default primitive value and a `true` null
                    // indicator
                    Builder.unboxNullable(code, pdRet.type,
                            bctx.builder.ensureClassDesc(pdRet.type.removeNullable()));
                    Builder.store(code, CD_boolean, slotValEx);
                    Builder.store(code, pdRet.cd, slotR);
                    break;

                case "XvmPrimitive->XvmPrimitive":
                    // iSynth - the primitive values into slots in reverse order
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length);
                    break;

                case "Specific->XvmPrimitive",
                     "Widened->XvmPrimitive":
                    assert fOptimized;
                    Builder.unbox(code, pdRet.type);
                    // iSynth - the primitive values into slots in reverse order
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length);
                    break;

                case "XvmPrimitive->NullableXvmPrimitive":
                    assert fOptimized;
                    // iSynth - `false` at iSynth+n and the primitive values in reverse
                    code.iconst_0();
                    Builder.store(code, CD_boolean, slotValEx);
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length - 1);
                    break;

                case "Specific->NullableXvmPrimitive",
                     "Widened->NullableXvmPrimitive":
                    assert fOptimized;
                    // e.g.: Int128? f() = Null; load the default values and a `true` null indicator
                    Builder.unboxNullable(code, pdRet.type,
                            bctx.builder.ensureClassDesc(pdRet.type.removeNullable()));
                    // iSynth - the null indicator and primitive values in reverse order
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length);
                    break;

                case "NullableXvmPrimitive->XvmPrimitive":
                    assert fOptimized;
                    code.pop();
                    // iSynth - the primitive values into slots in reverse order
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length);
                    break;

                case "NullableXvmPrimitive->NullableXvmPrimitive":
                    assert fOptimized;
                    // iSynth - the null indicator and primitive values in reverse order
                    storeOptimizedReturns(bctx, code, retIndexes, retIndexes.length);
                    break;

                default:
                    throw new UnsupportedOperationException("Not implemented: " + sTransform);
                }
            }

            assert m_nFinallyAddr > getAddress();
            code.goto_(bctx.ensureLabel(code, m_nFinallyAddr));
        } else {
            for (int i = cRets - 1; i >= 0; i--) {
                int[]        optIndexes;
                JitParamDesc pdRet;
                JitParamDesc pdExt;

                if (fOptimized) {
                    optIndexes = jmd.getAllOptimizedReturnIndexes(i);
                    pdRet      = jmd.optimizedReturns[optIndexes[0]];
                    pdExt      = jmd.optimizedReturns[optIndexes[optIndexes.length - 1]];
                } else {
                    optIndexes = null;
                    pdRet      = jmd.standardReturns[i];
                    pdExt      = null;
                }

                RegisterInfo regRet   = retLoaders[i].load(code);
                ClassDesc    cd       = regRet.cd();
                String       sTransform = regRet.flavor().name() + "->" + pdRet.flavor.name();

                switch (sTransform) {
                case "NullablePrimitive->NullablePrimitive":
                    assert fOptimized;
                    // e.g.: Int? f(Int? i) = i;
                    bctx.storeToContext(code, CD_boolean, pdExt.altIndex);
                    break;

                case "NullablePrimitive->Primitive":
                    // e.g.: Int f(Int? i) = i ?: -1;
                    code.pop();
                    break;

                case "Primitive->NullablePrimitive":
                    assert fOptimized;
                    // e.g.: Int? f() = 42;
                    // pass `false` at Ctx
                    code.iconst_0();
                    bctx.storeToContext(code, CD_boolean, pdExt.altIndex);
                    break;

                case "Primitive->Primitive",
                     "Specific->Specific",
                     "Specific->Widened",
                     "Widened->Widened":
                    break;

                case "Widened->Specific":
                    code.checkcast(pdRet.cd);
                    break;

                case "Specific->Primitive",
                     "Widened->Primitive":
                    Builder.unbox(code, pdRet.type);
                    cd = pdRet.cd;
                    break;

                case "Specific->NullablePrimitive",
                     "Widened->NullablePrimitive":
                    // e.g.: Int? f() = Null; load the default primitive value and a `true` null
                    // indicator
                    Builder.unboxNullable(code, pdRet.type,
                            bctx.builder.ensureClassDesc(pdRet.type.removeNullable()));
                    bctx.storeToContext(code, CD_boolean, pdExt.altIndex);
                    cd = pdRet.cd;
                    break;

                case "NullableXvmPrimitive->NullableXvmPrimitive":
                    assert fOptimized;
                    // e.g.: Int128? f(Int128? i) = i;
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 1);
                    cd = pdRet.cd;
                    break;

                case "NullableXvmPrimitive->XvmPrimitive":
                    // e.g.: Int f(Int? i) = i ?: -1;
                    assert fOptimized;
                    // pop the boolean nullable flag
                    code.pop();
                    // store the remaining primitives to the context
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 1);
                    cd = pdRet.cd;
                    break;

                case "XvmPrimitive->XvmPrimitive":
                    assert fOptimized;
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 1);
                    cd = pdRet.cd;
                    break;

                case "Specific->XvmPrimitive",
                     "Widened->XvmPrimitive":
                    Builder.unbox(code, pdRet.type);
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 1);
                    cd = pdRet.cd;
                    break;

                case "XvmPrimitive->NullableXvmPrimitive":
                    assert fOptimized;
                    // e.g.: Int? f() = 42;
                    // store the remaining primitives to the context
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 2);
                    // pass `false` in the Ctx slot for the boolean nullable flag
                    code.iconst_0();
                    bctx.storeToContext(code, CD_boolean, pdExt.altIndex);
                    cd = pdRet.cd;
                    break;

                case "Specific->NullableXvmPrimitive",
                     "Widened->NullableXvmPrimitive":
                    // e.g.: Int128? f() = Null; load the default values and a `true` null indicator
                    Builder.unboxNullable(code, pdRet.type,
                            bctx.builder.ensureClassDesc(pdRet.type.removeNullable()));
                    bctx.storeOptReturnsToContext(code, optIndexes, 1, optIndexes.length - 1);
                    cd = pdRet.cd;
                    break;

                default:
                    throw new UnsupportedOperationException("Not implemented: " + sTransform);
                }

                if (i == 0) {
                    // return the actual primitive value
                    Builder.addReturn(code, cd);
                } else {
                    // pass the actual primitive value at Ctx
                    bctx.storeToContext(code, pdRet.cd, pdRet.altIndex);
                }
            }
        }
        return -1;
    }

    /**
     * Store optimized return components from the Java stack into their synthetic return slots.
     */
    private void storeOptimizedReturns(BuildContext bctx, CodeBuilder code,
                                       int[] anRetIndexes, int cIndexes) {
        JitParamDesc[] apdRet = bctx.methodDesc.optimizedReturns;
        for (int i = cIndexes - 1; i >= 0; i--) {
            JitParamDesc pdRet = apdRet[anRetIndexes[i]];
            String       sName = GuardAll.returnSlotName(pdRet);
            int          nSlot = bctx.scope.getSynthetic(sName, true);
            Builder.store(code, pdRet.cd, nSlot);
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    protected transient int     m_ixAllGuard;
    protected transient boolean m_fCallFinally;
    protected transient int     m_nFinallyAddr;
}
