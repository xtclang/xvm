package org.xvm.javajit.builders;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;

import java.lang.classfile.instruction.ReturnInstruction;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Component;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.annotations.JitAnnotation;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitGenerated.Generator;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.NativeTypeSystem;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import org.xvm.javajit.builders.AnnotationHelper.AnnotationConstructor;

import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/**
 * The builder for native types that uses an existing Java class to augment with the Ecstasy natural
 * code.
 */
public class AugmentingBuilder extends CommonBuilder {

    public AugmentingBuilder(TypeSystem typeSystem, Artifact art, ClassModel model) {
        super(typeSystem, art);

        this.model          = model;
        this.isJitGenerated = JitAnnotation.isAnnotated(model, CD_JitGenerated);
    }

    /**
     * The augmenting Classfile model.
     */
    public final ClassModel model;

    /**
     * A flag indicating whether the class represented by the model is annotated with the
     * {@link JitGenerated} annotation.
     */
    public final boolean isJitGenerated;

    /**
     * A cache of elements identified as being natively implemented.
     */
    private final Map<NativeKey, Boolean> nativeElements = new HashMap<>();

    private static final ClassDesc CD_JitGenerated = ClassDesc.of(JitGenerated.class.getName());
    private static final ClassDesc CD_NaturalCode  = ClassDesc.of(JitGenerated.NaturalCode.class.getName());

    protected NativeTypeSystem getTypeSystem() {
        return (NativeTypeSystem) typeSystem;
    }

    @Override
    public ClassDesc getSuperCD() {
        return model.superclass().orElseThrow().asSymbol();
    }

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        // do not augment Object
        if (thisId.equals(pool().clzObject())) {
            return false;
        }

        // AugmentingBuilder uses the native class attributes except of the "ABSTRACT" flag
        // that is driven by the type
        int flags = model.flags().flagsMask();
        if ((flags & ClassFile.ACC_ABSTRACT) != 0 && !typeInfo.isAbstract()) {
            flags &= ~ClassFile.ACC_ABSTRACT;
        }
        classBuilder.withFlags(flags);

        // implemented interfaces may not be native; add them if necessary
        assembleInterfaces(classBuilder);

        // if there is any native Exception, we need to generate the "$createJavaException" method
        TypeConstant type        = thisType.removeAccess();
        TypeConstant T_EXCEPTION = pool().typeException();
        if (type.isA(T_EXCEPTION) && !type.equals(T_EXCEPTION)) {
            new ExceptionBuilder(typeSystem, art).assembleCreateException(classBuilder);
        }

        return true;
    }

    @Override
    protected boolean shouldAddInterface(TypeConstant type) {
        return !jitType.equals(pool().typeComparable()) && super.shouldAddInterface(type);
    }

    @Override
    protected void prependCLInit(CodeBuilder code) {
        MethodModel model = findMethod(ConstantDescs.CLASS_INIT_NAME, MTD_void);

        if (model != null) {
            // the native class had the static initializer, which was skipped during the "copy"
            // phase (see NativeTypeSystem.augmentNativeClass) and now needs to be incorporated;
            // the native code should go first and jump instead of return
            model.code().ifPresent(oldCode -> {
                Label endLabel = code.newLabel();
                oldCode.forEach(element -> {
                    // redirect every native return to the generated initialization
                    if (element instanceof ReturnInstruction) {
                        code.goto_(endLabel);
                    } else {
                        code.with(element);
                    }
                });
                code.labelBinding(endLabel);
            });
        }
    }

    @Override
    protected void assembleInit(ClassBuilder classBuilder, List<PropertyInfo> props) {
        if (!isNativeMethod(INIT_NAME, MD_xvmVoid)) {
            super.assembleInit(classBuilder, props);
        }
    }

    @Override
    protected void assembleField(ClassBuilder classBuilder, PropertyInfo prop) {
        String      jitName = prop.ensureJitName(typeSystem);
        JitTypeDesc jtd     = prop.getType().getJitDesc(this);

        boolean isNativeField = switch (jtd.flavor) {
            case XvmPrimitive, NullableXvmPrimitive -> {
                ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(prop.getType());
                assert cds != null && cds.length > 0;

                // all or none of the fields should be present
                boolean any = false;
                boolean all = true;
                for (int i = 0; i < cds.length; i++) {
                    boolean present = findField(jitName + "$" + i) != null;
                    any |= present;
                    all &= present;
                }
                if (jtd.flavor == JitFlavor.NullableXvmPrimitive) {
                    boolean present = findField(jitName + EXT) != null;
                    any |= present;
                    all &= present;
                }

                if (all != any) {
                    throw new IllegalStateException("Native fields for property \"" + jitName
                            + "\" must be either all present or all absent");
                }
                yield all;
            }
            default -> findField(jitName) != null;
        };

        if (!isNativeField) {
            super.assembleField(classBuilder, prop);
        }
    }

    @Override
    protected void assemblePropertyAccessor(ClassBuilder classBuilder, PropertyInfo prop,
                                            String jitName, JitMethodDesc jmd, boolean isGetter) {
        MethodTypeDesc md       = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
        MethodModel    mm       = findMethod(jitName, md);
        boolean        isNative = mm != null && isNativeElement(mm);
        if (isNative && ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    prop.isAbstract() || prop.isNative())) {
            // the native accessor still need its boxed entry point
            if (jmd.isOptimized) {
                String standardName = isGetter
                        ? prop.ensureGetterJitMethodName(typeSystem)
                        : prop.ensureSetterJitMethodName(typeSystem);
                if (!isNativeMethod(standardName, jmd.standardMD)) {
                    assembleMethodWrapper(classBuilder, standardName, jmd);
                }
            }
            return;
        }

        super.assemblePropertyAccessor(classBuilder, prop, jitName, jmd, isGetter);
    }

    @Override
    protected void assembleInflatedAccessors(ClassBuilder classBuilder, PropertyInfo prop) {
        String        jitName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmd     = prop.getGetterJitDesc(this, thisType);

        if (jmd.isOptimized && isNativeMethod(jitName + OPT, jmd.optimizedMD)) {
            // the native optimized getter still needs its boxed entry point
            if (!isNativeMethod(jitName, jmd.standardMD)) {
                assembleMethodWrapper(classBuilder, jitName, jmd);
            }
        } else if (!isNativeMethod(jitName, jmd.standardMD)) {
            super.assembleInflatedAccessors(classBuilder, prop);
        }
    }

    @Override
    protected void generateTrivialGetter(ClassBuilder classBuilder, PropertyInfo prop) {
        String        jitName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmd     = prop.getGetterJitDesc(this, thisType);

        if (jmd.isOptimized && isNativeMethod(jitName + OPT, jmd.optimizedMD)) {
            // the native optimized getter still needs its boxed entry point
            if (!isNativeMethod(jitName, jmd.standardMD)) {
                assembleMethodWrapper(classBuilder, jitName, jmd);
            }
        } else if (!isNativeMethod(jitName, jmd.standardMD)) {
            super.generateTrivialGetter(classBuilder, prop);
        }
    }

    @Override
    protected void generateTrivialSetter(ClassBuilder classBuilder, PropertyInfo prop) {
        String        jitName = prop.ensureSetterJitMethodName(typeSystem);
        JitMethodDesc jmd     = prop.getSetterJitDesc(this, thisType);

        if (jmd.isOptimized && isNativeMethod(jitName + OPT, jmd.optimizedMD)) {
            // the native optimized setter still needs its boxed entry point
            if (!isNativeMethod(jitName, jmd.standardMD)) {
                assembleMethodWrapper(classBuilder, jitName, jmd);
            }
        } else if (!isNativeMethod(jitName, jmd.standardMD)) {
            super.generateTrivialSetter(classBuilder, prop);
        }
    }

    @Override
    protected void assembleGenericProperty(ClassBuilder classBuilder, String name) {
        if (!isNativeMethod(name + "$get", null)) {
            super.assembleGenericProperty(classBuilder, name);
        }
    }

    @Override
    protected void assembleMethod(
            ClassBuilder classBuilder, MethodInfo method,
            String jitName, JitMethodDesc jmd) {
        if (method.isConstructorOnly()) {
            String        newName  = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
            JitMethodDesc newJmd   = Builder.convertConstructToNew(typeInfo, art.CD(), (JitCtorDesc) jmd);
            boolean       isNative = newJmd.isOptimized
                    ? isNativeMethod(newName + OPT, newJmd.optimizedMD)
                    : isNativeMethod(newName, newJmd.standardMD);
            if (isNative) {
                // the "new" method has been natively implemented; we should not attempt to generate
                // the constructor
                return;
            }
        }

        MethodModel mm = jmd.isOptimized
                ? findMethod(jitName + OPT, jmd.optimizedMD)
                : findMethod(jitName, jmd.standardMD);

        boolean isNative = isNativeElement(mm);

        if (mm != null && isNative && ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    method.isAbstract() || method.isNative())) {
            if (jmd.isOptimized && !isNativeMethod(jitName, jmd.standardMD)) {
                // we have the optimized native method; still need to generate the standard one
                assembleMethodWrapper(classBuilder, jitName, jmd);
            }

            if (jmd.isPrimitivized()) {
                // callers into a non-primitive base use an optimized virtual method, while a
                // primitive subclass implements that method as a static function with a "thi$";
                // e.g. route virtual "Boolean.estimateStringLength$p(Ctx)" (defined on Enum)
                //          to static "Boolean.estimateStringLength$p(boolean thi$, Ctx)"
                TypeConstant  typeDeclared = method.getJitIdentity().getClassIdentity().getType();
                JitMethodDesc jmdDeclared  = method.getJitDesc(this, typeDeclared);
                if (jmdDeclared.isOptimized && !jmdDeclared.isOptimizedStatic &&
                        !isNativeMethod(jitName + OPT, jmdDeclared.optimizedMD)) {
                    assembleOptimizedCap(classBuilder, jitName + OPT, jitName + OPT, jmdDeclared, jmd,
                            method.isConditionalReturn(typeInfo));
                }
            }

            // the method (and, if necessary, its standard wrapper) is already provided
            return;
        }

        if (method.getHead().isNative()) {
            // throw new IllegalStateException(...);
            System.err.println("*** Native implementation is missing " + art.className() + "#" + jitName +
                " for " + method.getSignature().getValueString());
            return;
        }

        super.assembleMethod(classBuilder, method, jitName, jmd);
    }

    @Override
    protected void assembleXvmType(ClassBuilder classBuilder) {
        // nObject.$xvmType() is only a fallback that calls $type(); every augmented class must
        // still get its own implementation unless it declares one natively
        MethodModel mm = findDeclaredMethod("$xvmType", MD_xvmType);
        if (mm == null) {
            super.assembleXvmType(classBuilder);
        }
    }

    @Override
    protected void assembleNew(ClassBuilder classBuilder, MethodInfo constructor, String jitName,
                               JitMethodDesc jmd, List<AnnotationConstructor> annoConstructors) {
        boolean isNative = jmd.isOptimized
                ? isNativeMethod(jitName + OPT, jmd.optimizedMD)
                : isNativeMethod(jitName, jmd.standardMD);

        if (!isNative) {
            super.assembleNew(classBuilder, constructor, jitName, jmd, annoConstructors);
        }
    }

    @Override
    protected boolean isInterface() {
        return super.isInterface() || classStruct.getFormat() == Component.Format.MIXIN;
    }

    /**
     * Apply any custom processing to the {@link ClassFileElement}.
     *
     * @param element       the {@link ClassFileElement} to customize
     * @param classBuilder  the {@link ClassBuilder} to use to generate code
     */
    public void processElement(ClassElement element, ClassBuilder classBuilder) {
        if (isJitGenerated) {
            Annotation generated = JitAnnotation.findAnnotation(element, CD_JitGenerated);
            if (generated != null) {
                ClassDesc genCD = getGeneratorClass(generated);
                if (CD_NaturalCode.equals(genCD)) {
                    // skip the Java stub; the JIT compiles the Ecstasy body
                    return;
                }

                // element has a JitGenerated annotation; execute its generator
                var clz = Builder.toClass(genCD, Generator.class);
                try {
                    var constructor = clz.getDeclaredConstructor();
                    var generator   = constructor.newInstance();
                    generator.generate(this, element, classBuilder);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                return;
            }
        }

        // element has no JIT annotations, so it is native code
        classBuilder.with(element);
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Find a FieldModel for the specified property.
     */
    protected FieldModel findField(String jitName) {
        for (FieldModel fm : model.fields()) {
            if (fm.fieldName().equalsString(jitName)) {
                return fm;
            }
        }
        return null;
    }

    /**
     * Find a MethodModel for the specified method.
     */
    protected MethodModel findMethod(String jitName, MethodTypeDesc md) {
        MethodModel method = findDeclaredMethod(jitName, md);
        if (method != null || jitName.equals(INIT_NAME) ||
                jitName.equals(ConstantDescs.CLASS_INIT_NAME)) {
            return method;
        }

        ClassModel       declaringModel = model;
        NativeTypeSystem typeSystem     = getTypeSystem();
        while (declaringModel.superclass().isPresent()) {
            declaringModel = typeSystem.getNativeClassModel(
                    declaringModel.superclass().orElseThrow().asSymbol());
            if (declaringModel == null) {
                return null;
            }

            method = findMethod(declaringModel, jitName, md, true);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    /**
     * Find a method declared directly by the class being augmented.
     */
    protected MethodModel findDeclaredMethod(String jitName, MethodTypeDesc md) {
        return findMethod(model, jitName, md, false);
    }

    /**
     * Find a method declared by the specified class model.
     *
     * @param checkSuper  true to allow inheritance chain traversal
     */
    private MethodModel findMethod(ClassModel declaringModel, String jitName,
                                   MethodTypeDesc md, boolean checkSuper) {
        for (MethodModel method : declaringModel.methods()) {
            if (method.methodName().equalsString(jitName) &&
                    (md == null || method.methodTypeSymbol().equals(md))) {
                int flags = method.flags().flagsMask();
                if (!checkSuper ||
                        (flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0) {
                    return method;
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isNativeMethod(String jitName, MethodTypeDesc md) {
        return nativeElements.computeIfAbsent(new NativeKey(jitName, md, false), k -> {
                    MethodModel mm = findMethod(jitName, md);
                    return isNativeElement(mm);
                });
    }

    @Override
    protected boolean isNativeField(String jitName) {
        return nativeElements.computeIfAbsent(new NativeKey(jitName, null, true), k -> {
                    FieldModel fm = findField(jitName);
                    return isNativeElement(fm);
                });
    }

    /**
     * Determine whether the specified element is coded natively.
     * <ul>
     *   <li>If the element is {@code null} it is not native.
     *   <li>If the element uses the default {@link JitGenerated.NaturalCode} generator,
     *       it is not native.
     *   <li>If the element specifies a custom generator via {@link JitGenerated}, the element
     *       is treated as native because that generator supplies its implementation.
     *   <li>If an existing element has no {@link JitGenerated} annotation, it is native.
     * </ul>
     *
     * @param element  the {@link ClassFileElement} to test
     *
     * @return {@code true} if the element is coded natively
     */
    public boolean isNativeElement(ClassFileElement element) {
        if (element == null) {
            return false;
        }
        Annotation an = JitAnnotation.findAnnotation(element, CD_JitGenerated);
        return an == null || !CD_NaturalCode.equals(getGeneratorClass(an));
    }

    /**
     * @return the explicit {@link Generator} class, or {@link JitGenerated.NaturalCode} if omitted
     */
    protected ClassDesc getGeneratorClass(Annotation annotation) {
        AnnotationValue value = JitAnnotation.findValue(annotation);
        return value == null ? CD_NaturalCode : ((AnnotationValue.OfClass) value).classSymbol();
    }

    // ----- inner record: NativeKey ---------------------------------------------------------------

    /**
     * The key used in the native element cache map {@link #nativeElements}.
     */
    private record NativeKey(String jitName, ConstantDesc desc, boolean isField) {}
}
