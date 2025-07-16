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

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

/**
 * Generic Java class builder.
 */
public class CommonBuilder
        implements Builder {
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
        // TODO
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
                    methodBuilder.withCode(codeBuilder ->
                        generateCode(className, codeBuilder, method, md, optimized));
                }
            }
        );
    }

    protected void generateCode(String className, CodeBuilder codeBuilder,
                                MethodInfo method, MethodTypeDesc md, boolean optimized) {

        MethodStructure methodStruct = method.getTopmostMethodStructure(typeInfo);
        assert methodStruct != null;

        Label startScope = codeBuilder.newLabel();
        Label endScope   = codeBuilder.newLabel();
        int   nLine      = methodStruct.getSourceLineNumber();
        codeBuilder
            .labelBinding(startScope)

            .lineNumber(nLine)
            .localVariable(0, "$ctx", CD_Ctx, startScope, endScope)

            .lineNumber(++nLine)
            .labelBinding(endScope)
            ;
        addDefaultReturn(codeBuilder, md.returnType());
    }

    /**
     * Generate a default return for the specified Java class.
     */
    private void addDefaultReturn(CodeBuilder codeBuilder, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    codeBuilder.iconst_0().ireturn();
                    break;
                case "J":
                    codeBuilder.lconst_0().lreturn();
                    break;
                case "F":
                    codeBuilder.fconst_0().freturn();
                    break;
                case "D":
                    codeBuilder.dconst_0().dreturn();
                    break;
                case "V":
                    codeBuilder.return_();
                    break;
            }
        } else {
            codeBuilder.aconst_null().areturn();
        }
    }
}
