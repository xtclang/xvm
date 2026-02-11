package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.XvmPrimitive;
import static org.xvm.javajit.JitFlavor.NullablePrimitive;
import static org.xvm.javajit.JitFlavor.Primitive;
import static org.xvm.javajit.JitFlavor.PrimitiveWithDefault;
import static org.xvm.javajit.JitFlavor.Specific;
import static org.xvm.javajit.JitFlavor.SpecificWithDefault;
import static org.xvm.javajit.JitFlavor.Widened;
import static org.xvm.javajit.JitFlavor.WidenedWithDefault;
import static org.xvm.javajit.JitFlavor.XvmPrimitiveWithDefault;

/**
 * JIT specific information for a method.
 */
public class JitMethodDesc {
    public JitMethodDesc(JitParamDesc[] standardReturns,  JitParamDesc[] standardParams,
                         JitParamDesc[] optimizedReturns, JitParamDesc[] optimizedParams) {
        this.standardReturns  = standardReturns;
        this.standardParams   = standardParams;
        this.optimizedReturns = optimizedReturns;
        this.optimizedParams  = optimizedParams;

        isOptimized = optimizedParams != null && optimizedReturns != null;
        standardMD  = computeMethodDesc(standardReturns, standardParams);
        optimizedMD = isOptimized
            ? computeMethodDesc(optimizedReturns, optimizedParams)
            : null;
    }

    /**
     * @return an optimized JitParamDesc for the specified standard argument index.
     */
    public JitParamDesc getOptimizedParam(int argIndex) {
        return optimizedParams[getOptimizedParamIndex(argIndex)];
    }

    /**
     * @return all the indexes of the optimized JitParamDesc for the specified standard argument
     * index.
     */
    public int[] getAllOptimizedParams(int retIndex) {
        List<Integer> list = new ArrayList<>(optimizedParams.length);
        for (int i = 0, c = optimizedParams.length; i < c; i++) {
            if (optimizedParams[i].index == retIndex) {
                list.add(i);
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Invalid param index");
        }
        return list.stream().mapToInt(i -> i).toArray();
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard argument index.
     */
    public int getOptimizedParamIndex(int argIndex) {
        for (int i = 0, c = optimizedParams.length; i < c; i++) {
            if (optimizedParams[i].index == argIndex) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid arg index");
    }

    /**
     * @return an optimized JitParamDesc for the specified standard return index.
     */
    public JitParamDesc getOptimizedReturn(int retIndex) {
        return optimizedReturns[getOptimizedReturnIndex(retIndex)];
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard return index.
     */
    public int getOptimizedReturnIndex(int retIndex) {
        for (int i = 0, c = optimizedReturns.length; i < c; i++) {
            if (optimizedReturns[i].index == retIndex) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid return index");
    }

    /**
     * @return all the indexes of the optimized JitParamDesc for the specified standard return
     * index.
     */
    public int[] getAllOptimizedReturnIndexes(int retIndex) {
        List<Integer> list = new ArrayList<>(optimizedReturns.length);
        for (int i = 0, c = optimizedReturns.length; i < c; i++) {
            if (optimizedReturns[i].index == retIndex) {
                list.add(i);
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Invalid return index");
        }
        return list.stream().mapToInt(i -> i).toArray();
    }

    protected MethodTypeDesc computeMethodDesc(JitParamDesc[] returns, JitParamDesc[] params) {
        int         extraCount = getImplicitParamCount();
        int         paramCount = params.length;
        ClassDesc[] paramCDs   = new ClassDesc[extraCount + paramCount];

        fillExtraClassDesc(paramCDs);

        for (int i = 0; i < paramCount; i++)
            {
            paramCDs[extraCount + i] = params[i].cd;
            }
        return MethodTypeDesc.of(returns.length == 0 ? CD_void : returns[0].cd, paramCDs);
        }

    /**
     * @return the number of extra parameters
     */
    public int getImplicitParamCount() {
        return 1;
    }

    /**
     * @return the number of added arguments
     */
    protected int fillExtraClassDesc(ClassDesc[] paramCDs) {
        // the argument zero is **always** the Ctx
        paramCDs[0] = CD_Ctx;
        return 1;
    }

    /**
     * @param cdContainer  the container class; used only for constructors
     *
     * @return the JitMethodDesc for the method associated with this signature for the specified
     *         container
     */
    public static JitMethodDesc of(TypeConstant[] paramTypes,
                                   TypeConstant[] returnTypes,
                                   boolean        isConstructor,
                                   ClassDesc      cdContainer,
                                   int            reqParamCount,
                                   TypeSystem     ts) {

        ConstantPool       pool         = ts.pool();
        List<JitParamDesc> stdParamList = new ArrayList<>();
        List<JitParamDesc> optParamList = new ArrayList<>();
        boolean            isOptimized  = false;

        for (int iOrig = 0, iStd = 0, iOpt = 0, cOrig = paramTypes.length; iOrig < cOrig; iOrig++) {
            TypeConstant type  = paramTypes[iOrig];
            boolean      fDflt = iOrig >= reqParamCount;
            ClassDesc cd;

            if ((cd = JitTypeDesc.getPrimitiveClass(type)) != null) {
                JitFlavor flavor = fDflt ? SpecificWithDefault : Specific;
                ClassDesc cdStd  = type.ensureClassDesc(ts);

                stdParamList.add(new JitParamDesc(type, flavor, cdStd, iOrig, iStd++, false));

                isOptimized = true;
                if (fDflt) {
                    optParamList.add(
                        new JitParamDesc(type, PrimitiveWithDefault, cd, iOrig, iOpt++, false));
                    optParamList.add(
                        new JitParamDesc(type, PrimitiveWithDefault, CD_boolean, iOrig, iOpt++, true));
                } else {
                    optParamList.add(new JitParamDesc(type, Primitive, cd, iOrig, iOpt++, false));
                }
            } else if ((cd = JitTypeDesc.getNullablePrimitiveClass(type)) != null) {
                JitFlavor stdFlavor = fDflt ? WidenedWithDefault : Widened;
                stdParamList.add(
                    new JitParamDesc(type, stdFlavor, CD_nObj, iOrig, iStd++, false));

                if (fDflt) {
                    // TODO: we can further optimize to a three-slot (multi-primitive with default)
                    optParamList.add(
                        new JitParamDesc(type, stdFlavor, CD_nObj, iOrig, iOpt++, false));
                } else {
                    isOptimized = true;
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitive, cd, iOrig, iOpt++, false));
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitive, CD_boolean, iOrig, iOpt++, true));
                }
            } else if ((cd = JitTypeDesc.getXvmPrimitiveClass(type)) != null) {
                isOptimized = true;
                stdParamList.add(
                        new JitParamDesc(type, Specific, cd, iOrig, iStd++, false));

                JitFlavor optFlavor = fDflt ? XvmPrimitiveWithDefault : XvmPrimitive;
                for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type)) {
                    optParamList.add(new JitParamDesc(type, optFlavor, cdArg, iOrig, iOpt++, false));
                }

                if (fDflt) {
                    optParamList.add(
                            new JitParamDesc(type, optFlavor, CD_boolean, iOrig, iOpt++, true));
                }
            } else if ((cd = JitTypeDesc.getNullableXvmPrimitiveClass(type)) != null) {
                boolean   nullable  = type.isNullable();
                JitFlavor stdFlavor = fDflt ? WidenedWithDefault : Widened;
                JitFlavor optFlavor = nullable ? NullableXvmPrimitive : XvmPrimitive;

                stdParamList.add(
                        new JitParamDesc(type, stdFlavor, cd, iOrig, iStd++, false));

                if (fDflt) {
                    // TODO: ??? we can further optimize to extra slots for the default
                    optParamList.add(
                            new JitParamDesc(type, stdFlavor, cd, iOrig, iOpt++, false));
                } else {
                    isOptimized = true;
                    for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type)) {
                        optParamList.add(
                                new JitParamDesc(type, optFlavor, cdArg, iOrig, iOpt++, false));
                    }
                    if (nullable) {
                        optParamList.add(
                                new JitParamDesc(type, optFlavor, CD_boolean, iOrig, iOpt++, true));
                    }
                }
            } else if ((cd = JitTypeDesc.getWidenedClass(type)) != null) {
                JitFlavor flavor = fDflt ? WidenedWithDefault : Widened;
                stdParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iStd++, false));
                optParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iOpt++, false));
            } else {
                assert type.isSingleUnderlyingClass(true);

                cd = type.ensureClassDesc(ts);

                JitFlavor flavor = fDflt ? SpecificWithDefault : Specific;

                stdParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iStd++, false));
                optParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iOpt++, false));
            }
        }

        JitParamDesc[] stdParams = stdParamList.toArray(JitParamDesc.NONE);
        JitParamDesc[] optParams = isOptimized
                ? optParamList.toArray(JitParamDesc.NONE)
                : stdParams;

        // reuse the lists for the return values
        stdParamList.clear();
        optParamList.clear();

        int ixLong   = -1; // an index of the long return value in the Ctx (only for optimized)
        int ixOptObj = -1; // an index of the Object return value in the Ctx for optimized
        int ixStdObj = -1; // an index of the Object return value in the Ctx for standard
        for (int iOrig = 0, c = returnTypes.length; iOrig < c; iOrig++) {
            TypeConstant type = returnTypes[iOrig];
            ClassDesc    cd;

            if ((cd = JitTypeDesc.getPrimitiveClass(type)) != null) {
                ClassDesc cdStd = type.ensureClassDesc(ts);

                stdParamList.add(new JitParamDesc(type, Specific, cdStd, iOrig, ixStdObj++, false));
                optParamList.add(new JitParamDesc(type, Primitive, cd,   iOrig, ixLong++, false));
                isOptimized = true;
            } else if ((cd = JitTypeDesc.getNullablePrimitiveClass(type)) != null) {
                TypeConstant typePrimitive = type.removeNullable();

                stdParamList.add(new JitParamDesc(type, Widened, CD_nObj, iOrig, ixStdObj++, false));
                optParamList.add(new JitParamDesc(typePrimitive,
                    NullablePrimitive, cd,         iOrig, ixLong++, false));
                optParamList.add(new JitParamDesc(pool.typeBoolean(),
                    NullablePrimitive, CD_boolean, iOrig, ixLong++, true));
                isOptimized = true;
            } else if ((cd = JitTypeDesc.getXvmPrimitiveClass(type)) != null) {
                isOptimized = true;
                stdParamList.add(new JitParamDesc(type, Specific, cd, iOrig, ixStdObj++, false));

                for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type)) {
                    optParamList.add(new JitParamDesc(type, XvmPrimitive, cdArg, iOrig, ixLong++, false));
                }
            } else if ((cd = JitTypeDesc.getNullableXvmPrimitiveClass(type)) != null) {
                isOptimized = true;
                stdParamList.add(new JitParamDesc(type, Widened, CD_nObj, iOrig, ixStdObj++, false));

                for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type)) {
                    optParamList.add(new JitParamDesc(type, NullableXvmPrimitive, cdArg, iOrig, ixLong++, false));
                }
                optParamList.add(new JitParamDesc(pool.typeBoolean(),
                        NullableXvmPrimitive, CD_boolean, iOrig, ixLong++, true));
            } else if ((cd = JitTypeDesc.getWidenedClass(type)) != null) {
                stdParamList.add(new JitParamDesc(type, Widened, cd, iOrig, ixStdObj++, false));
                optParamList.add(new JitParamDesc(type, Widened, cd, iOrig, ixOptObj++, false));
            } else {
                assert type.isSingleUnderlyingClass(true);

                cd = type.ensureClassDesc(ts);

                stdParamList.add(new JitParamDesc(type, Specific, cd, iOrig, ixStdObj++, false));
                optParamList.add(new JitParamDesc(type, Specific, cd, iOrig, ixOptObj++, false));
            }

            // prime optimized indexes
            if (ixLong == -1) {
                ixLong = 0;
            }
            if (ixOptObj == -1) {
                ixOptObj = 0;
            }
        }

        JitParamDesc[] stdReturns = stdParamList.toArray(JitParamDesc.NONE);
        JitParamDesc[] optReturns = isOptimized
                ? optParamList.toArray(JitParamDesc.NONE)
                : null;

        if (isConstructor) {
            boolean fAddCtorCtx = true; // TODO: isFinalizerRequired()

            return new JitCtorDesc(cdContainer, fAddCtorCtx, /*fAddType*/ false,
                                   stdReturns, stdParams, optReturns, optParams);
        } else {
            return new JitMethodDesc(stdReturns, stdParams, optReturns, optParams);
        }
    }


    // ----- fields --------------------------------------------------------------------------------

    public final JitParamDesc[] standardReturns;
    public final JitParamDesc[] standardParams;
    public final JitParamDesc[] optimizedReturns;
    public final JitParamDesc[] optimizedParams;

    public final boolean isOptimized;
    public final MethodTypeDesc standardMD;  // the generic "xObj" flavor
    public final MethodTypeDesc optimizedMD; // (optional) optimized primitive
}
