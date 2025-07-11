package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;


/**
 * The builder for Module types.
 */
public class ModuleBuilder extends CommonBuilder {

    public ModuleBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xModule =
        ClassDesc.of(org.xvm.javajit.intrinsic.xModule.class.getName());

    private static final ClassDesc CD_ModuleStructure =
        ClassDesc.of(org.xvm.asm.ModuleStructure.class.getName());

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_xModule)
            ;

        classBuilder.withField("module$",
            CD_ModuleStructure,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL
        );

        // static initializer
        classBuilder.withMethod(ConstantDescs.CLASS_INIT_NAME,
            MethodTypeDesc.of(CD_void),
            ClassFile.ACC_STATIC,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                Label startScope = codeBuilder.newLabel();
                Label endScope   = codeBuilder.newLabel();
                int   nLine      = 0;
                codeBuilder
                    .labelBinding(startScope)

                    .lineNumber(++nLine)
                    .localVariable(0, "ctx", CD_Ctx, startScope, endScope)
                    .invokestatic(CD_Ctx, "get", MethodTypeDesc.of(CD_Ctx))
                    .astore(0)

                    .lineNumber(++nLine)
                    .labelBinding(endScope)
                    .return_()
                    ;
            })
        );

        // public $module(long containerId) {
        //   super(containerId, org.xvm.javajit.Ctx.get().container.typeSystem.mainModule());
        // }
        classBuilder.withMethod(INIT_NAME,
            MethodTypeDesc.of(CD_void, CD_long),
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(codeBuilder -> {
                Label startScope = codeBuilder.newLabel();
                Label endScope   = codeBuilder.newLabel();
                int   nLine      = 0;
                codeBuilder
                    .localVariable(1, "containerId", CD_long, startScope, endScope)

                    .labelBinding(startScope)
                    .lineNumber(++nLine)
                    .aload(0)
                    .lload(1)
                    .invokestatic(CD_Ctx, "get", MethodTypeDesc.of(CD_Ctx))
                    .getfield(CD_Ctx, "container", CD_Container)
                    .getfield(CD_Container, "typeSystem", CD_TypeSystem)
                    .invokevirtual(CD_TypeSystem, "mainModule", MethodTypeDesc.of(CD_ModuleStructure))
                    .invokespecial(CD_xModule, INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_long, CD_ModuleStructure))

                    .lineNumber(++nLine)
                    .labelBinding(endScope)
                    .return_()
                    ;
            })
        );
    }

    @Override
    public void assemblePure(String className, ClassBuilder builder) {
    }
}
