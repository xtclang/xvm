package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import static org.xvm.javajit.Builder.CD_CtorCtx;
import static org.xvm.javajit.Builder.CD_TypeConstant;

/**
 * JIT specific information for a constructor.
 *
 * According to the step 6 of the "CommonBuilder.assembleNew" algorithm, the constructor signature
 * is:
 *  {@code construct$17(ctx, cctx, thi$, x, y, z)}, where "cctx" arg is optional,
 *
 * We also use this to create the MethodTypeDesc for {@code new$17(ctx, type, x, y, z)} function.
 */
public class JitCtorDesc
        extends JitMethodDesc {
    public JitCtorDesc(ClassDesc targetCD, boolean addCtorCtx, boolean addType,
                       JitParamDesc[] standardReturns, JitParamDesc[] standardParams,
                       JitParamDesc[] optimizedReturns, JitParamDesc[] optimizedParams) {
        this.targetCD   = targetCD;
        this.addCtorCtx = addCtorCtx;
        this.addType    = addType;

        super(standardReturns, standardParams, optimizedReturns, optimizedParams);
    }

    @Override
    public int getImplicitParamCount() {
        return super.getImplicitParamCount()
            + (addCtorCtx       ? 1 : 0)
            + (addType          ? 1 : 0)
            + (targetCD == null ? 0 : 1);
    }

    @Override
    protected int fillExtraClassDesc(ClassDesc[] paramCDs) {
        int ix = super.fillExtraClassDesc(paramCDs);

        if (addCtorCtx) {
            paramCDs[ix++] = CD_CtorCtx;
        }
        if (addType) {
            paramCDs[ix++] = CD_TypeConstant;
        }
        if (targetCD != null) {
            paramCDs[ix++] = targetCD;
        }
        return ix;
    }

    // ----- fields --------------------------------------------------------------------------------

    protected final ClassDesc targetCD;
    protected final boolean   addCtorCtx;
    protected final boolean   addType;
}
