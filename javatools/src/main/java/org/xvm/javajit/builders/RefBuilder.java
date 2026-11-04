package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.List;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCallDesc;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import org.xvm.javajit.builders.AnnotationHelper.AnnotationVariant;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.JitFlavor.Specific;

/**
 * Builds annotated Ref and Var implementations backed by nRef.
 */
public class RefBuilder extends CommonBuilder {
    public RefBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
        assert thisType.isJitAnnotatedRef();
    }

    @Override
    protected boolean isInterface() {
        // the source Ref/Var is an interface; this builder emits a concrete native subclass
        return false;
    }

    @Override
    public ClassDesc getSuperCD() {
        // inherit referent storage and native Ref/Var operations from nRef
        return CD_nRef;
    }

    /**
     * Resolve a super call, using nRef for methods declared by Ref or Var.
     */
    @Override
    public JitCallDesc resolveSuperCall(BuildContext bctx) {
        // the native bodies behind Ref/Var interface declarations live on nRef
        MethodBody   body  = bctx.callChain[bctx.callDepth + 1];
        TypeConstant owner = body.getIdentity().getClassIdentity().getType();
        return owner.isA(pool().typeRef())
                ? JitCallDesc.special(CD_nRef, body.getIdentity().ensureJitMethodName(typeSystem),
                        body.getJitDesc(this, owner), false)
                : super.resolveSuperCall(bctx);
    }

    @Override
    protected void assembleInit(ClassBuilder classBuilder, List<PropertyInfo> props) {
        // [callSuperInitializer]
        // this.$type = type;
        // [initialize annotation fields]
        classBuilder.withMethodBody(INIT_NAME, md(CD_void, CD_Ctx, CD_TypeConstant),
                ClassFile.ACC_PUBLIC, code -> {
            callSuperInitializer(code);
            code.aload(0)
                .aload(code.parameterSlot(1))
                .putfield(art.CD(), "$type", CD_TypeConstant);
            initializeFields(code, props);
            code.return_();
        });
    }

    @Override
    protected void callSuperInitializer(CodeBuilder code) {
        // super(ctx, null, type.getParamType(0), isVar);
        code.aload(0)
            .aload(code.parameterSlot(0))
            .aconst_null()
            .aload(code.parameterSlot(1))
            .iconst_0()
            .invokevirtual(CD_TypeConstant, "getParamType", md(CD_TypeConstant, CD_int));
        loadBoolean(code, thisId.equals(pool().clzVar()));
        code.invokespecial(CD_nRef, INIT_NAME,
                md(CD_void, CD_Ctx, CD_nObject, CD_TypeConstant, CD_boolean));
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        super.assembleMethods(classBuilder);

        // annotated Ref/Var classes have no Ecstasy constructors; generate factories that allocate
        // unassigned native storage before invoking the selected annotation constructors
        List<AnnotationVariant> variants =
                annotated.collectVariants(getInstantiatorDesc(this, thisType));
        for (AnnotationVariant variant : variants) {
            JitMethodDesc jmd = variant.desc();
            String name = NEW + variant.suffix();
            if (jmd.isOptimized) {
                assembleMethodWrapper(classBuilder, name, jmd);
                name += OPT;
            }

            MethodTypeDesc methodType = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
            classBuilder.withMethodBody(name, methodType,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                int ctxSlot  = code.parameterSlot(0);
                int thisSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.new_(art.CD()).dup()
                    .aload(ctxSlot)
                    .aload(code.parameterSlot(1))
                    .invokespecial(art.CD(), INIT_NAME, md(CD_void, CD_Ctx, CD_TypeConstant))
                    .astore(thisSlot);
                annotated.invokeAnnotationConstructors(code, variant.constructors(),
                        ctxSlot, -1, thisSlot, 2);
                code.aload(thisSlot).areturn();
            });
        }
    }

    /**
     * Leave a newly initialized annotated reference on the Java operand stack, with its referent
     * still unassigned.
     */
    public static void createRef(BuildContext bctx, CodeBuilder code, TypeConstant refType) {
        JitCallDesc call = AnnotationHelper.getInstantiatorCall(bctx, refType, NEW,
                            getInstantiatorDesc(bctx.builder, refType));
        bctx.loadCtx(code);
        bctx.loadTypeConstant(code, refType);
        AnnotationHelper.loadAnnotationConstructorArgs(bctx, code, refType);
        call.invoke(code, call.jmd().isOptimized);
    }

    /**
     * @return the reference factory descriptor before annotation parameters are appended
     */
    private static JitCtorDesc getInstantiatorDesc(Builder builder, TypeConstant refType) {
        JitParamDesc retPd = new JitParamDesc(refType, Specific,
                                builder.getICDesc(refType), 0, -1, false);
        return new JitCtorDesc(refType, null, false, true, false,
                new JitParamDesc[] {retPd}, JitParamDesc.NONE, null, null);
    }
}
