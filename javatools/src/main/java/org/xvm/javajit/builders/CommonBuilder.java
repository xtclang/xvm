package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        extends Builder {
    public CommonBuilder(TypeSystem typeSystem, TypeConstant type) {
        assert type.isSingleUnderlyingClass(true);

        this.typeSystem = typeSystem;
        this.typeInfo   = type.ensureAccess(Constants.Access.PRIVATE).ensureTypeInfo();
    }

    protected final TypeSystem typeSystem;
    protected final TypeInfo   typeInfo;

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
     * Assemble the class specific info for the "Impl" shape.
     */
    protected void assembleImplClass(String className, ClassBuilder classBuilder) {
        int flags = ClassFile.ACC_PUBLIC;

        switch (typeInfo.getClassStructure().getFormat()) {
            case CLASS, CONST, SERVICE, MIXIN, ENUMVALUE:
                flags |= ClassFile.ACC_SUPER; // see JLS 4.1
                TypeConstant superType = typeInfo.getExtends();
                if (superType != null) {
                    classBuilder.withSuperclass(
                        ClassDesc.of(typeSystem.ensureJitClassName(superType)));
                }
                break;

            case INTERFACE, ENUM:
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
        boolean isInterface =
            typeInfo.getClassStructure().getFormat() == Component.Format.INTERFACE;
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

    }

    /**
     * Assemble methods for the "Impl" shape.
     */
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        IdentityConstant thisId = typeInfo.getClassStructure().getIdentityConstant();

        for (MethodInfo method : typeInfo.getMethods().values()) {
            if (!method.getIdentity().getNamespace().equals(thisId)) {
                continue; // not our responsibility
            }

            assembleImplMethod(className, classBuilder, method);
        }
    }

    /**
     * Assemble the specified method for the "Impl" shape.
     */
    protected void assembleImplMethod(String className, ClassBuilder classBuilder, MethodInfo method) {
        boolean cap    = method.isCapped();
        boolean router = false;
        if (!cap) {
            MethodBody[] chain = method.getChain(); // REVIEW: do we need optimized chain?
            MethodBody   head  = chain[0];
            if (head.getImplementation() == Implementation.Delegating) {
                router = true;
            } else if (chain.length > 1) {
                String headJitName = head    .getSignature().ensureJitMethodName(typeSystem);
                String nextJitName = chain[1].getSignature().ensureJitMethodName(typeSystem);
                router = !headJitName.equals(nextJitName);
            }
        }

        String jitName = method.getSignature().ensureJitMethodName(typeSystem);

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
                    assembleOptWrapper(className, classBuilder, method, jitName, jmDesc);
                }
            } else {
                if (jmDesc.optimizedMD == null) {
                    assembleMethod(className, classBuilder, method, jitName, jmDesc.standardMD, false);
                } else {
                    assembleMethod(className, classBuilder, method, jitName + OPT, jmDesc.optimizedMD, true);
                    assembleOptWrapper(className, classBuilder, method, jitName, jmDesc);
                }
            }
        }
    }

    /**
     * Assemble a wrapper method for optimized flavor.
     */
    protected void assembleOptWrapper(String className, ClassBuilder classBuilder,
                                      MethodInfo method, String jitName, JitMethodDesc jmDesc) {
        ClassDesc CD_this = ClassDesc.of(className);

        // this method is "standard" and needs to call into the optimized one
        int flags = ClassFile.ACC_PUBLIC;
        if (method.isFunction()) {
            flags |= ClassFile.ACC_STATIC;
        }

        classBuilder.withMethodBody(jitName, jmDesc.standardMD, flags, code -> {

            // create a method preamble
            Label startScope = code.newLabel();
            Label endScope   = code.newLabel();

            code.labelBinding(startScope);

            int ctxSlot = code.parameterSlot(0);
            code.localVariable(ctxSlot, "$ctx", CD_Ctx, startScope, endScope);

            if (!method.isFunction()) {
                code.aload(0); // stack: this
            }

            code.aload(ctxSlot); // stack: Ctx

            JitParamDesc[] optParams = jmDesc.optimizedParams;
            JitParamDesc[] stdParams = jmDesc.standardParams;
            Parameter[]    asmParams = method.getHead().getMethodStructure().getParamArray();
            for (int i = 0, c = optParams.length; i < c; i++) {
                JitParamDesc optParamDesc = optParams[i];
                int          stdParamIx   = optParamDesc.index;
                int          stdParamSlot = code.parameterSlot(1 + stdParamIx);
                TypeConstant stdParamType = optParamDesc.type;

                code.localVariable(stdParamSlot, asmParams[stdParamIx].getName(),
                        stdParams[stdParamIx].cd, startScope, endScope);

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
            if (method.isFunction()) {
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
                    code
                        .iconst_1()
                        .if_icmpeq(ifNull)  // if true, go to Null
                        ;

                    box(code, typeSystem, optType, optCD);
                    if (optIx == 0) {
                        code.areturn();
                    } else {
                        storeToContext(code, stdCD, stdIx);
                        code.goto_(endIf);
                    }
                    code
                        .labelBinding(ifNull)
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
     * Assemble a "routing" method (could be more than one Java method).
     */
    protected void assembleRoutingMethod(String className, ClassBuilder classBuilder,
                                         MethodInfo srcMethod, MethodInfo dstMethod) {
        // TODO
    }

    /**
     * Assemble a constructor (could be more than one Java method).
     */
    protected void assembleConstructor(String className, ClassBuilder classBuilder, MethodInfo constructor,
                                       String jitName, MethodTypeDesc md, boolean optimized) {
        // TODO
    }

    /**
     * Assemble a standard method (could be more than one Java method).
     */
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, MethodTypeDesc md, boolean optimized) {
        int flags = ClassFile.ACC_PUBLIC;
        if (method.isAbstract()) {
            flags |= ClassFile.ACC_ABSTRACT;
        }

        classBuilder.withMethod(jitName, md, flags,
            methodBuilder -> {
                if (!method.isAbstract()) {
                    methodBuilder.withCode(code ->
                        generateCode(className, code, method, md, optimized));
                }
            }
        );
    }

    protected void generateCode(String className, CodeBuilder code,
                                MethodInfo method, MethodTypeDesc md, boolean optimized) {

        MethodStructure methodStruct = method.getTopmostMethodStructure(typeInfo);
        assert methodStruct != null;

        BuildContext bctx = new BuildContext(typeSystem, typeInfo, method);
        bctx.enterMethod(code);

        if (className.contains("test0")) {
            Op[] ops = methodStruct.getOps();
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