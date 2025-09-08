package org.xvm.javajit;

import java.lang.constant.ClassDesc;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.MethodBody;

import static org.xvm.javajit.Builder.CD_CtorCtx;

/**
 * JIT specific information for a constructor.
 *
 * According to the step 6 of the "CommonBuilder.assembleNew" algorithm, the constructor signature
 * is: {@code construct$0(ctx, cctx, thi$, x, y, z)}, where "cctx" arg is optional
 */
public class JitCtorDesc
        extends JitMethodDesc {
    public JitCtorDesc(TypeSystem ts, MethodBody methodBody,
                       JitParamDesc[] standardReturns, JitParamDesc[] standardParams,
                       JitParamDesc[] optimizedReturns, JitParamDesc[] optimizedParams) {
        this.ts         = ts;
        this.methodBody = methodBody;

        super(standardReturns, standardParams, optimizedReturns, optimizedParams);
    }

    @Override
    public int getImplicitParamCount() {
        return 3; // TODO: method.isFinalizerRequired ? 3 : 2;
    }

    @Override
    protected void fillExtraClassDesc(ClassDesc[] paramCDs) {
        super.fillExtraClassDesc(paramCDs);

        ClassStructure clz = methodBody.getMethodStructure().getContainingClass();

        paramCDs[1] = CD_CtorCtx;
        paramCDs[2] = clz.getCanonicalType().ensureClassDesc(ts);
    }

    // ----- fields --------------------------------------------------------------------------------

    protected final TypeSystem ts;
    protected final MethodBody methodBody;
}
