package org.xvm.javajit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_nObj;

import static org.xvm.javajit.JitFlavor.NullablePrimitiveWithDefault;
import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;
import static org.xvm.javajit.JitFlavor.NullableXvmPrimitiveWithDefault;
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
    public JitMethodDesc(
            TypeConstant   typeTarget,
            JitParamDesc[] standardReturns,
            JitParamDesc[] standardParams,
            JitParamDesc[] optimizedReturns,
            JitParamDesc[] optimizedParams,
            boolean        isStatic) {
        this.typeTarget        = typeTarget;
        this.standardReturns   = standardReturns;
        this.standardParams    = standardParams;
        this.optimizedReturns  = optimizedReturns;
        this.optimizedParams   = optimizedParams;
        this.isOptimized       = optimizedParams != null && optimizedReturns != null;
        this.isStandardStatic  = isStatic;
        this.isOptimizedStatic = isOptimized && (isStatic || typeTarget != null && typeTarget.isJavaPrimitive());
        this.standardMD        = computeMethodDesc(standardReturns, standardParams);
        this.optimizedMD       = isOptimized
                ? computeMethodDesc(optimizedReturns, optimizedParams)
                : null;
    }

    public final TypeConstant   typeTarget;
    public final JitParamDesc[] standardReturns;
    public final JitParamDesc[] standardParams;
    public final JitParamDesc[] optimizedReturns;
    public final JitParamDesc[] optimizedParams;

    public final boolean isOptimized;
    public final boolean isStandardStatic;
    public final boolean isOptimizedStatic;
    public final MethodTypeDesc standardMD;  // the generic "xObj" flavor
    public final MethodTypeDesc optimizedMD; // (optional) optimized primitive

    public boolean isPrimitivized() {
        return !isStandardStatic && isOptimizedStatic;
    }

    public JitMethodDesc deopt() {
        return isOptimized
                ? new JitMethodDesc(typeTarget, standardReturns, standardParams, null, null, isStandardStatic)
                : this;
    }
    /**
     * @return the index of the Ctx in the JVM parameters for the non-optimized signature; currently
     *         the index is always 0
     */
    public int standardCtx() {
        return 0;
    }

    /**
     * @return the index of the Ctx in the JVM parameters for the optimized signature; the index is
     *         non-zero for "primitivized" methods, i.e. for all methods on "JitPrimitive" classes
     */
    public int optimizedCtx() {
        if (!isOptimized || !isPrimitivized()) {
            return 0;
        }

        // skip over the primitive thi$
        var params = optimizedParams;
        int ix     = 0;
        int cx     = params.length;
        while (ix < cx && params[ix].index < 0) {
            ++ix;
        }
        return ix;
    }

    /**
     * @return an optimized JitParamDesc for the specified standard argument index
     */
    public JitParamDesc getOptimizedParam(int argIndex) {
        return optimizedParams[getOptimizedParamIndex(argIndex)];
    }

    /**
     * @return all the indexes of the optimized JitParamDesc for the specified standard argument
     *         index
     */
    public int[] getAllOptimizedParams(int argIndex) {
        List<Integer> list = new ArrayList<>(optimizedParams.length);
        for (int i = 0, c = optimizedParams.length; i < c; i++) {
            if (optimizedParams[i].index == argIndex) {
                list.add(i);
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Invalid param index");
        }
        return list.stream().mapToInt(i -> i).toArray();
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard argument index
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
     * @return an optimized JitParamDesc for the specified standard return index
     */
    public JitParamDesc getOptimizedReturn(int retIndex) {
        return optimizedReturns[getOptimizedReturnIndex(retIndex)];
    }

    /**
     * @return an index of the optimized JitParamDesc for the specified standard return index
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
     *         index
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

        int ix     = 0;
        int iFirst = 0;
        if (paramCount > 0 && params[0].index < 0) {
            paramCDs[ix++] = params[0].cd;
            iFirst = 1;
        }

        ix = fillExtraClassDesc(paramCDs, ix);

        for (int i = iFirst; i < paramCount; i++) {
            paramCDs[ix++] = params[i].cd;
        }

        return MethodTypeDesc.of(returns.length == 0 ? CD_void : returns[0].cd, paramCDs);
        }

    /**
     * @return the number of extra parameters
     */
    public int getImplicitParamCount() {
        return 1; // Ctx
    }

    /**
     * @param ix  the index to place the next argument
     * @return the number of added arguments
     */
    protected int fillExtraClassDesc(ClassDesc[] paramCDs, int ix) {
        paramCDs[ix++] = CD_Ctx;
        return ix;
    }

    /**
     *
     * @param builder        the Builder that is creating a call to the specified target
     * @param typeTarget     the target type on which the method if located (may be null in the case
     *                       of a function that exists only at runtime)
     * @param isStatic       true iff the method is static (function, constructor, etc.)
     * @param isConstructor  true iff the method is a constructor
     * @param paramTypes
     * @param returnTypes
     * @param reqParamCount
     *
     * @return the JitMethodDesc for the method associated with this signature for the specified
     *         container
     */
    public static JitMethodDesc of(
            Builder        builder,
            TypeConstant   typeTarget,
            boolean        isStatic,
            boolean        isConstructor,
            TypeConstant[] paramTypes,
            TypeConstant[] returnTypes,
            int            reqParamCount) {
        assert builder != null;
        // methods and constructors requires a target type
        assert (!isConstructor && isStatic) || typeTarget != null;

        ConstantPool       pool         = builder.typeSystem.pool();
        List<JitParamDesc> stdParamList = new ArrayList<>();
        List<JitParamDesc> optParamList = new ArrayList<>();
        boolean            isPrimitive  = !isStatic && typeTarget.isJitPrimitive();
        boolean            isOptimized  = isPrimitive;

        for (int iOrig = isPrimitive ? -1 : 0, iStd = 0, iOpt = 0, cOrig = paramTypes.length; iOrig < cOrig; iOrig++) {
            TypeConstant type  = iOrig >= 0 ? paramTypes[iOrig] : typeTarget;
            boolean      fDflt = iOrig >= reqParamCount;
            ClassDesc    cd;
// for -1 don't add to std
            if ((cd = JitTypeDesc.getJavaPrimitive(type)) != null) {
                if (iOrig >= 0) {
                    JitFlavor stdFlavor = fDflt ? SpecificWithDefault : Specific;
                    ClassDesc cdStd     = builder.ensureClassDesc(type);
                    stdParamList.add(new JitParamDesc(type, stdFlavor, cdStd, iOrig, iStd++, false));
                }

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
                assert type.isNullable();

                if (iOrig >= 0) {
                    JitFlavor stdFlavor = fDflt ? WidenedWithDefault : Widened;
                    stdParamList.add(
                            new JitParamDesc(type, stdFlavor, CD_nObj, iOrig, iStd++, false));
                }

                isOptimized = true;
                if (fDflt) {
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitiveWithDefault, cd, iOrig, iOpt++, false));
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitiveWithDefault, CD_int, iOrig, iOpt++, true));
                } else {
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitive, cd, iOrig, iOpt++, false));
                    optParamList.add(
                        new JitParamDesc(type, NullablePrimitive, CD_boolean, iOrig, iOpt++, true));
                }
            } else if ((cd = JitTypeDesc.getXvmPrimitiveClass(type)) != null) {
                if (iOrig >= 0) {
                    JitFlavor stdFlavor = fDflt ? SpecificWithDefault : Specific;
                    stdParamList.add(
                            new JitParamDesc(type, stdFlavor, cd, iOrig, iStd++, false));
                }

                isOptimized = true;
                JitFlavor optFlavor = fDflt ? XvmPrimitiveWithDefault : XvmPrimitive;
                for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type)) {
                    optParamList.add(new JitParamDesc(type, optFlavor, cdArg, iOrig, iOpt++, false));
                }
                if (fDflt) {
                    optParamList.add(
                            new JitParamDesc(type, optFlavor, CD_boolean, iOrig, iOpt++, true));
                }
            } else if ((cd = JitTypeDesc.getNullableXvmPrimitiveClass(type)) != null) {
                assert type.isNullable();

                if (iOrig >= 0) {
                    JitFlavor stdFlavor = fDflt ? WidenedWithDefault : Widened;
                    stdParamList.add(
                            new JitParamDesc(type, stdFlavor, CD_nObj, iOrig, iStd++, false));
                }

                isOptimized = true;
                JitFlavor optFlavor = fDflt ? NullableXvmPrimitiveWithDefault : NullableXvmPrimitive;
                for (ClassDesc cdArg : JitTypeDesc.getXvmPrimitiveClasses(type.removeNullable())) {
                    optParamList.add(new JitParamDesc(type, optFlavor, cdArg, iOrig, iOpt++, false));
                }
                if (fDflt) {
                    optParamList.add(
                            new JitParamDesc(type, NullableXvmPrimitiveWithDefault, CD_int, iOrig, iOpt++, true));
                } else {
                    optParamList.add(
                            new JitParamDesc(type, NullableXvmPrimitive, CD_boolean, iOrig, iOpt++, true));
                }
            } else if ((cd = JitTypeDesc.getWidenedClass(builder, type)) != null) {
                JitFlavor flavor = fDflt ? WidenedWithDefault : Widened;

                if (iOrig >= 0) {
                    stdParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iStd++, false));
                }

                optParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iOpt++, false));
            } else {
                assert type.isSingleUnderlyingClass(true);
                cd = builder.ensureClassDesc(type);
                JitFlavor flavor = fDflt ? SpecificWithDefault : Specific;

                if (iOrig >= 0) {
                    stdParamList.add(new JitParamDesc(type, flavor, cd, iOrig, iStd++, false));
                }

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

            if ((cd = JitTypeDesc.getJavaPrimitive(type)) != null) {
                ClassDesc cdStd = builder.ensureClassDesc(type);

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
            } else if ((cd = JitTypeDesc.getWidenedClass(builder, type)) != null) {
                stdParamList.add(new JitParamDesc(type, Widened, cd, iOrig, ixStdObj++, false));
                optParamList.add(new JitParamDesc(type, Widened, cd, iOrig, ixOptObj++, false));
            } else {
                assert type.isSingleUnderlyingClass(true);

                cd = builder.ensureClassDesc(type);

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
            return new JitCtorDesc(typeTarget, typeTarget.ensureClassDesc(builder.typeSystem),
                    fAddCtorCtx, /*fAddType*/ false, stdReturns, stdParams, optReturns, optParams);
        } else {
            return new JitMethodDesc(typeTarget, stdReturns, stdParams, optReturns, optParams, isStatic);
        }
    }

    @Override
    public String toString() {
        return isOptimized ? optimizedMD.toString() : standardMD.toString();
    }
}
