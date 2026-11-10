package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBindingConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCallDesc;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.registers.SingleSlot;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.OPT;
import static org.xvm.javajit.Builder.md;
import static org.xvm.javajit.JitFlavor.Primitive;
import static org.xvm.javajit.JitFlavor.Specific;

import static org.xvm.javajit.builders.AnnotationHelper.annotationConstructorName;
import static org.xvm.javajit.builders.AnnotationHelper.findAnnotationConstructor;

/**
 * Supports {@link CommonBuilder} in implementing a property's Ref/Var annotations on the
 * containing class.
 *
 * <p>It is used while compiling the Ref/Var implementation of a particular hosted property:
 * <ul>
 *   <li> Its getter, setter, and other property methods.
 *   <li> Its annotation constructors and validators.
 *   <li> Synthetic super and nested bodies derived from those contexts.
 * </ul>
 *
 * <p>For {@code @Lazy Int value}, the body of {@code Lazy.get()} is compiled with the containing
 * object as its Java receiver. References to {@code assigned} and calls to {@code calc()} and
 * {@code set()} in that body operate on {@code value}'s logical Ref/Var. The hosting information
 * supplies mappings to the containing class's fields and methods, together with the property's
 * generic bindings.
 */
public class PropertyHosting {
    public PropertyHosting(CommonBuilder builder, PropertyInfo property) {
        this.builder  = builder;
        this.ts       = builder.typeSystem;
        this.property = property;

        ConstantPool pool        = builder.pool();
        TypeConstant refType     = builder.getPropertyRefType(property);
        String       jitName     = property.ensureJitName(ts);
        Annotation[] annotations = refType.getAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            Annotation annotation = annotations[i];
            TypeInfo   info       = annotation.getFormalType().resolveGenerics(pool, refType).
                                        ensureAccess(Access.PRIVATE).ensureTypeInfo();

            for (PropertyInfo field : info.getProperties().values()) {
                if (field.hasField() && !field.isFormalType()) {
                    fields.putIfAbsent(field.getIdentity(), field);
                }
            }

            Constant[]     args  = annotation.getParams();
            TypeConstant[] types = new TypeConstant[args.length];
            for (int j = 0; j < args.length; j++) {
                if (args[j] instanceof MethodBindingConstant binding) {
                    MethodConstant id   = binding.getMethodConstant();
                    MethodBody     body = id.isLambda()
                            ? new MethodBody((MethodStructure) id.getComponent())
                            : builder.typeInfo.getMethodById(id).getHead();

                    TypeConstant type = body.asJitFunctionType(pool, builder.thisType);
                    types[j] = type.isMethod() ? pool.bindMethodTarget(type) : type;
                } else {
                    types[j] = args[j].getType();
                }
            }

            MethodInfo  method = findAnnotationConstructor(annotation, types, refType);
            String      name   = jitName + '$' + annotationConstructorName(builder, i, method);
            JitCtorDesc desc   = ((JitCtorDesc) method.getJitDesc(builder, refType))
                    .withTarget(builder.thisType, builder.art.CD());
            initializers.add(new Initializer(method, desc, name, args));
        }

        TypeInfo refInfo = refType.ensureAccess(Access.PRIVATE).ensureTypeInfo();

        this._assigned_ = pool.typeRef().ensureTypeInfo().findProperty("assigned");
        fields.put(_assigned_.getIdentity(), _assigned_);

        MethodInfo validator = refInfo.getMethodBySignature(pool.sigValidator());
        if (validator != null) {
            int index = 0;
            for (MethodBody body : validator.ensureOptimizedMethodChain(refInfo)) {
                MethodInfo  method = new MethodInfo(body, 0);
                String      name   = jitName + "$validate$" + index++;
                JitCtorDesc desc   = ((JitCtorDesc) body.getJitDesc(builder, refType)).
                                        withTarget(builder.thisType, builder.art.CD());

                initializers.add(new Initializer(method, desc, name, Constant.NO_CONSTS));
            }
        }
        this.pool    = pool;
        this.refType = refType;
    }

    private final TypeSystem ts;
    private final ConstantPool  pool;
    private final CommonBuilder builder;
    public  final PropertyInfo  property;
    public  final TypeConstant  refType;
    private final PropertyInfo _assigned_; // the info for the "Ref.assigned" property

    // includes Ref.assigned as a computed entry, without separate backing storage
    private final Map<PropertyConstant, PropertyInfo> fields       = new LinkedHashMap<>();
    private final List<Initializer>                   initializers = new ArrayList<>();

    private record Initializer(MethodInfo method, JitCtorDesc desc, String name, Constant[] args) {}

    /**
     * Declare annotation fields on the containing class.
     */
    public void assembleFields(ClassBuilder classBuilder) {
        for (PropertyInfo field : fields.values()) {
            if (field != _assigned_) {
                builder.assembleField(classBuilder, field, fieldName(field), ClassFile.ACC_PUBLIC);
            }
        }
        builder.assemblePropertyLambdas(classBuilder, property);
    }

    /**
     * Assemble annotation constructors and validators on the containing class.
     */
    public void assembleMethods(ClassBuilder classBuilder) {
        for (Initializer initializer : initializers) {
            MethodInfo method = initializer.method();
            if (method.getHead().getMethodStructure().isNoOp()) {
                continue;
            }

            JitCtorDesc desc = initializer.desc();
            String      name = initializer.name();

            if (desc.isOptimized) {
                builder.assembleMethodWrapper(classBuilder, name, desc);
                name += OPT;
            }

            TypeInfo info = method.getTypeInfo();
            if (info == null) {
                info = refType.ensureAccess(Access.PRIVATE).ensureTypeInfo();
            }

            BuildContext bctx = new BuildContext(builder, info, method, desc, builder.thisType, this);
            builder.doAssembleMethod(classBuilder, bctx, method, name,
                    desc.isOptimized ? desc.optimizedMD : desc.standardMD,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
        }
    }

    /**
     * Initialize annotation state once, during construction of the containing object.
     */
    public void initialize(CodeBuilder code, int ctxSlot) {
        builder.initializeFields(code, fields.values().stream()
                .filter(field -> field != _assigned_ && field.requiresJitInitialization())
                .toList(), this::fieldName);

        for (Initializer initializer : initializers) {
            MethodStructure method = initializer.method().getHead().getMethodStructure();
            if (method.isNoOp()) {
                continue;
            }

            JitCtorDesc desc = initializer.desc();
            code.aload(ctxSlot);
            if (desc.hasCtorCtx()) {
                code.aload(ctxSlot)
                    .invokevirtual(Builder.CD_Ctx, "ctorCtx", md(Builder.CD_CtorCtx));
            }
            code.aload(0);
            for (int i = 0; i < method.getParamCount(); i++) {
                Constant arg = i < initializer.args().length
                        ? initializer.args()[i] : method.getParam(i).getDefaultValue();
                MethodConstant methodId = arg instanceof MethodConstant id ? id
                        : arg instanceof MethodBindingConstant binding
                                ? binding.getMethodConstant() : null;
                RegisterInfo value = methodId == null ? builder.loadConstant(code, arg)
                        : builder.loadMethod(null, code, methodId, () -> {
                            code.aload(0);
                            return new SingleSlot(builder.thisType, Specific, builder.art.CD(), "");
                        }, ctxSlot);
                builder.convertCallArgument(code, desc, i, value, ctxSlot);
            }
            code.invokestatic(builder.art.CD(), initializer.name() + (desc.isOptimized ? OPT : ""),
                    desc.isOptimized ? desc.optimizedMD : desc.standardMD);
        }
    }

    /**
     * @return the hosted field or computed property identified by the constant, or null
     */
    public PropertyInfo getField(PropertyConstant id) {
        return fields.get(id);
    }

    /**
     * @return the method's implementation within this property, or null
     */
    public MethodInfo findMethod(MethodConstant id) {
        if (id.getNamespace() instanceof PropertyConstant container) {
            if (!property.containsBody(container)) {
                return null;
            }
        } else if (!Builder.isEmbedded(id.getClassIdentity().getComponent().getFormat()) &&
                !id.getClassIdentity().equals(pool.clzRef()) &&
                !id.getClassIdentity().equals(pool.clzVar())) {
            return null;
        }

        MethodConstant nested = nestedIdentity(id);
        return builder.typeInfo.getMethodById(pool.ensureMethodConstant(
                nested.getNamespace(), nested.getSignature().resolveGenericTypes(pool, refType)), true);
    }

    /**
     * @return the containing-class call target for a property method
     */
    public JitCallDesc resolveCall(MethodInfo method) {
        MethodConstant methodId = method.getIdentity();
        String         op       = methodId.getName();
        String         name;
        JitMethodDesc  desc;

        if (op.equals("get") && method.getSignature().getParamCount() == 0) {
            name = property.ensureGetterJitMethodName(ts);
            desc = property.getGetterJitDesc(builder, builder.thisType);
        } else if (op.equals("set") && method.getSignature().getParamCount() == 1) {
            name = property.ensureSetterJitMethodName(ts);
            desc = property.getSetterJitDesc(builder, builder.thisType);
        } else {
            name = methodName(methodId);

            TypeConstant targetType = methodId.getClassIdentity().getFormalType().getJitCCType();
            desc = method.getJitDesc(builder, targetType);
        }
        return new JitCallDesc(builder.art.CD(), name, desc, JitCallDesc.Kind.Virtual, false, null);
    }

    /**
     * @return the accessor chain with native Ref/Var storage replaced by the containing field
     */
    public MethodBody[] getAccessorChain(MethodBody[] chain) {
        int lastIx = chain.length - 1;
        if (lastIx >= 0) {
            MethodBody       tail    = chain[lastIx];
            MethodConstant   tailId  = tail.getIdentity();
            IdentityConstant classId = tailId.getClassIdentity();

            if (tail.getImplementation() == Implementation.Native) {
                assert classId.equals(pool.clzRef()) || classId.equals(pool.clzVar());

                chain         = chain.clone();
                chain[lastIx] = new MethodBody(tailId, tail.getSignature(),
                                               Implementation.Field, property.getFieldIdentity());
            }
        }
        return chain;
    }

    /**
     * Load a hosted field or computed property from the receiver already on the Java stack.
     */
    public RegisterInfo loadField(BuildContext bctx, CodeBuilder code, RegisterInfo target,
                                  PropertyInfo field) {
        if (field == _assigned_) {
            if (property.isJitPotentiallyUnassigned()) {
                TypeConstant targetType = property.getFieldOwnerType(builder, target.type());
                String       name       = property.ensureJitName(ts) + Builder.AssignedGet;
                ClassDesc    targetCD   = builder.getCCDesc(targetType);

                if (!target.cd().equals(targetCD)) {
                    code.checkcast(targetCD);
                }

                bctx.loadCtx(code);
                if (targetType.isJitCCInterface()) {
                    code.invokeinterface(targetCD, name, md(CD_boolean, CD_Ctx));
                } else {
                    code.invokevirtual(targetCD, name, md(CD_boolean, CD_Ctx));
                }
            } else {
                code.pop()
                    .iconst_1();
            }
            return new SingleSlot(pool.typeBoolean(), Primitive, CD_boolean, "");
        } else {
            return bctx.loadHostedField(code, target, field, fieldName(field));
        }
    }

    /**
     * Store a hosted annotation field on the containing object.
     */
    public void storeField(BuildContext bctx, CodeBuilder code, PropertyInfo field,
                           Builder.Loader targetLoader, Builder.Loader valueLoader) {
        assert field != _assigned_;
        bctx.storeHostedField(code, field, fieldName(field), targetLoader, valueLoader);
    }

    /**
     * @return the field name scoped to this property on the containing class
     */
    public String fieldName(PropertyInfo field) {
        return property.ensureJitName(ts) + '$' + field.ensureJitName(ts);
    }

    /**
     * @return the containing-class method name for a property member
     */
    public String methodName(MethodConstant method) {
        String name = method.ensureJitMethodName(ts);
        return method.getNamespace() instanceof PropertyConstant
                ? name
                : property.ensureJitName(ts) + '$' + name;
    }

    /**
     * @return the member identity scoped to this property
     */
    public MethodConstant nestedIdentity(MethodConstant method) {
        return method.getNamespace() instanceof PropertyConstant
                ? method
                : (MethodConstant) property.getIdentity().
                        appendNestedIdentity(pool, method.getNestedIdentity());
    }
}
