package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

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
import static java.lang.constant.ConstantDescs.CD_long;


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
        for (Contribution contrib : typeInfo.getContributionList()) {
            switch (contrib.getComposition()) {
                case Implements:
                    TypeConstant contribType = contrib.getTypeConstant().removeAccess();
                    if (contribType.equals(contribType.getConstantPool().typeObject())) {
                        // ignore "implements Object"
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

            int argOffset = 0;
            if (!method.isFunction()) {
                code.aload(argOffset++); // stack: this
            }
            int ctxIndex = argOffset;
            code.aload(argOffset++);     // stack: Ctx

            JitParamDesc[] optimizedParams = jmDesc.optimizedParams;
            for (int i = 0, c = optimizedParams.length; i < c; i++) {
                JitParamDesc thatParamDesc  = optimizedParams[i];
                int          thisParamIndex = argOffset + thatParamDesc.index;
                TypeConstant thisParamType  = thatParamDesc.type;
                switch (thatParamDesc.flavor) {
                    case Specific, Widened:
                        code.aload(thisParamIndex);
                        break;

                    case SpecificWithDefault, WidenedWithDefault:
                        // null indicates "default" value; pass it along regardless
                        code.aload(thisParamIndex);
                        break;

                    case Primitive:
                        code.aload(thisParamIndex);
                        unbox(code, typeSystem, thisParamType, thatParamDesc.cd);
                        break;

                    case PrimitiveWithDefault: {
                        // if the argument is Java `null`, pass the default value for the type and
                        // `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf     = code.newLabel();

                        code
                           .aload(thisParamIndex)
                           .aconst_null()
                           .if_acmpne(ifNotNull);
                        // the value is `null`
                        Builder.defaultLoad(code, thatParamDesc.cd); // default primitive
                        code.iconst_1();                             // true

                        code
                            .goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(thisParamIndex);
                        unbox(code, typeSystem, thisParamType, thatParamDesc.cd); // unwrapped primitive
                        code.iconst_0();                                          // false

                        code.labelBinding(endIf);
                        i++; // skip over the next "that" parameter
                        break;
                    }

                    case MultiSlotPrimitive: {
                        assert thisParamType.isNullable();
                        TypeConstant primitiveType = thisParamType.getUnderlyingType();
                        // if the argument is Ecstasy `Null`, pass the default value for the type
                        // and `true`; otherwise the unboxed primitive value and `false`
                        Label ifNotNull = code.newLabel();
                        Label endIf  = code.newLabel();

                        code
                           .aload(thisParamIndex)
                           .getstatic(CD_Null, "Null", CD_Null)
                           .if_acmpne(ifNotNull);
                        // the value is `Null`
                        Builder.defaultLoad(code, thatParamDesc.cd);  // default primitive
                        code.iconst_1();                              // true

                        code
                            .goto_(endIf)
                            .labelBinding(ifNotNull)
                            .aload(thisParamIndex);
                        unbox(code, typeSystem, primitiveType, thatParamDesc.cd); // unboxed primitive
                        code.iconst_0();                                          // false

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

            JitParamDesc[] optimizedReturns = jmDesc.optimizedReturns;
            int            thatReturnCount  = optimizedReturns.length;
            JitParamDesc[] standardReturns  = jmDesc.standardReturns;
            int            thisReturnCount  = standardReturns.length;
            if (thatReturnCount == 0) {
                code.return_();
                return;
            }

            // the natural return is at the top of the stack now; iterate returns in the inverse order
            for (int thatIx = thatReturnCount-1, thisIx = thisReturnCount-1; thatIx >= 0; thatIx--) {
                JitParamDesc thatDesc = optimizedReturns[thatIx];
                if (thatDesc.extension) {
                    // since we are in the reverse order, the "actual" return will use this value
                    continue;
                }
                ClassDesc    thatCD       = thatDesc.cd;
                TypeConstant thatType     = thatDesc.type;
                int          thatReturnIx = thatDesc.index;

                JitParamDesc thisDesc     = standardReturns[thisIx--];
                ClassDesc    thisCD       = thisDesc.cd;
                int          thisReturnIx = thisDesc.index;

                switch (thatDesc.flavor) {
                    case Specific, Widened:
                        if (thatIx == 0) {
                            // natural return
                            code.areturn();
                        } else {
                            if (thatReturnIx != thisReturnIx) {
                                loadFromContext(code, thatCD, ctxIndex, thatReturnIx);
                                storeToContext(code, thisCD, ctxIndex, thisReturnIx);
                            }
                        }
                        break;

                    case Primitive:
                        if (thatIx == 0) {
                            // natural return
                            box(code, typeSystem, thatType, thatCD);
                            code.areturn();
                        } else {
                            loadFromContext(code, thatCD, ctxIndex, thatReturnIx);
                            box(code, typeSystem, thatType, thatCD);
                            storeToContext(code, thisCD, ctxIndex, thisReturnIx);
                        }
                        break;

                    case MultiSlotPrimitive:
                        assert thatType.isNullable();
                        TypeConstant primitiveType = thatType.getUnderlyingType();

                        // if the extension is 'true', return is "Null", otherwise the unboxed
                        // primitive value
                        Label ifNull = code.newLabel();
                        Label endIf  = code.newLabel();

                        loadFromContext(code, CD_boolean, ctxIndex, thatIx+1);
                        code
                            .iconst_1()
                            .if_icmpeq(ifNull)  // if true, go to Null
                            ;

                        box(code, typeSystem, primitiveType, thatCD);
                        if (thatIx == 0) {
                            code.areturn();
                        } else {
                            storeToContext(code, thisCD, ctxIndex, thisIx);
                        }
                        code
                            .goto_(endIf)
                            .labelBinding(ifNull)
                            .getstatic(CD_Null, "Null", CD_Null);

                        if (thatIx == 0) {
                            code.areturn();
                        } else {
                            storeToContext(code, thisCD, ctxIndex, thisIx);
                        }

                        code.labelBinding(endIf);
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

        Label startScope = code.newLabel();
        Label endScope   = code.newLabel();
        code
            .labelBinding(startScope)
            .localVariable(code.parameterSlot(0), "$ctx", CD_Ctx, startScope, endScope)
            ;

        if (className.contains("test0") && method.getSignature().getName().equals("run")) {
            BuildContext bctx = new BuildContext(typeSystem, method);

            bctx.enterMethod(code);
            for (Op op : methodStruct.getOps()){
                op.build(bctx, code);
            }
        } else {
            code.lineNumber(methodStruct.getSourceLineNumber());

            defaultLoad(code, md.returnType());
            defaultReturn(code, md.returnType());
        }
        code.labelBinding(endScope);
    }

    /**
     * Generate a "load the return value from the context" for the specified Java class.
     * Out: The loaded value is at the stack top.
     *
     * @param returnIndex  the index of the value in the Ctx object
     */
    protected void loadFromContext(CodeBuilder code, ClassDesc cd, int ctxIndex, int returnIndex) {
        assert returnIndex >= 0;

        code.aload(ctxIndex);

        if (cd.isPrimitive()) {
            if (returnIndex < 8) {
                code // r = $ctx.i"returnIndex"
                    .getfield(CD_Ctx, "i" + (returnIndex), CD_long);
            } else {
                code // r = $ctx.iN[returnIndex-8]
                    .getfield(CD_Ctx, "iN", CD_long.arrayType())
                    .loadConstant(returnIndex-8)
                    .aaload();
            }

            // convert the long to the corresponding Java primitive
            switch (cd.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    code.l2i();
                    break;
                case "J":
                    break;
                case "F":
                    code.l2f();
                    break;
                case "D":
                    code.l2d();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else {
            if (returnIndex < 8) {
                code // r = $ctx.o"returnIndex"
                    .getfield(CD_Ctx, "o" + (returnIndex-1), CD_Object);
            } else {
                code // r = $ctx.oN[returnIndex-8]
                    .getfield(CD_Ctx, "oN", CD_Object.arrayType())
                    .loadConstant(returnIndex-8)
                    .aaload();
            }
        }
    }

    /**
     * Generate a "store the return value to the context" for the specified Java class.
     * In: The value to store is at the stack top.
     */
    protected void storeToContext(CodeBuilder code, ClassDesc cd, int ctxIndex, int returnIndex) {
        assert returnIndex >= 0;

        code.aload(ctxIndex)
                   .swap(); // stack: ($ctx, value)

        if (cd.isPrimitive()) {
            // all primitives are stored into "long" fields; convert
            switch (cd.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    code.i2l();
                    break;
                case "J":
                    break;
                case "F":
                    code.f2l();
                    break;
                case "D":
                    code.d2l();
                    break;
                default:
                    throw new IllegalStateException();
            }

            if (returnIndex < 8) {
                code // $ctx.i"returnIndex" = r
                    .putfield(CD_Ctx, "i" + returnIndex, CD_long);
            } else {
                // TODO: replace with a helper "Ctx.storeLong(i-8, value)"
                code // $ctx.iN[returnIndex-8] = r
                    .getfield(CD_Ctx, "iN", CD_long.arrayType())
                    .loadConstant(returnIndex-8)
                    .aastore();
            }
        } else {
            if (returnIndex < 8) {
                code // $ctx.o"returnIndex" = r
                    .putfield(CD_Ctx, "o" + (returnIndex), CD_Object);
            } else {
                // TODO: replace with a helper "Ctx.storeRef(i-8, value)"
                code // $ctx.oN[returnIndex-8] = r
                    .getfield(CD_Ctx, "oN", CD_Object.arrayType())
                    .loadConstant(returnIndex-8)
                    .aastore();
            }
        }
    }
}
