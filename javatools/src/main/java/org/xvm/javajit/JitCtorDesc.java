package org.xvm.javajit;

import java.lang.constant.ClassDesc;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.javajit.Builder.CD_Ctx;
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
    /**
     * @param typeTarget  the type that contains the constructor
     * @param targetCD    pass a non-null ClassDesc to add the target type as an implicit param
     * @param addCtorCtx  pass true to add the cctx as an implicit param
     * @param addType     pass true to add a TypeConstant as an implicit param
     */
    public JitCtorDesc(
            TypeConstant   typeTarget,
            ClassDesc      targetCD,
            boolean        addCtorCtx,
            boolean        addType,
            JitParamDesc[] standardReturns,
            JitParamDesc[] standardParams,
            JitParamDesc[] optimizedReturns,
            JitParamDesc[] optimizedParams) {
        super(typeTarget, standardReturns, standardParams, optimizedReturns, optimizedParams, true,
                implicitParams(targetCD, addCtorCtx, addType));
    }

    private static ClassDesc[] implicitParams(
            ClassDesc targetCD,
            boolean   addCtorCtx,
            boolean   addType) {
        ClassDesc[] paramCDs = new ClassDesc[1
                + (addCtorCtx       ? 1 : 0)
                + (addType          ? 1 : 0)
                + (targetCD == null ? 0 : 1)];

        int ix = 0;
        paramCDs[ix++] = CD_Ctx;
        if (addCtorCtx) {
            paramCDs[ix++] = CD_CtorCtx;
        }
        if (addType) {
            paramCDs[ix++] = CD_TypeConstant;
        }
        if (targetCD != null) {
            paramCDs[ix++] = targetCD;
        }
        return paramCDs;
    }
}
