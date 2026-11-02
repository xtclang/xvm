package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.LinkedHashSet;
import java.util.Set;

import java.util.function.BiConsumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCallDesc;
import org.xvm.javajit.JitCallDesc.Kind;
import org.xvm.javajit.JitDetachedBodyDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * Builds virtual children using the effective parent composition.
 *
 * <p>A base child has no same-name super child in its enclosing parent's inheritance hierarchy.
 * Base children are the "hard-left" classes in the virtual-child design. A derived child has a
 * same-name super child, even without an explicit declaration in the derived parent. For example,
 * if B declares C0 and C1 extending C0, both are hard-left classes. If D extends B, D.C0 and D.C1
 * are derived children with same-name super children B.C0 and B.C1.
 *
 * <p>The "Pure" shape is the child interface; the "Impl" shape supplies storage, virtual routes,
 * and detached bodies. Hard-left callable types and bodies use concrete receivers, while derived
 * children use interface receivers so that D.C0's body can run on D.C1 without a separate D.C0 object.
 * Super calls from hard-left bodies enter virtual super-router methods; derived-child bodies enter
 * their fixed predecessor bodies. See the virtual-child design in "doc/jit_class_names.txt".
 */
public class VirtualChildBuilder extends CommonBuilder {
    public VirtualChildBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);

        this.javaSuperType = computeJavaSuperType();
    }

    private final TypeConstant javaSuperType;

    private static final String SuperRouter = "super$";

    @Override
    public ClassDesc getSuperCD() {
        return javaSuperType == null
                ? thisType.isConst() ? CD_nConst : CD_nObject
                : javaSuperType.isVirtualChildClass()
                        ? javaSuperType.getJitICDesc(typeSystem)
                        : getCCDesc(javaSuperType);
    }

    @Override
    public boolean shouldGenerate(IdentityConstant id) {
        if (!hasConstructionSiteAnnotations() &&
                id instanceof PropertyConstant propId && !propId.isConstant() &&
                propId.getClassIdentity().getComponent() instanceof ClassStructure clz &&
                clz.isVirtualChildClass()) {
            // a contribution from a differently named child (e.g. D.C0 on D.C1) may need
            // storage not supplied by the Java superclass
            PropertyInfo inheritedProp = javaSuperType == null
                    ? null
                    : javaSuperType.ensureAccess(Access.STRUCT).ensureTypeInfo().findProperty(propId);
            return inheritedProp == null || !propId.equals(inheritedProp.getFieldIdentity());
        }
        return super.shouldGenerate(id);
    }

    @Override
    protected void callSuperInitializer(CodeBuilder code) {
        // the Java superclass can be a same-name super child even when TypeInfo has no "extends"
        boolean hasOuter = javaSuperType != null &&
                javaSuperType.getSingleUnderlyingClass(true).getComponent()
                        instanceof ClassStructure clz && clz.isInstanceChild();
        code.aload(0).aload(code.parameterSlot(0));
        if (hasOuter) {
            code.aload(code.parameterSlot(1));
        }
        code.invokespecial(getSuperCD(), INIT_NAME, hasOuter ? MD_xvmOuterVoid : MD_xvmVoid);
    }

    @Override
    protected MethodInfo getPropertyInitializer(MethodConstant initializer) {
        MethodInfo method = super.getPropertyInitializer(initializer);
        return method == null
                ? resolveType(initializer.getClassIdentity()).ensureAccess(Access.PRIVATE)
                        .ensureTypeInfo().getMethodById(initializer)
                : method;
    }

    @Override
    protected void invokePropertyInitializer(CodeBuilder code, MethodInfo initializer,
                                             JitMethodDesc jmd) {
        JitCallDesc call = resolveChildMethodCall(this, initializer.getHead(), thisType);
        code.aload(0).dup().checkcast(call.receiverCD()).aload(code.parameterSlot(0));
        call.invoke(code, false);
    }

    /**
     * Resolve a child method to its detached body on the declaring child's Impl class.
     * A hard-left body uses a concrete receiver; a derived-child body uses an interface receiver
     * that can also be implemented by differently named children.
     */
    public static JitCallDesc resolveChildMethodCall(Builder builder, MethodBody body,
                                                     TypeConstant receiverType) {
        TypeSystem   ts    = builder.typeSystem;
        TypeConstant owner = body.resolveDeclaringType(builder.pool(), receiverType);

        // a composed chain can carry a resolved signature (echo(Object)) while the original
        // declaration still uses a formal (echo(Element)); retain the declaration's JIT name
        MethodInfo method = owner.ensureAccess(Access.PRIVATE).ensureTypeInfo()
                .getMethodById(body.getMethodStructure().getIdentityConstant());
        assert method != null;
        return new JitCallDesc(builder.getImplDesc(owner), DetachedBody + method.ensureJitMethodName(ts),
                body.getJitDesc(builder, owner), Kind.Static, false, owner.getJitCCDesc(ts));
    }

    @Override
    public ClassDesc getThisCD(BuildContext bctx) {
        if (bctx.methodDesc instanceof JitDetachedBodyDesc body) {
            return body.receiverCD;
        }
        // child constructor bodies use the callable interface; annotation constructors use JIC
        return bctx.isConstructor && !hasConstructionSiteAnnotations()
                ? thisType.getJitCCDesc(typeSystem)
                : super.getThisCD(bctx);
    }

    @Override
    protected void assembleInterfaces(ClassBuilder classBuilder) {
        if (hasConstructionSiteAnnotations()) {
            // the unannotated superclass already implements the child interface
            super.assembleInterfaces(classBuilder);
        } else {
            // the callable interface contains both the child topology and ordinary interface contracts
            classBuilder.withInterfaceSymbols(getPureImplDesc(thisType));
        }
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        super.assembleMethods(classBuilder);

        // inherited Ecstasy bodies still need local Java routing methods; merely inheriting
        // B_C1.foo() on D_C1 could skip D.C0.foo(), which is in the Ecstasy composition chain
        // but not in D_C1's Java superclass chain
        for (MethodInfo method : typeInfo.getMethods().values()) {
            if (!(method.getIdentity().getNamespace() instanceof PropertyConstant) &&
                    method.isVirtual() && !method.isCapped() && !shouldGenerate(method.getIdentity()) &&
                    method.getHead().getIdentity().getClassIdentity().getComponent()
                            instanceof ClassStructure clz && clz.isVirtualChildClass()) {
                assembleMethod(classBuilder, method);
            }
        }
        assembleSuperRouters(classBuilder);
        assemblePropertySuperRouters(classBuilder);
        assembleConstructorSuperRouters(classBuilder);
        assembleRawFields(classBuilder);
    }

    private void assembleRawFields(ClassBuilder classBuilder) {
        forEachRawField(this, (name, cd) -> {
            classBuilder.withMethodBody(FieldGet + name, md(cd), ClassFile.ACC_PUBLIC, code -> {
                code.aload(0).getfield(art.CD(), name, cd);
                addReturn(code, cd);
            });

            classBuilder.withMethodBody(FieldSet + name, md(CD_void, cd), ClassFile.ACC_PUBLIC, code -> {
                code.aload(0);
                load(code, cd, code.parameterSlot(0));
                code.putfield(art.CD(), name, cd).return_();
            });
        });
    }

    /**
     * Visit the backing-field slots shared by virtual-child raw accessors and their contracts.
     */
    protected static void forEachRawField(CommonBuilder builder, BiConsumer<String, ClassDesc> consumer) {
        for (PropertyInfo prop : builder.structInfo.getProperties().values()) {
            if (!prop.hasField() || prop.isConstant() || prop.isFormalType()) {
                continue;
            }

            String         field   = prop.getFieldIdentity().ensureJitPropertyName(builder.typeSystem);
            JitMethodDesc  getter  = prop.getGetterJitDesc(builder, builder.thisType);
            JitParamDesc[] returns = getter.isOptimized
                    ? getter.getAllOptimizedReturns(0)
                    : getter.standardReturns;

            for (int i = 0; i < returns.length; i++) {
                JitParamDesc ret  = returns[i];
                String       name = ret.extension
                        ? field + EXT
                        : prop.getType().removeNullable().isXvmPrimitive() ? field + "$" + i : field;

                ClassDesc cd = i == 0 ? JitTypeDesc.getFieldClass(prop.getType(), ret.cd) : ret.cd;
                consumer.accept(name, cd);
            }
        }
    }

    /**
     * Resolve a composition-specific child super target, or return null for ordinary super resolution.
     *
     * <p>Suppose B.C1 extends B.C0 and D extends B with same-name children D.C1 and D.C0.
     * A super call in the hard-left B.C1 body must enter D.C0 on a D.C1 receiver, but B.C0 on
     * a B.C1 receiver. It therefore calls a virtual super-router rather than its fixed predecessor
     * body directly. A super call in the derived D.C0 body instead enters its fixed B.C0
     * predecessor; routing it virtually again would re-enter D.C0.
     */
    public JitCallDesc resolveChildSuperCall(BuildContext bctx) {
        MethodBody   body       = bctx.callChain[bctx.callDepth + 1];
        TypeConstant targetType = resolveType(body.getIdentity().getClassIdentity());

        if (!(bctx.methodDesc instanceof JitDetachedBodyDesc) || !targetType.isVirtualChildClass()) {
            return null;
        }

        JitCallDesc callDesc   = resolveChildCall(body, targetType);
        String      methodName = callDesc.name().substring(DetachedBody.length());

        if (isHardLeft(thisType)) {
            // use the same-name hard-left child's signature so derived compositions override the
            // same Java method instead of overloading it with a more-specific receiver parameter
            TypeConstant baseType = findHardLeft(targetType);
            String       baseName = baseType.getSingleUnderlyingClass(true).getName();
            return new JitCallDesc(art.CD(), SuperRouter + baseName + "$" + methodName,
                    resolveChildCall(body, baseType).jmd(), Kind.Virtual, false,
                    getPureImplDesc(baseType));
        }
        return callDesc;
    }

    /**
     * Resolve a child method or property accessor to its exact Java call target.
     */
    public JitCallDesc resolveChildCall(MethodBody body, TypeConstant ownerType) {
        if (body.getIdentity().getNamespace() instanceof PropertyConstant id) {
            PropertyInfo prop = ownerType.ensureAccess(Access.PRIVATE).ensureTypeInfo().
                                    findProperty(id.getName());
            if (prop == null) {
                // a derived child can introduce a property absent from the hard-left contract
                prop = id.getPropertyInfo(id.getClassIdentity().getFormalType().
                            resolveGenerics(pool(), getThisType()));
            }

            boolean isGetter = body.getIdentity().getName().equals("get");
            String  name     = isGetter
                    ? prop.ensureGetterJitMethodName(typeSystem)
                    : prop.ensureSetterJitMethodName(typeSystem);

            JitMethodDesc jmd = isGetter
                    ? prop.getGetterJitDesc(this, ownerType)
                    : prop.getSetterJitDesc(this, ownerType);

            return new JitCallDesc(getImplDesc(ownerType), DetachedBody + name, jmd,
                    Kind.Static, false, ownerType.getJitCCDesc(typeSystem));
        }
        return resolveChildMethodCall(this, body, ownerType);
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        super.assembleProperties(classBuilder);

        for (PropertyInfo prop : typeInfo.getProperties().values()) {
            if (!prop.isConstant() && !prop.isFormalType() && !prop.getName().equals("outer") &&
                    prop.getIdentity().getClassIdentity().getType().isVirtualChildClass() &&
                    !shouldGenerate(prop.getIdentity())) {
                // inherited storage does not imply inherited accessor dispatch: a derived C0
                // getter or setter must still be reachable on this composition's C1 receiver
                assembleProperty(classBuilder, prop);
            }
        }
    }

    @Override
    protected void assembleProperty(ClassBuilder classBuilder, PropertyInfo prop) {
        if (prop.isConstant() || prop.isFormalType() ||
                !prop.getIdentity().getClassIdentity().getType().isVirtualChildClass()) {
            super.assembleProperty(classBuilder, prop);
            return;
        }
        assembleChildPropertyAccessor(classBuilder, prop, true);
        if (prop.isVar()) {
            assembleChildPropertyAccessor(classBuilder, prop, false);
        }
    }

    /**
     * Assemble a virtual-child getter or setter, including its declaration or routing method and
     * any locally declared detached body.
     */
    private void assembleChildPropertyAccessor(ClassBuilder classBuilder, PropertyInfo prop,
                                               boolean isGetter) {
        MethodBody   body       = getPropertyChain(prop, isGetter)[0];
        TypeConstant targetType = resolveType(body.getIdentity().getClassIdentity());

        if (!targetType.isVirtualChildClass()) {
            super.assembleProperty(classBuilder, prop);
            return;
        }

        String name = isGetter
                ? prop.ensureGetterJitMethodName(typeSystem)
                : prop.ensureSetterJitMethodName(typeSystem);

        JitMethodDesc jmd = isGetter
                ? prop.getGetterJitDesc(this, thisType)
                : prop.getSetterJitDesc(this, thisType);

        if (body.isAbstract()) {
            addAbstractMethod(classBuilder, name, jmd);
        } else {
            assemblePropertyBody(classBuilder, prop, isGetter);
            assembleChildRouting(classBuilder, name, jmd, body, targetType);
        }
    }

    /**
     * Assemble the detached getter or setter body declared on the child class being assembled.
     */
    private void assemblePropertyBody(ClassBuilder classBuilder, PropertyInfo prop,
                                      boolean isGetter) {
        MethodBody body = getPropertyChain(prop, isGetter)[0];
        if (body.isAbstract() || hasConstructionSiteAnnotations() ||
                !body.getIdentity().getClassIdentity().equals(thisId)) {
            return;
        }

        JitCallDesc         jcd = resolveChildCall(body, thisType);
        JitDetachedBodyDesc jmd = new JitDetachedBodyDesc(jcd.jmd(), jcd.receiverCD());

        if (jmd.isOptimized) {
            assembleMethodWrapper(classBuilder, jcd.name(), jmd.staticWrapper());
        }

        MethodTypeDesc bodyMD = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
        String         name   = jcd.name() + (jmd.isOptimized ? OPT : "");
        int            flags  = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        if (body.getImplementation() == MethodBody.Implementation.Field) {
            classBuilder.withMethodBody(name, bodyMD, flags,
                    code -> generateFieldBody(code, prop, isGetter, jmd));
        } else if (body.getMethodStructure().hasCode()) {
            BuildContext bctx = new BuildContext(this, typeInfo, prop, isGetter, jmd);
            doAssembleMethod(classBuilder, bctx, body.getMethodInfo(), name, bodyMD, flags);
        }
    }

    private MethodBody[] getPropertyChain(PropertyInfo prop, boolean isGetter) {
        return isGetter
                ? prop.ensureOptimizedGetChain(typeInfo, null)
                : prop.ensureOptimizedSetChain(typeInfo, null);
    }

    /**
     * Generate a field-backed detached accessor, including multi-slot values and setter mutability
     * checks.
     */
    private void generateFieldBody(CodeBuilder code, PropertyInfo prop, boolean isGetter,
                                   JitDetachedBodyDesc jmd) {
        int ctxSlot = code.parameterSlot(1);
        if (!isGetter) {
            if (thisType.isConst()) {
                throwReadOnly(code, prop.getName(), ctxSlot);
                return;
            }
            code.aload(0).checkcast(CD_nObject).aload(ctxSlot)
                .invokevirtual(CD_nObject, "$checkImmut", MD_xvmVoid);
        }
        String         field  = prop.getFieldIdentity().ensureJitPropertyName(typeSystem);
        JitParamDesc[] values = isGetter
                ? jmd.isOptimized ? jmd.optimizedReturns : jmd.standardReturns
                : jmd.isOptimized ? jmd.optimizedParams  : jmd.standardParams;
        for (int i = 0; i < values.length; i++) {
            JitParamDesc value     = values[i];
            String       fieldName = value.extension ? field + EXT
                    : prop.getType().removeNullable().isXvmPrimitive() ? field + "$" + i : field;
            ClassDesc    cd        = i == 0 ? JitTypeDesc.getFieldClass(prop.getType(), value.cd) : value.cd;
            code.aload(0);
            if (isGetter) {
                loadField(code, thisType, fieldName, cd);
                if (i > 0) {
                    storeToContext(code, value.cd, value.altIndex, ctxSlot);
                }
            } else {
                load(code, value.cd, code.parameterSlot(i + 2));
                storeField(code, thisType, fieldName, cd);
            }
        }
        if (isGetter) {
            normalizePrimitiveField(code, prop.getType());
            addReturn(code, jmd.getReturn(0).cd);
        } else {
            code.return_();
        }
    }

    /**
     * Assemble super-routers for virtual-child property getters and setters.
     */
    private void assemblePropertySuperRouters(ClassBuilder classBuilder) {
        for (PropertyInfo prop : typeInfo.getProperties().values()) {
            if (prop.isConstant() || prop.isFormalType() ||
                    !prop.getIdentity().getClassIdentity().getType().isVirtualChildClass()) {
                continue;
            }

            assemblePropertySuperRouters(classBuilder, prop, true);
            if (prop.isVar()) {
                assemblePropertySuperRouters(classBuilder, prop, false);
            }
        }
    }

    /**
     * Generate one router per child name for the specified getter or setter.
     */
    private void assemblePropertySuperRouters(ClassBuilder classBuilder, PropertyInfo prop,
                                              boolean isGetter) {
        Set<String> routedChildNames = new LinkedHashSet<>();
        for (MethodBody body : getPropertyChain(prop, isGetter)) {
            IdentityConstant owner = body.getIdentity().getClassIdentity();
            if (body.isAbstract() || !owner.getType().isVirtualChildClass() ||
                    !routedChildNames.add(owner.getName())) {
                continue;
            }

            TypeConstant targetType = resolveType(owner);
            TypeConstant baseType   = findHardLeft(targetType);
            JitCallDesc  baseCall   = resolveChildCall(body, baseType);
            JitCallDesc  targetCall = resolveChildCall(body, targetType);

            assert baseCall.name().startsWith(DetachedBody);

            String routerName = SuperRouter + owner.getName() + "$" +
                    baseCall.name().substring(DetachedBody.length());

            assembleSuperRouter(classBuilder, routerName, baseCall.jmd(),
                    getPureImplDesc(baseType), targetCall);
        }
    }

    @Override
    public JitCallDesc resolveConstructorCall(MethodInfo constructor, TypeConstant targetType,
                                              ClassDesc targetCD, String name) {
        IdentityConstant ownerId      = constructor.getHead().getIdentity().getClassIdentity();
        TypeConstant     declaredType = resolveType(ownerId);

        if (!declaredType.isVirtualChildClass()) {
            return super.resolveConstructorCall(constructor, targetType, targetCD, name);
        }

        // a phantom re-emits its inherited constructor; use the declaring child's hard-left
        // status rather than the phantom's to preserve runtime-parent dispatch
        if (isHardLeft(thisId.getType()) && !ownerId.getName().equals(thisId.getName())) {
            // B.C1's constructor enters the C0 constructor selected by the actual parent, just
            // like a hard-left super call; D.C0 then calls its fixed B.C0 predecessor.
            MethodInfo   orig     = constructor.getChildConstructorOrigin();
            TypeConstant origType = resolveType(orig.getHead().getIdentity().getClassIdentity());
            return new JitCallDesc(thisType.getJitICDesc(typeSystem),
                    SuperRouter + ownerId.getName() + "$" + orig.ensureJitMethodName(typeSystem),
                    orig.getJitDesc(this, origType), Kind.Virtual, false, null);
        }
        return JitCallDesc.static_(getImplDesc(declaredType),
                constructor.ensureJitMethodName(typeSystem),
                constructor.getJitDesc(this, declaredType),
                false);
    }

    private void assembleConstructorSuperRouters(ClassBuilder classBuilder) {
        TypeInfo parentInfo = thisType.getParentType().ensureAccess(Access.PRIVATE).ensureTypeInfo();

        // the parent has already selected the effective declaration for each child name;
        // only children in this child's composition need constructor super-routers
        for (ChildInfo child : parentInfo.getChildInfosByName().values()) {
            if (!child.isVirtualClass()) {
                continue;
            }

            TypeConstant targetType = resolveType(child.getIdentity());
            TypeInfo     targetInfo = targetType.ensureAccess(Access.PRIVATE).ensureTypeInfo();

            for (MethodConstant ctorId : targetInfo.findMethods("construct", -1, MethodKind.Constructor)) {
                MethodInfo    ctor      = targetInfo.getMethodById(ctorId);
                MethodInfo    orig      = ctor.getChildConstructorOrigin();
                String        origName  = orig.ensureJitMethodName(typeSystem);
                TypeConstant  origType  = resolveType(orig.getHead().getIdentity().getClassIdentity());
                JitMethodDesc origJmd   = orig.getJitDesc(this, origType);
                JitMethodDesc targetJmd = ctor.getJitDesc(this, targetType);
                String        name      = SuperRouter + child.getName() + "$" + origName;
                JitCallDesc   target    = JitCallDesc.static_(getImplDesc(targetType),
                        ctor.ensureJitMethodName(typeSystem), targetJmd, false);

                assembleSuperRouter(classBuilder, name, origJmd, null, target);
            }
        }
    }

    /**
     * Generate one super-router per child name in each method chain. For example, D_C1's
     * super$C0$foo routes to D.C0's body, while super$C1$foo routes to D.C1's body; ordinary
     * foo() enters only the head of the complete composition chain.
     */
    private void assembleSuperRouters(ClassBuilder classBuilder) {
        for (MethodInfo method : typeInfo.getMethods().values()) {
            if (!method.isVirtual() || method.isCapped() ||
                    method.getIdentity().getNamespace() instanceof PropertyConstant) {
                continue;
            }

            // the chain visits more-specific bodies first; select D.C0 before the same-name
            // child B.C0, keeping one router per child name
            Set<String> routedChildNames = new LinkedHashSet<>();
            for (MethodBody body : method.ensureOptimizedMethodChain(typeInfo)) {
                IdentityConstant owner = body.getIdentity().getClassIdentity();
                if (body.isAbstract() || !(owner.getComponent() instanceof ClassStructure clz) ||
                        !clz.isVirtualChildClass() || !routedChildNames.add(owner.getName())) {
                    continue;
                }

                TypeConstant  targetType = resolveType(owner);
                TypeConstant  baseType   = findHardLeft(targetType);
                JitMethodDesc baseJmd    = body.getJitDesc(this, baseType);
                JitCallDesc   target     = resolveChildCall(body, targetType);
                ClassDesc     baseCD     = getPureImplDesc(baseType);
                String        methodName = target.name().substring(DetachedBody.length());
                String        routerName = SuperRouter + owner.getName() + "$" + methodName;

                assembleSuperRouter(classBuilder, routerName, baseJmd, baseCD, target);
            }
        }
    }

    /**
     * Emit a composition-specific continuation with the hard-left contract's descriptor.
     * For B.C1 extending B.C0 and D declaring same-name children D.C0 and D.C1, the route on D_C1 is:
     *
     *     void super$C0$foo(B_iC0 thi$, Ctx ctx) {
     *         D_C0.s$foo((D_iC0) thi$, ctx);
     *     }
     *
     * B_C1's already-compiled body invokes this route virtually on the same receiver.
     * Constructor descriptors already contain the receiver; other routes prepend baseCD.
     */
    private void assembleSuperRouter(ClassBuilder classBuilder, String name, JitMethodDesc baseJmd,
                                     ClassDesc baseCD, JitCallDesc target) {
        int variants = baseJmd.isOptimized && target.jmd().isOptimized ? 2 : 1;
        for (int variant = 0; variant < variants; variant++) {
            boolean        optimized  = variant == 1;
            String         routerName = name + (optimized ? OPT : "");
            MethodTypeDesc baseMD     = optimized ? baseJmd.optimizedMD : baseJmd.standardMD;
            MethodTypeDesc routerMD   = baseCD == null ? baseMD : baseMD.insertParameterTypes(0, baseCD);

            MethodTypeDesc targetMD = target.methodType(optimized);
            assert routerMD.parameterCount() == targetMD.parameterCount();
            classBuilder.withMethodBody(routerName, routerMD, ClassFile.ACC_PUBLIC, code -> {
                // the public router keeps the hard-left parameter types (e.g. B_iC0), while
                // its exact target can require a derived view (D_iC0); adapt only at this boundary
                for (int i = 0; i < routerMD.parameterCount(); i++) {
                    ClassDesc cd = routerMD.parameterType(i);
                    load(code, cd, code.parameterSlot(i));
                    if (!cd.equals(targetMD.parameterType(i))) {
                        code.checkcast(targetMD.parameterType(i));
                    }
                }
                target.invoke(code, optimized);
                addReturn(code, routerMD.returnType());
            });
        }
    }

    @Override
    protected void assembleMethod(ClassBuilder classBuilder, MethodInfo method,
                                    String name, JitMethodDesc jmd) {
        if (method.isCtorOrValidator() || method.isFunction() || method.isCapped() || method.isAbstract()) {
            super.assembleMethod(classBuilder, method, name, jmd);
            return;
        }

        IdentityConstant owner = method.getHead().getIdentity().getClassIdentity();
        if (method.getHead().getMethodStructure().isPropertyInitializer() && !owner.equals(thisId)) {
            // the declaring child's Impl class supplies this property initializer
            return;
        }

        TypeConstant targetType = resolveType(owner);
        if (!targetType.isVirtualChildClass()) {
            super.assembleMethod(classBuilder, method, name, jmd);
            return;
        }

        if (owner.equals(thisId) && !hasConstructionSiteAnnotations()) {
            assembleDetachedBody(classBuilder, method, name, jmd);
        }

        // private methods and property initializers need no virtual routing; their callers invoke
        // the declaring child's detached bodies directly
        if (method.isVirtual()) {
            assembleChildRouting(classBuilder, name, jmd, method.getHead(), targetType);
        }
    }

    /**
     * Route an ordinary method or accessor through a detached body or a hard-left super-router.
     *
     * <p>A declared D.C1.foo() gets a trivial entrance calling D_C1.s$foo(this, ctx, ...).
     * If hard-left B.C1 instead inherits foo() from B.C0, its entrance must call
     * this.super$C0$foo(this, ctx, ...). That forwarding entrance establishes the B.C1 boundary:
     * on a D.C1 object, Java dispatch selects D_C1's router and enters D.C0.foo(), rather than
     * bypassing it by directly entering B.C0.foo(). Neither entrance copies the Ecstasy body.
     */
    private void assembleChildRouting(ClassBuilder classBuilder, String name, JitMethodDesc jmd,
                                       MethodBody body, TypeConstant targetType) {
        JitCallDesc targetCall    = resolveChildCall(body, targetType);
        String      targetName    = targetCall.name();
        String      ownerName     = body.getIdentity().getClassIdentity().getName();
        boolean     isSuperRouter = isHardLeft(thisType) && !ownerName.equals(thisId.getName());

        JitCallDesc routeCall;
        if (isSuperRouter) {
            assert targetName.startsWith(DetachedBody);

            String    routeName  = SuperRouter + ownerName + "$" + targetName.substring(DetachedBody.length());
            ClassDesc receiverCD = getPureImplDesc(findHardLeft(targetType));

            routeCall = new JitCallDesc(art.CD(), routeName, jmd, Kind.Virtual, false, receiverCD);
        } else {
            routeCall = new JitCallDesc(targetCall.owner(), targetName, jmd, Kind.Static, false,
                            targetCall.receiverCD());
        }

        routeBody(classBuilder, name, routeCall, false);
        if (jmd.isOptimized) {
            routeBody(classBuilder, name + OPT, routeCall, true);
        }
    }

    private void routeBody(ClassBuilder classBuilder, String name, JitCallDesc call, boolean optimized) {
        MethodTypeDesc md = optimized ? call.jmd().optimizedMD : call.jmd().standardMD;
        classBuilder.withMethodBody(name, md, ClassFile.ACC_PUBLIC, code -> {
            // static call:  Owner.s$foo(this, ctx, ...)
            // virtual call: this.super$C0$foo(this, ctx, ...); the same object is both the
            // Java dispatch receiver and the explicit receiver passed to the detached body
            code.aload(0);
            if (call.isVirtual()) {
                code.aload(0);
            }
            for (int i = 0; i < md.parameterCount(); i++) {
                load(code, md.parameterType(i), code.parameterSlot(i));
            }
            call.invoke(code, optimized);
            addReturn(code, md.returnType());
        });
    }

    private void assembleDetachedBody(ClassBuilder classBuilder, MethodInfo method, String name,
                                      JitMethodDesc jmd) {
        if (!method.getHead().getMethodStructure().hasCode()) {
            return;
        }

        JitDetachedBodyDesc bodyJmd  = new JitDetachedBodyDesc(jmd, getCCDesc(thisType));
        String              bodyName = DetachedBody + name;
        BuildContext        bctx     = new BuildContext(this, typeInfo, method, bodyJmd);

        if (jmd.isOptimized) {
            assembleMethodWrapper(classBuilder, bodyName, bodyJmd.staticWrapper());
            bodyName += OPT;
        }

        MethodTypeDesc bodyMD = jmd.isOptimized ? bodyJmd.optimizedMD : bodyJmd.standardMD;

        doAssembleMethod(classBuilder, bctx, method, bodyName, bodyMD,
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
    }

    /**
     * Determine whether the specified type is represented by a hard-left class, as defined in the
     * class name specification.
     *
     * @see "doc/jit_class_names.txt"
     */
    public static boolean isHardLeft(TypeConstant type) {
        if (type.isVirtualChild() && !type.isPhantom()) {
            TypeConstant typeSuper = type.ensureAccess(Access.PRIVATE).ensureTypeInfo().getExtends();
            if (typeSuper == null || !typeSuper.isVirtualChildClass()) {
                return true;
            }

            IdentityConstant idThis  = type.getSingleUnderlyingClass(true);
            IdentityConstant idSuper = typeSuper.getSingleUnderlyingClass(true);
            return !idSuper.getName().equals(idThis.getName());
        }
        return false;
    }

    /**
     * Find the oldest same-name child in the inheritance chain, which is represented by a hard-left
     * class, as defined in the class name specification.
     */
    public TypeConstant findHardLeft(TypeConstant type) {
        while (true) {
            TypeConstant superType = getSuperChild(type);
            if (superType == null) {
                return type;
            }
            type = superType;
        }
    }

    /**
     * Find the nearest inherited virtual child with the same name as the specified child;
     * for example, D.C1 finds B.C1, not the differently named B.C0 that B.C1 extends.
     *
     * @return the inherited child type that doesn't retain the origin parent type, or null
     *         if there is no same-name super child
     */
    private TypeConstant getSuperChild(TypeConstant type) {
        assert type.isVirtualChild();

        if (type.isPhantom()) {
            return type.dropOriginParent(pool());
        }

        TypeConstant superType = type.ensureAccess(Access.PRIVATE).ensureTypeInfo().getExtends();
        if (superType != null && superType.isVirtualChildClass()) {
            IdentityConstant idThis  = type.getSingleUnderlyingClass(true);
            IdentityConstant idSuper = superType.getSingleUnderlyingClass(true);
            if (idSuper.getName().equals(idThis.getName())) {
                return superType.dropOriginParent(pool());
            }
        }
        return null;
    }

    /**
     * Choose the single Java superclass, not the complete Ecstasy composition chain. Prefer a
     * same-name super child (D_C1 extends B_C1); only a hard-left child follows its explicit
     * Ecstasy extends contribution (B_C1 extends B_C0). D.C0 participates in D.C1's Ecstasy
     * composition through interfaces and routing, not through Java class inheritance.
     */
    private TypeConstant computeJavaSuperType() {
        if (hasConstructionSiteAnnotations()) {
            return annotated.getBaseType();
        }

        // a phantom has no local declaration; its underlying identity can already be
        // the inherited child that must supply the Java superclass
        TypeConstant superType = getSuperChild(thisType);
        if (superType != null) {
            return superType;
        }
        superType = typeInfo.getExtends();
        return superType != null && superType.isVirtualChildClass()
                ? resolveType(superType.getSingleUnderlyingClass(true)) : superType;
    }

    /**
     * Resolve generic parameters for the specified class against the child type whose Java class
     * this builder is assembling, retaining the declaration's parent. For example, when
     * {@code D<T> extends B<T>}, resolving {@code B.C0} while assembling {@code D<String>.C1}
     * yields {@code B<String>.C0}, not {@code D<String>.C0}.
     */
    private TypeConstant resolveType(IdentityConstant id) {
        return id.getFormalType().resolveGenerics(pool(), thisType);
    }
}
