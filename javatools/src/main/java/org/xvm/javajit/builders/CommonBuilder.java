package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.DoubleSlot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.Ctx;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;

import org.xvm.util.ShallowSizeOf;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.JitFlavor.NullablePrimitive;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        extends Builder {
    public CommonBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem);

        assert type.isSingleUnderlyingClass(true);

        ConstantPool pool = typeSystem.pool();

        this.typeInfo    = pool.ensureAccessTypeConstant(type, Access.PRIVATE).ensureTypeInfo();
        this.structInfo  = pool.ensureAccessTypeConstant(type, Access.STRUCT).ensureTypeInfo();
        this.classStruct = typeInfo.getClassStructure();
        this.thisId      = classStruct.getIdentityConstant();
    }

    protected final TypeInfo         typeInfo;
    protected final TypeInfo         structInfo;
    protected final ClassStructure   classStruct;
    protected final IdentityConstant thisId;

    /**
     * The shallow size of object in bytes.
     */
    protected long implSize;

    /**
     * Methods that were added during the compilation. They are either nested in properties/methods
     * or methods declared on the mixins or annotations that were added to the "impl" class.
     */
    protected Set<IdentityConstant> extraMethods = new HashSet<>();

    /**
     * TEMPORARY: compensation for TypeInfo dupes.
     */
    protected final Set<String> methodNames = new HashSet<>();

    @Override
    public void assembleImpl(String className, ClassBuilder classBuilder) {
        implSize = ShallowSizeOf.align(computeInstanceSize());
        assembleImplClass(className, classBuilder);
        assembleImplProperties(className, classBuilder);
        assembleImplMethods(className, classBuilder);
    }

    @Override
    public void assemblePure(String className, ClassBuilder classBuilder) {
        // assemblePureClass(className, classBuilder);
        // assemblePureProperties(className, classBuilder);
        // assemblePureMethods(className, classBuilder);
    }

    @Override
    protected TypeConstant getThisType() {
        return typeInfo.getType();
    }

    /**
     * Compute the ClassDesc for the super class.
     */
    protected ClassDesc getSuperCD() {
        TypeConstant superType = typeInfo.getExtends();
        return superType == null
            ? CD_nObj
            : superType.ensureClassDesc(typeSystem);
    }

    /**
     * Compute the instance size of the generated class(es). If more than one class gets generated,
     * the return value reflects the total size of all instantiated objects. Specialized builders
     * should override this method augmenting the memory requirement accordingly.
     */
    protected int computeInstanceSize() {
        int size = 0;
        for (Map.Entry<PropertyConstant, PropertyInfo> entry :
                    structInfo.getProperties().entrySet()) {
            PropertyInfo infoProp = entry.getValue();

            // see ClassComposition.ensureFieldLayoutImpl()
            if (infoProp.hasField()) {
                PropertyConstant idProp = entry.getKey();
                if (!idProp.isTopLevel()) {
                    IdentityConstant idParent = idProp.getParentConstant();
                    switch (idParent.getFormat()) {
                    case Property:
                        if (!structInfo.getClassChain().
                                containsKey(infoProp.getIdentity().getClassIdentity())) {
                            // the property is defined by the underlying type; currently those
                            // nested properties are stored in the corresponding Ref "box"
                            continue;
                        }
                        break;

                    case Method:
                        break;
                    }
                }
               size += computePropertySize(infoProp);
            }
        }
        return size;
    }

    /**
     * Compute the size of the property field.
     */
    protected int computePropertySize(PropertyInfo prop) {
        if (prop.isRefAnnotated()) {
            // TODO
            return ShallowSizeOf.fieldOf(Object.class);
        }
        TypeConstant type = prop.getType();
        ClassDesc    cd   = type.isPrimitive()
                ? JitTypeDesc.getPrimitiveClass(type)
                : null;

        return cd == null
            ? ShallowSizeOf.fieldOf(Object.class)
            : ShallowSizeOf.fieldOf(cd);

    }

    /**
     * Assemble the class specific info for the "Impl" shape.
     */
    protected void assembleImplClass(String className, ClassBuilder classBuilder) {
        int flags = ClassFile.ACC_PUBLIC;

        switch (classStruct.getFormat()) {
            case CLASS, CONST, SERVICE, ENUMVALUE:
                flags |= ClassFile.ACC_SUPER; // see JLS 4.1
                classBuilder.withSuperclass(getSuperCD());
                break;

            case INTERFACE, ENUM:
                flags |= ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT;
                break;

            case ANNOTATION, MIXIN:
                // annotations and mixins are incorporated (copied) into every class that
                // annotates the annotation or incorporates the mixin
                return;

            default:
                // TODO: support for mixin, annotations, etc
                throw new RuntimeException("Not implemented " + typeInfo.getType());
        }
        classBuilder.withFlags(flags);

        assembleImplInterfaces(classBuilder);
    }

    /**
     * Assemble interfaces for the "Impl" shape.
     */
    protected void assembleImplInterfaces(ClassBuilder classBuilder) {
        boolean         isInterface = classStruct.getFormat() == Format.INTERFACE;
        List<ClassDesc> interfaces  = new ArrayList<>();
        for (Contribution contrib : typeInfo.getContributionList()) {
            switch (contrib.getComposition()) {
                case Implements:
                    TypeConstant contribType = contrib.getTypeConstant().removeAccess();
                    if  (!isInterface &&
                            contribType.equals(contribType.getConstantPool().typeObject())) {
                        // ignore "implements Object" for classes
                        continue;
                    }
                    interfaces.add(contribType.ensureClassDesc(typeSystem));
                    break;
            }
        }
        if (!interfaces.isEmpty()) {
            classBuilder.withInterfaceSymbols(interfaces);
        }
    }

    /**
     * Assemble properties for the "Impl" shape.
     */
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        List<PropertyInfo> constProps = new ArrayList<>();
        List<PropertyInfo> initProps  = new ArrayList<>();

        for (PropertyInfo prop : structInfo.getProperties().values()) {
            if ((prop.hasField() || prop.isInjected()) &&
                    shouldGenerate(prop.getFieldIdentity())) {
                assembleField(className, classBuilder, prop);

                if (prop.isConstant()) {
                    constProps.add(prop);
                }
                else if (prop.isInitialized()) {
                    initProps.add(prop);
                }
            }
            else {
                // not our responsibility
            }
        }

        boolean isSingleton = typeInfo.isSingleton();
        if (isSingleton) {
            // public static final $INSTANCE;
            classBuilder.withField(Instance, ClassDesc.of(className),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }

        if (!constProps.isEmpty() || isSingleton || hasStaticInitializer()) {
            assembleStaticInitializer(className, classBuilder, constProps);
        }

        Format format = classStruct.getFormat();
        switch (format) {
        case CLASS, CONST, SERVICE, MODULE, PACKAGE, ENUM, ENUMVALUE:
            assembleInitializer(className, classBuilder, initProps);
            break;
        }

        for (PropertyInfo prop : typeInfo.getProperties().values()) {
            if (prop.isFormalType()) {
                // processed below
                continue;
            }

            IdentityConstant ownerId = prop.getIdentity().getNamespace();
            if (prop.getIdentity().getNamespace().equals(thisId)) {
                assembleImplProperty(className, classBuilder, prop);
            } else {
                Format ownerFormat = ownerId.getComponent().getFormat();
                if (ownerFormat == Format.ANNOTATION || ownerFormat == Format.MIXIN) {
                    assembleImplProperty(className, classBuilder, prop);
                }
            }
        }

        if (format != Format.INTERFACE && typeInfo.hasGenericTypes()) {
            for (var entry : typeInfo.getTypeParams().entrySet()) {
                if (entry.getKey() instanceof String name) {
                    assembleGenericProperty(classBuilder, name);
                }
            }
        }
    }

    /**
     * Assemble the field(s) for the specified Ecstasy property.
     */
    protected void assembleField(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String      jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
        JitTypeDesc jtd     = prop.getType().getJitDesc(typeSystem);

        int flags = ClassFile.ACC_PUBLIC;
        if (prop.isConstant()) {
            flags |= ClassFile.ACC_STATIC;
        }
        switch (jtd.flavor) {
        case Specific, Widened, Primitive:
            classBuilder.withField(jitName, jtd.cd, flags);
            break;

        case NullablePrimitive:
            classBuilder.withField(jitName, jtd.cd, flags);
            classBuilder.withField(jitName+EXT, CD_boolean, flags);
            break;

        default:
            throw new IllegalStateException("Unsupported property flavor: " + jtd.flavor);
        }
    }

    /**
     * Add constant fields initialization to the static initializer.
     */
    protected void assembleStaticInitializer(String className, ClassBuilder classBuilder,
                                             List<PropertyInfo> props) {
        ClassDesc CD_this = ClassDesc.of(className);

        classBuilder.withMethod(ConstantDescs.CLASS_INIT_NAME,
            MethodTypeDesc.of(CD_void),
            ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code -> {
                Label startScope = code.newLabel();
                Label endScope   = code.newLabel();
                code.labelBinding(startScope);

                int ctxSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.localVariable(ctxSlot, "ctx", CD_Ctx, startScope, endScope)
                    .invokestatic(CD_Ctx, "get", MethodTypeDesc.of(CD_Ctx))
                    .astore(0);

                // add static field initialization
                TypeSystem ts = typeSystem;
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        RegisterInfo reg     = loadConstant(code, prop.getInitialValue());
                        String       jitName = prop.getIdentity().ensureJitPropertyName(ts);
                        if (reg instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == NullablePrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code.ifne(ifTrue)
                                .putstatic(CD_this, jitName +EXT, CD_boolean)
                                .goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putstatic(CD_this, jitName, reg.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert reg.isSingle();
                            code.putstatic(CD_this, jitName, reg.cd());
                        }
                    } else {
                        throw new UnsupportedOperationException("Static field initializer");
                    }
                }

                if (typeInfo.isSingleton()) {
                    // $INSTANCE = new Singleton($ctx);
                    // $ctx.allocated(implSize);
                    // $INSTANCE.$init($ctx);
                    MethodConstant ctorId  = typeInfo.findConstructor(TypeConstant.NO_TYPES);
                    String         jitInit = ctorId.ensureJitMethodName(ts).replace("construct", INIT);
                    invokeDefaultConstructor(code, CD_this);
                    code.dup()
                        .putstatic(CD_this, Instance, CD_this)
                        .aload(ctxSlot)
                        .ldc(implSize)
                        .invokevirtual(CD_Ctx, "allocated", MethodTypeDesc.of(CD_void, CD_long))
                        .aload(ctxSlot)
                        .invokevirtual(CD_this, jitInit, MethodTypeDesc.of(CD_this, CD_Ctx))
                        .pop()
                    ;
                }

                augmentStaticInitializer(className, code);

                code.labelBinding(endScope)
                    .return_();
            }));
    }

    /**
     * Allow subclasses to augment the <clinit> assembly.
     */
    protected boolean hasStaticInitializer() {
        return false;
    }

    /**
     * Allow subclasses to augment the <clinit> assembly.
     */
    protected void augmentStaticInitializer(String className, CodeBuilder code) {
    }

    /**
     * Add fields initialization to the Java constructor {@code void <init>(Ctx ctx)}.
     */
    protected void assembleInitializer(String className, ClassBuilder classBuilder,
                                       List<PropertyInfo> props) {
        ClassDesc CD_this = ClassDesc.of(className);

        classBuilder.withMethodBody(INIT_NAME,
            MD_Initializer,
            ClassFile.ACC_PUBLIC,
            code -> {
                Label startScope = code.newLabel();
                Label endScope   = code.newLabel();
                code.labelBinding(startScope);

                code.localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, startScope, endScope);

                callSuperInitializer(code);

                // add field initialization
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        code.aload(0); // Stack: { this }

                        RegisterInfo reg     = loadConstant(code, prop.getInitialValue());
                        String       jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
                        if (reg instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == NullablePrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code
                                .ifne(ifTrue)
                                .putfield(CD_this, jitName +EXT, CD_boolean);
                            code.goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putfield(CD_this, jitName, doubleSlot.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert reg.isSingle();
                            code.putfield(CD_this, jitName, reg.cd());
                        }
                    } else {
                        throw new UnsupportedOperationException("Field initializer");
                    }
                }
                code.labelBinding(endScope)
                    .return_();
            }
        );
    }

    /**
     * Assemble the super class constructor call.
     */
    protected void callSuperInitializer(CodeBuilder code) {
        // super($ctx);
        code.aload(0)
            .aload(code.parameterSlot(0))
            .invokespecial(getSuperCD(), INIT_NAME, MD_Initializer);
    }

    /**
     * Assemble the property accessors for the "Impl" shape.
     */
    protected void assembleImplProperty(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        MethodInfo getterInfo = typeInfo.getMethodById(prop.getGetterId());
        if (getterInfo == null) {
            if (prop.hasField() && shouldGenerate(prop.getFieldIdentity())) {
                if (prop.isInjected()) {
                    generateInjected(className, classBuilder, prop);
                } else {
                    generateTrivialGetter(className, classBuilder, prop);
                }
            }
        } else {
            switch (getterInfo.getHead().getImplementation()) {
            case Field:
                generateTrivialGetter(className, classBuilder, prop);
                break;
            case Explicit:
                String         jitName = prop.ensureGetterJitMethodName(typeSystem);
                JitMethodDesc  jmDesc  = prop.getGetterJitDesc(typeSystem);
                boolean        isOpt   = jmDesc.isOptimized;
                MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
                if (isOpt) {
                    jitName += OPT;
                }
                assemblePropertyAccessor(className, classBuilder, prop, jitName, md, isOpt, true);
                break;
            }
        }

        MethodInfo setterInfo = typeInfo.getMethodById(prop.getSetterId());
        if (setterInfo == null) {
            if (prop.hasField() && shouldGenerate(prop.getFieldIdentity())) {
                generateTrivialSetter(className, classBuilder, prop);
            }
        } else {
            switch (getterInfo.getHead().getImplementation()) {
            case Field:
                generateTrivialSetter(className, classBuilder, prop);
                break;

            case Explicit:
                String         jitName = prop.ensureSetterJitMethodName(typeSystem);
                JitMethodDesc  jmDesc  = prop.getSetterJitDesc(typeSystem);
                boolean        isOpt   = jmDesc.isOptimized;
                MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
                if (isOpt) {
                    jitName += OPT;
                }
                assemblePropertyAccessor(className, classBuilder, prop, jitName, md, isOpt, false);
                break;
            }
        }
    }

    private void generateTrivialGetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitGetterName = prop.ensureGetterJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

        ClassDesc CD_this = ClassDesc.of(className);
        int       flags   = ClassFile.ACC_PUBLIC;
        if (prop.isConstant()) {
            flags |= ClassFile.ACC_STATIC;
        }
        JitMethodDesc  jmd     = prop.getGetterJitDesc(typeSystem);
        boolean        isOpt   = jmd.isOptimized;
        MethodTypeDesc md      = isOpt ? jmd.optimizedMD : jmd.standardMD;
        String         jitName = isOpt ? jitGetterName+OPT : jitGetterName;

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            if (isOpt) {
                JitParamDesc pdOpt = jmd.optimizedReturns[0];
                ClassDesc    cdOpt = pdOpt.cd;
                switch (pdOpt.flavor) {
                case Specific, Widened, Primitive:
                    if (prop.isConstant()) {
                        code.getstatic(CD_this, jitFieldName, cdOpt);
                    } else {
                        code.aload(0)
                            .getfield(CD_this, jitFieldName, cdOpt);
                    }
                    addReturn(code, cdOpt);
                    break;

                case NullablePrimitive:
                    if (prop.isConstant()) {
                        code.getstatic(CD_this, jitFieldName, cdOpt)
                            .getstatic(CD_this, jitFieldName+EXT, CD_boolean);
                    } else {
                        code.aload(0)
                            .getfield(CD_this, jitFieldName, cdOpt)
                            .getfield(CD_this, jitFieldName+EXT, CD_boolean);
                    }
                    storeToContext(code, CD_boolean, 0);
                    addReturn(code, cdOpt);
                    break;

                default:
                    throw new IllegalStateException("Unsupported property flavor: " + pdOpt.flavor);
                }
            } else {
                JitParamDesc pdStd = jmd.standardReturns[0];
                if (prop.isConstant()) {
                    code.getstatic(CD_this, jitFieldName, pdStd.cd);
                } else {
                    code.aload(0)
                        .getfield(CD_this, jitFieldName, pdStd.cd);
                }
                code.areturn();
            }
        });

        if (isOpt) {
            // generate a wrapper
            assembleMethodWrapper(className, classBuilder, jitGetterName, jmd,
                    prop.isConstant(), false);
        }
    }

    private void generateTrivialSetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitSetterName = prop.ensureSetterJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

        ClassDesc CD_this = ClassDesc.of(className);
        int       flags   = ClassFile.ACC_PUBLIC;
        if (prop.isConstant()) {
            flags |= ClassFile.ACC_STATIC;
        }
        JitMethodDesc  jmd     = prop.getSetterJitDesc(typeSystem);
        boolean        isOpt   = jmd.isOptimized;
        MethodTypeDesc md      = isOpt ? jmd.optimizedMD : jmd.standardMD;
        String         jitName = isOpt ? jitSetterName+OPT : jitSetterName;

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            int argSlot = code.parameterSlot(1); // compensate for $ctx
            if (isOpt) {
                JitParamDesc pdOpt = jmd.optimizedParams[0];
                ClassDesc    cdOpt = pdOpt.cd;

                switch (pdOpt.flavor) {
                case Specific, Widened, Primitive:
                    if (prop.isConstant()) {
                        load(code, cdOpt, argSlot);
                        code.putstatic(CD_this, jitFieldName, cdOpt);
                    } else {
                        code.aload(0);
                        load(code, cdOpt, argSlot);
                        code.putfield(CD_this, jitFieldName, cdOpt);
                    }
                    break;

                case NullablePrimitive:
                    int extSlot = argSlot + toTypeKind(cdOpt).slotSize();
                    if (prop.isConstant()) {
                        load(code, cdOpt, argSlot);
                        code.putstatic(CD_this, jitFieldName, cdOpt)
                            .iload(extSlot)
                            .putstatic(CD_this, jitFieldName+EXT, CD_boolean);
                    } else {
                        code.aload(0);
                        load(code, cdOpt, argSlot);
                        code.putfield(CD_this, jitFieldName, cdOpt)
                            .iload(extSlot)
                            .putfield(CD_this, jitFieldName+EXT, CD_boolean);
                    }
                    break;

                default:
                    throw new IllegalStateException("Unsupported property flavor: " + pdOpt.flavor);
                }
            } else {
                JitParamDesc pdStd = jmd.standardParams[0];

                if (prop.isConstant()) {
                    code.aload(argSlot);
                    code.putstatic(CD_this, jitFieldName, pdStd.cd);
                } else {
                    code.aload(0)
                        .aload(argSlot)
                        .putfield(CD_this, jitFieldName, pdStd.cd);
                }
            }
            code.return_();
        });

        if (isOpt) {
            // generate a wrapper
            assembleMethodWrapper(className, classBuilder, jitSetterName, jmd,
                    prop.isConstant(), false);
        }
    }

    /**
     * Assemble the generic property accessors for the "Impl" shape.
     */
    private void assembleGenericProperty(ClassBuilder classBuilder, String name) {
        classBuilder.withMethodBody(name + "$get", MethodTypeDesc.of(CD_nType, CD_Ctx),
            ClassFile.ACC_PUBLIC, code ->
                code.aload(0)                     // this
                    .aload(code.parameterSlot(0)) // ctx
                    .ldc(name)
                    .invokevirtual(CD_nObj, "$type", MethodTypeDesc.of(CD_nType, CD_Ctx, CD_JavaString))
                    .areturn()
        );
    }

    private void generateInjected(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        assert !prop.isConstant();

        String jitGetterName = prop.ensureGetterJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

        ClassDesc CD_this = ClassDesc.of(className);
        int       flags   = ClassFile.ACC_PUBLIC;

        JitMethodDesc  jmd     = prop.getGetterJitDesc(typeSystem);
        boolean        isOpt   = jmd.isOptimized;
        MethodTypeDesc md      = isOpt ? jmd.optimizedMD : jmd.standardMD;
        String         jitName = isOpt ? jitGetterName+OPT : jitGetterName;

        TypeConstant resourceType = prop.getType();
        Annotation   anno         = prop.getRefAnnotations()[0];
        Constant[]   params       = anno.getParams();
        int          paramCount   = params.length;

        Constant nameConst = paramCount > 0 ? params[0] : null;
        Constant optsConst = paramCount > 1 ? params[1] : null;
        String   resourceName = nameConst instanceof StringConstant stringConst
                        ? stringConst.getValue()
                        : prop.getName();
        if (optsConst != null && !(optsConst instanceof RegisterConstant regConst &&
                                   regConst.getRegisterIndex() == Op.A_DEFAULT)) {
            throw new UnsupportedOperationException("retrieve opts");
        }

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            // generate the following:
            // T value = this.prop;
            // if (value == null} { value = this.prop = $ctx.inject(type, name, opts);}
            // return value;

            if (isOpt) {
                JitParamDesc pdOpt = jmd.optimizedReturns[0];
                ClassDesc    cdOpt = pdOpt.cd;
                switch (pdOpt.flavor) {
                case Primitive:
                    code.aload(0)
                        .getfield(CD_this, jitFieldName, cdOpt);
                    throw new UnsupportedOperationException("Primitive injection");

                case NullablePrimitive:
                    code.aload(0)
                        .getfield(CD_this, jitFieldName, cdOpt)
                        .getfield(CD_this, jitFieldName+EXT, CD_boolean);
                    throw new UnsupportedOperationException("MultiSlotPrimitive injection");

                default:
                    throw new IllegalStateException("Unsupported property flavor: " + pdOpt.flavor);
                }
            } else {
                JitParamDesc pdStd     = jmd.standardReturns[0];
                Label        endLbl    = code.newLabel();
                int          valueSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.aload(0)
                    .getfield(CD_this, jitFieldName, pdStd.cd)
                    .dup()
                    .astore(valueSlot)
                    .ifnonnull(endLbl)
                    .aload(1); // $ctx
                Builder.loadTypeConstant(code, typeSystem, resourceType);
                code.ldc(resourceName)
                    .aconst_null() // opts
                    .invokevirtual(CD_Ctx, "inject", Ctx.MD_inject)
                    .checkcast(pdStd.cd)
                    .dup()
                    .astore(valueSlot)
                    .aload(0)
                    .swap()
                    .putfield(CD_this, jitFieldName, pdStd.cd)
                    .labelBinding(endLbl)
                    .aload(valueSlot)
                    .areturn();
            }
        });

        if (isOpt) {
            // generate a wrapper
            assembleMethodWrapper(className, classBuilder, jitGetterName, jmd, false, false);
        }
    }

    /**
     * Assemble methods for the "Impl" shape.
     */
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        boolean assembleDeclared = classStruct.getFormat() != Format.INTERFACE;

        for (MethodInfo method : typeInfo.getMethods().values()) {
            if (method.isNative()) {
                continue; // not our responsibility
            }

            if (assembleDeclared &&
                    method.getHead().getImplementation() == Implementation.Declared) {
                assembleImplMethod(className, classBuilder, method);
            }

            if (shouldGenerate(method.getIdentity())) {
                assembleImplMethod(className, classBuilder, method);
            }
        }

        if (typeInfo.getClassStructure().getFormat() != Format.INTERFACE) {
            assembleXvmType(className, classBuilder);
        }
    }

    /**
     * Assemble the "public TypeConstant $xvmType()" method.
     */
    protected void assembleXvmType(String className, ClassBuilder classBuilder) {
        boolean hasType = typeInfo.hasGenericTypes();

        if (hasType) {
            classBuilder.withField("$type", CD_TypeConstant, ClassFile.ACC_PUBLIC);
        }

        classBuilder.withMethodBody("$xvmType", MD_xvmType,
                ClassFile.ACC_PUBLIC, code -> {
            if (hasType) {
                code.aload(0)
                    .getfield(ClassDesc.of(className), "$type", CD_TypeConstant);
            } else {
                loadTypeConstant(code, typeSystem, typeInfo.getType());
            }
            code.areturn();
        });
    }

    /**
     * Assemble the method(s) for the "Impl" shape of the specified Ecstasy method.
     */
    protected void assembleImplMethod(String className, ClassBuilder classBuilder, MethodInfo method) {
        boolean cap    = method.isCapped();
        boolean router = false;

        String jitName = method.ensureJitMethodName(typeSystem);

        // TODO REMOVE: temporary compensation for duplicates in the TypeInfo
        if (!methodNames.add(jitName)) {
            return;
        }

        if (!cap) {
            MethodBody[] chain = method.ensureOptimizedMethodChain(typeInfo);
            int          depth = chain.length;
            if (depth > 0) {
                router = chain[0].getImplementation() == Implementation.Delegating;
            }
        }

        if (cap || router) {
            MethodInfo targetMethod = cap ? typeInfo.getNarrowingMethod(method) : method;
            assert targetMethod != null;
            assembleRoutingMethod(className, classBuilder, method, targetMethod);
        } else {
            JitMethodDesc jmDesc = method.getJitDesc(typeSystem, typeInfo.getType());
            assembleMethod(className, classBuilder, method, jitName, jmDesc);

            if (method.isCtorOrValidator()) {
                String        newName = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
                JitMethodDesc newDesc = Builder.convertConstructToNew(typeInfo, className, (JitCtorDesc) jmDesc);
                assembleNew(className, classBuilder, method, newName, newDesc);
            }
        }
    }

    /**
     * Assemble a "standard" wrapper method for the optimized method.
     */
    protected void assembleMethodWrapper(String className, ClassBuilder classBuilder,
                                         String jitName, JitMethodDesc jmDesc,
                                         boolean isStatic, boolean isConstructor) {
        ClassDesc CD_this = ClassDesc.of(className);

        // this method is "standard" and needs to call into the optimized one
        int flags = ClassFile.ACC_PUBLIC;
        if (isStatic) {
            flags |= ClassFile.ACC_STATIC;
        }

        classBuilder.withMethodBody(jitName, jmDesc.standardMD, flags, code -> {

            if (!isStatic) {
                code.aload(0); // stack: this
            }

            int extraCount = jmDesc.getImplicitParamCount();
            for (int i = 0; i < extraCount; i++) {
                code.aload(code.parameterSlot(i));
            }

            JitParamDesc[] optParams = jmDesc.optimizedParams;
            for (int i = 0, c = optParams.length; i < c; i++) {
                JitParamDesc optParamDesc = optParams[i];
                int          stdParamIx   = optParamDesc.index;
                int          stdParamSlot = code.parameterSlot(extraCount + stdParamIx);
                TypeConstant stdParamType = optParamDesc.type;

                switch (optParamDesc.flavor) {
                    case Specific, Widened:
                        code.aload(stdParamSlot);
                        break;

                    case SpecificWithDefault, WidenedWithDefault:
                        // null indicates "default" value; pass it along regardless
                        code.aload(stdParamSlot);
                        break;

                    case Primitive:
                        code.aload(stdParamSlot);
                        unbox(code, stdParamType, optParamDesc.cd);
                        break;

                    case PrimitiveWithDefault: {
                        // if the argument is Java `null`, pass the default value for the type and
                        // `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code
                           .aload(stdParamSlot)
                           .ifnonnull(ifNotNull);
                        // the value is `null`
                        Builder.defaultLoad(code, optParamDesc.cd); // default primitive
                        code.iconst_1();                            // true

                        code
                            .goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot);
                        unbox(code, stdParamType, optParamDesc.cd); // unwrapped primitive
                        code.iconst_0();                            // false

                        code.labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case NullablePrimitive: {
                        assert stdParamType.isNullable();
                        TypeConstant primitiveType = stdParamType.removeNullable();
                        // if the argument is Ecstasy `Null`, pass the default value for the type
                        // and `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code.aload(stdParamSlot);
                        Builder.loadNull(code);
                        code.if_acmpne(ifNotNull);
                        // the value is `Null`
                        Builder.defaultLoad(code, optParamDesc.cd);  // default primitive
                        code.iconst_1();                             // true

                        code
                            .goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot);
                        unbox(code, primitiveType, optParamDesc.cd); // unboxed primitive
                        code.iconst_0();                             // false

                        code.labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case AlwaysNull:
                        throw new UnsupportedOperationException();
                }
            }

            if (isStatic) {
                code.invokestatic(CD_this, jitName+OPT, jmDesc.optimizedMD);
            } else {
                code.invokevirtual(CD_this, jitName+OPT, jmDesc.optimizedMD);
            }

            JitParamDesc[] optReturns     = jmDesc.optimizedReturns;
            int            optReturnCount = optReturns.length;
            JitParamDesc[] stdReturns     = jmDesc.standardReturns;
            int            stdReturnCount = stdReturns.length;
            if (optReturnCount == 0) {
                code.return_();
                return;
            }

            // the natural return is at the top of the stack now; iterate returns in the inverse order
            for (int optIx = optReturnCount-1, stdIx = stdReturnCount-1; optIx >= 0; optIx--) {
                JitParamDesc optDesc = optReturns[optIx];
                if (optDesc.extension) {
                    // since we are in the reverse order, the "actual" return will use this value
                    continue;
                }
                ClassDesc    optCD    = optDesc.cd;
                TypeConstant optType  = optDesc.type;
                int          optRetIx = optDesc.altIndex;

                JitParamDesc stdDesc  = stdReturns[stdIx--];
                ClassDesc    stdCD    = stdDesc.cd;
                TypeConstant stdType  = stdDesc.type;
                int          stdRetIx = stdDesc.altIndex;

                switch (optDesc.flavor) {
                case Specific, Widened:
                    if (optIx == 0) {
                        // natural return
                        code.areturn();
                    } else {
                        if (optRetIx != stdRetIx) {
                            loadFromContext(code, optCD, optRetIx);
                            storeToContext(code, stdCD, stdRetIx);
                        }
                    }
                    break;

                case Primitive:
                    if (optIx == 0) {
                        // natural return
                        box(code, optType, optCD);
                        code.areturn();
                    } else {
                        loadFromContext(code, optCD, optRetIx);
                        box(code, optType, optCD);
                        storeToContext(code, stdCD, stdRetIx);
                    }
                    break;

                case NullablePrimitive:
                    assert stdType.isNullable();

                    // if the extension is 'true', the return value is "Null", otherwise the
                    // unboxed primitive value
                    Label ifNull = code.newLabel();
                    Label endIf  = code.newLabel();

                    JitParamDesc optExt = optReturns[optIx + 1];
                    loadFromContext(code, CD_boolean, optExt.altIndex);
                    code.iconst_1()
                        .if_icmpeq(ifNull)  // if true, go to Null
                        ;

                    box(code, optType, optCD);
                    if (optIx == 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdIx);
                        code.goto_(endIf);
                    }
                    code.labelBinding(ifNull);
                    Builder.loadNull(code);

                    if (optIx == 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdIx);
                        code.labelBinding(endIf);
                    }

                    break;

                case SpecificWithDefault:
                case WidenedWithDefault:
                case PrimitiveWithDefault:
                case AlwaysNull:
                    throw new UnsupportedOperationException();
                }
            }
        });
    }

    /**
     * Assemble the "routing" method(s).
     */
    protected void assembleRoutingMethod(String className, ClassBuilder classBuilder,
                                         MethodInfo srcMethod, MethodInfo dstMethod) {
        // TODO
    }

    /**
     * Assemble the "$new" method.
     *
     * <code><pre>
     * Ecstasy:
     *      class C {...}
     *      val o = new C(x, y, z);
     * Java:
     *      C o = C.$new$17($ctx, x, y, z);
     *
     * For generic types:
     * Ecstasy:
     *      class C<Element> {...}
     *      val o = new C<A>(x, y, z);
     * Java:
     *      C o = C.$new$17($ctx, $type, x, y, z);
     * where TC is a TypeConstant for the actual type C<A>.
     *
     * For singletons, referencing the instance of the singleton for the first time causes it to be
     * created using the well-known "Java singleton pattern" that leverages the Java ClassLoader
     * and memory model guarantees to ensure that only one instance is created, and that accessing
     * that instance (and making sure it exists) is "free" for all users of the singleton -- other
     * than very first time it is referenced. Since the ClassLoader (invoking the static
     * initializer) instantiates the singleton, there is no "$new()" static method; instead,
     * singletons have a non-static "$init()" method that is signature-wise identical to the
     * "$new()" method and performs everything in the following list of steps starting with step 4.
     *
     * public static C $new$17(Ctx $ctx, X x, Y y, Z z)
     * or
     * public static C $new$17(Ctx $ctx, TC $type, X x, Y y, Z z)
     *    // note: singletons use this signature instead:
     *    public C $init$17(Ctx ctx)
     *
     *    // step 1: ONLY FOR "anonymous inner classes": a "wrapper constructor" (in lieu
     *    //   of steps 3 and 5) (TODO GG)
     *
     *    // step 2: get permission to use the memory
     *    // note: singletons move this step to the Java static initializer
     *    ctx.alloc(32); // the RAM size is calculated by the TypeInfo or compiler
     *
     *    // step 3 (initializer) gets handled in the Java constructor for the class
     *    // - the constructor needs the context for any allocations
     *    // - this inits any fields that are not supposed to be null (reference
     *    //   types) or not supposed to be 0 etc. (primitive types)
     *    // note: singletons move this step to the Java static initializer
     *    C thi$ = new C(ctx);
     *
     *    // step 3a (optional) if the type is specified, assign the "$type" field
     *    thi$.$type = $type;
     *
     *    // step 4: a constructor context is required if a “finally” chain will exist
     *    CtorCtx cctx = ctx.ctorCtx();
     *
     *    // step 5: annotation constructors are executed
     *    // (TODO GG / to discuss)
     *
     *    // step 6: the constructor gets called
     *    // - we do NOT have to save off the arguments for the `finally()` call
     *    //   in the constructor context because we have the arguments right here
     *    // note: cctx argument is optional
     *    construct$17(ctx, cctx, thi$, x, y, z);
     *
     *    // step 7: `assert()` is called (may be one on each constituent piece -- e.g.
     *    // base classes, mixins, annotations -- of the composition)
     *    // - there is no assert method if this class has no assert() defined and
     *    //   none of its supers has an assert() defined
     *    // - each calls its super first (TODO GG verify)
     *    // - TODO GG define mixin and annotation assert() order
     *    //   - depending on ordering requirements this could be a method (not a
     *    //     "static")
     *    $assert();
     *
     *    // step 8: all fields not marked as @Unassigned (which includes @Lazy property
     *    //    fields) are checked for being unassigned
     *    // - a field is unassigned iff it is a reference with the value Java `null`
     *    // - TODO GG can fields on B be overridden as @Unassigned on D? i.e. if a
     *    //   field is ok to be not unassigned, is that true for all super and sub
     *    //   classes?
     *    $verify();
     *
     *    // step 9: post construction work e.g. const freezing
     *    //    TODO GG define
     *
     *    // step 10: any `finally {...}` methods corresponding to constructors
     *    // from #3 and #4 are executed, in reverse order of what the constructors
     *    // were called in
     *    // note: cctx argument is optional
     *    finally$17(ctx, cctx, thi$, x, y, z);
     * }
     *
     * // assuming C extends B and B has a finally that takes some (P p, Q q):
     * public static construct$17(CtorCtx cctx, C thi$, X x, Y y, Z z) {
     *    // user logic before the `construct B(p,q);`
     *    P p;
     *    Q q;
     *    // ...
     *
     *    // this is the translation of the call to `construct B(p,q);`
     *    cctx.push(p);
     *    cctx.push(q);
     *    B.construct$17(cctx, thi$, p, q);
     *
     *    // user logic after the `construct B(p,q);`
     *    // ...
     * }
     *
     * public static finally$17(CtorCtx cctx, C thi$, X x, Y y, Z z) {
     *    // run the `finally` on B before running the `finally` on D
     *    // (we saved off the args in the constructor .. it's basically
     *    // a hand-rolled closure)
     *    {
     *    Q q = cctx.pop();
     *    P p = cctx.pop();
     *    B.finally$17(cctx, thi$, p, q);
     *    }
     *
     *    // user logic from C.finally goes here
     *    // ...
     * }
     * </pre></code>
     */
    protected void assembleNew(String className, ClassBuilder classBuilder, MethodInfo constructor,
                               String jitName, JitMethodDesc jmd) {
        boolean   isSingleton = typeInfo.isSingleton();
        boolean   hasType     = typeInfo.hasGenericTypes();
        ClassDesc CD_this     = ClassDesc.of(className);

        // Note: the "$init" is a virtual method for singletons and "$new" is static otherwise
        //       (see assembleStaticInitializer)
        int flags = ClassFile.ACC_PUBLIC;
        if (!isSingleton) {
            flags |= ClassFile.ACC_STATIC;
        }

        MethodTypeDesc md;
        if (jmd.isOptimized) {
            assembleMethodWrapper(className, classBuilder, jitName, jmd, true, false);
            jitName += OPT;
            md = jmd.optimizedMD;
        } else {
            md = jmd.standardMD;
        }

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            Label startScope = code.newLabel();
            Label endScope   = code.newLabel();

            code.labelBinding(startScope);

            int ctxSlot    = code.parameterSlot(0);
            int extraSlots = 1;
            code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);

            int typeSlot = -1;
            if (hasType) {
                typeSlot = code.parameterSlot(1);
                extraSlots++;
            }

            // for singleton classes the steps 0-2 are performed by the static initializer;
            // see "assembleStaticInitializer()"
            int thisSlot;
            if (isSingleton) {
                thisSlot = 0;
            } else {
                // step 1: TODO

                // step 2: get permission to use the memory
                code.aload(ctxSlot)
                    .ldc(implSize)
                    .invokevirtual(CD_Ctx, "alloc", MethodTypeDesc.of(CD_void, CD_long));

                // step 3: (initializer)
                thisSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.localVariable(thisSlot, "thi$", CD_this, startScope, endScope);
                invokeDefaultConstructor(code, CD_this);
                code.astore(thisSlot);

                if (hasType) {
                    code.aload(thisSlot)
                        .aload(typeSlot)
                        .putfield(CD_this, "$type", CD_TypeConstant);
                }
            }

            // steps 4: a constructor context is required if a “finally” chain will exist
            //          (assume true for now)
            // CtorCtx cctx = ctx.ctorCtx();

            int cctxSlot = code.allocateLocal(TypeKind.REFERENCE);
            code.localVariable(cctxSlot, "cctx", CD_CtorCtx, startScope, endScope);
            code.aload(ctxSlot)
                .invokevirtual(CD_Ctx, "ctorCtx", MethodTypeDesc.of(CD_CtorCtx))
                .astore(cctxSlot)
            ;

            // step 6: call the constructor
            // construct$17(ctx, cctx, [type], thi$, x, y, z);
            String        ctorName = constructor.ensureJitMethodName(typeSystem);
            JitMethodDesc ctorDesc = constructor.getJitDesc(typeSystem, typeInfo.getType());

            code.aload(ctxSlot)
                .aload(cctxSlot)
                .aload(thisSlot);

            // if this "new$" is optimized, the underlying constructor is optimized and vice versa
            assert jmd.isOptimized == ctorDesc.isOptimized;

            JitParamDesc[] ctorPds;
            MethodTypeDesc ctorMd;
            if (jmd.isOptimized) {
                ctorName += OPT;
                ctorPds   = ctorDesc.optimizedParams;
                ctorMd    = ctorDesc.optimizedMD;
            } else {
                ctorPds = ctorDesc.standardParams;
                ctorMd  = ctorDesc.standardMD;
            }

            for (int i = 0, c = ctorPds.length; i < c; i++) {
                JitParamDesc pd = ctorPds[i];
                load(code, pd.cd, code.parameterSlot(extraSlots + i));
            }

            code.invokestatic(CD_this, ctorName, ctorMd);

            // step 7, 8, 9, 10: TODO

            code.labelBinding(endScope)
                .aload(thisSlot)
                .areturn();
        });
    }

    /**
     * Assemble the property accessor (optimized if possible, standard otherwise).
     */
    protected void assemblePropertyAccessor(String className, ClassBuilder classBuilder,
                                            PropertyInfo prop, String jitName, MethodTypeDesc md,
                                            boolean isOptimized, boolean isGetter) {
        int flags = ClassFile.ACC_PUBLIC;
        if (prop.isAbstract()) {
            flags |= ClassFile.ACC_ABSTRACT;
        }

        BuildContext bctx = new BuildContext(this, className, typeInfo, prop, isGetter);

        classBuilder.withMethod(jitName, md, flags,
            methodBuilder -> {
                if (!prop.isAbstract()) {
                    methodBuilder.withCode(code -> generateCode(md, bctx, code));
                }
            }
        );
    }

    /**
     * Assemble the method (optimized if possible, standard otherwise).
     */
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, JitMethodDesc jmd) {
        MethodTypeDesc md;
        if (jmd.isOptimized) {
            assembleMethodWrapper(className, classBuilder, jitName, jmd,
                    method.isFunction(), method.isCtorOrValidator());
            jitName += OPT;
            md = jmd.optimizedMD;
        } else {
            md = jmd.standardMD;
        }

        int flags = ClassFile.ACC_PUBLIC;
        if (method.isAbstract()) {
            flags |= ClassFile.ACC_ABSTRACT;
        }
        if (method.isFunction() || method.isCtorOrValidator()) {
            if (classStruct.getFormat() == Format.INTERFACE) {
                // this must be a funky interface method; just ignore
                return;
            }

            flags |= ClassFile.ACC_STATIC;
        }

        BuildContext bctx = new BuildContext(this, className, typeInfo, method);

        classBuilder.withMethod(jitName, md, flags, methodBuilder -> {
            if (!method.isAbstract()) {
                methodBuilder.withCode(code -> generateCode(md, bctx, code));
            }
        });

        BuildContext bctxDeferred = bctx.getDeferred();
        while (bctxDeferred != null) {
            BuildContext    bctxNext     = bctxDeferred;
            MethodStructure methodStruct = bctxNext.methodStruct;
            boolean         fStatic      = methodStruct.isStatic();

            if (extraMethods.add(methodStruct.getIdentityConstant())) {
                JitMethodDesc  jmdNext  = bctxNext.methodDesc;
                boolean        isOpt    = jmdNext.isOptimized;
                MethodTypeDesc mdNext   = isOpt ? jmdNext.optimizedMD : jmdNext.standardMD;
                String         nameNext = bctxNext.methodJitName;
                if (isOpt) {
                    assembleMethodWrapper(className, classBuilder, nameNext, jmdNext, fStatic, false);
                    nameNext += OPT;
                }

                int flagsNext =  fStatic ? flags | ClassFile.ACC_STATIC : flags;

                classBuilder.withMethodBody(nameNext, mdNext, flagsNext,
                    code -> generateCode(mdNext, bctxNext, code));
            }
            bctxDeferred = bctxNext.getDeferred();
        }
    }

    protected void generateCode(MethodTypeDesc md, BuildContext bctx, CodeBuilder code) {

        String moduleName = thisId.getModuleConstant().getName();
        if (Arrays.stream(TEST_SET).anyMatch(name ->
                    bctx.className.contains(name) || moduleName.contains(name))) {
            bctx.assembleCode(code);
        } else {
            if (SKIP_SET.add(bctx.className)) {
                System.err.println("*** Skipping code gen for " + bctx.className);
            }
            defaultLoad(code, md.returnType());
            addReturn(code, md.returnType());
        }
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * @return true iff the code for the specified method or property accessors should be generated
     *         inside this class
     */
    private boolean shouldGenerate(IdentityConstant id) {
        IdentityConstant containerId = id.getNamespace();
        if (containerId.equals(thisId)) {
            return true;
        }

        Format ownerFormat = containerId.getComponent().getFormat();
        return ownerFormat == Format.ANNOTATION || ownerFormat == Format.MIXIN;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString() {
        return thisId.getValueString();
    }

    private final static String[] TEST_SET = new String[] {
        "Test", "test",
        "IOException", "OutOfBounds", "Unsupported", "IllegalArgument", "IllegalState",
        "Boolean", "Ordered",
//        "Array",
        "TerminalConsole",
    };
    private final static HashSet<String> SKIP_SET = new HashSet<>();
}