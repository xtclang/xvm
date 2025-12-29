package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpJump;
import org.xvm.asm.Scope;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;

import org.xvm.runtime.Frame;
import org.xvm.runtime.Frame.AllGuard;


/**
 * GUARDALL addr ; (implicit ENTER)
 */
public class GuardAll
        extends OpJump {
    /**
     * Construct a GUARDALL op based on the destination Op.
     *
     * @param op  the Op to jump to when the guarded section completes
     */
    public GuardAll(Op op) {
        super(op);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GuardAll(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GUARD_ALL;
    }

    @Override
    public boolean isEnter() {
        return true;
    }

    @Override
    public int process(Frame frame, int iPC) {
        int iScope = frame.enterScope(m_nNextVar);

        AllGuard guard = m_guard;
        if (guard == null) {
            m_guard = guard = new AllGuard(iPC, iScope, m_ofJmp);
        }
        frame.pushGuard(guard);

        return iPC + 1;
    }

    @Override
    public void simulate(Scope scope) {
        scope.enterGuardAll();
        scope.enter(this);

        m_nNextVar = scope.getCurVars();
    }

    @Override
    public boolean advances() {
        return true;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    /**
     * Store the information about the jump destination and whether or not return is required.
     */
    public void registerJumps(List<Integer> jumps, boolean fDoReturn) {
        m_jumps     = jumps;
        m_fDoReturn = fDoReturn;
    }

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        // the GuardAll introduces two scopes:
        //  - outer with synthetic vars: $rethrow, $contLoop, $breakLoop
        //  - inner loop that is guarded by "FinallyStart" op
        org.xvm.javajit.Scope scopeOuter = bctx.enterScope(code);

        // $rethrow = null;
        scopeOuter.allocateRethrow(code);

        // $jump1 = false; $jump2 = false; ...
        scopeOuter.allocateJumps(code, m_jumps);
        if (m_fDoReturn) {
            JitMethodDesc jmd        = bctx.methodDesc;
            boolean       fOptimized = bctx.isOptimized;
            int           cRets      = jmd.standardReturns.length;
            String        sRetVal    = "$ret";

            // $retN = false;
            int slotRet = scopeOuter.allocateSynthetic("$doReturn", TypeKind.BOOLEAN);
            assert slotRet != -1;
            code.iconst_0()
                .istore(slotRet);

            for (int i = 0; i < cRets; i++) {
                int          iOpt  = fOptimized ? jmd.getOptimizedReturnIndex(i) : -1;
                JitParamDesc pdRet = fOptimized ? jmd.optimizedReturns[iOpt] : jmd.standardReturns[i];
                ClassDesc    cd    = pdRet.cd;

                // $retN = 0 or null
                int slotR = scopeOuter.allocateSynthetic(sRetVal + i, Builder.toTypeKind(cd));
                Builder.defaultLoad(code, cd);
                Builder.store(code, cd, slotR);

                switch (pdRet.flavor) {
                case MultiSlotPrimitive:
                    int slotEx = scopeOuter.allocateSynthetic(sRetVal + (i + 1), TypeKind.BOOLEAN);
                    code.iconst_0()
                        .istore(slotEx);
                    break;
                }
            }
        }

        bctx.enterScope(code); // guarded by FinallyStart
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nNextVar;

    private transient AllGuard m_guard; // cached struct

    private transient List<Integer> m_jumps;
    private transient boolean       m_fDoReturn;
}
