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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.Ctx;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.ModuleLoader;
import org.xvm.javajit.NativeTypeSystem;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TypeSystem;

import org.xvm.javajit.registers.ExtendedSlot;
import org.xvm.javajit.registers.MultiSlot;

import org.xvm.util.ShallowSizeOf;

import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import static org.xvm.javajit.JitFlavor.NullablePrimitive;
import static org.xvm.javajit.JitFlavor.NullableXvmPrimitive;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        extends Builder {
    public CommonBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem);

        assert type.isSingleUnderlyingClass(true) &&
              !type.containsFormalType(true) &&
              !type.isAccessSpecified();

        this.thisType    = type.ensureAccess(Access.PRIVATE);
        this.typeInfo    = thisType.ensureTypeInfo();
        this.structInfo  = thisType.ensureAccess(Access.STRUCT).ensureTypeInfo();
        this.classStruct = typeInfo.getClassStructure();
        this.formalType  = classStruct.getFormalType().ensureAccess(Access.PRIVATE);
        this.formalInfo  = formalType.ensureTypeInfo();
        this.thisId      = classStruct.getIdentityConstant();
        this.isInterface = classStruct.getFormat() == Format.INTERFACE;
    }

    protected final TypeConstant     thisType;      // PRIVATE
    protected final TypeInfo         typeInfo;      // PRIVATE
    protected final TypeInfo         structInfo;
    protected final ClassStructure   classStruct;
    protected final TypeConstant     formalType;    // PRIVATE
    protected final TypeInfo         formalInfo;    // PRIVATE
    protected final IdentityConstant thisId;
    protected final boolean          isInterface;

    /**
     * The shallow size of object in bytes.
     */
    protected long implSize;

    /**
     * List of constant properties for every class name this builder assembles.
     *
     * Note: a vast majority of builders assemble one and only one class.
     */
    protected Map<String, List<PropertyInfo>> constProperties = new HashMap<>(1);

    /**
     * Registry of TypeConstant objects used by the code generator; for each class name this
     * builder assembles, the (TypeConstant, Integer) entry represents the suffix ("$typeN") of a
     * synthetic static property holding the corresponding TypeConstant object.
     *
     * Note: a vast majority of builders assemble one and only one class.
     */
    protected Map<String, Map<TypeConstant, Integer>> typeConstants = new HashMap<>(1);

    /**
     * Methods that were added during the compilation. They are either nested in properties/methods
     * or methods declared on the mixins or annotations that were added to the "impl" class.
     */
    protected final Set<IdentityConstant> extraMethods = new HashSet<>();

    /**
     * TEMPORARY: compensation for TypeInfo dupes.
     */
    protected final Set<String> methodNames = new HashSet<>();

    @Override
    public void assembleImpl(String className, ClassBuilder classBuilder) {
        implSize = ShallowSizeOf.align(computeInstanceSize());

        // prime the type registry with "this" type
        Map<TypeConstant, Integer> types = new HashMap<>();
        types.put(thisType, 0);
        typeConstants.put(className, types);

        if (assembleImplClass(className, classBuilder)) {
            assembleImplProperties(className, classBuilder);
            assembleImplMethods(className, classBuilder);

            // static initializer must be assembled at the very end, after all potentially used
            // TypeConstants have been collected
            assembleStaticInitializer(className, classBuilder);
        }
    }

    @Override
    public void assemblePure(String className, ClassBuilder classBuilder) {
        // assemblePureClass(className, classBuilder);
        // assemblePureProperties(className, classBuilder);
        // assemblePureMethods(className, classBuilder);
    }

    @Override
    public TypeConstant getThisType() {
        return thisType;
    }

    @Override
    protected void loadTypeConstant(CodeBuilder code, String className, TypeConstant type) {
        Map<TypeConstant, Integer> types =
            typeConstants.computeIfAbsent(className, _ -> new HashMap<>());

        Integer index = types.computeIfAbsent(type, _ -> types.size());

        // see assembleStaticInitializer()
        ClassDesc CD_this = ClassDesc.of(className);
        code.getstatic(CD_this, "$type" + index, CD_TypeConstant);
    }

    /**
     * Compute the ClassDesc for the super class.
     */
    protected ClassDesc getSuperCD() {
        TypeConstant superType = typeInfo.getExtends();
        return superType == null
            ? CD_nObj
            : ensureClassDesc(superType);
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
        ClassDesc    cd   = type.isJavaPrimitive()
                ? JitTypeDesc.getPrimitiveClass(type)
                : null;

        return cd == null
            ? ShallowSizeOf.fieldOf(Object.class)
            : ShallowSizeOf.fieldOf(cd);

    }

    /**
     * Assemble the class specific info for the "Impl" shape.
     *
     * @return false iff no more assembly required for this class
     */
    protected boolean assembleImplClass(String className, ClassBuilder classBuilder) {
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
                return false;

            default:
                // TODO: support for mixin, annotations, etc
                throw new RuntimeException("Not implemented " + thisType);
        }
        classBuilder.withFlags(flags);

        assembleImplInterfaces(classBuilder);
        return true;
    }

    /**
     * Assemble interfaces for the "Impl" shape.
     */
    protected void assembleImplInterfaces(ClassBuilder classBuilder) {
        List<ClassDesc> interfaces  = new ArrayList<>();
        for (Contribution contrib : typeInfo.getContributionList()) {
            switch (contrib.getComposition()) {
                case Implements:
                    TypeConstant contribType = contrib.getTypeConstant().removeAccess();
                    if  (shouldAddInterface(contribType)) {
                        interfaces.add(ensureClassDesc(contribType));
                    }
                    break;
            }
        }
        if (!interfaces.isEmpty()) {
            classBuilder.withInterfaceSymbols(interfaces);
        }
    }

    /**
     * @return true iff we should add the specified type to the list of interfaces
     *         implemented/exteneded by this class
     */
    protected boolean shouldAddInterface(TypeConstant type) {
        // ignore "implements Object" for classes
        return this.isInterface || !type.equals(pool().typeObject());
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

        if (typeInfo.isSingleton()) {
            // public static final $INSTANCE;
            classBuilder.withField(Instance, ClassDesc.of(className),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
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

        // save off the constant properties list to be added to the static initializer
        constProperties.put(className, constProps);
    }

    /**
     * Assemble the field(s) for the specified Ecstasy property.
     */
    protected void assembleField(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String      jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
        JitTypeDesc jtd     = prop.getType().getJitDesc(this);

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

        case XvmPrimitive:
        case NullableXvmPrimitive:
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(prop.getType());
            assert cds != null && cds.length > 0;
            for (int i = 0; i < cds.length; i++) {
                classBuilder.withField(jitName+ "$" + i, cds[i], flags);
            }
            if (jtd.flavor == NullableXvmPrimitive) {
                classBuilder.withField(jitName + EXT, CD_boolean, flags);
            }
            break;

        default:
            throw new IllegalStateException("Unsupported property flavor: " + jtd.flavor);
        }
    }

    /**
     * Add constant fields (including synthetic TypeConstant fields) initialization to the static
     * initializer.
     */
    protected void assembleStaticInitializer(String className, ClassBuilder classBuilder) {
        List<PropertyInfo>         props = constProperties.getOrDefault(className, Collections.emptyList());
        Map<TypeConstant, Integer> types = typeConstants.getOrDefault(className, Collections.emptyMap());

        // add synthetic TypeConstant fields
        for (int i = 0, c = types.size(); i < c; i++) {
            classBuilder.withField("$type" + i, CD_TypeConstant,
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }

        ClassDesc CD_this = ClassDesc.of(className);

        classBuilder.withMethodBody(ConstantDescs.CLASS_INIT_NAME, MTD_void,
                ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC, code -> {
            Label startScope = code.newLabel();
            Label endScope   = code.newLabel();
            code.labelBinding(startScope);

            int ctxSlot = code.allocateLocal(TypeKind.REFERENCE);
            if (isDebugInfo()) {
                code.localVariable(ctxSlot, "ctx", CD_Ctx, startScope, endScope);
            }
            code.invokestatic(CD_Ctx, "get", MethodTypeDesc.of(CD_Ctx))
                .astore(0);

            // add static field initialization
            TypeSystem ts = typeSystem;
            for (PropertyInfo prop : props) {
                if (prop.getInitializer() == null) {
                    RegisterInfo reg     = loadConstant(code, prop.getInitialValue());
                    String       jitName = prop.getIdentity().ensureJitPropertyName(ts);
                    if (reg instanceof ExtendedSlot extSlot) {
                        assert extSlot.flavor() == NullablePrimitive;
                        // loadConstant() has already loaded the value and the boolean
                        Label ifTrue = code.newLabel();
                        Label endIf  = code.newLabel();
                        code.ifne(ifTrue)
                            .putstatic(CD_this, jitName + EXT, CD_boolean)
                            .goto_(endIf)
                            .labelBinding(ifTrue);
                        pop(code, extSlot.cd());
                        code.putstatic(CD_this, jitName, reg.cd());
                        code.labelBinding(endIf);
                    } else if (reg instanceof MultiSlot multiSlot) {
                        ClassDesc[] cds = multiSlot.slotCds();
                        for (int i = cds.length - 1; i >= 0; i--) {
                            code.putstatic(CD_this, jitName + "$" + i, cds[i]);
                        }
                        if (multiSlot.flavor() == NullableXvmPrimitive) {
                            Label ifTrue = code.newLabel();
                            Label endIf = code.newLabel();
                            code.ifne(ifTrue)
                                    .putstatic(CD_this, jitName + EXT, CD_boolean)
                                    .goto_(endIf)
                                    .labelBinding(ifTrue);
                            for (ClassDesc cd : cds) {
                                pop(code, cd);
                            }
                            code.putstatic(CD_this, jitName, reg.cd());
                            code.labelBinding(endIf);
                        }
                    } else {
                        assert reg.isSingle();
                        code.putstatic(CD_this, jitName, reg.cd());
                    }
                } else {
                    throw new UnsupportedOperationException("Static field initializer for " +
                        prop.getIdentity().getValueString());
                }
            }

            // initialize synthetic TypeConstant fields; to make the jasm look neater
            // generate the assignments in the lexicographical order
            ModuleLoader loader   = typeSystem.findOwnerLoader(className);
            boolean      nativeTS = typeSystem instanceof NativeTypeSystem;
            ConstantPool pool     = loader.module.getConstantPool();
            types.entrySet().stream()
                 .sorted(Map.Entry.comparingByValue())
                 .forEach(entry -> {
                    TypeConstant type = entry.getKey();
                    String       name = "$type" + entry.getValue();

                    assert type.isShared(pool);
                    type = pool.register(type);

                    int index = type.getPosition();
                    if (nativeTS) {
                        index = -index;
                    }
                    code.aload(ctxSlot)
                        .loadConstant(className)
                        .loadConstant(index)
                        .invokevirtual(CD_Ctx, "getConstant", Ctx.MD_getConstant) // <- const
                        .checkcast(CD_TypeConstant)                               // <- type
                        .putstatic(CD_this, name, CD_TypeConstant);
                 });

            if (typeInfo.isSingleton()) {
                // $INSTANCE = new Singleton($ctx);
                // $ctx.allocated(implSize);
                // $INSTANCE.$init($ctx);
                MethodConstant ctorId  = typeInfo.findConstructor();
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
        });
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
                if (isDebugInfo()) {
                    code.localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, startScope, endScope);
                }

                callSuperInitializer(code, className);

                // add field initialization
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        code.aload(0); // Stack: { this }

                        TypeConstant type       = prop.getType();
                        TypeConstant baseType   = type.removeNullable();
                        ClassDesc    cdProp     = type.ensureClassDesc(typeSystem);
                        RegisterInfo reg        = loadConstant(code, prop.getInitialValue());
                        String       jitName    = prop.getIdentity()
                                                      .ensureJitPropertyName(typeSystem);
                        JitFlavor    regFlavor  = reg.flavor();
                        JitFlavor    propFlavor = baseType.isJavaPrimitive()
                                                    ? JitFlavor.Primitive
                                                    : baseType.isXvmPrimitive()
                                                        ? JitFlavor.XvmPrimitive
                                                        : JitFlavor.Specific;

                        // Switch on the register flavor (the value being set into the property)
                        // and then switch on the property flavor
                        //
                        // Specific     -> Specific     e.g. String s = "Foo" or String? s = Null
                        // Specific     -> Primitive    must be setting primitive property to Null
                        // Specific     -> XvmPrimitive must be setting primitive property to Null
                        // Primitive    -> Primitive    e.g. Int = 100
                        // Primitive    -> Specific     e.g. Int | String is = 100
                        // XvmPrimitive -> XvmPrimitive e.g. Int128 = 100
                        // XvmPrimitive -> Specific     e.g. Int128 | String is = 100

                        boolean invalid = false;
                        switch (reg.flavor()) {
                            case Specific:
                                switch (propFlavor) {
                                case Specific:
                                    code.putfield(CD_this, jitName, cdProp);
                                    break;

                                case Primitive:
                                    // must be setting a primitive to Null
                                    assert reg.type().isOnlyNullable();
                                    code.pop();
                                    ClassDesc cd = JitTypeDesc.getPrimitiveClass(baseType);
                                    Builder.defaultLoad(code, cd);
                                    code.putfield(CD_this, jitName, cd)
                                        .aload(0)
                                        .iconst_1()
                                        .putfield(CD_this, jitName + EXT, CD_boolean);
                                    break;

                                case XvmPrimitive:
                                    // must be setting a XVM primitive to Null
                                    assert reg.type().isOnlyNullable();
                                    code.pop();
                                    ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(baseType);
                                    for (int i = 0; i < cds.length; i++) {
                                        Builder.defaultLoad(code, cds[i]);
                                        code.putfield(CD_this, jitName + "$" + i, cds[i])
                                            .aload(0);
                                    }
                                    code.iconst_1()
                                        .putfield(CD_this, jitName + EXT, CD_boolean);
                                    break;

                                default:
                                    invalid = true;
                                }
                                break;

                            case Primitive:
                                switch (propFlavor) {
                                case Primitive:
                                    code.putfield(CD_this, jitName, reg.cd());
                                    break;

                                case Specific:
                                    Builder.box(code, reg);
                                    code.putfield(CD_this, jitName, cdProp);
                                    break;

                                default:
                                    invalid = true;
                                }
                                break;

                            case XvmPrimitive:
                                switch (propFlavor) {
                                case XvmPrimitive:
                                    ClassDesc[] cds = reg.slotCds();
                                    for (int i = cds.length - 1; i >= 0; i--) {
                                        code.aload(0) // Stack: { this }
                                            .dup_x2()
                                            .pop()
                                            .putfield(CD_this, jitName + "$" + i, cds[i]);
                                    }
                                    code.pop(); // pop the extra "aload(0)" from the stack
                                    break;

                                case Specific:
                                    Builder.box(code, reg);
                                    code.putfield(CD_this, jitName, cdProp);
                                    break;

                                default:
                                    invalid = true;
                                }
                                break;

                            default:
                                invalid = true;
                        }

                        if (invalid) {
                            throw new IllegalStateException("Invalid register flavor: " + regFlavor +
                                " for property: " + propFlavor);
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
    protected void callSuperInitializer(CodeBuilder code, String className) {
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
            } else if (prop.isAbstract()) {
                assemblePropertyGetter(className, classBuilder, prop);
            }
        } else if (prop.getHead().hasGetter()) {
            switch (getterInfo.getHead().getImplementation()) {
                case Field:
                    generateTrivialGetter(className, classBuilder, prop);
                    break;
                case Explicit, Default:
                    assemblePropertyGetter(className, classBuilder, prop);
                    break;
                }
            }

        MethodInfo setterInfo = typeInfo.getMethodById(prop.getSetterId());
        if (setterInfo == null) {
            if (prop.hasField() && shouldGenerate(prop.getFieldIdentity())) {
                generateTrivialSetter(className, classBuilder, prop);
            } else if (prop.isAbstract()) {
                assemblePropertySetter(className, classBuilder, prop);
            }
        } else {
            switch (getterInfo.getHead().getImplementation()) {
            case Field:
                generateTrivialSetter(className, classBuilder, prop);
                break;

            case Explicit, Default:
                assemblePropertySetter(className, classBuilder, prop);
                break;
            }
        }
    }

    private void assemblePropertyGetter(String className, ClassBuilder classBuilder,
                                        PropertyInfo prop) {
        String         jitName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc  jmDesc  = prop.getGetterJitDesc(this);
        boolean        isOpt   = jmDesc.isOptimized;
        MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
        if (isOpt) {
            jitName += OPT;
        }
        assemblePropertyAccessor(className, classBuilder, prop, jitName, md, isOpt, true);
    }

    private void assemblePropertySetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String         jitName = prop.ensureSetterJitMethodName(typeSystem);
        JitMethodDesc  jmDesc  = prop.getSetterJitDesc(this);
        boolean        isOpt   = jmDesc.isOptimized;
        MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
        if (isOpt) {
            jitName += OPT;
        }
        assemblePropertyAccessor(className, classBuilder, prop, jitName, md, isOpt, false);
    }

    protected void generateTrivialGetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitGetterName = prop.ensureGetterJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

        ClassDesc CD_this = ClassDesc.of(className);
        int       flags   = ClassFile.ACC_PUBLIC;
        if (prop.isConstant()) {
            flags |= ClassFile.ACC_STATIC;
        }
        JitMethodDesc  jmd     = prop.getGetterJitDesc(this);
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
                            .aload(0)
                            .getfield(CD_this, jitFieldName+EXT, CD_boolean);
                    }
                    storeToContext(code, CD_boolean, 1);
                    addReturn(code, cdOpt);
                    break;

                case XvmPrimitive:
                case NullableXvmPrimitive:
                    TypeConstant type = prop.getType();
                    ClassDesc[]  cds  = JitTypeDesc.getXvmPrimitiveClasses(type);
                    int          slot = 0;
                    if (prop.isConstant()) {
                        code.getstatic(CD_this, jitFieldName + "$0", cds[0]);
                        for (int i = 1; i < cds.length; i++) {
                            code.getstatic(CD_this, jitFieldName + "$" + i, cds[i]);
                            storeToContext(code, cds[i], slot++);
                        }
                        if (pdOpt.flavor == NullableXvmPrimitive) {
                            code.getstatic(CD_this, jitFieldName + EXT, CD_boolean);
                            storeToContext(code, CD_boolean, slot);
                        }
                    } else {
                        code.aload(0).getfield(CD_this, jitFieldName + "$0", cds[0]);
                        for (int i = 1; i < cds.length; i++) {
                            code.aload(0).getfield(CD_this, jitFieldName + "$" + i, cds[i]);
                            storeToContext(code, cds[i], slot++);
                        }
                        if (pdOpt.flavor == NullableXvmPrimitive) {
                            code.aload(0).getfield(CD_this, jitFieldName + EXT, CD_boolean);
                            storeToContext(code, CD_boolean, slot);
                        }
                    }
                    addReturn(code, cds[0]);
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
                    prop.isConstant());
        }
    }

    protected void generateTrivialSetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitSetterName = prop.ensureSetterJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

        ClassDesc CD_this = ClassDesc.of(className);
        int       flags   = ClassFile.ACC_PUBLIC;
        if (prop.isConstant()) {
            flags |= ClassFile.ACC_STATIC;
        }
        JitMethodDesc  jmd     = prop.getSetterJitDesc(this);
        boolean        isOpt   = jmd.isOptimized;
        MethodTypeDesc md      = isOpt ? jmd.optimizedMD : jmd.standardMD;
        String         jitName = isOpt ? jitSetterName+OPT : jitSetterName;

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            int argSlot = code.parameterSlot(1); // compensate for $ctx
            if (isOpt) {
                JitParamDesc pdOpt   = jmd.optimizedParams[0];
                ClassDesc    cdOpt   = pdOpt.cd;
                int          extSlot = argSlot + toTypeKind(cdOpt).slotSize();

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
                    if (prop.isConstant()) {
                        load(code, cdOpt, argSlot);
                        code.putstatic(CD_this, jitFieldName, cdOpt)
                            .iload(extSlot)
                            .putstatic(CD_this, jitFieldName+EXT, CD_boolean);
                    } else {
                        code.aload(0);
                        load(code, cdOpt, argSlot);
                        code.putfield(CD_this, jitFieldName, cdOpt)
                            .aload(0)
                            .iload(extSlot)
                            .putfield(CD_this, jitFieldName+EXT, CD_boolean);
                    }
                    break;

                case XvmPrimitive:
                    for (int i = 0; i < jmd.optimizedParams.length; i++) {
                        JitParamDesc param     = jmd.optimizedParams[i];
                        int          slotSize  = toTypeKind(param.cd).slotSize();
                        String       fieldName = jitFieldName + "$" + i;
                        if (prop.isConstant()) {
                            load(code, param.cd, argSlot);
                            code.putstatic(CD_this, fieldName, param.cd);
                        } else {
                            code.aload(0);
                            load(code, param.cd, argSlot);
                            code.putfield(CD_this, fieldName, param.cd);
                        }
                        argSlot += slotSize;
                    }
                    break;

                case NullableXvmPrimitive:
                    for (int i = 0; i < jmd.optimizedParams.length - 1; i++) {
                        JitParamDesc param     = jmd.optimizedParams[i];
                        int          slotSize  = toTypeKind(param.cd).slotSize();
                        String       fieldName = jitFieldName + "$" + i;
                        if (prop.isConstant()) {
                            load(code, param.cd, argSlot);
                            code.putstatic(CD_this, fieldName, param.cd);
                        } else {
                            code.aload(0);
                            load(code, param.cd, argSlot);
                            code.putfield(CD_this, fieldName, param.cd);
                        }
                        argSlot += slotSize;
                    }
                    if (prop.isConstant()) {
                        code.iload(argSlot)
                                .putstatic(CD_this, jitFieldName+EXT, CD_boolean);
                    } else {
                        code.aload(0)
                                .iload(argSlot)
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
                    prop.isConstant());
        }
    }

    /**
     * Assemble the generic property accessors for the "Impl" shape.
     */
    private void assembleGenericProperty(ClassBuilder classBuilder, String name) {
        classBuilder.withMethodBody(name + "$get", MethodTypeDesc.of(CD_nType, CD_Ctx),
            ClassFile.ACC_PUBLIC, code ->
                // return nObj.$type(ctx, name);
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

        JitMethodDesc  jmd     = prop.getGetterJitDesc(this);
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
            // if (value == null) { value = this.prop = $ctx.inject(type, name, opts);}
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

                case XvmPrimitive:
                case NullableXvmPrimitive:
                    throw new UnsupportedOperationException("XVM Primitive injection");

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
                loadTypeConstant(code, className, resourceType);
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
            assembleMethodWrapper(className, classBuilder, jitGetterName, jmd, false);
        }
    }

    /**
     * Assemble methods for the "Impl" shape.
     */
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        boolean assembleDeclared = !typeInfo.isAbstract();

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

        if (!isInterface) {
            assembleXvmType(className, classBuilder);
        }

        if (typeInfo.getFormat() == Format.CONST) {
            assembleConstMethods(className, classBuilder);
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

        ClassDesc CD_this = ClassDesc.of(className);
        classBuilder.withMethodBody("$xvmType", MD_xvmType,
                ClassFile.ACC_PUBLIC, code -> {
            if (hasType) {
                // the field is initialized in assembleNew()
                code.aload(0)
                    .getfield(CD_this, "$type", CD_TypeConstant);
            } else {
                // the static field is initialized in assembleStaticInitializer()
                code.getstatic(CD_this, "$type0", CD_TypeConstant);
            }
            code.areturn();
        });
    }

    /**
     * Assemble the method(s) for the "Impl" shape of the specified Ecstasy method.
     */
    protected void assembleImplMethod(String className, ClassBuilder classBuilder, MethodInfo method) {
        if (method.isCapped()) {
            MethodInfo targetMethod = typeInfo.getNarrowingMethod(method);
            assert targetMethod != null;
            assembleCapRouting(className, classBuilder, method, targetMethod);
        } else if (method.isDelegating()) {
            PropertyConstant propDelegate = method.getHead().getPropertyConstant();
            assert propDelegate != null;
            assemblePropertyDelegation(className, classBuilder, method, propDelegate);
        } else {
            String jitName = method.ensureJitMethodName(typeSystem);

            // TODO REMOVE: temporary compensation for duplicates in the TypeInfo
            if (!methodNames.add(jitName)) {
                return;
            }

            JitMethodDesc jmDesc = method.getJitDesc(this);
            assembleMethod(className, classBuilder, method, jitName, jmDesc);

            if (method.isCtorOrValidator() && !typeInfo.isAbstract()) {
                String        newName = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
                JitMethodDesc newDesc = Builder.convertConstructToNew(typeInfo,
                                            ClassDesc.of(className), (JitCtorDesc) jmDesc);
                assembleNew(className, classBuilder, method, newName, newDesc);
            }
        }
    }

    /**
     * Assemble a "standard" wrapper method for the optimized method.
     */
    protected void assembleMethodWrapper(String className, ClassBuilder classBuilder,
                                         String jitName, JitMethodDesc jmDesc, boolean isStatic) {
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
                        unbox(code, stdParamType);
                        break;

                    case PrimitiveWithDefault: {
                        // if the argument is Java `null`, pass the default value for the type and
                        // `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code.aload(stdParamSlot)
                            .ifnonnull(ifNotNull);
                        // the value is `null`
                        Builder.defaultLoad(code, optParamDesc.cd); // default primitive
                        code.iconst_1();                            // true

                        code.goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot);
                        unbox(code, stdParamType); // unwrapped primitive
                        code.iconst_0();           // false

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

                        code.goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot)
                            .checkcast(ensureClassDesc(primitiveType));
                        unbox(code, primitiveType); // unboxed primitive
                        code.iconst_0();            // false

                        code.labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case NullablePrimitiveWithDefault: {
                        assert stdParamType.isNullable();
                        TypeConstant primitiveType = stdParamType.removeNullable();
                        // if the argument is Ecstasy `Null`, pass the default value for the type
                        // and `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotJavaNull = code.newLabel();
                        Label ifNotXvmNull  = code.newLabel();
                        Label endIf         = code.newLabel();

                        code.aload(stdParamSlot)
                             .aconst_null()
                             .if_acmpne(ifNotJavaNull);
                        // the value is `null`
                        Builder.defaultLoad(code, optParamDesc.cd);  // default primitive
                        code.iconst_m1()                             // -1
                            .goto_(endIf)
                            .labelBinding(ifNotJavaNull);

                        code.aload(stdParamSlot);
                        Builder.loadNull(code);
                        code.if_acmpne(ifNotXvmNull);
                        // the value is `Null`
                        Builder.defaultLoad(code, optParamDesc.cd);  // default primitive
                        code.iconst_1()                              // +1
                            .goto_(endIf)
                            .labelBinding(ifNotXvmNull);

                        code.aload(stdParamSlot)
                            .checkcast(ensureClassDesc(primitiveType));
                        unbox(code, primitiveType); // unboxed primitive
                        code.iconst_0()             // false
                            .labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case XvmPrimitive: {
                        code.aload(stdParamSlot);
                        ClassDesc cd = JitTypeDesc.getXvmPrimitiveClass(stdParamType);
                        assert cd != null;
                        unbox(code, stdParamType);
                        // skip over the additional parameters that we have just loaded by unboxing
                        i += JitTypeDesc.getXvmPrimitiveSlotCount(stdParamType) - 1;
                        break;
                    }

                    case XvmPrimitiveWithDefault: {
                        // if the argument is Java `null`, pass the default value for the types and
                        // `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(stdParamType);

                        code.aload(stdParamSlot)
                            .ifnonnull(ifNotNull);
                        // the value is `null`
                        for (ClassDesc classDesc : cds) {
                            Builder.defaultLoad(code, classDesc);
                        }
                        code.iconst_1() // true
                            .goto_(endIf);

                        code.labelBinding(ifNotNull)
                            .aload(stdParamSlot);
                        unbox(code, stdParamType); // unwrapped primitives
                        code.iconst_0()            // false
                            .labelBinding(endIf);

                        // skip over the additional parameters that we have just loaded by unboxing
                        i += cds.length;
                        break;
                    }

                    case NullableXvmPrimitive: {
                        assert stdParamType.isNullable();

                        TypeConstant baseType = stdParamType.removeNullable();
                        ClassDesc[]  cds      = JitTypeDesc.getXvmPrimitiveClasses(baseType);

                        // if the argument is Ecstasy `Null`, pass the default value for the type
                        // and `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code.aload(stdParamSlot);
                        Builder.loadNull(code);
                        code.if_acmpne(ifNotNull);
                        // the value is `Null`
                        for (ClassDesc cd : cds) {
                            Builder.defaultLoad(code, cd);
                        }
                        code.iconst_1(); // true

                        code.goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot)
                            .checkcast(ensureClassDesc(baseType));

                        ClassDesc cd = JitTypeDesc.getXvmPrimitiveClass(baseType);
                        assert cd != null;
                        unbox(code, baseType); // unboxed primitives
                        code.iconst_0()        // false
                            .labelBinding(endIf);

                        // skip over the additional parameters that we have just loaded
                        i += cds.length;
                        break;
                    }

                    case NullableXvmPrimitiveWithDefault: {
                        TypeConstant baseType = stdParamType.removeNullable();
                        ClassDesc    baseCD   = ensureClassDesc(baseType);

                        // if the argument is Java `null`, pass default values and `-1`;
                        // if the argument is `Null`, pass default values and `+1`;
                        // otherwise the unboxed primitive value and `0`
                        Label ifNotJavaNull = code.newLabel();
                        Label ifNotXvmNull  = code.newLabel();
                        Label endIf         = code.newLabel();

                        ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(stdParamType);

                        code.aload(stdParamSlot)
                            .ifnonnull(ifNotJavaNull);
                        // the value is `null`
                        for (ClassDesc classDesc : cds) {
                            Builder.defaultLoad(code, classDesc);
                        }
                        code.iconst_m1() // -1
                            .goto_(endIf);

                        code.labelBinding(ifNotJavaNull);
                        code.aload(stdParamSlot);
                        Builder.loadNull(code);
                        code.if_acmpne(ifNotXvmNull);
                        // the value is `Null`
                        for (ClassDesc classDesc : cds) {
                            Builder.defaultLoad(code, classDesc);
                        }
                        code.iconst_1() // +1
                            .goto_(endIf);

                        code.labelBinding(ifNotXvmNull)
                            .aload(stdParamSlot)
                            .checkcast(baseCD);
                        unbox(code, baseType);
                        code.iconst_0() // false
                            .labelBinding(endIf);

                        // skip over the additional parameters that we have just loaded by unboxing
                        i += cds.length;
                        break;
                    }

                    default:
                        throw new UnsupportedOperationException("Not implemented: " + optParamDesc.flavor);
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
                int          ix       = stdIx == 0 ? 0 : stdIx--;

                JitParamDesc stdDesc  = stdReturns[ix];
                ClassDesc    stdCD    = stdDesc.cd;
                TypeConstant stdType  = stdDesc.type;
                int          stdRetIx = stdDesc.altIndex;

                JitParamDesc optExt;
                int          idx;

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
                        box(code, optType);
                        code.areturn();
                    } else {
                        loadFromContext(code, optCD, optRetIx);
                        box(code, optType);
                        storeToContext(code, stdCD, stdRetIx);
                    }
                    break;

                case NullablePrimitive: {
                    assert stdType.isNullable();

                    // if the extension is 'true', the return value is "Null", otherwise the
                    // boxed primitive value
                    Label ifNull = code.newLabel();
                    Label endIf  = code.newLabel();

                    optExt = optReturns[optIx + 1];
                    loadFromContext(code, CD_boolean, optExt.altIndex);
                    code.iconst_1()
                        .if_icmpeq(ifNull)  // if true, go to Null
                        ;

                    if (optIx == 0) {
                        // box the natural return
                        box(code, optType);
                        code.areturn();
                    } else {
                        // load the primitive to box from the context
                        loadFromContext(code, optDesc.cd, optDesc.altIndex);
                        box(code, optType);
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
                }

                case XvmPrimitive: {
                    int[] optIndexes = jmDesc.getAllOptimizedReturnIndexes(optDesc.index);
                    optIx -= optIndexes.length - 1; // skip the Opt returns we will process
                    idx =  optDesc.index == 0 ? 1 : 0;
                    for (; idx < optIndexes.length; idx++) {
                        JitParamDesc optReturn = optReturns[optIndexes[idx]];
                        loadFromContext(code, optReturn.cd, optReturn.altIndex);
                    }
                    box(code, optType);
                    if (optIx <= 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdRetIx);
                    }
                    break;
                }

                case NullableXvmPrimitive: {
                    assert stdType.isNullable();
                    // if the extension is 'true', the return value is "Null", otherwise the
                    // boxed primitive value
                    Label ifNull = code.newLabel();
                    Label endIf  = code.newLabel();

                    int[] optIndexes = jmDesc.getAllOptimizedReturnIndexes(optDesc.index);
                    optIx -= optIndexes.length - 2; // skip the Opt returns we will process
                    optExt = optReturns[optIndexes[optIndexes.length - 1]];
                    loadFromContext(code, CD_boolean, optExt.altIndex);
                    code.iconst_1()
                        .if_icmpeq(ifNull);  // if true, go to Null

                    idx = optDesc.index == 0 ? 1 : 0;
                    for (; idx < optIndexes.length - 1; idx++) {
                        JitParamDesc optReturn = optReturns[optIndexes[idx]];
                        loadFromContext(code, optReturn.cd, optReturn.altIndex);
                    }
                    box(code, optType);

                    if (optIx <= 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdIx);
                        code.goto_(endIf);
                    }
                    code.labelBinding(ifNull);
                    Builder.loadNull(code);

                    if (optIx <= 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdIx);
                        code.labelBinding(endIf);
                    }
                    break;
                }

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
     * Assemble the necessary methods for a constant type in the provided class builder.
     *
     * @param className      The name of the class being built.
     * @param classBuilder   The class builder to which methods will be added.
     */
    protected void assembleConstMethods(String className, ClassBuilder classBuilder) {
        if (!typeInfo.getType().isJitPrimitive()) {
            // we only generate equals and compare for non-primitive types
            assembleEqualsMethod(className, classBuilder);
            assembleCompareMethod(className, classBuilder);
        }
        assembleConstHashCodeMethod(className, classBuilder);
        // TODO Stringable appendTo estimateStringLength
    }

    /**
     * Determine whether the specified method exists in a template class for the current
     * {@link #typeInfo} in this builder.
     * <p>
     * This method is typically overridden in subclasses, such as the {@link AugmentingBuilder}.
     *
     * @return {@code true} if the specified method exists for the current type's template class
     */
    protected boolean isMethodOnTemplateClass(String jitName, MethodTypeDesc md) {
        return false;
    }

    /**
     * Generate the const implementation of:
     * <pre>
     *     static <CompileType extends T> Boolean equals(T value1, T value2)
     * </pre>
     * Generate the "equals", "equals$p" and possibly $equals methods for a const type if the
     * methods do not already exist.
     */
    protected void assembleEqualsMethod(String className, ClassBuilder classBuilder) {
        SignatureConstant eqSig      = pool().sigEquals();
        MethodInfo        eqMethod   = typeInfo.getMethodBySignature(eqSig);
        TypeConstant      type       = typeInfo.getType();
        Implementation    impl       = eqMethod.getHead().getImplementation();
        Constant          thisConst  = type.getDefiningConstant();
        IdentityConstant  declaredOn = eqMethod.getIdentity()
                                               .getParentConstant()
                                               .getParentConstant();

        // If the method is not explicitly implemented or the declaring type does not match the
        // current type (i.e. the method is declared on a supe class), then we can build the method
        if (impl != Implementation.Explicit || !thisConst.equals(declaredOn)) {
            TypeConstant   resolvedType = type.resolveConstraints();
            ClassDesc      cd           = ensureClassDesc(resolvedType);
            String         eqName       = eqSig.getName();
            String         eqOptName    = eqName + OPT;
            MethodTypeDesc mdWrapper    = MethodTypeDesc.of(CD_Boolean, CD_Ctx, CD_nType, cd, cd);
            MethodTypeDesc mdPrimitive  = MethodTypeDesc.of(CD_boolean, CD_Ctx, CD_nType, cd, cd);

            if (!isMethodOnTemplateClass(eqName, mdWrapper)) {
                // generate the standard "equals" wrapper that delegates to the optimized "equals$p"
                classBuilder.withMethodBody(eqName, mdWrapper,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        (code) -> {
                            loadCtx(code);
                            code.aload(1)
                                .aload(2)
                                .aload(3)
                                .invokestatic(cd, eqOptName, mdPrimitive);
                            Builder.box(code, pool().typeBoolean());
                            code.areturn();
                        });

                // generate the optimized "equals$p" with the actual implementation
                if (!isMethodOnTemplateClass(eqOptName, mdPrimitive)) {
                    classBuilder.withMethodBody(eqOptName, mdPrimitive,
                            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            (code) ->
                                    assembleEqualsMethod(className, code, type, cd, eqSig));
                }
            }
        }
    }

    /**
     * Generate the body of the "equals$p" method for a const type.
     * <p>
     * The generated method signature is:
     * <pre>
     *     public static boolean equals$p(Ctx ctx, nType type, T value1, T value2)
     * </pre>
     * where T is the const type being built.
     * <p>
     * Slot 0 = Ctx, Slot 1 = nType, Slot 2 = value1, Slot 3 = value2
     */
    private void assembleEqualsMethod(String className, CodeBuilder code, TypeConstant type,
                                      ClassDesc cd, SignatureConstant eqSig) {
        // all primitives must have a manually coded native implementation
        assert !type.isJitPrimitive();

        Label  returnFalse = code.newLabel();
        String eqOptName   = eqSig.getName() + OPT;

        TypeConstant baseType = getImplementationBase(eqSig);
        if (baseType != null) {
            // found super class with equals method, so call it first
            ClassDesc      cdExt   = ensureClassDesc(baseType);
            MethodTypeDesc mdSuper = MethodTypeDesc.of(CD_boolean, CD_Ctx, CD_nType, cdExt, cdExt);

            loadCtx(code);
            code.aload(1)
                .aload(2)
                .aload(3)
                .invokestatic(cdExt, eqOptName, mdSuper)
                .ifeq(returnFalse);
        }

        // create the list of properties to be compared so we can sort them
        List<PropertyInfo> props = new ArrayList<>();
        for (PropertyInfo prop : structInfo.getProperties().values()) {
            TypeConstant propType = prop.getType();

            if (!propType.isNullable() && propType instanceof UnionTypeConstant) {
                throw new UnsupportedOperationException("Union types not yet supported");
            }

            if (!isConstFormingProperty(prop, baseType)) {
                continue;
            }
            props.add(prop);
        }

        int value1Slot    = 2;
        int value2Slot    = 3;
        int nullCheckSlot = 4; // we can use slot 4 to hold the result of the null check

        // iterate over all the properties needed to be compared
        // we sort by the properties rank and compare in that order
        props.sort(Comparator.comparingInt(PropertyInfo::getRank));
        for (PropertyInfo prop : props) {
            if (!isConstFormingProperty(prop, baseType)) {
                continue;
            }

            PropertyConstant propId    = prop.getIdentity();
            TypeConstant     propType  = prop.getType();
            Label            skipProp  = code.newLabel();
            Label            checkProp = code.newLabel();

            if (propType.isNullable()) {
                // properties may be null, so assemble null check
                Label ifNull1    = code.newLabel();
                Label ifNull2    = code.newLabel();
                Label checkProp2 = code.newLabel();
                Label compare    = code.newLabel();
                Label notEqual   = code.newLabel();

                code.aload(value1Slot);
                loadProperty(code, type, propId, false);
                loadNull(code);
                code.if_acmpeq(ifNull1)
                    .iconst_0() // zero on the stack if null
                    .goto_(checkProp2)
                    .labelBinding(ifNull1)
                    .iconst_1(); // one on the stack if not null

                code.labelBinding(checkProp2)
                    .istore(nullCheckSlot) // store the result of the null check in slot 4
                    .aload(value2Slot);
                loadProperty(code, type, propId, false);
                loadNull(code);
                code.if_acmpeq(ifNull2)
                    .iconst_0() // zero on the stack if null
                    .goto_(compare)
                    .labelBinding(ifNull2)
                    .iconst_1() // one on the stack if not null
                    .labelBinding(compare)
                    .iload(nullCheckSlot) // reload the null check result
                    .if_icmpne(notEqual)  // if two ints on the stack are not equal jump
                    .iload(nullCheckSlot) // reload the null check result
                    .ifeq(skipProp)       // if null skip
                    .goto_(checkProp)     // else props are not null, so we need to compare
                    .labelBinding(notEqual)
                    .goto_(returnFalse);
            }

            propType = propType.removeNullable();

            code.labelBinding(checkProp);
            if (propType.isJavaPrimitive()) {
                // Java primitive: load both property values as primitives and compare directly
                code.aload(value1Slot);
                loadProperty(code, type, propId, true);
                code.aload(value2Slot);
                loadProperty(code, type, propId, true);

                ClassDesc cdPrim = JitTypeDesc.getPrimitiveClass(propType);
                assert cdPrim != null;
                switch (cdPrim.descriptorString()) {
                    case "I", "S", "B", "Z":
                        code.if_icmpne(returnFalse);
                        break;
                    case "J":
                        code.lcmp()
                            .ifne(returnFalse);
                        break;
                    case "F":
                        code.fcmpl()
                            .ifne(returnFalse);
                        break;
                    case "D":
                        code.dcmpl()
                            .ifne(returnFalse);
                        break;
                }
            } else if (propType.isXvmPrimitive()) {
                // XVM primitive: call static $equals(primitives1..., primitives2...)
                ClassDesc[]    cdParams = getJitPrimitivePairMethodParams(propType);
                MethodTypeDesc md       = MethodTypeDesc.of(CD_boolean, cdParams);

                // load both property values as unboxed primitives onto the stack
                code.aload(value1Slot);
                JitMethodDesc jmd = loadProperty(code, type, propId, true);
                loadOptimizedReturnsToStack(code, jmd);
                code.aload(value2Slot);
                jmd = loadProperty(code, type, propId, true);
                loadOptimizedReturnsToStack(code, jmd);

                code.invokestatic(ensureClassDesc(propType), XVM_PRIMITIVE_EQUALS, md)
                    .ifeq(returnFalse);
            } else if (propType.isA(pool().typeService())){
                buildGetIdentityHashCode(code, prop, value1Slot);
                buildGetIdentityHashCode(code, prop, value2Slot);
                code.lcmp()
                    .ifne(returnFalse);
            } else {
                // Object type: call static equals$p(Ctx, nType, T, T) -> boolean
                MethodInfo    propEqMethod = propType.ensureTypeInfo().getMethodBySignature(eqSig);
                JitMethodDesc propEqJmd    = propEqMethod.getJitDesc(this, propType);

                loadCtx(code);

                // load nType for the property type: nType.$ensureType(Ctx, TypeConstant)
                loadCtx(code);
                loadTypeConstant(code, className, propType);
                code.invokestatic(CD_nType, "$ensureType",
                        MethodTypeDesc.of(CD_nType, CD_Ctx, CD_TypeConstant));

                // load value1 to the stack
                code.aload(value1Slot);
                loadProperty(code, type, propId, false);
                if (propType.isInterfaceType()) {
                    code.checkcast(propEqJmd.optimizedParams[1].cd);
                }

                // load value2 to the stack
                code.aload(value2Slot);
                loadProperty(code, type, propId, false);
                if (propType.isInterfaceType()) {
                    code.checkcast(propEqJmd.optimizedParams[2].cd);
                }

                code.invokestatic(ensureClassDesc(propType), eqOptName, propEqJmd.optimizedMD)
                    .ifeq(returnFalse);
            }
            // we jump here if the prop is nullable and the null check determined both were null
            code.labelBinding(skipProp);
        }

        // fall-through, all properties equal
        code.iconst_1()
            .ireturn();

        // at least one property is not equal
        code.labelBinding(returnFalse)
            .iconst_0()
            .ireturn();
    }

    /**
     * Generate the "compare" method if it does not already exist.
     * <pre>
     *     static <CompileType extends T> Ordered compare(T value1, T value2)
     * </pre>
     */
    protected void assembleCompareMethod(String className, ClassBuilder classBuilder) {
        SignatureConstant cmpSig     = pool().sigCompare();
        MethodInfo        cmpMethod  = typeInfo.getMethodBySignature(cmpSig);
        TypeConstant      type       = typeInfo.getType();
        Implementation    impl       = cmpMethod.getHead().getImplementation();
        Constant          thisConst  = type.getDefiningConstant();
        IdentityConstant  declaredOn = cmpMethod.getIdentity()
                                                .getParentConstant()
                                                .getParentConstant();

        // If the method is not explicitly implemented or the declaring type does not match the
        // current type (i.e., the method is declared on a supe class), then we can build the method
        if (impl != Implementation.Explicit || !thisConst.equals(declaredOn)) {
            TypeConstant   resolvedType = type.resolveConstraints();
            ClassDesc      cd           = ensureClassDesc(resolvedType);
            String         cmpName      = cmpSig.getName();
            MethodTypeDesc md           = MethodTypeDesc.of(CD_Ordered, CD_Ctx, CD_nType, cd, cd);

            if (!isMethodOnTemplateClass(cmpName, md)) {
                // generate the standard "compare" method
                classBuilder.withMethodBody(cmpName, md,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        (code) ->
                                assembleCompareMethod(className, code, type, cmpSig));
            }
        }
    }

    /**
     * Generate the body of the "compare" method for a const type.
     * <p>
     * The generated method signature is:
     * <pre>
     *     public static Ordered compare(Ctx ctx, nType CompileType, T value1, T value2)
     * </pre>
     * where T is the const type being built.
     * <p>
     * Slot 0 = Ctx, Slot 1 = nType, Slot 2 = value1, Slot 3 = value2
     */
    private void assembleCompareMethod(String className, CodeBuilder code, TypeConstant type,
                                       SignatureConstant cmpSig) {
        // all primitives must have a manually coded native implementation
        assert !type.isJitPrimitive();

        ConstantPool       pool          = pool();
        TypeConstant       typeOrderable = pool.typeOrderable();
        List<PropertyInfo> props         = new ArrayList<>();
        TypeConstant       baseType      = getImplementationBase(cmpSig);

        // create the list of properties to be compared so we can sort them
        for (PropertyInfo prop : structInfo.getProperties().values()) {
            if (!isConstFormingProperty(prop, baseType)) {
                continue;
            }

            TypeConstant propType = prop.getType();
            if (!propType.isNullable() && propType instanceof UnionTypeConstant) {
                throw new UnsupportedOperationException("Union types not yet supported");
            }

            props.add(prop);
        }

        if (baseType != null) {
            // found super class with compare method, so call it first
            ClassDesc      cdExt   = ensureClassDesc(baseType.resolveConstraints());
            MethodTypeDesc mdSuper = MethodTypeDesc.of(CD_Ordered, CD_Ctx, CD_nType, cdExt, cdExt);

            loadCtx(code);
            code.aload(1)
                    .aload(2)
                    .aload(3)
                    .invokestatic(cdExt, cmpSig.getName(), mdSuper)
                    .dup();
            loadConstant(code, pool.valEqual());
            Label propEqual = code.newLabel();
            code.if_acmpeq(propEqual)
                    .areturn();
            code.labelBinding(propEqual)
                    .pop();
        }

        int value1Slot    = 2;
        int value2Slot    = 3;
        int nullCheckSlot = 4; // we can use slot 4 to hold the result of the null check

        // iterate over all the properties needed to be compared
        // we sort by the property rank and compare in that order
        props.sort(Comparator.comparingInt(PropertyInfo::getRank));
        for (PropertyInfo prop : props) {
            PropertyConstant propId    = prop.getIdentity();
            TypeConstant     propType  = prop.getType();
            Label            skipProp  = code.newLabel();
            Label            checkProp = code.newLabel();

            if (propType.isNullable()) {
                // properties may be null, so assemble null check
                Label ifNull1    = code.newLabel();
                Label ifNull2    = code.newLabel();
                Label checkProp2 = code.newLabel();
                Label compare    = code.newLabel();

                code.aload(value1Slot);
                loadProperty(code, type, propId, false);
                loadNull(code);
                code.if_acmpeq(ifNull1)
                    .iconst_0() // zero on the stack if null
                    .goto_(checkProp2)
                    .labelBinding(ifNull1)
                    .iconst_1(); // one on the stack if not null

                code.labelBinding(checkProp2)
                    .istore(nullCheckSlot) // store the result of the null check in slot 4
                    .aload(value2Slot);
                loadProperty(code, type, propId, false);
                loadNull(code);
                code.if_acmpeq(ifNull2)
                    .iconst_0() // zero on the stack if null
                    .goto_(compare)
                    .labelBinding(ifNull2)
                    .iconst_1() // one on the stack if not null
                    .labelBinding(compare)
                    .iload(nullCheckSlot) // reload the prop1 null check result
                    .isub(); // two ints on the stack, subtract to get result
                convertIntToOrdered(code, false);
                // to get here must be Equal
                code.iload(nullCheckSlot) // reload the prop1 null check result
                    .ifeq(skipProp);      // if it is zero, both props were null so jump to skip
            }

            code.labelBinding(checkProp);
            propType = propType.removeNullable();

            if (propType.isJavaPrimitive()) {
                // Java primitive: load both values, compare directly, convert int to Ordered
                ClassDesc cdPrim = JitTypeDesc.getPrimitiveClass(propType);
                assert cdPrim != null;

                code.aload(value1Slot);
                loadProperty(code, type, propId, true);
                convertIfUnsignedPrimitive(code, propType);
                code.aload(value2Slot);
                loadProperty(code, type, propId, true);
                convertIfUnsignedPrimitive(code, propType);

                // produce an int comparison result on the stack
                switch (cdPrim.descriptorString()) {
                    case "I", "S", "B", "Z":
                        code.isub();
                        break;
                    case "J":
                        code.lcmp();
                        break;
                    case "F":
                        code.fcmpl();
                        break;
                    case "D":
                        code.dcmpl();
                        break;
                }

                // int result on stack: if zero, this property is equal; continue to next
                // otherwise convert to an Ordered and return
                convertIntToOrdered(code, false);
            } else if (propType.isXvmPrimitive()) {
                // XVM primitive: call static $compare(primitives1..., primitives2...)
                ClassDesc[]    cdParams = getJitPrimitivePairMethodParams(propType);
                MethodTypeDesc md       = MethodTypeDesc.of(CD_int, cdParams);

                code.aload(value1Slot);
                JitMethodDesc jmd = loadProperty(code, type, propId, true);
                loadOptimizedReturnsToStack(code, jmd);
                code.aload(value2Slot);
                jmd = loadProperty(code, type, propId, true);
                loadOptimizedReturnsToStack(code, jmd);

                code.invokestatic(ensureClassDesc(propType), XVM_PRIMITIVE_COMPARE, md);

                // int result on stack: if zero, this property is equal; continue to next
                // otherwise convert to an Ordered and return
                convertIntToOrdered(code, false);
            } else if (propType.isA(pool().typeService())) {
                // for services, we compare the service identity
                buildGetIdentityHashCode(code, prop, value1Slot);
                buildGetIdentityHashCode(code, prop, value2Slot);
                code.lcmp();
                // int result on stack: if zero, this property is equal; continue to next
                // otherwise convert to an Ordered and return
                convertIntToOrdered(code, false);
            } else if (!propType.isA(typeOrderable)) {
                // property is an Object but not Orderable
                // as doc'ed in Const.x, we compare the identity hash code
                buildGetIdentityHashCode(code, prop, value1Slot);
                buildGetIdentityHashCode(code, prop, value2Slot);
                code.lcmp();
                // int result on stack: if zero, this property is equal; continue to next
                // otherwise convert to an Ordered and return
                convertIntToOrdered(code, false);
            } else {
                // Object type: call static compare(Ctx, nType, T, T) -> Ordered
                MethodInfo    propCmpMethod = propType.ensureTypeInfo().getMethodBySignature(cmpSig);
                JitMethodDesc propCmpJmd    = propCmpMethod.getJitDesc(this, propType);

                // load the context to the stack (compare param 0)
                loadCtx(code);

                // get and load the nType to the stack (compare param 1)
                loadCtx(code);
                loadTypeConstant(code, className, propType);
                code.invokestatic(CD_nType, "$ensureType",
                        MethodTypeDesc.of(CD_nType, CD_Ctx, CD_TypeConstant));

                // load the first value to the stack (compare param 2)
                code.aload(value1Slot);
                loadProperty(code, type, propId, false);

                // load the second value to the stack (compare param 3)
                code.aload(value2Slot);
                loadProperty(code, type, propId, false);

                // invoke the static compare method
                code.invokestatic(ensureClassDesc(propType), cmpSig.getName(), propCmpJmd.standardMD)
                    .dup();
                loadConstant(code, pool.valEqual());
                Label propEqual = code.newLabel();
                code.if_acmpeq(propEqual)
                    .areturn();
                code.labelBinding(propEqual)
                    .pop();
            }
            // we jump here if the prop is nullable and the null check determined both were null
            code.labelBinding(skipProp);
        }

        // all properties equal
        loadConstant(code, pool.valEqual());
        code.areturn();
    }

    /**
     * Generate the "hashCode", "hashCode$p" if the methods do not already exist.
     * <pre>
     *     static <CompileType extends T> Int hashCode(T value)
     * </pre>
     */
    protected void assembleConstHashCodeMethod(String className, ClassBuilder classBuilder) {
        SignatureConstant hashSig    = pool().sigHashCode();
        MethodInfo        hashMethod = typeInfo.getMethodBySignature(hashSig);
        TypeConstant      type       = typeInfo.getType();
        Implementation    impl       = hashMethod.getHead().getImplementation();
        Constant          thisConst  = type.getDefiningConstant();
        IdentityConstant  declaredOn = hashMethod.getIdentity()
                                                 .getParentConstant()
                                                 .getParentConstant();

        // If the method is not explicitly implemented or the declaring type does not match the
        // current type (i.e. the method is declared on a supe class), then we can build the method
        if (impl != Implementation.Explicit || !thisConst.equals(declaredOn)) {
            // create a {@link Long} field in the class to act as a hashCode cache
            classBuilder.withField("$savedHashCode", CD_Long, ClassFile.ACC_PRIVATE);

            TypeConstant   resolvedType = type.resolveConstraints();
            ClassDesc      cd           = ensureClassDesc(resolvedType);
            String         hashName     = hashSig.getName();
            String         hashOptName  = hashName + OPT;
            MethodTypeDesc mdWrapper    = MethodTypeDesc.of(CD_Int64, CD_Ctx, CD_nType, cd);
            MethodTypeDesc mdPrimitive  = MethodTypeDesc.of(CD_long, CD_Ctx, CD_nType, cd);

            if (!isMethodOnTemplateClass(hashName, mdWrapper)) {
                // generate the standard "hashCode" wrapper that delegates to the optimized
                // "hashCode$p"
                classBuilder.withMethodBody(hashName, mdWrapper,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        (code) -> {
                            loadCtx(code);
                            code.aload(1)
                                .aload(2)
                                .invokestatic(cd, hashOptName, mdPrimitive);
                            Builder.box(code, pool().typeInt64());
                            code.areturn();
                        });

                // generate the optimized "hashCode$p" with the actual implementation
                if (!isMethodOnTemplateClass(hashOptName, mdPrimitive)) {
                    classBuilder.withMethodBody(hashOptName, mdPrimitive,
                            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                            (code) ->
                                    assembleConstHashCodeMethod(className, code, type, hashSig));
                }
            }
        }
    }

    /**
     * Generate the body of the "hashCode$p" method for a const type.
     * <p>
     * The generated method signature is:
     * <pre>
     *     public static long hashCode$p(Ctx ctx, nType CompileType, T value)
     * </pre>
     * Slot 0 = Ctx, Slot 1 = nType, Slot 2 = value
     */
    private void assembleConstHashCodeMethod(String className, CodeBuilder code, TypeConstant type,
                                             SignatureConstant hashSig) {

        TypeConstant typeHashable = pool().typeHashable();
        ClassDesc    cdThis       = ensureClassDesc(type);
        int          valueSlot    = 2;
        int          resultSlot   = 3;
        long         hashFactor   = 37L;

        if (type.isJitPrimitive()) {
            // when the const type itself is a JIT primitive (Java primitive or XVM primitive),
            // hashCode$p receives the boxed primitve value, and we just unbox and generate the
            // hash code from the primitive types
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(type);

            // load the value and unbox
            Builder.load(code, cdThis, valueSlot);
            Builder.unbox(code, type);
            Builder.buildPrimitiveToLong(cds[0], code);
            code.lstore(resultSlot);

            for (int i = 1; i < cds.length; i++) {
                Builder.buildPrimitiveToLong(cds[i], code);
                code.lload(resultSlot)
                        .ldc(hashFactor)
                        .lmul()
                        .ladd()
                        .lstore(resultSlot);
            }

            code.lload(resultSlot)
                    .lreturn();
            return;
        }

        TypeConstant       baseType    = getImplementationBase(hashSig);
        String             hashOptName = hashSig.getName() + OPT;
        List<PropertyInfo> props       = new ArrayList<>();

        // create the list of properties to be compared so we can sort them
        for (PropertyInfo prop : structInfo.getProperties().values()) {
            if (!isConstFormingProperty(prop, baseType)) {
                continue;
            }

            TypeConstant propType = prop.getType();
            if (!propType.isNullable() && propType instanceof UnionTypeConstant) {
                throw new UnsupportedOperationException("Union types not yet supported");
            }
            props.add(prop);
        }

        // generate the code to check the cached hash code
        // if the cached hashCode field $savedHashCode is non-null, return the cached value
        Label compute = code.newLabel();
        code.aload(valueSlot)
            .getfield(cdThis, "$savedHashCode", CD_Long)
            .dup()
            .ifnull(compute)
            .invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(CD_long))
            .lreturn();

        code.labelBinding(compute)
            .pop();   // pop the null

        if (baseType != null) {
            // found super class with hashCode method, so call it first
            ClassDesc      cdExt   = ensureClassDesc(baseType.resolveConstraints());
            MethodTypeDesc mdSuper = MethodTypeDesc.of(CD_long, CD_Ctx, CD_nType, cdExt);

            loadCtx(code);
            code.aload(1)
                .aload(valueSlot)
                .invokestatic(cdExt, hashOptName, mdSuper)
                // long hashCode from super class is on the stack, store into the result slot
                .lstore(resultSlot);
        } else {
            // start with a zero result
            code.lconst_0()
                .lstore(resultSlot);
        }

        if (props.isEmpty()) {
            // if there are no properties, there's nothing to mix in; derive a stable
            // hash from the type's Ecstasy class name (computed at generation time)
            code.lload(resultSlot)
                .loadConstant(hashFactor)
                .lmul()
                .ldc((long) type.getEcstasyClassName().hashCode())
                .ladd()
                .lreturn();
            return;
        }

        // iterate over the properties in rank order to generate a hash code
        props.sort(Comparator.comparingInt(PropertyInfo::getRank));
        for (PropertyInfo prop : props) {
            PropertyConstant propId     = prop.getIdentity();
            TypeConstant     propType   = prop.getType();
            Label            doProp     = code.newLabel();
            Label            propIsNull = code.newLabel();

            // result = result * 37 + propHash
            code.lload(resultSlot)
                .ldc(hashFactor)
                .lmul();

            if (propType.isNullable()) {
                // property may be null, so assemble null check
                code.aload(valueSlot);
                loadProperty(code, type, propId, false);
                loadNull(code);
                code.if_acmpne(doProp)  // not Null, so process the property
                    .lconst_0()         // hash code is zero if Null
                    .goto_(propIsNull); // jump to the end, property is Null
            }

            code.labelBinding(doProp);
            if (propType.isJavaPrimitive()) {
                // load the primitive value and convert to long in-line — all Java primitives
                // fit into a 64-bit long
                code.aload(valueSlot);
                loadProperty(code, type, propId, true);
                ClassDesc cd = JitTypeDesc.getPrimitiveClass(propType);
                assert cd != null;
                Builder.buildPrimitiveToLong(cd, code);
                // long primitive hashCode on the stack
            } else if (propType.isA(pool().typeService())) {
                buildGetIdentityHashCode(code, prop, valueSlot);
            } else if (!propType.isA(typeHashable)) {
                // property is not Hashable, as doc'ed in Const.x we use zero for the hash code
                code.lconst_0();
            } else {
                // call hashCode$p(Ctx, nType, T) -> long
                loadCtx(code);

                // nType for the property type: nType.$ensureType(Ctx, TypeConstant)
                loadCtx(code);
                loadTypeConstant(code, className, propType);
                code.invokestatic(CD_nType, "$ensureType",
                        MethodTypeDesc.of(CD_nType, CD_Ctx, CD_TypeConstant));

                code.aload(valueSlot);
                loadProperty(code, type, propId, false);
                ClassDesc      cd = ensureClassDesc(propType);
                MethodTypeDesc md = MethodTypeDesc.of(CD_long, CD_Ctx, CD_nType, cd);
                code.invokestatic(ensureClassDesc(propType), hashOptName, md);
                // long hashCode on the stack
            }
            // add the result and store
            code.labelBinding(propIsNull)
                .ladd()
                .lstore(resultSlot);
        }
        // load the result, store in the hashCode cache field and return the hashCode
        code.aload(valueSlot)
            .lload(resultSlot)
            .invokestatic(CD_Long, "valueOf", MethodTypeDesc.of(CD_Long, CD_long))
            .putfield(cdThis, "$savedHashCode", CD_Long)
            .lload(resultSlot)
            .lreturn();
    }

    /**
     * Determine whether the specified property should be used when auto-generating constant
     * method for Orderable, Hashable, Stringable, Comparable, etc.
     *
     * @param prop      the property to check
     * @param baseType  the base type to check against
     *
     * @return          true if the property can be used for constant method generation
     */
    protected boolean isConstFormingProperty(PropertyInfo prop, TypeConstant baseType) {
        return isConstFormingProperty(prop, baseType, false);
    }

    /**
     * Determine whether the specified property should be used when auto-generating constant
     * method for Orderable, Hashable, Stringable, Comparable, etc.
     *
     * @param prop       the property to check
     * @param baseType   the base type to check against
     * @param allowLazy  {@code true} if lazy properties should be considered for constant method
     *                   generation
     *
     * @return          true if the property can be used for constant method generation
     */
    protected boolean isConstFormingProperty(PropertyInfo prop, TypeConstant baseType,
                                             boolean allowLazy) {
        PropertyConstant propId = prop.getIdentity();
        if (baseType != null && baseType.ensureTypeInfo().findProperty(propId, true) != null) {
            // we are only interested in properties not known to the base class
            return false;
        }

        return prop.hasField() && !prop.isTransient() && (allowLazy || !prop.isLazy());
    }

    /**
     * Return the ClassDesc array representing two primitive values of the given type — used as
     * parameters for the {@code $equals} and {@code $compare} methods, which both take two sets
     * of the primitive slots that make up the type.
     *
     * @param type  the JIT primitive type to obtain the parameters for
     *
     * @return the ClassDesc array {@code [slots..., slots...]} — two copies of the primitive slots
     */
    protected ClassDesc[] getJitPrimitivePairMethodParams(TypeConstant type) {
        ClassDesc[] cdParams;
        if (type.isXvmPrimitive()) {
            ClassDesc[] cds  = JitTypeDesc.getXvmPrimitiveClasses(type);
            cdParams = new ClassDesc[cds.length * 2];
            System.arraycopy(cds, 0, cdParams, 0, cds.length);
            System.arraycopy(cds, 0, cdParams, cds.length, cds.length);
        } else {
            ClassDesc cd = JitTypeDesc.getPrimitiveClass(type);
            cdParams = new ClassDesc[]{cd, cd};
        }
        return cdParams;
    }

    /**
     * @return the first type from this type's class hierarchy that is either a const or has an
     *         implementation of the specified method; or return {code null} if no class in the
     *         hierarchy has the specified method
     */
    protected TypeConstant getImplementationBase(SignatureConstant sig) {
        TypeConstant typeExtends = typeInfo.getExtends();
        while (typeExtends != null) {
            if (typeExtends.ensureTypeInfo().getFormat() == Format.CONST) {
                return typeExtends;
            }

            // super class is not a const, so look for an implementation of the method
            TypeInfo   info   = typeExtends.ensureTypeInfo();
            MethodInfo method = info.getMethodBySignature(sig);
            if (method != null) {
                if (method.getIdentity().getNamespace().equals(info.getIdentity())) {
                    return typeExtends;
                }
            }
            typeExtends = info.getExtends();
        }
        return null;
    }

    protected static void convertIfUnsignedPrimitive(CodeBuilder code, TypeConstant type) {
        String name = type.getSingleUnderlyingClass(false).getName();
        switch (name) {
            case "UInt32":
                code.loadConstant(Integer.MIN_VALUE)
                    .iadd();
                break;
            case "UInt64":
                code.loadConstant(Long.MIN_VALUE)
                    .ladd();
                break;
        }
    }

    /**
     * If the Java primitive {@code int} on the stack is non-zero build the code to return the
     * corresponding {@code Ordered} otherwise fall-through.
     *
     * @param code           the {@link CodeBuilder} to use to generate the code
     * @param returnIfEqual  {@code true} to generate an areturn op to return Equal or {@code
     *                       false} to just pop the result from the stack and fall through if the
     *                       result is Equal
     */
    protected void convertIntToOrdered(CodeBuilder code, boolean returnIfEqual) {
        ConstantPool pool      = pool();
        Label        propEqual = code.newLabel();
        Label        propLt    = code.newLabel();

        code.dup()
            .ifeq(propEqual);

        // non-zero: return Lesser or Greater
        code.iflt(propLt);
        loadConstant(code, pool.valGreater());
        code.areturn()
            .labelBinding(propLt);
        loadConstant(code, pool.valLesser());
        code.areturn();

        code.labelBinding(propEqual);
        if (returnIfEqual) {
            loadConstant(code, pool.valEqual());
            code.areturn();
        } else {
            code.pop();
        }
    }

    /**
     * Generate the byte codes to put the identity hash code for a property onto the stack.
     *
     * @param code       the {@link CodeBuilder} to use to generate byte codes
     * @param prop       the {@link PropertyInfo} for the property
     * @param ownerSlot  the slot of the property owner
     */
    void buildGetIdentityHashCode(CodeBuilder code, PropertyInfo prop, int ownerSlot) {
        // TODO the JIT does not yet support getting identity,
        //  in Ecstasy it is &value.identity.hashCode()
        //  for now we just use the Java System.identityHashCode()
        PropertyConstant propId = prop.getIdentity();

        code.aload(ownerSlot);
        loadProperty(code, thisType, propId, false);
        // call Java's System.identityHashCode(prop);
        code.invokestatic(CD_JavaSystem, "identityHashCode",
                          MethodTypeDesc.of(CD_int, CD_JavaObject))
            .i2l();
    }

    /**
     * Assemble the "routing" method for a capped method.
     */
    protected void assembleCapRouting(String className, ClassBuilder classBuilder,
                                      MethodInfo srcMethod, MethodInfo dstMethod) {
        JitMethodDesc jmdSrc = srcMethod.getJitDesc(this);
        JitMethodDesc jmdDst = dstMethod.getJitDesc(this);

        String srcName = srcMethod.ensureJitMethodName(typeSystem);
        String dstName = dstMethod.ensureJitMethodName(typeSystem);

        if (srcName.equals(dstName)) {
            // it must be a cap with a covariant return;
            // at the moment SignatureConstant.ensureJitMethodName() ignore the return values,
            // but we may need to change that...
            return;
        }

        assert jmdSrc.getImplicitParamCount() == jmdDst.getImplicitParamCount();
        assert !srcMethod.isFunction() && !srcMethod.isCtorOrValidator() ||
                srcMethod.containsVirtualConstructor();

        assembleStandardCap(className, classBuilder, srcName, dstName, jmdSrc, jmdDst);
        if (jmdSrc.isOptimized) {
            assert jmdDst.isOptimized;
            assembleOptimizedCap(className, classBuilder, srcName+OPT, dstName+OPT, jmdSrc, jmdDst);
        }
    }

    /**
     * Assemble a "standard" routing call from a cap to its target method.
     */
    private void assembleStandardCap(String className, ClassBuilder classBuilder,
                                     String srcName, String dstName,
                                     JitMethodDesc jmdSrc, JitMethodDesc jmdDst) {
        classBuilder.withMethodBody(srcName, jmdSrc.standardMD, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0); // this

            int extraCount = jmdSrc.getImplicitParamCount();
            for (int i = 0; i < extraCount; i++) {
                code.aload(code.parameterSlot(i));
            }

            JitParamDesc[] srcParams = jmdSrc.standardParams;
            JitParamDesc[] dstParams = jmdDst.standardParams;
            for (int i = 0, c = srcParams.length; i < c; i++) {
                JitParamDesc srcPd        = srcParams[i];
                int          srcParamSlot = code.parameterSlot(extraCount + srcPd.index);
                TypeConstant srcParamType = srcPd.type;
                JitParamDesc dstPd        = dstParams[i];
                TypeConstant dstParamType = dstPd.type;

                code.aload(srcParamSlot);
                if (!srcParamType.equals(dstParamType)) {
                    generateCheckCast(code, dstParamType);
                }
            }
            for (int i = srcParams.length, c = dstParams.length; i < c; i++) {
                code.aconst_null();
            }
            code.invokevirtual(ClassDesc.of(className), dstName, jmdDst.standardMD);

            JitParamDesc[] srcReturns = jmdSrc.standardReturns;
            int            retCount   = srcReturns.length;
            if (retCount == 0) {
                code.return_();
                return;
            }

//            JitParamDesc[] dstReturns = jmdDst.standardReturns;
//            TypeConstant   srcRetType = srcReturns[0].type;
//            TypeConstant   dstRetType = dstReturns[0].type;

            // the natural return is at the top of the stack now;
            // TODO TEMPORARY: assume the same Ctx positions for returns
            code.areturn();
        });
    }

    /**
     * Assemble an "optimized" routing call from a cap to its target method.
     */
    private void assembleOptimizedCap(String className, ClassBuilder classBuilder,
                                      String srcName, String dstName,
                                      JitMethodDesc jmdSrc, JitMethodDesc jmdDst) {
        classBuilder.withMethodBody(srcName, jmdSrc.optimizedMD, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0); // this

            int extraCount = jmdSrc.getImplicitParamCount();
            for (int i = 0; i < extraCount; i++) {
                code.aload(code.parameterSlot(i));
            }

            JitParamDesc[] srcParams = jmdSrc.optimizedParams;
            JitParamDesc[] dstParams = jmdDst.optimizedParams;
            for (int i = 0, c = srcParams.length; i < c; i++) {
                JitParamDesc srcPd     = srcParams[i];
                int          srcSlot   = code.parameterSlot(extraCount + srcPd.index);
                TypeConstant srcType   = srcPd.type;
                JitParamDesc dstPd     = dstParams[i];
                TypeConstant dstType   = dstPd.type;
                JitFlavor    srcFlavor = srcPd.flavor;
                JitFlavor    dstFlavor = dstPd.flavor;
                boolean      checkCast = false;
                boolean      invalid   = false;

                if (srcFlavor == dstFlavor) {
                    load(code, srcPd.cd, srcSlot);
                    checkCast = !srcType.isJitPrimitive();
                } else {
                    AddTransformation:
                    switch (srcPd.flavor) {
                    case Specific:
                        switch (dstPd.flavor) {
                        case Primitive, XvmPrimitive:
                            code.aload(srcSlot);
                            if (!srcType.equals(dstType)) {
                                generateCheckCast(code, dstType);
                            }
                            Builder.unbox(code, dstType);
                            break AddTransformation;

                        default:
                            invalid = true;
                            break;
                    }

                    default:
                        invalid = true;
                        break;
                    }
                }

                if (checkCast && !srcType.equals(dstType)) {
                    generateCheckCast(code, dstType);
                }
                if (invalid) {
                    throw new UnsupportedOperationException("Not implemented: src=" + srcFlavor +
                                                            "; dst=" + dstFlavor);
                }
            }

            for (int i = srcParams.length, c = dstParams.length; i < c; i++) {
                JitParamDesc dstPd = dstParams[i];

                switch (dstPd.flavor) {
                case PrimitiveWithDefault:
                    defaultLoad(code, dstPd.cd);
                    code.iconst_1(); // default = true
                    i++;             // consume the extension
                    break;

                case NullablePrimitiveWithDefault:
                    defaultLoad(code, dstPd.cd);
                    code.iconst_m1(); // default = true
                    i++;              // consume the extension
                    break;

                case SpecificWithDefault, WidenedWithDefault:
                    code.aconst_null();
                    break;

                default:
                    throw new UnsupportedOperationException("Not implemented: dst=" + dstPd.flavor);
                }
            }

            code.invokevirtual(ClassDesc.of(className), dstName, jmdDst.optimizedMD);

            JitParamDesc[] srcReturns = jmdSrc.optimizedReturns;
            int            retCount   = srcReturns.length;
            if (retCount == 0) {
                code.return_();
                return;
            }

            JitParamDesc[] dstReturns = jmdDst.optimizedReturns;

            JitParamDesc srcPd = srcReturns[0];
            JitParamDesc dstPd = dstReturns[0];

            // the natural return is at the top of the stack now;
            // TEMPORARY: assume the same Ctx positions for returns TODO
            assert srcPd.flavor == dstPd.flavor;
            addReturn(code, srcPd.cd);
        });
    }

    /**
     * Assemble the "routing" method for a capped method.
     */
    protected void assemblePropertyDelegation(String className, ClassBuilder classBuilder,
                                              MethodInfo srcMethod, PropertyConstant propDelegate) {
        String        srcName   = srcMethod.ensureJitMethodName(typeSystem);
        JitMethodDesc jmd       = srcMethod.getJitDesc(this);
        PropertyInfo  propInfo  = typeInfo.findProperty(propDelegate);
        TypeConstant  dstType   = propInfo.getType();
        TypeInfo      dstInfo   = dstType.ensureTypeInfo();
        MethodInfo    dstMethod = dstInfo.getMethodById(srcMethod.getIdentity());
        String        dstName   = dstMethod.ensureJitMethodName(typeSystem);

        assert !srcName.equals(dstName);

        int extraCount = jmd.getImplicitParamCount();

        assembleDelegation(classBuilder, propDelegate, srcName, dstMethod, dstName,
            jmd.standardMD, extraCount, jmd.standardParams, jmd.standardReturns);

        if (jmd.isOptimized) {
            assembleDelegation(classBuilder, propDelegate, srcName+OPT, dstMethod, dstName+OPT,
                jmd.optimizedMD, extraCount, jmd.optimizedParams, jmd.optimizedReturns);
        }
    }

    private void assembleDelegation(ClassBuilder classBuilder, PropertyConstant propDelegate,
                                    String srcName, MethodInfo dstMethod, String dstName,
                                    MethodTypeDesc md, int extraCount,
                                    JitParamDesc[] params, JitParamDesc[] returns) {
        classBuilder.withMethodBody(srcName, md, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0); // this
            loadProperty(code, thisType, propDelegate, /*don't unbox*/ false);

            TypeConstant dstType = dstMethod.getJitIdentity().getNamespace().getType();

            boolean objectDelegation = dstType.isJitInterface() && dstType.equals(pool().typeObject());
            if (objectDelegation) {
                // we are delegating an nObj method for an interface; need a cast
                code.checkcast(CD_nObj);
            }

            code.aload(code.parameterSlot(0)); // ctx

            for (JitParamDesc pd : params) {
                Builder.load(code, pd.cd, code.parameterSlot(extraCount + pd.index));
            }

            if (dstType.isJitInterface() && dstMethod.isAbstract() && !objectDelegation) {
                code.invokeinterface(ensureClassDesc(dstType), dstName, md);
            } else {
                code.invokevirtual(ensureClassDesc(dstType), dstName, md);
            }

            if (returns.length == 0) {
                code.return_();
            } else {
                Builder.addReturn(code, returns[0].cd);

                // we assume that all Ctx values stay at the same positions
            }
        });
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

        // Note: the "$init" is a virtual method for singletons and "$new" is static otherwise;
        //       see assembleStaticInitializer()
        int flags = ClassFile.ACC_PUBLIC;
        if (!isSingleton) {
            flags |= ClassFile.ACC_STATIC;
        }

        MethodTypeDesc md;
        if (jmd.isOptimized) {
            assembleMethodWrapper(className, classBuilder, jitName, jmd, true);
            jitName += OPT;
            md = jmd.optimizedMD;
        } else {
            md = jmd.standardMD;
        }

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            Label   startScope = code.newLabel();
            Label   endScope   = code.newLabel();
            boolean debugInfo  = isDebugInfo();

            code.labelBinding(startScope);

            int ctxSlot    = code.parameterSlot(0);
            int extraSlots = 1;
            int typeSlot   = -1;

            if (debugInfo) {
                code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);
            }

            if (hasType) {
                typeSlot = code.parameterSlot(1);
                extraSlots++;
            }

            // for singleton classes the steps 0-2 are performed by the static initializer;
            // see assembleStaticInitializer()
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
                if (debugInfo) {
                    code.localVariable(thisSlot, "thi$", CD_this, startScope, endScope);
                }
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
            if (debugInfo) {
                code.localVariable(cctxSlot, "cctx", CD_CtorCtx, startScope, endScope);
            }
            code.aload(ctxSlot)
                .invokevirtual(CD_Ctx, "ctorCtx", MethodTypeDesc.of(CD_CtorCtx))
                .astore(cctxSlot)
            ;

            // step 6: call the constructor
            // construct$17(ctx, cctx, [type], thi$, x, y, z);
            String        ctorName = constructor.ensureJitMethodName(typeSystem);
            JitMethodDesc ctorDesc = constructor.getJitDesc(this);

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
        int     flags      = ClassFile.ACC_PUBLIC;
        boolean isAbstract = prop.isAbstract() &&
                                !(isGetter ? prop.getHead().hasGetter() : prop.getHead().hasSetter());
        if (isAbstract) {
            flags |= ClassFile.ACC_ABSTRACT;
        }

        classBuilder.withMethod(jitName, md, flags,
            methodBuilder -> {
                if (!isAbstract) {
                    BuildContext bctx =
                        new BuildContext(this, className, typeInfo, formalInfo, prop, isGetter);
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
        int flags = ClassFile.ACC_PUBLIC;
        if (!method.getHead().getMethodStructure().hasCode()) {
            if (method.isAbstract()) {
                flags |= ClassFile.ACC_ABSTRACT;
            } else {
                // this must be a "sans-code" override; just ignore it
                return;
            }
        }

        MethodTypeDesc md;
        if (jmd.isOptimized) {
            assembleMethodWrapper(className, classBuilder, jitName, jmd, method.isFunction());
            jitName += OPT;
            md = jmd.optimizedMD;
        } else {
            md = jmd.standardMD;
        }

        if (method.isFunction() || method.isCtorOrValidator()) {
            if (method.isAbstract() && classStruct.getFormat() == Format.INTERFACE) {
                // this must be a funky interface method; just ignore
                return;
            }

            flags |= ClassFile.ACC_STATIC;
        }

        BuildContext bctx = new BuildContext(this, className, typeInfo, formalInfo, method);

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
                    assembleMethodWrapper(className, classBuilder, nameNext, jmdNext, fStatic);
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
        String className  = bctx.className;
        GenerateStub:
        if (Arrays.stream(CLASS_WHITE_LIST).anyMatch(name -> {
                if (name.endsWith("*")) {
                    name = name.substring(0, name.length() - 1);
                    return className.contains(name) || moduleName.contains(name);
                } else {
                    return className.endsWith(name) || moduleName.endsWith(name);
                }})) {

                if (Arrays.stream(CLASS_BLACK_LIST).anyMatch(className::endsWith)) {
                    break GenerateStub;
                }

            bctx.assembleCode(code);
            return;
        }

        if (SKIP_SET.add(bctx.className)) {
            System.err.println("*** Skipping code gen for " + bctx.className);
        }
        defaultLoad(code, md.returnType());
        addReturn(code, md.returnType());
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

    private final static String[] CLASS_WHITE_LIST = new String[] {
        "Test*", "test*",
        "IOException", "OutOfBounds", "Unsupported", "IllegalArgument", "IllegalState",
        "Boolean", "Ordered", "Orderable",
        "String",
        "Stringable",
        "StringBuffer",
        "Float*",
        "Array",
        "Iterable",
//        "Iterator",
        "List",
        "TerminalConsole",
//        "Dec32", "Dec64", // need to change to SingleSlot
//        "UInt",     // depends on GP_DIVREM
//        "FPNumber", // depends on Bit support
//        "Int",      // depends on "switch" implementation
    };

    private final static String[] CLASS_BLACK_LIST = new String[] {
    };

    private final static HashSet<String> SKIP_SET = new HashSet<>();
}