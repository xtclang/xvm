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

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;
import org.xvm.javajit.BuildContext.DoubleSlot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitFlavor;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import org.xvm.util.ShallowSizeOf;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        extends Builder {
    public CommonBuilder(TypeSystem typeSystem, TypeConstant type) {
        assert type.isSingleUnderlyingClass(true);

        ConstantPool pool = typeSystem.pool();

        this.typeSystem  = typeSystem;
        this.typeInfo    = pool.ensureAccessTypeConstant(type, Access.PRIVATE).ensureTypeInfo();
        this.structInfo  = pool.ensureAccessTypeConstant(type, Access.STRUCT).ensureTypeInfo();
        this.classStruct = typeInfo.getClassStructure();
        this.thisId      = classStruct.getIdentityConstant();
    }

    protected final TypeSystem       typeSystem;
    protected final TypeInfo         typeInfo;
    protected final TypeInfo         structInfo;
    protected final ClassStructure   classStruct;
    protected final IdentityConstant thisId;

    protected long implSize;

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
     * Compute the ClassDesc for the super class.
     */
    protected ClassDesc getSuperDesc() {
        TypeConstant superType = typeInfo.getExtends();
        return superType == null
            ? CD_xObj
            : ClassDesc.of(typeSystem.ensureJitClassName(superType));
    }

    /**
     * Assemble the class specific info for the "Impl" shape.
     */
    protected void assembleImplClass(String className, ClassBuilder classBuilder) {
        int flags = ClassFile.ACC_PUBLIC;

        switch (classStruct.getFormat()) {
            case CLASS, CONST, SERVICE, ENUMVALUE:
                flags |= ClassFile.ACC_SUPER; // see JLS 4.1
                classBuilder.withSuperclass(getSuperDesc());
                break;

            case INTERFACE, MIXIN, ENUM:
                flags |= ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT;
                break;

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
        boolean isInterface = classStruct.getFormat() == Component.Format.INTERFACE;
        for (Contribution contrib : typeInfo.getContributionList()) {
            switch (contrib.getComposition()) {
                case Implements:
                    TypeConstant contribType = contrib.getTypeConstant().removeAccess();
                    if  (!isInterface &&
                            contribType.equals(contribType.getConstantPool().typeObject())) {
                        // ignore "implements Object" for classes
                        continue;
                    }
                    classBuilder.withInterfaceSymbols(
                        ClassDesc.of(typeSystem.ensureJitClassName(contribType)));
                    break;
            }
        }
    }

    /**
     * Assemble properties for the "Impl" shape.
     */
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        List<PropertyInfo> constProps = new ArrayList<>();
        List<PropertyInfo> initProps = new ArrayList<>();

        for (PropertyInfo prop : structInfo.getProperties().values()) {
            if (!prop.hasField() || !prop.getFieldIdentity().getNamespace().equals(thisId)) {
                continue; // not our responsibility
            }
            assembleField(className, classBuilder, prop);

            if (prop.isConstant()) {
                constProps.add(prop);
            } else if (prop.isInitialized()) {
                initProps.add(prop);
            }
        }

        boolean isSingleton = typeInfo.isSingleton();
        if (isSingleton) {
            // public static final $INSTANCE;
            classBuilder.withField(Instance, ClassDesc.of(className),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }

        if (!constProps.isEmpty() || isSingleton) {
            assembleStaticInitializer(className, classBuilder, constProps);
        }

        switch (classStruct.getFormat()) {
        case CLASS, CONST, SERVICE, MODULE, ENUMVALUE:
            assembleInitializer(className, classBuilder, initProps);
            break;
        }

        for (PropertyInfo prop : typeInfo.getProperties().values()) {
            if (!prop.getIdentity().getNamespace().equals(thisId)) {
                continue; // not our responsibility
            }
            assembleImplProperty(className, classBuilder, prop);
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

        case MultiSlotPrimitive:
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
                        Slot   slot    = BuildContext.loadConstant(ts, code, prop.getInitialValue());
                        String jitName = prop.getIdentity().ensureJitPropertyName(ts);
                        if (slot instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code
                                .iconst_0()
                                .if_icmpne(ifTrue);
                                code.putstatic(CD_this, jitName +EXT, CD_boolean);
                            code.goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putstatic(CD_this, jitName, slot.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert slot.isSingle();
                            code.putstatic(CD_this, jitName, slot.cd());
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
                    invokeDefaultConstructor(className, code);
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
                code.labelBinding(endScope)
                    .return_();
            }));
    }

    /**
     * Call the default constructor.
     */
    protected void invokeDefaultConstructor(String className, CodeBuilder code) {
        ClassDesc CD_this = ClassDesc.of(className);
        code.new_(CD_this)
            .dup()
            .aload(0) // $ctx
            .invokespecial(CD_this, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx));
   }

    /**
     * Add fields initialization to the Java constructor {@code void <init>(Ctx ctx)}.
     */
    protected void assembleInitializer(String className, ClassBuilder classBuilder,
                                       List<PropertyInfo> props) {
        ClassDesc CD_this = ClassDesc.of(className);

        classBuilder.withMethod(INIT_NAME,
            MD_Initializer,
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code -> {
                Label startScope = code.newLabel();
                Label endScope   = code.newLabel();
                code.labelBinding(startScope);

                int ctxSlot = code.parameterSlot(0);
                code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);

                // super($ctx);
                code.aload(0)
                    .aload(ctxSlot)
                    .invokespecial(getSuperDesc(), INIT_NAME, MD_Initializer);

                // add field initialization
                TypeSystem ts = typeSystem;
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        code.aload(0); // Stack: { this }

                        Slot   slot    = BuildContext.loadConstant(ts, code, prop.getInitialValue());
                        String jitName = prop.getIdentity().ensureJitPropertyName(ts);
                        if (slot instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code
                                .iconst_0()
                                .if_icmpne(ifTrue)
                                .putfield(CD_this, jitName +EXT, CD_boolean);
                            code.goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putfield(CD_this, jitName, doubleSlot.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert slot.isSingle();
                            code.putfield(CD_this, jitName, slot.cd());
                        }
                    } else {
                        throw new UnsupportedOperationException("Field initializer");
                    }
                }
                code.labelBinding(endScope)
                    .return_();
            })
        );
    }

    /**
     * Assemble the property accessors for the "Impl" shape.
     */
    protected void assembleImplProperty(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        MethodInfo getterInfo = typeInfo.getMethodById(prop.getGetterId());
        if (getterInfo == null) {
            if (prop.hasField() && prop.getFieldIdentity().getNamespace().equals(thisId)) {
                generateTrivialGetter(className, classBuilder, prop);
            }
        } else {
            switch (getterInfo.getHead().getImplementation()) {
            case Field:
                generateTrivialGetter(className, classBuilder, prop);
                break;
            case Explicit:
                String         jitName = prop.getGetterId().ensureJitMethodName(typeSystem);
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
            if (prop.hasField() && prop.getFieldIdentity().getNamespace().equals(thisId)) {
                generateTrivialSetter(className, classBuilder, prop);
            }
        } else {
            switch (getterInfo.getHead().getImplementation()) {
            case Field:
                generateTrivialSetter(className, classBuilder, prop);
                break;

            case Explicit:
                String         jitName = prop.getSetterId().ensureJitMethodName(typeSystem);
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
        String jitGetterName = prop.getGetterId().ensureJitMethodName(typeSystem);
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

                case MultiSlotPrimitive:
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
        String jitSetterName = prop.getSetterId().ensureJitMethodName(typeSystem);
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
            int argSlot = code.parameterSlot(1); // compensate for ctx$
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

                case MultiSlotPrimitive:
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
     * Assemble methods for the "Impl" shape.
     */
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        for (MethodInfo method : typeInfo.getMethods().values()) {
            if (!method.getIdentity().getNamespace().equals(thisId)) {
                continue; // not our responsibility
            }

            assembleImplMethod(className, classBuilder, method);
        }
    }

    /**
     * Assemble the method(s) for the "Impl" shape of the specified Ecstasy method.
     */
    protected void assembleImplMethod(String className, ClassBuilder classBuilder, MethodInfo method) {
        boolean cap    = method.isCapped();
        boolean router = false;

        String jitName = method.getIdentity().ensureJitMethodName(typeSystem);

        if (!cap) {
            MethodBody[] chain = method.ensureOptimizedMethodChain(typeInfo);
            int          depth = chain.length;
            if (depth > 0) {
                if (chain[0].getImplementation() == Implementation.Delegating) {
                    router = true;
                } else if (depth > 1) {
                    String nextJitName = chain[1].getIdentity().ensureJitMethodName(typeSystem);
                    router = !jitName.equals(nextJitName);
                }
            }
        }

        if (cap || router) {
            MethodInfo targetMethod = cap ? typeInfo.getNarrowingMethod(method) : method;
            assert targetMethod != null;
            assembleRoutingMethod(className, classBuilder, method, targetMethod);
        } else {
            JitMethodDesc jmDesc = method.getJitDesc(typeSystem);
            if (jmDesc.isOptimized) {
                assembleMethod(className, classBuilder, method, jitName+OPT, jmDesc.optimizedMD, true);
                assembleMethodWrapper(className, classBuilder, jitName, jmDesc,
                    method.isFunction(), method.isConstructor());
            } else {
                assembleMethod(className, classBuilder, method, jitName, jmDesc.standardMD, false);
            }

            if (method.isConstructor()) {
                String        newName = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
                JitMethodDesc newDesc = Builder.convertConstructToNew(typeInfo, className, jmDesc);
                if (newDesc.isOptimized) {
                    assembleNew(className, classBuilder, method, newName+OPT, newDesc.optimizedMD, true);
                    assembleMethodWrapper(className, classBuilder, newName, newDesc, true, false);
                }
                else {
                    assembleNew(className, classBuilder, method, newName, newDesc.standardMD, false);
                }
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
                        unbox(code, typeSystem, stdParamType, optParamDesc.cd);
                        break;

                    case PrimitiveWithDefault: {
                        // if the argument is Java `null`, pass the default value for the type and
                        // `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code
                           .aload(stdParamSlot)
                           .aconst_null()
                           .if_acmpne(ifNotNull);
                        // the value is `null`
                        Builder.defaultLoad(code, optParamDesc.cd); // default primitive
                        code.iconst_1();                            // true

                        code
                            .goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(stdParamSlot);
                        unbox(code, typeSystem, stdParamType, optParamDesc.cd); // unwrapped primitive
                        code.iconst_0();                                        // false

                        code.labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case MultiSlotPrimitive: {
                        assert stdParamType.isNullable();
                        TypeConstant primitiveType = stdParamType.getUnderlyingType();
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
                        unbox(code, typeSystem, primitiveType, optParamDesc.cd); // unboxed primitive
                        code.iconst_0();                                         // false

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
                        box(code, typeSystem, optType, optCD);
                        code.areturn();
                    } else {
                        loadFromContext(code, optCD, optRetIx);
                        box(code, typeSystem, optType, optCD);
                        storeToContext(code, stdCD, stdRetIx);
                    }
                    break;

                case MultiSlotPrimitive:
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

                    box(code, typeSystem, optType, optCD);
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
     *      val o = new C(x, y, z);
     * Java:
     *      C o = C.$new$0(ctx, x, y, z);
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
     * public static C $new$0(Ctx ctx, X x, Y y, Z z) {
     *    // note: singletons use this signature instead:
     *    public C $init$0(Ctx ctx)
     *
     *    // step 1: ONLY FOR "anonymous inner classes": a "wrapper constructor" (in lieu
     *    //   of steps 3 and 5) (TODO GG)
     *
     *    // step 2: get permission to use the memory
     *    // note: singletons move this step to the Java static initializer
     *    ctx.alloc(32); // the RAM size is calc'd by the TypeInfo or compiler
     *
     *    // step 3 (initializer) gets handled in the Java constructor for the class
     *    // - the constructor needs the context for any allocations
     *    // - this inits any fields that are not supposed to be null (reference
     *    //   types) or not supposed to be 0 etc. (primitive types)
     *    // note: singletons move this step to the Java static initializer
     *    C thi$ = new C(ctx);
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
     *    construct$0(ctx, cctx, thi$, x, y, z);
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
     *    finally$0(ctx, cctx, thi$, x, y, z);
     * }
     *
     * // assuming C extends B and B has a finally that takes some (P p, Q q):
     * public static construct$0(CtorCtx cctx, C thi$, X x, Y y, Z z) {
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
     * public static finally$0(CtorCtx cctx, C thi$, X x, Y y, Z z) {
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
                               String jitName, MethodTypeDesc md, boolean isOptimized) {
        boolean   isSingleton = typeInfo.isSingleton();
        ClassDesc CD_this     = ClassDesc.of(className);

        // Note: the "$init" is a virtual method for singletons and "$new" is static otherwise
        //       (see assembleStaticInitializer)
        int flags = ClassFile.ACC_PUBLIC;
        if (!isSingleton) {
            flags |= ClassFile.ACC_STATIC;
        }

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            Label startScope = code.newLabel();
            Label endScope   = code.newLabel();

            code.labelBinding(startScope);

            int ctxSlot = code.parameterSlot(0);
            code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);

            // for singleton classes the steps 0-2 are performed by the static initializer;
            // see "assembleStaticInitializer()"
            int thisSlot;
            if (isSingleton) {
                thisSlot = 0;
            } else {
                // step 0 TODO anonymous classes support

                // get permission to use the memory
                code.aload(ctxSlot)
                    .ldc(implSize)
                    .invokevirtual(CD_Ctx, "alloc", MethodTypeDesc.of(CD_void, CD_long));

                // step 1 (initializer)
                thisSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.localVariable(thisSlot, "thi$", CD_this, startScope, endScope);
                invokeDefaultConstructor(className, code);
                code.astore(thisSlot);

                // TODO step 2: execute annotation constructors

            }

            // step 3: call the constructor;
            // a constructor context is required if a “finally” chain will exist
            // CtorCtx cctx = ctx.ctorCtx();
            // construct$0(ctx, cctx, thi$, x, y, z);

            int cctxSlot = code.allocateLocal(TypeKind.REFERENCE);
            code.localVariable(cctxSlot, "cctx", CD_CtorCtx, startScope, endScope);
            code.aload(ctxSlot)
                .invokevirtual(CD_Ctx, "ctorCtx", MethodTypeDesc.of(CD_CtorCtx))
                .astore(cctxSlot)
            ;


            String        ctorName = constructor.getIdentity().ensureJitMethodName(typeSystem);
            JitMethodDesc ctorDesc = constructor.getJitDesc(typeSystem);

            code.aload(ctxSlot)
                .aload(cctxSlot)
                .aload(thisSlot);

            // if this "new$" is optimized, the underlying constructor is optimized and vice versa
            assert isOptimized == ctorDesc.isOptimized;

            JitParamDesc[] ctorPds;
            MethodTypeDesc ctorMd;
            if (isOptimized) {
                ctorName += OPT;
                ctorPds   = ctorDesc.optimizedParams;
                ctorMd    = ctorDesc.optimizedMD;
            } else {
                ctorPds = ctorDesc.standardParams;
                ctorMd  = ctorDesc.standardMD;
            }

            for (int i = 0, c = ctorPds.length; i < c; i++) {
                JitParamDesc pd = ctorPds[i];
                load(code, pd.cd, code.parameterSlot(1 + i));
            }

            code.invokestatic(CD_this, ctorName, ctorMd);

            // step 4:
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

        BuildContext bctx = new BuildContext(typeSystem, typeInfo, prop, isGetter);

        classBuilder.withMethod(jitName, md, flags,
            methodBuilder -> {
                if (!prop.isAbstract()) {
                    methodBuilder.withCode(code ->
                        generateCode(className, md, bctx, code));
                }
            }
        );
    }

    /**
     * Assemble the method (optimized if possible, standard otherwise).
     */
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, MethodTypeDesc md, boolean isOptimized) {
        int flags = ClassFile.ACC_PUBLIC;
        if (method.isAbstract()) {
            flags |= ClassFile.ACC_ABSTRACT;
        }
        if (method.isFunction() || method.isConstructor()) {
            flags |= ClassFile.ACC_STATIC;
        }

        BuildContext bctx = new BuildContext(typeSystem, typeInfo, method);

        classBuilder.withMethod(jitName, md, flags,
            methodBuilder -> {
                if (!method.isAbstract()) {
                    methodBuilder.withCode(code ->
                        generateCode(className, md, bctx, code));
                }
            }
        );
    }

    protected void generateCode(String className, MethodTypeDesc md, BuildContext bctx,
                                CodeBuilder code) {

        bctx.enterMethod(code);

        if (Arrays.stream(TEST_SET).anyMatch(name -> className.toLowerCase().contains(name))) {
            Op[] ops = bctx.methodStruct.getOps();
            for (Op op : ops) {
                op.preprocess(bctx, code);
            }

            // the collected labels need to be bound before the corresponding ops being processed
            var   labels     = bctx.labels;
            int   labelCount = labels.size();
            int   nextIndex  = 0;
            int   nextAddr   = -1;
            Label nextLabel  = null;
            if (labelCount > 0) {
                var nextEntry = labels.entryAt(0);
                nextAddr  = nextEntry.getKey();
                nextLabel = nextEntry.getValue();
            }
            for (int i = 0, c = ops.length; i < c; i++) {
                if (i == nextAddr) {
                    code.labelBinding(nextLabel);

                    if (++nextIndex < labelCount) {
                        var nextEntry = labels.entryAt(nextIndex);
                        nextAddr  = nextEntry.getKey();
                        nextLabel = nextEntry.getValue();
                    } else {
                        nextAddr = -1;
                    }
                }

                ops[i].build(bctx, code);
            }
        } else {
            if (SKIP_SET.add(className)) {
                System.err.println("*** Skipping code gen for " + className);
            }
            defaultLoad(code, md.returnType());
            addReturn(code, md.returnType());
        }
        bctx.exitMethod(code);
    }

    private final static String[] TEST_SET = new String[] {"test", "tck"};
    private final static HashSet<String> SKIP_SET = new HashSet<>();
}