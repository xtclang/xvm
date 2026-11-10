package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * Builds a reference view that routes property operations to the containing object.
 */
public class PropertyRefBuilder extends Builder {
    public PropertyRefBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
        propertyType = (PropertyClassTypeConstant) art.type();
    }

    private final PropertyClassTypeConstant propertyType;

    @Override
    public ClassDesc getSuperCD() {
        return CD_nRefProp;
    }

    @Override
    public void build(ClassBuilder classBuilder) {
        classBuilder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                    .withSuperclass(CD_nRefProp);

        PropertyInfo     property = propertyType.getPropertyInfo();
        PropertyConstant propId   = property.getIdentity();
        TypeConstant     owner    = propertyType.getParentType();
        List<MethodInfo> methods  = owner.ensureAccess(Access.PRIVATE)
                .ensureTypeInfo().getMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getNamespace().equals(propId))
                .map(Map.Entry::getValue).toList();

        List<ClassDesc> interfaces = new ArrayList<>();
        for (Annotation annotation :
                AnnotationHelper.filterAnnotations(property.getBaseRefType()).getAnnotations()) {
            interfaces.add(getCCDesc(annotation.getAnnotationType()));
        }
        classBuilder.withInterfaceSymbols(interfaces);

        MethodTypeDesc init = md(CD_void, CD_Ctx, CD_TypeConstant, CD_boolean,
                CD_nObject, CD_MethodHandle, CD_MethodHandle, CD_MethodHandle);
        classBuilder.withMethodBody(INIT_NAME, init, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            for (int i = 0; i < init.parameterCount(); i++) {
                load(code, init.parameterType(i), code.parameterSlot(i));
            }
            code.invokespecial(CD_nRefProp, INIT_NAME, init)
                .return_();
        });

        Set<String> emitted = new HashSet<>();
        for (MethodInfo method : methods) {
            for (MethodBody body : method.getChain()) {
                Implementation implementation = body.getImplementation();
                if (implementation != Implementation.Explicit &&
                        implementation != Implementation.Native &&
                        implementation != Implementation.Default) {
                    continue;
                }

                MethodConstant methodId = body.getIdentity();
                if (methodId.getNamespace() instanceof PropertyConstant nested &&
                        !property.containsBody(nested)) {
                    continue;
                }

                String            opName = methodId.getName();
                SignatureConstant sig    = body.getSignature();
                if (!(opName.equals("get") && sig.getParamCount() == 0 ||
                      opName.equals("set") && sig.getParamCount() == 1)) {
                    continue;
                }

                JitMethodDesc desc = body.getJitDesc(this, property.getBaseRefType());
                String        name = methodId.ensureJitMethodName(typeSystem);

                assembleAccessor(classBuilder, property, opName, name, desc, false, emitted);
                if (desc.isOptimized) {
                    assembleAccessor(classBuilder, property, opName, name + OPT, desc, true, emitted);
                }

                TypeConstant  targetType = methodId.getClassIdentity().getFormalType().getJitCCType();
                JitMethodDesc jmd        = body.getJitDesc(this, targetType);

                assembleAccessor(classBuilder, property, opName, name, jmd, false, emitted);
                if (jmd.isOptimized) {
                    assembleAccessor(classBuilder, property, opName, name + OPT, jmd, true, emitted);
                }
            }
        }

        String prefix = property.ensureJitName(typeSystem);
        for (MethodInfo method : methods) {
            if (method.isNative() || method.isFunction() || method.isCtorOrValidator() ||
                    method.getAccess() == Access.PRIVATE ||
                    !isEmbedded(method.getHead().getIdentity().getNamespace().getComponent().getFormat())) {
                continue;
            }

            MethodConstant methodId = method.getIdentity();
            if (methodId.getName().equals("get") || methodId.getName().equals("set")) {
                continue;
            }

            String        methodName = method.ensureJitMethodName(typeSystem);
            TypeConstant  targetType = methodId.getClassIdentity().getFormalType().getJitCCType();
            JitMethodDesc jmd        = method.getJitDesc(this, targetType);
            String        targetName = prefix + '$' + methodName;

            assembleMember(classBuilder, owner, targetName, methodName, jmd, false, emitted);
            if (jmd.isOptimized) {
                assembleMember(classBuilder, owner, targetName, methodName + OPT, jmd, true, emitted);
            }
        }
    }

    private void assembleMember(ClassBuilder classBuilder, TypeConstant owner, String targetName,
                                String name, JitMethodDesc desc, boolean optimized, Set<String> emitted) {
        MethodTypeDesc md = optimized ? desc.optimizedMD : desc.standardMD;
        if (!emitted.add(name + md.descriptorString())) {
            return;
        }

        ClassDesc ownerCD = getCCDesc(owner);
        classBuilder.withMethodBody(name, md, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0).getfield(CD_nRefProp, Outer, CD_nObject).checkcast(ownerCD);
            for (int i = 0; i < md.parameterCount(); i++) {
                load(code, md.parameterType(i), code.parameterSlot(i));
            }
            String target = targetName + (optimized ? OPT : "");
            if (owner.isJitCCInterface()) {
                code.invokeinterface(ownerCD, target, md);
            } else {
                code.invokevirtual(ownerCD, target, md);
            }
            addReturn(code, md.returnType());
        });
    }

    private void assembleAccessor(ClassBuilder classBuilder, PropertyInfo property,
                                   String operation, String name, JitMethodDesc desc,
                                   boolean optimized, Set<String> emitted) {
        MethodTypeDesc md = optimized ? desc.optimizedMD : desc.standardMD;
        if (!emitted.add(name + md.descriptorString())) {
            return;
        }

        classBuilder.withMethodBody(name, md, ClassFile.ACC_PUBLIC, code -> {
            int ctxSlot = code.parameterSlot(0);
            code.aload(0)
                .aload(ctxSlot);

            TypeConstant type = property.getType();
            if (operation.equals("set")) {
                JitParamDesc[] params = optimized ? desc.optimizedParams : desc.standardParams;
                for (int i = 0; i < params.length; i++) {
                    load(code, params[i].cd, code.parameterSlot(i + 1));
                }
                if (optimized) {
                    if (type.isNullable()) {
                        boxNullable(code, type);
                    } else {
                        box(code, type);
                    }
                }
                code.invokespecial(CD_nRefProp, "set", md(CD_void, CD_Ctx, CD_Object)).return_();
            } else {
                code.invokespecial(CD_nRefProp, "get", md(CD_Object, CD_Ctx));
                if (optimized) {
                    if (type.isNullable()) {
                        unboxNullable(code, type, getCCDesc(type.removeNullable()));
                    } else {
                        code.checkcast(getCCDesc(type));
                        unbox(code, type);
                    }
                    for (int i = desc.optimizedReturns.length - 1; i > 0; i--) {
                        JitParamDesc result = desc.optimizedReturns[i];
                        storeToContext(code, result.cd, result.altIndex, ctxSlot);
                    }
                    addReturn(code, md.returnType());
                } else {
                    code.checkcast(md.returnType()).areturn();
                }
            }
        });
    }
}
