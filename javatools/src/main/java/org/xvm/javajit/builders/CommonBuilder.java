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
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodInfo;
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

import static java.lang.constant.ConstantDescs.CD_boolean;
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

    @Override
    public void assembleImpl(String className, ClassBuilder classBuilder) {
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

        boolean isSingleton = classStruct.isSingleton();
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
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        Slot   slot         = BuildContext.loadConstant(code, prop.getInitialValue());
                        String jitFieldName = prop.getIdentity().ensureJitPropertyName(typeSystem);
                        if (slot instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code
                                .iconst_0()
                                .if_icmpne(ifTrue);
                                code.putstatic(CD_this, jitFieldName+EXT, CD_boolean);
                            code.goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putstatic(CD_this, jitFieldName, slot.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert slot.isSingle();
                            code.putstatic(CD_this, jitFieldName, slot.cd());
                        }
                    } else {
                        throw new UnsupportedOperationException("Static field initializer");
                    }
                }

                if (classStruct.isSingleton()) {
                    // $INSTANCE = Singleton.$new($ctx);
                    invokeDefaultConstructor(className, code);
                    code.putstatic(CD_this, Instance, CD_this);
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
                for (PropertyInfo prop : props) {
                    if (prop.getInitializer() == null) {
                        code.aload(0); // Stack: { this }

                        Slot   slot         = BuildContext.loadConstant(code, prop.getInitialValue());
                        String jitFieldName = prop.getIdentity().ensureJitPropertyName(typeSystem);
                        if (slot instanceof DoubleSlot doubleSlot) {
                            assert doubleSlot.flavor() == JitFlavor.MultiSlotPrimitive;
                            // loadConstant() has already loaded the value and the boolean
                            Label ifTrue = code.newLabel();
                            Label endIf  = code.newLabel();
                            code
                                .iconst_0()
                                .if_icmpne(ifTrue)
                                .putfield(CD_this, jitFieldName+EXT, CD_boolean);
                            code.goto_(endIf)
                                .labelBinding(ifTrue);
                                pop(code, doubleSlot.cd());
                                code.putfield(CD_this, jitFieldName, doubleSlot.cd());
                            code.labelBinding(endIf);
                        } else {
                            assert slot.isSingle();
                            code.putfield(CD_this, jitFieldName, slot.cd());
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
                boolean        isOpt   = jmDesc.optimizedMD != null;
                MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
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
                boolean        isOpt   = jmDesc.optimizedMD != null;
                MethodTypeDesc md      = isOpt ? jmDesc.optimizedMD : jmDesc.standardMD;
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
        boolean        opt     = jmd.optimizedMD != null;
        MethodTypeDesc md      = opt ? jmd.optimizedMD : jmd.standardMD;
        String         jitName = opt ? jitGetterName+OPT : jitGetterName;

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            if (opt) {
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

        if (opt) {
            // generate a wrapper
            assembleMethodWrapper(className, classBuilder, jitGetterName, jmd, prop.isConstant());
        }
    }

    private void generateTrivialSetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitGetterName = prop.getSetterId().ensureJitMethodName(typeSystem);
        String jitFieldName  = prop.getIdentity().ensureJitPropertyName(typeSystem);

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
            if (method.isConstructor()){
                if (jmDesc.optimizedMD == null) {
                    assembleConstructor(className, classBuilder, method, jitName, jmDesc.standardMD, false);
                } else {
                    assembleConstructor(className, classBuilder, method, jitName + OPT, jmDesc.standardMD, true);
                    assembleMethodWrapper(className, classBuilder, jitName, jmDesc, method.isFunction());
                }
            } else {
                if (jmDesc.optimizedMD == null) {
                    assembleMethod(className, classBuilder, method, jitName, jmDesc.standardMD, false);
                } else {
                    assembleMethod(className, classBuilder, method, jitName + OPT, jmDesc.optimizedMD, true);
                    assembleMethodWrapper(className, classBuilder, jitName, jmDesc, method.isFunction());
                }
            }
        }
    }

    /**
     * Assemble a "standard" wrapper method for the optimized method.
     */
    protected void assembleMethodWrapper(String className, ClassBuilder classBuilder,
                                         String jitName, JitMethodDesc jmDesc,
                                         boolean isStatic) {
        ClassDesc CD_this = ClassDesc.of(className);

        // this method is "standard" and needs to call into the optimized one
        int flags = ClassFile.ACC_PUBLIC;
        if (isStatic) {
            flags |= ClassFile.ACC_STATIC;
        }

        classBuilder.withMethodBody(jitName, jmDesc.standardMD, flags, code -> {

            // create a method preamble
            Label startScope = code.newLabel();
            Label endScope   = code.newLabel();

            code.labelBinding(startScope);

            int ctxSlot = code.parameterSlot(0);
            code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);

            if (!isStatic) {
                code.aload(0); // stack: this
            }

            code.aload(ctxSlot); // stack: Ctx

            JitParamDesc[] optParams = jmDesc.optimizedParams;
            for (int i = 0, c = optParams.length; i < c; i++) {
                JitParamDesc optParamDesc = optParams[i];
                int          stdParamIx   = optParamDesc.index;
                int          stdParamSlot = code.parameterSlot(1 + stdParamIx);
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

                        code
                           .aload(stdParamSlot)
                           .getstatic(CD_Nullable, "Null", CD_Nullable)
                           .if_acmpne(ifNotNull);
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
                code.invokestatic(CD_this, jitName + OPT, jmDesc.optimizedMD);
            } else {
                code.invokevirtual(CD_this, jitName + OPT, jmDesc.optimizedMD);
            }

            JitParamDesc[] optReturns     = jmDesc.optimizedReturns;
            int            optReturnCount = optReturns.length;
            JitParamDesc[] stdReturns     = jmDesc.standardReturns;
            int            stdReturnCount = stdReturns.length;
            if (optReturnCount == 0) {
                code.return_();
                code.labelBinding(endScope);
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
                    code.labelBinding(ifNull)
                        .getstatic(CD_Nullable, "Null", CD_Nullable);

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
        code.labelBinding(endScope);
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
     * Assemble the constructor(s).
     */
    protected void assembleConstructor(String className, ClassBuilder classBuilder, MethodInfo constructor,
                                       String jitName, MethodTypeDesc md, boolean optimized) {
        // TODO
    }

    /**
     * Assemble the property accessor (optimized if possible, standard otherwise).
     */
    protected void assemblePropertyAccessor(String className, ClassBuilder classBuilder,
                                            PropertyInfo prop, String jitName, MethodTypeDesc md,
                                            boolean optimized, boolean isGetter) {
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
                                  String jitName, MethodTypeDesc md, boolean optimized) {
        int flags = ClassFile.ACC_PUBLIC;
        if (method.isAbstract()) {
            flags |= ClassFile.ACC_ABSTRACT;
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

        if (className.toLowerCase().contains("test")) {
            Op[] ops = bctx.methodStruct.getOps();
            for (Op op : ops){
                op.build(bctx, code);
            }
        } else {
            defaultLoad(code, md.returnType());
            addReturn(code, md.returnType());
        }
        bctx.exitMethod(code);
    }
}