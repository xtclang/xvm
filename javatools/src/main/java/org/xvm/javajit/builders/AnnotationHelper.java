package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCallDesc;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.RegisterInfo;

import static org.xvm.javajit.Builder.CD_CtorCtx;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.OPT;
import static org.xvm.javajit.TypeSystem.AT;

/**
 * Annotation processing shared by builders for every kind of incorporating class.
 */
public class AnnotationHelper {
    /**
     * Create a helper for a type whose composition contains annotations.
     *
     * @param builder             the builder assembling the incorporating type
     * @param annotations         the nonempty TypeInfo annotation array, in invocation order
     * @param isConstructionSite  true iff the type has construction site annotations
     */
    public AnnotationHelper(CommonBuilder builder, Annotation[] annotations, boolean isConstructionSite) {
        assert annotations.length > 0;

        this.builder            = builder;
        this.annotations        = annotations;
        this.thisType           = builder.thisType;
        this.isConstructionSite = isConstructionSite;

        TypeConstant baseType = isConstructionSite
                ? thisType.removeAnnotations()
                : builder.typeInfo.getExtends();
        if (baseType == null) {
            baseType = thisType.isConst()
                    ? builder.pool().typeConstRB()
                    : builder.pool().typeObject();
        }
        this.baseType              = baseType.ensureAccess(Access.PRIVATE);
        this.constructionSiteCount = isConstructionSite ? thisType.getAnnotations().length : 0;
    }

    private final CommonBuilder builder;
    private final Annotation[]  annotations;

    /**
     * The number of leading construction site annotations in the invocation order.
     */
    private final int constructionSiteCount;

    /**
     * The incorporating type; PRIVATE access.
     */
    private final TypeConstant thisType;

    /**
     * The unannotated type for construction site annotations, or the superclass for declaration
     * site annotations; PRIVATE access.
     */
    private final TypeConstant baseType;

    /**
     * True if the type has construction site annotations; it may also have declaration site
     * annotations.
     */
    public final boolean isConstructionSite;

    /**
     * @return the unannotated type for construction site annotations, or the superclass for
     *         declaration site annotations; never null
     */
    public TypeConstant getBaseType() {
        return baseType;
    }

    /**
     * @param id  the method or property identity being considered for generation
     *
     * @return true iff the member is supplied by the unannotated superclass; false for
     *         constructors, validators and declaration site annotations
     */
    public boolean isInherited(IdentityConstant id) {
        IdentityConstant owner = id instanceof PropertyConstant
                ? id.getClassIdentity()
                : id.getNamespace();
        return isConstructionSite && owner.equals(builder.thisId) &&
                !(id instanceof MethodConstant method &&
                        (method.isConstructor() || method.isValidator()));
    }

    /**
     * Assemble annotation constructor bodies on the implementation class.
     */
    public void assembleAnnotationConstructors(ClassBuilder classBuilder) {
        for (AnnotationConstructor[] constructors : collectAnnotationConstructors()) {
            for (AnnotationConstructor constructor : constructors) {
                MethodInfo method = constructor.method();
                if (method.getHead().getMethodStructure().isNoOp()) {
                    continue;
                }

                String      name = annotationConstructorName(constructor);
                JitCtorDesc jmd  = constructor.desc();

                if (jmd.isOptimized) {
                    builder.assembleMethodWrapper(classBuilder, name, jmd);
                    name += OPT;
                }

                BuildContext bctx = new BuildContext(
                        builder, method.getTypeInfo(), method, jmd, thisType, null);
                builder.doAssembleMethod(classBuilder, bctx, method, name,
                        jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
            }
        }
    }

    /**
     * @return one row of constructor candidates per annotation, with rows in annotation invocation
     *         order and no-op constructors included; construction site rows contain all overloads
     *         sorted lexicographically by method identity, while declaration site rows contain only
     *         the constructor selected by the annotation arguments
     *
     * @throws IllegalStateException if an annotation has no constructor
     */
    private AnnotationConstructor[][] collectAnnotationConstructors() {
        AnnotationConstructor[][] candidates = new AnnotationConstructor[annotations.length][];
        for (int i = 0; i < annotations.length; i++) {
            Annotation annotation         = annotations[i];
            TypeInfo   info               = getAnnotationInfo(annotation, thisType);
            boolean    isConstructionSite = i < constructionSiteCount;

            MethodConstant[] ids;
            if (isConstructionSite) {
                ids = info.findMethods("construct", -1, MethodKind.Constructor)
                        .toArray(MethodConstant[]::new);
                Arrays.sort(ids, Comparator.comparing(MethodConstant::getValueString));
                if (ids.length == 0) {
                    throw new IllegalStateException(
                            "Missing annotation constructor for " + annotation);
                }
            } else {
                ids = new MethodConstant[] {findAnnotationConstructor(annotation).getIdentity()};
            }

            AnnotationConstructor[] ctors = new AnnotationConstructor[ids.length];
            for (int j = 0; j < ids.length; j++) {
                MethodInfo method = info.getMethodById(ids[j], false);
                ctors[j] = new AnnotationConstructor(i, isConstructionSite, method,
                        (JitCtorDesc) method.getJitDesc(builder, thisType));
            }
            candidates[i] = ctors;
        }
        return candidates;
    }

    /**
     * @param constructor  the annotation constructor and its position in invocation order
     *
     * @return the synthetic constructor method name, also used as an instantiator suffix
     */
    private String annotationConstructorName(AnnotationConstructor constructor) {
        return annotationConstructorName(builder, constructor.annotationIndex(), constructor.method());
    }

    /**
     * @param index    the zero-based annotation index in invocation order
     * @param method   the annotation's Ecstasy constructor
     *
     * @return the synthetic constructor name, such as {@code @1$2}, using '@' for AT
     */
    public static String annotationConstructorName(Builder builder, int index, MethodInfo method) {
        // every annotation segment includes an explicit constructor ordinal, including zero
        String name    = method.ensureJitMethodName(builder.typeSystem);
        String ordinal = name.equals("construct") ? "0" : name.substring("construct$".length());
        return AT + Integer.toString(index) + '$' + ordinal;
    }

    /**
     * Collect the instantiator variants for a base constructor. Each variant includes all selected
     * annotation constructors in invocation order. Only construction site annotations contribute
     * overload combinations, appended parameters and name suffixes; declaration site annotations
     * retain the constructors selected by their arguments.
     *
     * <p>For example, assume that A1 has two constructors and A0 has three:
     * <pre>
     * annotation A1 into B {
     *     construct(Int value)    {...}
     *     construct(String value) {...}
     * }
     * annotation A0 into B {
     *     construct(Int value)    {...}
     *     construct(String value) {...}
     *     construct(UInt value)   {...}
     * }
     * </pre>
     * For {@code @A1 @A0 B}, each base constructor has six variants, using XTC argument types
     * and '@' for AT:
     * <pre>
     *   $new@0$0@1$0(Ctx, TypeConstant, baseArgs..., Int    v1, Int    v0)
     *   $new@0$0@1$1(Ctx, TypeConstant, baseArgs..., Int    v1, String v0)
     *   $new@0$0@1$2(Ctx, TypeConstant, baseArgs..., Int    v1, UInt   v0)
     *   $new@0$1@1$0(Ctx, TypeConstant, baseArgs..., String v1, Int    v0)
     *   $new@0$1@1$1(Ctx, TypeConstant, baseArgs..., String v1, String v0)
     *   $new@0$1@1$2(Ctx, TypeConstant, baseArgs..., String v1, UInt   v0)
     * </pre>
     * Standard and optimized Java entry points are handled separately.
     *
     * @param baseDesc  the instantiator descriptor before the construction site annotation
     *                  arguments are appended
     *
     * @return all instantiator variants, each with a complete annotation constructor list
     */
    public List<AnnotationVariant> collectVariants(JitMethodDesc baseDesc) {
        // a reusable Java class must support every combination of annotation constructor overloads
        // for this base constructor; collectAnnotationConstructors() groups them by annotation;
        // each row of combinations selects one constructor overload from each annotation's
        // candidate row and defines one instantiator; declaration site rows have one candidate

        AnnotationConstructor[][] candidates   = collectAnnotationConstructors();
        int[][]                   combinations = collectColumnIndexes(candidates);

        // the example has two annotation rows, containing two and three constructors:
        //    candidates[0] = [A1(Int), A1(String)]
        //    candidates[1] = [A0(Int), A0(String), A0(UInt)]
        // combinations has six instantiator rows and two annotation columns;
        // column 0 selects overload 0 or 1 of A1, and column 1 selects overload 0, 1 or 2 of A0

        List<AnnotationVariant> variants = new ArrayList<>(combinations.length);
        for (int[] indexes : combinations) {
            List<AnnotationConstructor> selected    = new ArrayList<>(candidates.length);
            JitMethodDesc               variantDesc = baseDesc;
            StringBuilder               suffix      = new StringBuilder();

            // on the third pass indexes is [0,2], so the rows contribute Int, UInt
            for (int i = 0; i < candidates.length; i++) {
                AnnotationConstructor annoCtor = candidates[i][indexes[i]];
                selected.add(annoCtor);
                if (annoCtor.isConstructionSite()) {
                    variantDesc = ((JitCtorDesc) variantDesc).appendParams(annoCtor.desc());
                    suffix.append(annotationConstructorName(annoCtor));
                }
            }

            // that pass produces suffix @0$0@1$2, with A1 and A0 arguments in that order
            variants.add(new AnnotationVariant(suffix.toString(), variantDesc, selected));
        }
        return variants;
    }

    /**
     * @param rows  the input rows; element values are ignored
     *
     * @return all valid column-index selections, one index per input row, with the last index
     *         varying fastest; no input rows yields one empty selection, and an empty input row
     *         yields no selections
     *
     * @throws ArithmeticException if the number of combinations exceeds the int range
     */
    private static int[][] collectColumnIndexes(Object[][] rows) {
        int count = 1;
        for (Object[] row : rows) {
            count = Math.multiplyExact(count, row.length);
        }

        int[][] combinations = new int[count][];
        if (count == 0) {
            return combinations;
        }
        combinations[0] = new int[rows.length]; // all zeros
        for (int row = 1; row < count; row++) {
            int[] indexes = combinations[row - 1].clone();

            // advance the rightmost available choice and reset the following choices;
            // from [0,2], column 1 resets to zero and column 0 advances, producing [1,0]
            for (int i = indexes.length - 1; i >= 0; i--) {
                if (++indexes[i] < rows[i].length) {
                    break;
                }
                indexes[i] = 0;
            }
            combinations[row] = indexes;
        }
        return combinations;
    }

    /**
     * Resolve the instantiator selected by the unstripped annotation applications.
     *
     * @param targetType  the construction target retaining the original annotation arguments
     * @param baseName    the instantiator name before annotation suffixes are appended
     * @param baseDesc    the instantiator descriptor before annotation arguments are appended
     *
     * @return a static call descriptor with the selected overload suffixes and appended parameters
     *
     * @throws IllegalStateException if an annotation constructor cannot be resolved
     */
    public static JitCallDesc getInstantiatorCall(BuildContext bctx, TypeConstant targetType,
                                                  String baseName, JitCtorDesc baseDesc) {
        Annotation[]     annos = targetType.getAnnotations();
        List<MethodInfo> ctors = new ArrayList<>(annos.length);

        for (Annotation anno : annos) {
            ctors.add(findAnnotationConstructor(bctx, anno, targetType));
        }
        return getInstantiatorCall(bctx.builder, targetType, baseName, baseDesc, ctors);
    }

    /**
     * Resolve an instantiator call using the selected construction site annotation constructors.
     *
     * @param constructors  the selected construction site constructors, in invocation order
     *
     * @return the instantiator call with annotation suffixes and parameters appended
     */
    public static JitCallDesc getInstantiatorCall(Builder builder, TypeConstant targetType,
                                                  String baseName, JitCtorDesc baseDesc,
                                                  List<MethodInfo> constructors) {
        JitCtorDesc   instorDesc = baseDesc;
        StringBuilder instorName = new StringBuilder(baseName);
        for (int i = 0; i < constructors.size(); i++) {
            MethodInfo constructor = constructors.get(i);

            instorDesc = instorDesc.appendParams(constructor.getJitDesc(builder, targetType));
            instorName.append(annotationConstructorName(builder, i, constructor));
        }
        return JitCallDesc.static_(builder.getICDesc(targetType), instorName.toString(), instorDesc, false);
    }

    /**
     * Load construction site annotation arguments after the base constructor arguments.
     *
     * <p>The base arguments must already be on the Java operand stack. Annotation arguments are
     * appended in invocation order; omitted arguments and default markers use constructor defaults.
     * Declaration site arguments are loaded by the instantiator itself.
     *
     * @param targetType  the construction target retaining the original annotation arguments
     *
     * @throws IllegalStateException if an annotation constructor cannot be resolved
     */
    public static void loadAnnotationConstructorArgs(BuildContext bctx, CodeBuilder code,
                                                     TypeConstant targetType) {
        for (Annotation annotation : targetType.getAnnotations()) {
            MethodInfo    ctor = findAnnotationConstructor(bctx, annotation, targetType);
            JitMethodDesc jmd  = ctor.getJitDesc(bctx.builder, targetType);
            Constant[]    args = annotation.getParams();

            bctx.loadCallArguments(code, jmd, i -> {
                Constant value = i < args.length ? args[i] : null;
                if (value == null || value instanceof RegisterConstant register &&
                        register.getRegisterIndex() == Op.A_DEFAULT) {
                    return null;
                }

                return value instanceof RegisterConstant register
                        ? bctx.loadArgument(code, register.getRegisterIndex())
                        : bctx.loadConstant(code, value);
            }, targetType);
        }
    }

    /**
     * Find the constructor overload for an annotation application, such as {@code @A(value)}.
     *
     * @param annotation  the annotation application retaining its original arguments
     * @param targetType  the type supplying the annotation's generic bindings
     *
     * @return the matching Ecstasy constructor's MethodInfo
     *
     * @throws IllegalStateException if no matching constructor is found
     */
    private static MethodInfo findAnnotationConstructor(BuildContext bctx, Annotation annotation,
                                                        TypeConstant targetType) {
        Constant[]     args     = annotation.getParams();
        TypeConstant[] argTypes = new TypeConstant[args.length];

        for (int i = 0; i < args.length; i++) {
            Constant value = args[i];
            if (value instanceof RegisterConstant register) {
                if (register.getRegisterIndex() != Op.A_DEFAULT) {
                    argTypes[i] = bctx.getRegisterInfo(register.getRegisterIndex()).type();
                }
            } else if (value instanceof PropertyClassTypeConstant property) {
                argTypes[i] = property.getProperty().getValueType(bctx.pool(), null);
            } else {
                argTypes[i] = value.getType();
            }
        }

        return findAnnotationConstructor(annotation, argTypes, targetType);
    }

    /**
     * Emit annotation constructor calls before the base constructor and validators.
     *
     * @param constructors  all selected annotation constructors in invocation order
     * @param ctxSlot       the Java local slot containing the execution context
     * @param cctxSlot      the Java local slot containing the constructor context, or a negative
     *                      value if none is available
     * @param thisSlot      the Java local slot containing the instance being constructed
     * @param paramIndex    the Java parameter index, not local slot, of the first construction
     *                      site annotation argument
     */
    public void invokeAnnotationConstructors(CodeBuilder code,
                                             List<AnnotationConstructor> constructors,
                                             int ctxSlot, int cctxSlot, int thisSlot,
                                             int paramIndex) {
        for (AnnotationConstructor constructor : constructors) {
            JitCtorDesc    jmd    = constructor.desc();
            JitParamDesc[] params = jmd.isOptimized
                    ? jmd.optimizedParams : jmd.standardParams;
            int firstParamIndex = paramIndex;

            if (constructor.isConstructionSite()) {
                // consume appended parameters even when the constructor body is a no-op
                paramIndex += params.length;
            }

            if (constructor.method().getHead().getMethodStructure().isNoOp()) {
                continue;
            }

            if (jmd.hasCtorCtx() && cctxSlot < 0) {
                cctxSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.aload(ctxSlot)
                    .invokevirtual(CD_Ctx, "ctorCtx", Builder.md(CD_CtorCtx))
                    .astore(cctxSlot);
            }

            code.aload(ctxSlot);
            if (jmd.hasCtorCtx()) {
                code.aload(cctxSlot);
            }
            code.aload(thisSlot);
            if (constructor.isConstructionSite()) {
                // forward the argument representation, including nullable and default flags
                for (JitParamDesc pd : params) {
                    Builder.load(code, pd.cd, code.parameterSlot(firstParamIndex++));
                }
            } else {
                Constant[]      args   = annotations[constructor.annotationIndex()].getParams();
                MethodStructure method = constructor.method().getHead().getMethodStructure();
                for (int i = 0; i < jmd.standardParams.length; i++) {
                    Constant value = i < args.length ? args[i] : null;
                    if (value == null || value instanceof RegisterConstant register &&
                            register.getRegisterIndex() == Op.A_DEFAULT) {
                        value = method.getParam(i).getDefaultValue();
                    }

                    RegisterInfo source = builder.loadConstant(code, value);
                    builder.convertCallArgument(code, jmd, i, source, ctxSlot);
                }
            }
            code.invokestatic(builder.art.CD(), annotationConstructorName(constructor) +
                    (jmd.isOptimized ? OPT : ""),
                    jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD);
        }
    }

    /**
     * @param annotation  a declaration site annotation retaining its constant arguments
     *
     * @return the constructor selected by the annotation arguments
     *
     * @throws IllegalStateException if an annotation constructor cannot be resolved
     */
    private MethodInfo findAnnotationConstructor(Annotation annotation) {
        Constant[]     args     = annotation.getParams();
        TypeConstant[] argTypes = new TypeConstant[args.length];
        for (int i = 0; i < args.length; i++) {
            Constant value = args[i];
            if (value instanceof RegisterConstant register &&
                    register.getRegisterIndex() == Op.A_DEFAULT) {
                continue;
            }
            argTypes[i] = value instanceof PropertyClassTypeConstant property
                    ? property.getProperty().getValueType(builder.pool(), null)
                    : value.getType();
        }
        return findAnnotationConstructor(annotation, argTypes, thisType);
    }

    /**
     * Find the annotation constructor for the supplied argument types.
     *
     * @param annotation  the annotation whose constructor overloads are searched
     * @param argTypes    the supplied argument types; null entries leave the corresponding types
     *                    unspecified, and omitted trailing arguments use constructor defaults
     * @param targetType  the type supplying the annotation's generic bindings
     *
     * @return the matching Ecstasy constructor
     *
     * @throws IllegalStateException if no matching constructor is found
     */
    public static MethodInfo findAnnotationConstructor(Annotation annotation, TypeConstant[] argTypes,
                                                       TypeConstant targetType) {
        TypeInfo       info = getAnnotationInfo(annotation, targetType);
        MethodConstant id   = info.findConstructor(argTypes);

        if (id == null) {
            throw new IllegalStateException("Missing annotation constructor for " + annotation);
        }
        return info.getMethodById(id, false);
    }

    /**
     * @return the annotation's private TypeInfo with generic arguments resolved by the target
     */
    private static TypeInfo getAnnotationInfo(Annotation annotation, TypeConstant targetType) {
        return annotation.getFormalType().resolveGenerics(targetType.getConstantPool(), targetType)
                .ensureAccess(Access.PRIVATE).ensureTypeInfo();
    }

    /**
     * Remove compiler-only annotations from the specified annotation type. The well-known classes
     * to filter out are:
     * <ul>
     *   <li>{@code Abstract}
     *   <li>{@code Concurrent}
     *   <li>{@code Override}
     *   <li>{@code RO}
     *   <li>{@code Synchronized}
     *   <li>{@code Transient}
     *   <li>{@code Unassigned}
     *   <li>{@code Volatile}
     * </ul>
     *
     * @param type  the type whose annotations are being filtered
     *
     * @return the composition without compiler-only annotations, retaining other annotations and
     *         their arguments in their original order
     */
    public static TypeConstant filterAnnotations(TypeConstant type) {
        if (!(type instanceof AnnotatedTypeConstant annotated)) {
            return type;
        }

        TypeConstant underlying = filterAnnotations(annotated.getUnderlyingType());

        return isCompilerOnlyAnnotation(annotated.getAnnotationClass())
                ? underlying
                : annotated.replaceUnderlying(type.getConstantPool(), _ -> underlying);
    }

    /**
     * @return true iff at least one annotation is not compiler-only
     */
    public static boolean hasRuntimeAnnotations(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (!isCompilerOnlyAnnotation((ClassConstant) annotation.getAnnotationClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true iff the annotation is a well-known compiler-only annotation
     */
    public static boolean isCompilerOnlyAnnotation(ClassConstant annoId) {
        return switch (annoId.getImplicitImportName()) {
            case "Abstract", "Concurrent", "Override", "RO", "Synchronized", "Transient",
                 "Unassigned", "Volatile" -> true;
            case null, default -> false;
        };
    }

    /**
     * An annotation constructor and its position in the annotated composition.
     *
     * @param annotationIndex     the zero-based annotation index in invocation order
     * @param isConstructionSite  true iff arguments are appended to the instantiator parameters;
     *                            otherwise they are loaded from the declaration site annotation
     * @param method              the annotation's Ecstasy constructor
     * @param desc                the constructor descriptor for the incorporating type
     */
    public record AnnotationConstructor(int annotationIndex, boolean isConstructionSite,
                                        MethodInfo method, JitCtorDesc desc) {}

    /**
     * An instantiator's annotation suffix, descriptor and complete selected constructor list.
     *
     * <p>One variant represents one combination of construction site annotation constructors for
     * one base constructor, together with all selected declaration site constructors. It produces
     * one standard instantiator, or a standard wrapper and an optimized body. With only declaration
     * site annotations, there is one variant with the unchanged base name and descriptor.
     *
     * <p>In the {@link AnnotationHelper#collectVariants(JitMethodDesc)} example, the variant
     * selecting {@code A1(Int)} and {@code A0(UInt)} produces both entry points below, using '@'
     * for AT:
     * <pre>
     * $new@0$0@1$2(Ctx,   TypeConstant, baseArgs..., Int64 v1, UInt64 v0)
     * $new@0$0@1$2$p(Ctx, TypeConstant, baseArgs..., long v1, long v0)
     * </pre>
     *
     * @param suffix        the construction site constructor names appended to the instantiator
     *                      name
     * @param desc          the instantiator descriptor, including construction site arguments
     * @param constructors  all selected annotation constructors in invocation order; empty if none
     */
    public record AnnotationVariant(String suffix, JitMethodDesc desc,
                                    List<AnnotationConstructor> constructors) {}

}
