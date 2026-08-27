package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ReturnInstruction;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import java.util.List;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.NativeTypeSystem;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/**
 * The builder for native types that uses an existing Java class to augment with the Ecstasy natural
 * code.
 */
public class AugmentingBuilder extends CommonBuilder {

    public AugmentingBuilder(TypeSystem typeSystem, Artifact art, ClassModel model) {
        super(typeSystem, art);

        this.model = model;
    }

    /**
     * The augmenting Classfile model.
     */
    public final ClassModel model;

    protected NativeTypeSystem getTypeSystem() {
        return (NativeTypeSystem) typeSystem;
    }

    @Override
    public ClassDesc getSuperCD() {
        return model.superclass().orElseThrow().asSymbol();
    }

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        // do not augment Object and since nRef is both Ref and Var, ignore "Var" interface; it
        // causes circular initialization
        if (thisId.equals(pool().clzObject()) || thisId.equals(pool().clzVar())) {
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
        TypeConstant T_EXCEPTION = type.getConstantPool().typeException();
        if (type.isA(T_EXCEPTION) && !type.equals(T_EXCEPTION)) {
            new ExceptionBuilder(typeSystem, art).assembleCreateException(classBuilder);
        }

        switch (typeInfo.getFormat()) {
        case ENUMVALUE:
            // for now, native enum values need to be fully functional (no code gen)
            return false;

        case ENUM:
            // for all native enums generate the "equals" and "compare"
            EnumBuilder.generateOrderable(classBuilder, this);
            // fall through
        default:
            return true;
        }
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
    protected void assembleInit(
            ClassBuilder classBuilder,
            List<PropertyInfo> props) {
        MethodModel mm = findMethod(INIT_NAME, MD_xvmVoid);
        if (mm == null) { // TODO && !ENUM ?? or !isPrimitive ??
            super.assembleInit(classBuilder, props);
        }
    }

    @Override
    protected void assembleField(ClassBuilder classBuilder, PropertyInfo prop) {
        String jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
        if (findField(jitName) == null) {
            super.assembleField(classBuilder, prop);
        }
    }

    @Override
    protected void assemblePropertyAccessor(
            ClassBuilder classBuilder,
            PropertyInfo prop, String jitName,
            JitMethodDesc jmd, boolean isGetter) {
        MethodTypeDesc md = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
        MethodModel    mm = findMethod(jitName, md);
        if (mm != null && ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    prop.isAbstract() || prop.isNative())) {
            // the property is already copied by the NativeTypeSystem
            return;
        }

        super.assemblePropertyAccessor(classBuilder, prop, jitName, jmd, isGetter);
    }

    @Override
    protected void generateTrivialGetter(ClassBuilder classBuilder, PropertyInfo prop) {
        if (findMethod(prop.ensureGetterJitMethodName(typeSystem), null) == null) {
            super.generateTrivialGetter(classBuilder, prop);
        }
    }

    @Override
    protected void generateTrivialSetter(ClassBuilder classBuilder, PropertyInfo prop) {
        if (findMethod(prop.ensureSetterJitMethodName(typeSystem), null) == null) {
            super.generateTrivialSetter(classBuilder, prop);
        }
    }

    @Override
    protected void assembleGenericProperty(ClassBuilder classBuilder, String name) {
        if (findMethod(name + "$get", null) == null) {
            super.assembleGenericProperty(classBuilder, name);
        }
    }

    @Override
    protected void assembleMethod(
            ClassBuilder classBuilder, MethodInfo method,
            String jitName, JitMethodDesc jmd) {
        if (method.isCtorOrValidator()) {
            String        newName = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
            JitMethodDesc newJmd  = Builder.convertConstructToNew(typeInfo, art.CD(), (JitCtorDesc) jmd);
            MethodModel   newMM   = newJmd.isOptimized
                    ? findMethod(newName+OPT, newJmd.optimizedMD)
                    : findMethod(newName, newJmd.standardMD);
            if (newMM != null) {
                // the "new" method has been natively implemented; we should not attempt to generate
                // the constructor
                return;
            }
        }

        MethodModel mm = jmd.isOptimized
                ? findMethod(jitName+OPT, jmd.optimizedMD)
                : findMethod(jitName, jmd.standardMD);

        if (mm != null &&
                ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    method.isAbstract() || method.isNative())) {
            if (jmd.isOptimized && findMethod(jitName, jmd.standardMD) == null) {
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
                        findMethod(jitName+OPT, jmdDeclared.optimizedMD) == null) {
                    assembleOptimizedCap(classBuilder, jitName+OPT, jitName+OPT, jmdDeclared, jmd);
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
    protected void assembleNew(
            ClassBuilder classBuilder, MethodInfo constructor,
            String jitName, JitMethodDesc jmd) {
        MethodModel mm = jmd.isOptimized
                ? findMethod(jitName+OPT, jmd.optimizedMD)
                : findMethod(jitName, jmd.standardMD);

        if (mm != null) {
            return;
        }
        super.assembleNew(classBuilder, constructor, jitName, jmd);
    }

    // ----- helper methods ------------------------------------------------------------------------

    @Override protected boolean shouldGenerate(IdentityConstant id) {
        // we do not generate constructors for native enums
        if (id instanceof MethodConstant methodId && methodId.isConstructor()) {
            TypeConstant type = getThisType();
            if (type.isEnum() || type.isEnumValue()) {
                return false;
            }
        }

        return super.shouldGenerate(id);
    }

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

        ClassModel declaringModel = model;
        while (declaringModel.superclass().isPresent()) {
            declaringModel = getTypeSystem().getNativeClassModel(
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
        return findMethod(jitName, md) != null;
    }

    @Override
    protected boolean isNativeField(String jitName, ClassDesc cd) {
        FieldModel fm = findField(jitName);
        if (fm != null) {
            assert fm.fieldTypeSymbol().equals(cd);
            return true;
        }
        return false;
    }
}
