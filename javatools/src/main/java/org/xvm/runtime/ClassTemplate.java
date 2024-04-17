package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.function.Function;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.ObjectHandle.InitializingHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.TransientId;
import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlacePropertyBinary;
import org.xvm.runtime.Utils.InPlacePropertyUnary;
import org.xvm.runtime.Utils.UnaryAction;

import org.xvm.runtime.template.Proxy;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;
import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;

import org.xvm.util.Handy;


/**
 * ClassTemplate represents a run-time class.
 */
public abstract class ClassTemplate
        implements OpSupport
    {
    // construct the template
    public ClassTemplate(Container container, ClassStructure structClass)
        {
        f_container = container;
        f_struct    = structClass;
        f_sName     = structClass.getIdentityConstant().getPathString();

        // calculate the parents (inheritance and "native")
        ClassStructure structSuper = null;

        Contribution contribExtend = structClass.findContribution(Composition.Extends);
        if (contribExtend != null)
            {
            IdentityConstant idExtend = (IdentityConstant) contribExtend.
                getTypeConstant().getDefiningConstant();

            structSuper = (ClassStructure) idExtend.getComponent();
            }

        f_structSuper = structSuper;

        Set<String> setFieldsImplicit = registerImplicitFields(null);

        f_asFieldsImplicit = setFieldsImplicit == null
                ? Utils.NO_NAMES
                : setFieldsImplicit.toArray(Utils.NO_NAMES);
        }

    /**
     * Add all implicit fields to the specified set.
     */
    protected Set<String> registerImplicitFields(Set<String> setFields)
        {
        if (f_struct.isInstanceChild())
            {
            if (setFields == null)
                {
                setFields = new HashSet<>();
                }

            setFields.add(GenericHandle.OUTER);
            }
        return setFields;
        }

    /**
     * Add any native templates that may supplement the functionality of this template.
     */
    public void registerNativeTemplates()
        {
        }

    /**
     * Register the specified native template with the registry. Note, that this method can only
     * be called from {@link #registerNativeTemplates()} method.
     *
     * @param template  the new template
     */
    protected void registerNativeTemplate(ClassTemplate template)
        {
        ((NativeContainer) f_container).registerNativeTemplate(template.getCanonicalType(), template);
        }

    /**
     * Initialize native properties, methods and functions.
     */
    public void initNative()
        {
        }

    /**
     * Obtain the ClassStructure for this template.
     */
    public ClassStructure getStructure()
        {
        return f_struct;
        }

    /**
     * Obtain the canonical type that is represented by this {@link ClassTemplate}
     */
    public TypeConstant getCanonicalType()
        {
        return f_struct.getCanonicalType();
        }

    /**
     * Obtain the ClassConstant for this {@link ClassTemplate}.
     */
    public IdentityConstant getClassConstant()
        {
        return f_struct.getIdentityConstant();
        }

    /**
     * Obtain the inception ClassConstant that is represented by this {@link ClassTemplate}.
     *
     * Most of the time the inception class is the same as the structure's class, except
     * for a number of native rebased interfaces (Ref, Var, Const, Enum).
     *
     * Note: the following should always hold true:
     *      getInceptionClass().asTypeConstant().getOpSupport() == this;
     */
    protected IdentityConstant getInceptionClassConstant()
        {
        return getClassConstant();
        }

    /**
     * @return a super template; null only for Object
     */
    public ClassTemplate getSuper()
        {
        ClassTemplate templateSuper = m_templateSuper;
        if (templateSuper == null)
            {
            if (f_structSuper == null)
                {
                if ("Object".equals(f_sName))
                    {
                    return null;
                    }
                templateSuper = m_templateSuper = xObject.INSTANCE;
                }
            else
                {
                templateSuper = m_templateSuper =
                    f_container.getTemplate(f_structSuper.getIdentityConstant());
                }
            }
        return templateSuper;
        }

    /**
     * Obtain the canonical ClassComposition for this template at template's pool.
     *
     * This method should be used with care since it may be placing the ClassComposition *not*
     * in the current ConstantPool (used mostly by the native container injections).
     */
    public ClassComposition getCanonicalClass()
        {
        ClassComposition clz = m_clazzCanonical;
        if (clz == null)
            {
            m_clazzCanonical = clz = getCanonicalClass(f_container);
            }
        return clz;
        }

    /**
     * Obtain the canonical ClassComposition for this template at the specified pool.
     *
     * @param container  the pool to place the ClassComposition at
     */
    public ClassComposition getCanonicalClass(Container container)
        {
        TypeConstant typeCanonical = getCanonicalType();
        return (ClassComposition) ensureClass(container, computeInceptionType(typeCanonical), typeCanonical);
        }

    /**
     * Produce a ClassComposition for this template using the actual types for formal parameters.
     *
     * @param container   the ConstantPool to place a potentially created new type into
     * @param typeParams  the type parameters
     */
    public TypeComposition ensureParameterizedClass(Container container, TypeConstant... typeParams)
        {
        ConstantPool pool          = container.getConstantPool();
        TypeConstant typeInception = pool.ensureParameterizedTypeConstant(
            getInceptionClassConstant().getType(), typeParams).normalizeParameters();

        TypeConstant typeMask = getCanonicalType().adoptParameters(pool, typeParams);

        return ensureClass(container, typeInception, typeMask);
        }

    /**
     * Produce a TypeComposition using the specified actual type.
     * <p/>
     * Note: the passed type should be fully resolved and normalized
     *       (all formal parameters resolved)
     */
    public TypeComposition ensureClass(Container container, TypeConstant typeActual)
        {
        return ensureClass(container, computeInceptionType(typeActual), typeActual);
        }

    /**
     * Compute the inception type based on the actual type.
     */
    private TypeConstant computeInceptionType(TypeConstant typeActual)
        {
        IdentityConstant constInception = getInceptionClassConstant();
        if (typeActual.getDefiningConstant().equals(constInception))
            {
            return typeActual.isAccessSpecified()
                    ? typeActual.getUnderlyingType()
                    : typeActual;
            }

        // replace the TerminalType of the typeActual with the inception type
        Function<TypeConstant, TypeConstant> transformer = new Function<>()
            {
            public TypeConstant apply(TypeConstant type)
                {
                return type instanceof TerminalTypeConstant
                    ? constInception.getType()
                    : type.replaceUnderlying(typeActual.getConstantPool(), this);
                }
            };

        return transformer.apply(typeActual);
        }

    /**
     * Produce a ClassComposition for this type using the specified actual (inception) type
     * and the revealed (mask) type.
     *
     * Note: the passed inception and mask types should be fully resolved and normalized
     *       (all formal parameters resolved)
     * Note2: the following should always hold true: typeInception.getOpSupport() == this;
     */
    public TypeComposition ensureClass(Container container,
                                       TypeConstant typeInception, TypeConstant typeMask)
        {
        ClassComposition clz = container.ensureClassComposition(typeInception, this);

        assert typeMask.normalizeParameters().equals(typeMask);

        return typeMask.equals(typeInception) ? clz : clz.maskAs(typeMask);
        }

    @Override
    public int hashCode()
        {
        return f_sName.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        // class templates are singletons
        return this == obj;
        }

    @Override
    public String toString()
        {
        return f_struct.toString();
        }


    // ----- constructions  ------------------------------------------------------------------------

    /**
     * Specifies whether this template uses a GenericHandle for its objects.
     */
    public boolean isGenericHandle()
        {
        return true;
        }

    /**
     * Create an object handle for the specified constant and push it on the frame's local stack.
     * <p/>
     * Note: the overriding method *should never* push DeferredCallHandles on the stack.
     *
     * @param frame     the current frame
     * @param constant  the constant
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int createConstHandle(Frame frame, Constant constant)
        {
        return frame.raiseException("Unknown constant: " + constant);
        }

    /**
     * Construct an {@link ObjectHandle} of the specified class with the specified constructor.
     *
     * The following steps are to be performed:
     * <ul>
     *   <li>Invoke the auto-generated initializer for the "inception" type;
     *   <li>Invoke the specified constructor, potentially calling some super constructors
     *       passing "this:struct" as a target
     *   <li>Invoke all finalizers in the inheritance chain starting at the base passing
     *       "this:private" as a target
     * </ul>
     *
     * @param frame        the current frame
     * @param constructor  the MethodStructure for the constructor
     * @param clazz        the target class
     * @param hParent      (optional) parent instance
     * @param ahVar        the construction parameters
     * @param iReturn      the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz);

        if (hParent != null)
            {
            // strictly speaking a static child doesn't need to hold the parent's ref,
            // but that decision (not to hold) could be deferred or even statistically implemented,
            // since there could be benefits (e.g. during debugging) for knowing the parent
            ((GenericHandle) hStruct).setField(frame, GenericHandle.OUTER, hParent);
            }

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }

    /**
     * Create an ObjectHandle of the "struct" access for the specified natural class.
     *
     * @param frame  the current frame
     * @param clazz  the TypeComposition for the newly created handle
     *
     * @return the newly allocated handle
     */
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert clazz.getTemplate() == this;

        return new GenericHandle(clazz.ensureAccess(Access.STRUCT));
        }

    /**
     * Continuation of the {@link #construct} sequence after the struct has been created.
     *
     * @param frame        the current frame
     * @param constructor  (optional) the constructor to call; must be null if the struct
     *                     has already been initialized (fInitStruct == false)
     * @param fInitStruct  if true, the struct needs to be initialized; otherwise it already
     *                     has been and "constructor" must be null
     * @param hStruct      the struct handle
     * @param ahVar        the invocation parameters
     * @param iReturn      the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int proceedConstruction(Frame frame, MethodStructure constructor, boolean fInitStruct,
                                   ObjectHandle hStruct, ObjectHandle[] ahVar, int iReturn)
        {
        assert fInitStruct || constructor == null;

        return new Construct(constructor, fInitStruct, hStruct, ahVar, iReturn).proceed(frame);
        }

    /**
     * Perform any necessary action on validated structure before it turns into the "public" type.
     *
     * @param frame    the current frame
     * @param hStruct  the struct handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    protected int postValidate(Frame frame, ObjectHandle hStruct)
        {
        return Op.R_NEXT;
        }


    // ----- mutability ----------------------------------------------------------------------------

    /**
     * Make the specified object handle immutable.
     *
     * @param hTarget  the object handle
     *
     * @return true if the object has been successfully marked as immutable; false otherwise
     */
    protected boolean makeImmutable(ObjectHandle hTarget)
        {
        return !hTarget.isMutable() || hTarget.makeImmutable();
        }

    /**
     * Create a proxy handle that could be sent over the service/container boundaries and place it
     * on the caller frame stack.
     *
     * @param frame      the current frame
     * @param ctxTarget  the service context that the proxy "belongs" to
     * @param hTarget    the object handle that needs to be proxied
     * @param typeProxy  (optional) the [revealed] type of the proxy handle
     *
     * @return Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION
     */
    public int createProxyHandle(Frame frame, ServiceContext ctxTarget, ObjectHandle hTarget,
                                 TypeConstant typeProxy)
        {
        ClassComposition clzTarget  = (ClassComposition) hTarget.getComposition();
        TypeConstant     typeTarget = hTarget.getType();
        if (!hTarget.isMutable())
            {
            // the only reason we need to create a ProxyHandle for an immutable object is that its
            // type is "foreign" - doesn't belong to the type system of the service we're about to
            // pass it through; moreover even if "typeProxy" is known, there is no reason to widen
            // the proxy to it, since the receiving service may need to cast it to a narrower
            // type that is known within its type system; an example would be passing a module
            // across the container lines that may know to cast it to the WebApp or CatalogMetadata
            ProxyComposition clzProxy = new ProxyComposition(clzTarget, typeTarget);
            return frame.assignValue(Op.A_STACK, Proxy.makeHandle(clzProxy, ctxTarget, hTarget));
            }

        if (typeProxy == null)
            {
            return frame.raiseException(xException.mutableObject(frame, typeTarget));
            }

        if (typeProxy.isInterfaceType())
            {
            if (typeProxy.containsGenericType(true))
                {
                typeProxy = typeProxy.resolveGenerics(frame.poolContext(), typeTarget);
                }
            assert typeTarget.isA(typeProxy);

            ProxyComposition clzProxy = clzTarget.ensureProxyComposition(typeProxy);
            return frame.assignValue(Op.A_STACK, Proxy.makeHandle(clzProxy, ctxTarget, hTarget));
            }

        return frame.raiseException(xException.mutableObject(frame, typeTarget));
        }


    // ----- invocations ---------------------------------------------------------------------------

    /**
     * Invoke a method with zero or one return value on the specified target.
     *
     * @param frame    the current frame
     * @param chain    the CallChain representing the target method
     * @param hTarget  the target handle
     * @param ahVar    the invocation parameters
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Invoke a method with a return value of Tuple.
     *
     * @param frame    the current frame
     * @param chain    the CallChain representing the target method
     * @param hTarget  the target handle
     * @param ahVar    the invocation parameters
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invokeT(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Invoke a method with more than one return value.
     *
     * @param frame     the current frame
     * @param chain     the CallChain representing the target method
     * @param hTarget   the target handle
     * @param ahVar     the invocation parameters
     * @param aiReturn  the array of register ids to place the results of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget,
                        ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.invokeN(chain, 0, hTarget, ahVar, aiReturn);
        }

    /**
     * Invoke a native method with exactly one argument and zero or one return value.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param hArg     the invocation arguments
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.raiseException("Unknown native(1) method: \"" + method + "\" on " + this);
        }

    /**
     * Invoke a native method with zero or more than one argument and zero or one return value.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param ahArg    the invocation arguments
     * @param iReturn  the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                switch (method.getName())
                    {
                    case "toString":
                        return buildStringValue(frame, hTarget, iReturn);

                    case "makeImmutable":
                        return makeImmutable(hTarget)
                            ? frame.assignValue(iReturn, hTarget)
                            : frame.raiseException(
                                xException.unsupported(frame, "makeImmutable"));
                    }
                break;

            case 3:
                if ("equals".equals(method.getName()))
                    {
                    return frame.assignValue(iReturn,
                            xBoolean.makeHandle(ahArg[1] == ahArg[2]));
                    }
                break;
            }

        return frame.raiseException("Unknown native(N) method: \"" + method + "\" on " + this);
        }

    /**
     * Invoke a native method with any number of argument and return value of a Tuple.
     *
     * @param frame    the current frame
     * @param method   the target method
     * @param hTarget  the target handle
     * @param ahArg    the invocation arguments
     * @param iReturn  the register id to place the resulting Tuple into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokeNativeT(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        if (method.getParamCount() == 1)
            {
            switch (method.getReturnCount())
                {
                case 0:
                    switch (invokeNative1(frame, method, hTarget, ahArg[0], Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            return frame.assignValue(iReturn, xTuple.H_VOID);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, xTuple.H_VOID));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                case 1:
                    switch (invokeNative1(frame, method, hTarget, ahArg[0], Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return frame.assignTuple(iReturn, frame.popStack());

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignTuple(iReturn, frameCaller.popStack()));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                default:
                    // create a temporary frame with N registers; call invokeNativeNN into it
                    // and then convert the results into a Tuple
                    throw new UnsupportedOperationException();
                }
            }
        else
            {
            return switch (method.getReturnCount())
                {
                case 0 ->
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT      -> frame.assignValue(iReturn, xTuple.H_VOID);
                        case Op.R_EXCEPTION -> Op.R_EXCEPTION;
                        default             -> throw new IllegalStateException();
                        };

                case 1 ->
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Op.A_STACK))
                        {
                        case Op.R_NEXT      -> frame.assignTuple(iReturn, frame.popStack());
                        case Op.R_EXCEPTION -> Op.R_EXCEPTION;
                        default             -> throw new IllegalStateException();
                        };

                default ->
                    // create a temporary frame with N registers; call invokeNativeNN into it
                    // and then convert the results into a Tuple
                    throw new UnsupportedOperationException();
                };
            }
        }

    /**
     * Invoke a native method with any number of arguments and more than one return value.
     *
     * @param frame     the current frame
     * @param method    the target method
     * @param hTarget   the target handle
     * @param ahArg     the invocation arguments
     * @param aiReturn  the array of register ids to place the results of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        return frame.raiseException("Unknown native(NN) method: \"" + method + "\" on " + this);
        }


    // ----- property operations -------------------------------------------------------------------

    /**
     * Return the implicit field names for this template. These are fields which are not declared,
     * but are required by the runtime.
     *
     * @return the implicit field names
     */
    public String[] getImplicitFields()
        {
        return f_asFieldsImplicit;
        }

    /**
     * Retrieve a property value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        assert idProp != null;

        TypeComposition clzTarget = hTarget.getComposition();
        CallChain       chain     = clzTarget.getPropertyGetterChain(idProp);

        UnknownProperty:
        if (chain == null)
            {
            if (hTarget instanceof RefHandle hRef)
                {
                if (hRef.isProperty() && clzTarget instanceof PropertyComposition clzProp)
                    {
                    // this is likely a property access from a dynamically created Ref for a
                    // non-inflated property; ask the parent instead
                    clzTarget = clzProp.getParentComposition();
                    chain     = clzTarget.getPropertyGetterChain(idProp);
                    if (chain != null)
                        {
                        hTarget = hRef.getReferentHolder();
                        break UnknownProperty;
                        }
                    }
                }
            else
                {
                Component container = frame.f_function.getParent().getParent();
                if (container instanceof PropertyStructure prop)
                    {
                    // this is a Ref property access from a non-inflated property;
                    // create a Ref on the stack to access the property (e.g. "assigned")
                    switch (createPropertyRef(frame, hTarget, prop.getIdentityConstant(),
                                !prop.isVarAccessible(Access.PUBLIC), Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            {
                            RefHandle hRef = (RefHandle) frame.popStack();
                            return hRef.getTemplate().getPropertyValue(frame, hRef, idProp, iReturn);
                            }

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                {
                                RefHandle hRef = (RefHandle) frameCaller.popStack();
                                return hRef.getTemplate().
                                        getPropertyValue(frameCaller, hRef, idProp, iReturn);
                                });
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            // raise an exception for the original property instead
                            break ;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                }

            return frame.raiseException(
                xException.unknownProperty(frame, idProp.getName(), hTarget.getType()));
            }

        if (chain.isNative())
            {
            return invokeNativeGet(frame, idProp.getName(), hTarget, iReturn);
            }

        if (clzTarget.isStruct() || chain.isField())
            {
            return clzTarget.getFieldValue(frame, hTarget, idProp, iReturn);
            }

        MethodStructure method = chain.getTop();
        ObjectHandle[]  ahVar  = new ObjectHandle[method.getMaxVars()];
        FieldInfo       field  = clzTarget.getFieldInfo(idProp);

        if (field != null && field.isInflated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, field);

            if (hRef.getComposition().isStruct())
                {
                final CallChain chain0 = chain;
                Frame.Continuation stepNext = frameCaller ->
                    frameCaller.invoke1(chain0, 0, frameCaller.popStack(), ahVar, iReturn);

                return finishRefConstruction(frame, hRef, hThis, idProp, stepNext);
                }
            hTarget = hRef;
            }

        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Retrieve a field value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        assert idProp != null;

        if (!isGenericHandle())
            {
            return frame.raiseException("Not supported property: " + idProp.getName() +
                    " for " + hTarget.getType().getValueString());
            }

        GenericHandle hThis  = (GenericHandle) hTarget;
        FieldInfo     field  = hThis.getFieldInfo(idProp);
        ObjectHandle  hValue = field.isTransient()
                ? hThis.getTransientField(frame, field)
                : hThis.getField(field.getIndex());

        Uninitialized:
        if (hValue == null)
            {
            if (hThis.isInjected(idProp))
                {
                // there is a possibility of multiple service threads getting here at the same time
                // (e.g. getting an injected property on a module).
                synchronized (hThis)
                    {
                    hValue = hThis.getField(field.getIndex());
                    if (hValue == null)
                        {
                        return getInjectedProperty(frame, hThis, idProp, iReturn);
                        }
                    }

                if (Op.isDeferred(hValue))
                    {
                    // this can only be a deferred injected property construction call
                    // initialized by another service (thread); all we can do is wait...
                    // Note, that we assume that the native deferred injection never throws
                    return waitForInjectedProperty(frame, hThis, idProp, iReturn);
                    }

                break Uninitialized;
                }
            else if (field.isTransient())
                {
                Constant contInit = field.constInit;
                if (contInit != null)
                    {
                    TransientId hId = (TransientId) hThis.getField(field.getIndex());

                    hValue = frame.getConstHandle(contInit);
                    if (Op.isDeferred(hValue))
                        {
                        return hValue.proceed(frame, frameCaller ->
                            {
                            ObjectHandle hV = frameCaller.popStack();
                            frameCaller.f_context.setTransientValue(hId, hV);
                            return frameCaller.assignValue(iReturn, hV);
                            });
                        }
                    frame.f_context.setTransientValue(hId, hValue);
                    break Uninitialized;
                    }
                }

            String sErr = hThis.containsField(idProp) ?
                        "Un-initialized property \"" : "Invalid property \"";
            return frame.raiseException(xException.illegalState(frame, sErr + idProp + '"'));
            }

        if (field.isInflated())
            {
            RefHandle hRef = (RefHandle) hValue;
            if (!(hRef instanceof FutureHandle))
                {
                if (hRef.getComposition().isStruct())
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        {
                        RefHandle hR = (RefHandle) frameCaller.popStack();
                        return ((xRef) hR.getTemplate()).getReferent(frameCaller, hR, iReturn);
                        };

                    return finishRefConstruction(frame, hRef, hThis, idProp, stepNext);
                    }
                return ((xRef) hRef.getTemplate()).getReferent(frame, hRef, iReturn);
                }
            // Frame deals with FutureHandle itself
            }

        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Get the injected property value.
     *
     * Strictly speaking we would need to create an InjectedHandle, but for now just keep the
     * value itself.
     */
    private int getInjectedProperty(Frame frame, GenericHandle hThis, PropertyConstant idProp,
                                    int iReturn)
        {
        TypeInfo     info      = hThis.getType().ensureTypeInfo();
        PropertyInfo prop      = info.findProperty(idProp);
        Annotation   anno      = prop.getRefAnnotations()[0];
        Constant[]   aParams   = anno.getParams();
        Constant     constName = aParams.length == 0 ? null : aParams[0];
        String       sResource = constName instanceof StringConstant constString
                                ? constString.getValue()
                                : prop.getName();

        ObjectHandle hOpts = aParams.length < 2 ? xNullable.NULL : frame.getConstHandle(aParams[1]);
        if (Op.isDeferred(hOpts))
            {
            return hOpts.proceed(frame, frameCaller ->
                getInjectedProperty(frameCaller, hThis, idProp, iReturn));
            }

        ObjectHandle hValue = frame.getInjected(sResource, prop.getType(), hOpts);
        if (hValue == null)
            {
            return frame.raiseException(
                xException.illegalState(frame, "Unknown injectable resource \"" +
                    prop.getType().getValueString() + ' ' + sResource + '"'));
            }

        // store off the value (even if deferred), so a concurrent operation wouldn't "double dip"
        hThis.setField(frame, idProp, hValue);

        // native injection can return a deferred handle
        if (Op.isDeferred(hValue))
            {
            return hValue.proceed(frame, frameCaller ->
                {
                ObjectHandle hVal = frameCaller.popStack();
                hThis.setField(frame, idProp, hVal);
                return frameCaller.assignValue(iReturn, hVal);
                });
            }

        return frame.assignValue(iReturn, hValue);
        }

    /**
     * A helper method that causes the service to pause until the deferred injected value
     * is calculated by another service.
     */
    private int waitForInjectedProperty(Frame frame, GenericHandle hThis,
                                        PropertyConstant idProp, int iReturn)
        {
        Op[] aOpCheckAndPause = new Op[]
            {
            new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    ObjectHandle hValue = hThis.getField(frame, idProp);
                    return isDeferred(hValue)
                        ? R_PAUSE
                        : frame.returnValue(hValue, false);
                    }

                public String toString()
                    {
                    return "CheckAndYield";
                    }
                }
            };

        Frame frameWait = frame.createNativeFrame(aOpCheckAndPause, Utils.OBJECTS_NONE, iReturn, null);
        return frame.call(frameWait);
        }

    /**
     * Set a property value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property id
     * @param hValue   the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int setPropertyValue(Frame frame, ObjectHandle hTarget,
                                PropertyConstant idProp, ObjectHandle hValue)
        {
        assert idProp != null;

        if (hTarget.isStruct())
            {
            return setFieldValue(frame, hTarget, idProp, hValue);
            }

        TypeComposition clzTarget = hTarget.getComposition();
        CallChain       chain     = clzTarget.getPropertySetterChain(idProp);

        if (chain == null)
            {
            return frame.raiseException(
                xException.unknownProperty(frame, idProp.getName(), hTarget.getType()));
            }

        if (chain.isNative())
            {
            return invokeNativeSet(frame, hTarget, idProp.getName(), hValue);
            }

        if (chain.isField())
            {
            return clzTarget.setFieldValue(frame, hTarget, idProp, hValue);
            }

        MethodStructure method = chain.getTop();
        ObjectHandle[]  ahVar  = new ObjectHandle[method.getMaxVars()];
        ahVar[0] = hValue;

        FieldInfo field = clzTarget.getFieldInfo(idProp);
        if (field != null && field.isInflated())
            {
            hTarget = ((GenericHandle) hTarget).getField(frame, field);
            assert hTarget instanceof RefHandle;
            }

        return frame.invoke1(chain, 0, hTarget, ahVar, Op.A_IGNORE);
        }

    /**
     * Set a field value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hValue   the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int setFieldValue(Frame frame, ObjectHandle hTarget,
                             PropertyConstant idProp, ObjectHandle hValue)
        {
        assert idProp != null;

        GenericHandle hThis = (GenericHandle) hTarget;

        if (!hThis.containsField(idProp))
            {
            // this should've been caught by the compiler/verifier
            return frame.raiseException("Property is missing: " + idProp.getValueString());
            }

        FieldInfo field = hThis.getFieldInfo(idProp);
        if (!hThis.isMutable() && !field.isTransient())
            {
            return frame.raiseException(
                xException.immutableObjectProperty(frame, idProp.getName(), hThis.getType()));
            }

        if (!(hValue instanceof InitializingHandle)
                && !hValue.getType().isA(field.getType()))
            {
            return frame.raiseException(
                xException.typeMismatch(frame, hValue.getType().getValueString()));
            }

        if (field.isInflated())
            {
            RefHandle hRef = (RefHandle) (field.isTransient()
                    ? hThis.getTransientField(frame, field)
                    : hThis.getField(field.getIndex()));
            xVar template = (xVar) hRef.getTemplate();
            if (hThis.isStruct())
                {
                template.setNativeReferent(frame, hRef, hValue);
                }
            else
                {
                template.setReferent(frame, hRef, hValue);
                }
            }
        else
            {
            if (field.isTransient())
                {
                hThis.setTransientField(frame, field.getIndex(), hValue);
                }
            else
                {
                hThis.setField(field.getIndex(), hValue);
                }
            }
        return Op.R_NEXT;
        }

    /**
     * Invoke a native property "get" operation.
     *
     * @param frame     the current frame
     * @param sPropName the property name
     * @param hTarget   the target handle
     * @param iReturn   the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant typeTarget = hTarget.getType();
        if (typeTarget.containsGenericParam(sPropName))
            {
            TypeConstant type = typeTarget.resolveGenericType(sPropName);

            return frame.assignValue(iReturn, type.ensureTypeHandle(frame.f_context.f_container));
            }

        return frame.raiseException("Unknown native property: \"" + sPropName + "\" on " + this);
        }

    /**
     * Invoke a native property "set" operation.
     *
     * @param frame     the current frame
     * @param sPropName the property name
     * @param hTarget   the target handle
     * @param hValue    the new property value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        return frame.raiseException("Unknown native property: \"" + sPropName + "\" on " + this);
        }

    /**
     * Increment the property value and retrieve the new value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePreInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle hRef = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarPreInc(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
                UnaryAction.INC, this, hTarget, idProp, false, iReturn).doNext(frame);
        }

    /**
     * Retrieve the property value and then increment it.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePostInc(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarPostInc(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
                UnaryAction.INC, this, hTarget, idProp, true, iReturn).doNext(frame);
        }

    /**
     * Decrement the property value and retrieve the new value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePreDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarPreDec(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
                UnaryAction.DEC, this, hTarget, idProp, false, iReturn).doNext(frame);
        }

    /**
     * Retrieve the property value and then decrement it.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param iReturn  the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePostDec(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarPostDec(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
                UnaryAction.DEC, this, hTarget, idProp, true, iReturn).doNext(frame);
        }

    /**
     * Add the specified argument to the property value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyAdd(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarAdd(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.ADD, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Subtract the specified argument from the property value.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertySub(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarSub(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.SUB, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Multiply the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyMul(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarMul(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.MUL, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Divide the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyDiv(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarDiv(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.DIV, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Mod the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyMod(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarMod(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.MOD, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Shift-left the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyShl(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarShl(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.SHL, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Shift-right the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyShr(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarShr(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.SHR, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Unsigned shift-right the property value by the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyShrAll(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarShrAll(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.USHR, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * "And" the property value with the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyAnd(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarAnd(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.AND, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * "Or" the property value with the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyOr(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarOr(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.OR, this, hTarget, idProp, hArg).doNext(frame);
        }

    /**
     * Exclusive-or the property value with the specified argument.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property name
     * @param hArg     the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int invokePropertyXor(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
        {
        if (hTarget.isInflated(idProp))
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(frame, idProp);
            return hRef.getVarSupport().invokeVarXor(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
                BinaryAction.XOR, this, hTarget, idProp, hArg).doNext(frame);
        }


    // ----- Ref operations ------------------------------------------------------------------------

    /**
     * Create a property Ref or Var for the specified target and property.
     *
     * @param frame    the current frame
     * @param hTarget  the target handle
     * @param idProp   the property constant
     * @param fRO      true iff a Ref is required; Var otherwise
     * @param iReturn  the register to place the result in
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int createPropertyRef(Frame frame, ObjectHandle hTarget,
                                 PropertyConstant idProp, boolean fRO, int iReturn)
        {
        TypeComposition  clzTarget = hTarget.getComposition();
        ClassComposition clzThis;
        GenericHandle    hThis;

        if (clzTarget instanceof ClassComposition clz)
            {
            clzThis = clz;
            hThis   = (GenericHandle) hTarget;
            }
        else if (clzTarget instanceof PropertyComposition clzProp &&
                 hTarget instanceof RefHandle hRef)
            {
            clzThis = clzProp.getParentComposition();
            hThis   = (GenericHandle) hRef.getReferentHolder();
            }
        else
            {
            return frame.raiseException(
                    "Invalid Ref for " + idProp.getName() + " at " + hTarget.getType());
            }

        if (!hThis.containsField(idProp) &&
                clzThis.getPropertyGetterChain(idProp) == null)
            {
            return frame.raiseException(
                xException.unknownProperty(frame, idProp.getName(), hThis.getType()));
            }

        if (hThis.isInflated(idProp))
            {
            RefHandle hRef = (RefHandle) hThis.getField(frame, idProp);
            if (hRef.getComposition().isStruct())
                {
                Frame.Continuation stepNext = frameCaller ->
                    frameCaller.assignValue(iReturn, frameCaller.popStack());
                return finishRefConstruction(frame, hRef, hThis, idProp, stepNext);
                }
            return frame.assignValue(iReturn, hRef);
            }

        PropertyInfo infoProp = clzThis.getPropertyInfo(idProp);
        if (infoProp == null)
            {
            return frame.raiseException(
                xException.unknownProperty(frame, idProp.getName(), hThis.getType()));
            }

        TypeComposition clzRef = clzThis.ensurePropertyComposition(infoProp);
        RefHandle       hRef   = new RefHandle(clzRef, frame, hThis, idProp);

        return frame.assignValue(iReturn, hRef);
        }


    // ----- support for equality and comparison ---------------------------------------------------

    /**
     * Compare for equality two object handles that both belong to the specified class.
     *
     * @param frame    the current frame
     * @param clazz    the class to use for the equality check
     * @param hValue1  the first value
     * @param hValue2  the second value
     * @param iReturn  the register id to place a Boolean result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        // if there is an "equals" function that is not native (on the Object itself),
        // we need to call it
        CallChain chain = clazz.getMethodCallChain(clazz.getConstantPool().sigEquals());
        if (chain != null && !chain.isNative())
            {
            ObjectHandle[] ahVars = new ObjectHandle[chain.getMaxVars()];
            ahVars[0] = clazz.getType().ensureTypeHandle(frame.f_context.f_container);
            ahVars[1] = hValue1;
            ahVars[2] = hValue2;
            return frame.call1(chain.getTop(), null, ahVars, iReturn);
            }

        return callEqualsImpl(frame, clazz, hValue1, hValue2, iReturn);
        }

    /**
     * Default implementation for "equals"; overridden only by xConst.
     */
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        TypeConstant  type;
        ClassTemplate template;
        if (!hValue1.isMutable() && !hValue2.isMutable() &&
            (type = hValue1.getType()).equals(hValue2.getType()) &&
                (template = hValue1.getTemplate()) == hValue2.getTemplate() &&
                 template instanceof xConst)
            {
            // we are in Object.equals() method for two constants; according to the doc:
            //   "comparing any two objects will only result in equality if they are the
            //    same object, or if they are two constant objects with identical values"
            clazz = template.ensureClass(frame.f_context.f_container, type);

            return template.callEquals(frame, clazz, hValue1, hValue2, iReturn);
            }
        return frame.assignValue(iReturn, xBoolean.FALSE);
        }

    /**
     * Compare for order two object handles that both belong to the specified class.
     *
     * @param frame    the current frame
     * @param clazz    the class to use for the comparison test
     * @param hValue1  the first value
     * @param hValue2  the second value
     * @param iReturn  the register id to place an Ordered result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
            }

        // if there is a "compare" function, we need to call it
        CallChain chain = clazz.getMethodCallChain(clazz.getConstantPool().sigCompare());
        if (chain != null && !chain.isNative())
            {
            ObjectHandle[] ahVars = new ObjectHandle[chain.getMaxVars()];
            ahVars[0] = clazz.getType().ensureTypeHandle(frame.f_context.f_container);
            ahVars[1] = hValue1;
            ahVars[2] = hValue2;
            return frame.call1(chain.getTop(), null, ahVars, iReturn);
            }

        return callCompareImpl(frame, clazz, hValue1, hValue2, iReturn);
        }

    /**
     * Default implementation for "compare"; overridden only by xConst.
     */
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.raiseException("No implementation for \"compare()\" function at " + f_sName);
        }

    /**
     * Compare for identity equality two object handles that both associated with this template.
     *
     * As documented at Ref.x equals() function:
     * <pre><quote>
     *   Specifically, two references are equal if they reference the same runtime object.
     *   Additionally, for optimization purposes, the runtime is *permitted* to indicate that two
     *   references to two separate runtime objects are equal, in the case where both references
     *   are to immutable objects whose structures are identical.
     * </quote></pre>
     *
     * Note: this method is inherently native; it must be answered without calling any natural code
     *
     * @param hValue1  the first value
     * @param hValue2  the second value
     *
     * @return true iff the identities are equal
     */
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return hValue1 == hValue2;
        }


    // ---- OpSupport implementation ---------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        if (type instanceof AnnotatedTypeConstant typeAnno)
            {
            while (true)
                {
                TypeConstant typeBase = typeAnno.getUnderlyingType();
                if (typeBase instanceof AnnotatedTypeConstant typeAnnoBase)
                    {
                    typeAnno = typeAnnoBase;
                    }
                else
                    {
                    return f_container.getTemplate(typeBase);
                    }
                }
            }
        return this;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "add", "+", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "sub", "-", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "mul", "*", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "div", "/", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "mod", "%", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "shiftLeft", "<<", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "shiftRight", ">>", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "shiftAllRight", ">>>", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "and", "&", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "or", "|", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "xor", "^", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        return getOpChain(frame, hTarget, "divrem", "/%", hArg).invoke(frame, hTarget, hArg, aiReturn);
        }

    @Override
    public int invokeIRangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "to", "..", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeERangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "exTo", ">..", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeIRangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "toEx", "..<", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeERangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(frame, hTarget, "exToEx", ">..<", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(frame, hTarget, "neg", null, null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(frame, hTarget, "not", "~", null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(frame, hTarget, "nextValue", null, null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(frame, hTarget, "prevValue", null, null).invoke(frame, hTarget, iReturn);
        }

    /**
     * @return a call chain for the specified op; throw if none exists
     */
    protected CallChain getOpChain(Frame frame, ObjectHandle hTarget, String sName, String sOp,
                                   ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, sName, sOp, hArg);
        if (chain == null)
            {
            chain = new CallChain.ExceptionChain(xException.makeHandle(frame,
                     "Missing operation \"" + sOp + "\" on " + hTarget.getType().getValueString()));
            }
        return chain;
        }

    /**
     * @return a call chain for the specified op and argument or null if none exists
     */
    public CallChain findOpChain(ObjectHandle hTarget, String sName, String sOp, ObjectHandle hArg)
        {
        TypeInfo info = hTarget.getType().ensureTypeInfo();

        Set<MethodConstant> setMethods = info.findOpMethods(sName, sOp, hArg == null ? 0 : 1);
        switch (setMethods.size())
            {
            case 0:
                return null;

            case 1:
                {
                MethodConstant    idMethod = setMethods.iterator().next();
                SignatureConstant sig      = idMethod.getSignature();
                if (hArg != null)
                    {
                    TypeConstant typeArg   = hArg.getType();
                    TypeConstant typeParam = sig.getRawParams()[0];

                    if (!typeArg.isA(typeParam))
                        {
                        // soft assert
                        System.err.println("Invalid argument type \"" + typeArg.getValueString() +
                            "\" for \"" + sName + "\" operation on " + hTarget.getType().getValueString());
                        return null;
                        }
                    }
                return hTarget.getComposition().getMethodCallChain(sig);
                }

            default:
                {
                if (hArg != null)
                    {
                    SignatureConstant sigBest = null;
                    TypeConstant      typeArg = hArg.getType();
                    for (MethodConstant idMethod : setMethods)
                        {
                        SignatureConstant sig       = idMethod.getSignature();
                        TypeConstant      typeParam = sig.getRawParams()[0];

                        if (typeArg.isA(typeParam))
                            {
                            if (sigBest == null)
                                {
                                sigBest = sig;
                                }
                            else if (!sigBest.equals(sig))
                                {
                                // We know that the compiler didn't see any ambiguity, which means
                                // it's caused now by the argument actual type. Consider an example:
                                //      Object[] array = [];
                                //      Object   value = "abc";
                                //      array += value;
                                // Since the actual "value" type is String, which is Iterable<Char>,
                                // both "@Op("+") add(Object)" and "@Op("+") addAll(Iterable<Object>)"
                                // methods fit.
                                //
                                // Until we have a better solution, let's choose a signature with a
                                // simpler type, because it should've been the one chosen by the
                                // compiler.
                                int nBestDepth  = sigBest.getRawParams()[0].getTypeDepth();
                                int nParamDepth = typeParam.getTypeDepth();

                                if (nParamDepth < nBestDepth)
                                    {
                                    sigBest = sig;
                                    }
                                else if (nParamDepth == nBestDepth)
                                    {
                                    sigBest = null;
                                    break;
                                    }
                                }
                            }
                        }
                    if (sigBest != null)
                        {
                        return hTarget.getComposition().getMethodCallChain(sigBest);
                        }
                    }

                // soft assert
                System.err.println("Ambiguous \"" + sName + "\" operation on " +
                        hTarget.getType().getValueString());
                return null;
                }
            }
        }

    /**
     * @return a call chain for the specified op and arguments or null if none exists
     */
    public CallChain findOpChain(ObjectHandle hTarget, String sOp, ObjectHandle[] ahArg)
        {
        TypeInfo info  = hTarget.getType().ensureTypeInfo();
        int      cArgs = ahArg.length;

        Set<MethodConstant> setMethods = info.findOpMethods(sOp, sOp, cArgs);
        switch (setMethods.size())
            {
            case 0:
                return null;

            case 1:
                {
                MethodConstant idMethod = setMethods.iterator().next();
                return hTarget.getComposition().getMethodCallChain(idMethod.getSignature());
                }

            default:
                {
                NextMethod:
                for (MethodConstant idMethod : setMethods)
                    {
                    SignatureConstant sig = idMethod.getSignature();

                    for (int i = 0; i < cArgs; i++)
                        {
                        ObjectHandle hArg      = ahArg[i];
                        TypeConstant typeArg   = hArg.getType();
                        TypeConstant typeParam = sig.getRawParams()[i];

                        if (!typeArg.isA(typeParam))
                            {
                            continue NextMethod;
                            }
                        }
                    return hTarget.getComposition().getMethodCallChain(sig);
                    }

                // soft assert
                System.err.println("Ambiguous \"" + sOp + "\" operation on " +
                        hTarget.getType().getValueString());
                return null;
                }
            }
        }

    /**
     * Call the first (if any) validator for this class.
     *
     * @param frame    the current frame
     * @param hStruct  the struct handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    protected int callValidator(Frame frame, ObjectHandle hStruct)
        {
        TypeComposition clz   = hStruct.getComposition();
        CallChain       chain = clz.getMethodCallChain(clz.getConstantPool().sigValidator());
        if (chain.isNative())
            {
            return Op.R_NEXT;
            }

        MethodStructure method   = chain.getTop();
        Frame           frameTop = frame.createFrame1(method, hStruct,
                                        new ObjectHandle[method.getMaxVars()], Op.A_IGNORE);
        if (chain.getDepth() > 1)
            {
            Frame.Continuation nextStep = new Frame.Continuation()
                {
                @Override
                public int proceed(Frame frameCaller)
                    {
                    MethodStructure methodNext = chain.getMethod(index);
                    Frame           frameNext  = frameCaller.createFrame1(methodNext, hStruct,
                                new ObjectHandle[methodNext.getMaxVars()], Op.A_IGNORE);
                    if (++index < chain.getDepth())
                        {
                        frameNext.addContinuation(this);
                        }
                    return frame.callInitialized(frameNext);
                    }
                private int index = 1;
                };
            frameTop.addContinuation(nextStep);
            }
        return frame.callInitialized(frameTop);
        }

    /**
     * Finish the construction of a Ref-annotated property.
     *
     * @param frame         the current frame
     * @param hRef          the RefHandle holding the "inflated" property structure
     * @param hOuter        the property holder handle
     * @param idProp        the property id
     * @param continuation  the continuation to perform after the construction is done
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    protected int finishRefConstruction(Frame frame, RefHandle hRef, GenericHandle hOuter,
                                        PropertyConstant idProp, Frame.Continuation continuation)
        {
        // call annotation constructors;
        // hRef's type is "struct of annotated type" or PropertyClassTypeConstant
        AnnotatedTypeConstant typeAnno  = (AnnotatedTypeConstant) hRef.getComposition().getBaseType();
        TypeConstant          typeMixin = typeAnno.getAnnotationType();
        ClassTemplate         mixin     = frame.f_context.f_container.getTemplate(typeMixin);

        switch (mixin.proceedConstruction(frame, null, true, hRef, Utils.OBJECTS_NONE, Op.A_STACK))
            {
            case Op.R_NEXT:
                hRef = (RefHandle) frame.peekStack();
                hRef.setField(frame, GenericHandle.OUTER, hOuter);
                hOuter.setField(frame, idProp, hRef);
                return continuation.proceed(frame);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    RefHandle hRefPublic = (RefHandle) frameCaller.peekStack();
                    hRefPublic.setField(frame, GenericHandle.OUTER, hOuter);
                    hOuter.setField(frameCaller, idProp, hRefPublic);
                    return continuation.proceed(frameCaller);
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }


    // ----- numeric support -----------------------------------------------------------------------

    /**
     * Raise an overflow exception.
     *
     * @return {@link Op#R_EXCEPTION}
     */
    public int overflow(Frame frame)
        {
        return frame.raiseException(xException.outOfBounds(frame, f_sName + " overflow"));
        }


    // ----- toString() support --------------------------------------------------------------------

    /**
     * Build a String handle for a human-readable representation of the target handle.
     *
     * @param frame    the current frame
     * @param hTarget  the target
     * @param iReturn  the register id to place a String result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Invalidate the TypeInfo for the canonical type.
     */
    protected void invalidateTypeInfo()
        {
        getStructure().getCanonicalType().invalidateTypeInfo();
        }

    /**
     * @return the constant pool associated with the container this template belongs to
     */
    public ConstantPool pool()
        {
        return f_container.getConstantPool();
        }


    // =========== TEMPORARY =======================================================================

    /**
     * Mark the specified method as native.
     */
    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        TypeConstant[] atypeParam  = getTypeConstants(this, asParamType);
        TypeConstant[] atypeReturn = getTypeConstants(this, asRetType);

        MethodStructure method = getStructure().findMethodDeep(sName, m ->
                {
                if (atypeParam != null)
                    {
                    TypeConstant[] atypeParamTest = m.getIdentityConstant().getRawParams();
                    int            cParams        = atypeParamTest.length;
                    if (cParams != atypeParam.length)
                        {
                        return false;
                        }

                    for (int i = 0; i < cParams; i++)
                        {
                        if (!atypeParamTest[i].isA(atypeParam[i]))
                            {
                            return false;
                            }
                        }
                    }
                if (atypeReturn != null)
                    {
                    TypeConstant[] atypeReturnTest = m.getIdentityConstant().getRawReturns();
                    int            cReturns        = atypeReturnTest.length;
                    if (cReturns != atypeReturn.length)
                        {
                        return false;
                        }

                    for (int i = 0; i < cReturns; i++)
                        {
                        if (!atypeReturnTest[i].isA(atypeReturn[i]))
                            {
                            return false;
                            }
                        }
                    }
                return true;
                });

        if (method == null)
            {
            System.err.println("Missing method " + f_sName + '.' + sName + ' '
                    + Arrays.toString(asParamType) + "->" + Arrays.toString(asRetType));
            }
        else
            {
            if (!method.isNative())
                {
                ClassStructure clz = method.getContainingClass();
                if (clz != f_struct)
                    {
                    if (method.isFunction())
                        {
                        throw new IllegalStateException("Native function " +
                                method.getIdentityConstant().getValueString() + " at " + f_sName);
                        }
                    Access access = method.getAccess();
                    if (access == Access.PRIVATE)
                        {
                        throw new IllegalStateException("Inaccessible method " +
                                method.getIdentityConstant().getValueString() + " at " + f_sName);
                        }

                    ConstantPool pool         = pool();
                    Annotation   annoOverride = pool.ensureAnnotation(pool.clzOverride());

                    method = f_struct.createMethod(false, access, new Annotation[]{annoOverride},
                                method.getReturnArray(), method.getName(), method.getParamArray(),
                                false, false);
                    method.setSynthetic(true);
                    }

                method.markNative();
                }
            }
        }

    /**
     * Get a class type for the specified name in the context of the specified template.
     */
    private TypeConstant[] getTypeConstants(ClassTemplate template, String[] asType)
        {
        if (asType == null)
            {
            return null;
            }

        int cTypes = asType.length;
        TypeConstant[] aType = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            aType[i] = getClassType(asType[i].trim(), template);
            }
        return aType;
        }

    private TypeConstant getClassType(String sName, ClassTemplate template)
        {
        ConstantPool pool = template.pool();

        if (sName.startsWith("immutable "))
            {
            return pool.ensureImmutableTypeConstant(
                    getClassType(sName.substring("immutable ".length()), template));
            }

        if (sName.startsWith("@"))
            {
            int ofEnd = sName.indexOf(" ", 1);
            if (ofEnd < 0)
                {
                throw new IllegalArgumentException("Invalid annotation: " + sName);
                }
            TypeConstant typeAnno = getClassType(sName.substring(1, ofEnd), template);
            TypeConstant typeMain = getClassType(sName.substring(ofEnd + 1), template);
            return pool.ensureAnnotatedTypeConstant(typeAnno.getDefiningConstant(), null, typeMain);
            }

        boolean fNullable = sName.endsWith("?");
        if (fNullable)
            {
            sName = sName.substring(0, sName.length() - 1);
            }

        TypeConstant constType = null;

        int ofTypeParam = sName.indexOf('<');
        if (ofTypeParam >= 0)
            {
            String sParam = sName.substring(ofTypeParam + 1, sName.length() - 1);
            String sSimpleName = sName.substring(0, ofTypeParam);

            if (sSimpleName.endsWith("!"))
                {
                sSimpleName = sSimpleName.substring(0, sSimpleName.length() - 1);
                }

            IdentityConstant idClass = pool.ensureEcstasyClassConstant(sSimpleName);
            if (idClass != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                TypeConstant[] acType = getTypeConstants(template, asType);

                constType = pool.ensureClassTypeConstant(idClass, null, acType);
                }
            }
        else
            {
            if ("this".equals(sName))
                {
                IdentityConstant constId = template == null ?
                    pool.clzObject() : template.getClassConstant();
                return pool.ensureThisTypeConstant(constId, null);
                }

            ClassStructure struct = template.getStructure();
            if (template != null && struct.indexOfGenericParameter(sName) >= 0)
                {
                // generic type property
                PropertyStructure prop = (PropertyStructure) struct.getChild(sName);
                return pool.ensureTerminalTypeConstant(prop.getIdentityConstant());
                }

            Component component = f_container.getClassStructure(sName);
            if (component != null)
                {
                IdentityConstant constId = component.getIdentityConstant();
                constType = switch (constId.getFormat())
                    {
                    case Module,
                         Package,
                         Class    -> constId.getType();
                    case Typedef  -> ((TypedefStructure) component).getType();
                    default       -> constType;
                    };
                }
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
            }

        return fNullable ? constType.ensureNullable() : constType;
        }

    /**
     * Mark the specified property and its accessors as native.
     * <p/>
     * Note: if there are no accessors and the native property is a read/write
     *       (not ref-annotated and no explicit read-only at the declaration level),
     *       then we will mark the property as @Unassigned, which will retain the property field,
     *       but will exempt it from the post-construction assignability check.
     */
    public void markNativeProperty(String sPropName)
        {
        PropertyStructure prop = getStructure().findPropertyDeep(sPropName);
        if (prop == null)
            {
            System.err.println("Missing property " + f_sName + "." + sPropName);
            }
        else
            {
            Access accessRef = prop.getAccess();
            if (!prop.isNative())
                {
                ClassStructure clz = prop.getContainingClass();
                if (clz == f_struct)
                    {
                    MethodStructure methGetter = prop.getGetter();
                    if (methGetter != null)
                        {
                        methGetter.markNative();
                        }

                    MethodStructure methSetter = prop.getSetter();
                    if (methSetter != null)
                        {
                        methSetter.markNative();
                        }

                    if (methGetter == null && methSetter == null &&
                            !prop.isExplicitReadOnly() && !prop.isExplicitOverride() && !prop.isRefAnnotated())
                        {
                        prop.addAnnotation(pool().clzUnassigned());
                        }
                    prop.markNative();
                    }
                else if (accessRef != Access.PRIVATE)
                    {
                    if (prop.isStatic())
                        {
                        throw new IllegalStateException("Native static property " +
                                    sPropName + " at " + f_sName);
                        }
                    Access accessVar = prop.getVarAccess();
                    if (accessVar == Access.PRIVATE)
                        {
                        throw new IllegalStateException("Inaccessible property " +
                                    sPropName + " at " + f_sName);
                        }
                    ConstantPool      pool     = pool();
                    PropertyStructure propOver = f_struct.createProperty(false, accessRef, accessVar,
                                prop.getType(), sPropName);
                    if (prop.containsPropertyAnnotation(pool.clzRO()))
                        {
                        propOver.addAnnotation(pool.clzRO());
                        }
                    propOver.addAnnotation(pool.clzOverride());
                    propOver.setSynthetic(true);
                    propOver.markNative();

                    if (prop.getGetter() != null)
                        {
                        Parameter[] aParams  = Parameter.NO_PARAMS;
                        Parameter[] aReturns = new Parameter[] {new Parameter(pool, prop.getType(),
                                                    null, null, true, 0, false)};

                        MethodStructure methodGet = propOver.createMethod(false, accessRef, null,
                                                        aReturns, "get", aParams, false, false);
                        methodGet.addAnnotation(pool.clzOverride());
                        methodGet.setSynthetic(true);
                        methodGet.markNative();
                        }
                    }
                }
            }
        }

    /**
     * Helper class for construction actions.
     */
    protected class Construct
            implements Frame.Continuation
        {
        // passed in arguments
        private final MethodStructure constructor;
        private final ObjectHandle    hStruct;
        private final ObjectHandle[]  ahVar;
        private final int             iReturn;

        // internal fields
        private final Annotation[] aAnnoMixin; // annotation mixins to construct
        private final boolean      fAnonymous;
        private int                ixAnno;     // index of the next annotation mixin
        private List<Frame>        listFinalizable;
        private int                ixStep;

        public Construct(MethodStructure constructor,
                         boolean         fInitStruct,
                         ObjectHandle    hStruct,
                         ObjectHandle[]  ahVar,
                         int             iReturn)
            {
            this.constructor = constructor;
            this.hStruct     = hStruct;
            this.ahVar       = ahVar;
            this.iReturn     = iReturn;

            TypeComposition composition = hStruct.getComposition();
            assert composition.isStruct();

            // if the structure is already initialized, there should be no constructor to call
            assert fInitStruct || constructor == null;

            aAnnoMixin = composition.getBaseType().ensureTypeInfo().getMixinAnnotations();
            ixStep     = fInitStruct ? 0 : 2;
            ixAnno     = 0;
            fAnonymous = constructor != null && constructor.isAnonymousClassWrapperConstructor();
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            // assume that we have class D with an auto-generated initializer (AI), a constructor (CD),
            // and a finalizer (FD) that extends B with a constructor (CB) and a finalizer (FB)
            // the call sequence should be:
            //
            //  ("new" op-code) => AI -> CD => CB -> FB -> FD -> "assign" (continuation)
            //
            // -> indicates a call via continuation
            // => indicates a call via Construct op-code
            //
            // the only exception to that flow is an anonymous class wrapper constructor that assigns
            // captured values to the anonymous class properties and needs to be called prior to
            // the class initializer; it also calls the default initializer internally

            while (true)
                {
                int iResult;
                switch (ixStep++)
                    {
                    case 0: // call an anonymous class "wrapper" constructor first
                        if (fAnonymous)
                            {
                            // wrapper constructor calls the initializer itself; skip steps 1 and 3
                            ixStep  = 2;
                            iResult = frameCaller.call1(constructor, hStruct, ahVar, Op.A_IGNORE);
                            break;
                            }
                        ixStep++;
                        // fall through

                    case 1: // call auto-generated default initializer
                        {
                        MethodStructure methodAI = hStruct.getComposition().ensureAutoInitializer();
                        if (methodAI != null)
                            {
                            iResult = frameCaller.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);
                            break;
                            }
                        ixStep++;
                        // fall through
                        }

                    case 2: // call annotation mixin constructors
                        {
                        if (aAnnoMixin.length > 0)
                            {
                            Annotation      anno       = aAnnoMixin[ixAnno++];
                            Constant[]      aconstArgs = anno.getParams();
                            int             cArgs      = aconstArgs.length;
                            ClassConstant   idAnno     = (ClassConstant) anno.getAnnotationClass();
                            ClassStructure  structAnno = (ClassStructure) idAnno.getComponent();
                            MethodStructure ctorAnno   = structAnno.findMethod("construct", cArgs);

                            if (ctorAnno.isNoOp())
                                {
                                iResult = Op.R_NEXT;
                                }
                            else
                                {
                                ObjectHandle[] ahArgs = new ObjectHandle[ctorAnno.getMaxVars()];

                                Frame frameCtor = frameCaller.createFrame1(
                                    ctorAnno, hStruct, ahArgs, Op.A_IGNORE);

                                // if an annotation argument is represented by a RegisterConstant,
                                // then it's the *current* frame's register value;
                                // otherwise, Frame#getConstHandle() may return a deferred handle,
                                // and since we are going to pass it as an argument for the
                                // constructor, we need to use the *constructor* frame to
                                // [potentially] create that deferred handle
                                for (int i = 0; i < cArgs; i++)
                                    {
                                    Constant constArg = aconstArgs[i];

                                    ahArgs[i] = constArg instanceof RegisterConstant constReg
                                            ? constReg.getHandle(frameCaller)
                                            : frameCtor.getConstHandle(constArg);
                                    }

                                prepareFinalizer(frameCtor, ctorAnno, ahArgs);

                                iResult = frameCaller.callInitialized(frameCtor);
                                }

                            if (ixAnno < aAnnoMixin.length)
                                {
                                // repeat step 2
                                ixStep = 2;
                                }
                            break;
                            }
                        ixStep++;
                        // fall through
                        }

                    case 3: // call the base constructor
                        if (constructor != null && !constructor.isNoOp() && !fAnonymous)
                            {
                            Frame frameCD = frameCaller.createFrame1(
                                    constructor, hStruct, ahVar, Op.A_IGNORE);

                            prepareFinalizer(frameCD, constructor, ahVar);

                            iResult = frameCaller.callInitialized(frameCD);
                            break;
                            }
                        ixStep++;
                        // fall through

                    case 4: // validation
                        iResult = callValidator(frameCaller, hStruct);
                        break;

                    case 5: // check unassigned
                        {
                        List<String> listUnassigned;
                        if ((listUnassigned = hStruct.validateFields()) != null)
                            {
                            return frameCaller.raiseException(xException.unassignedFields(
                                    frameCaller, hStruct.getType().getValueString(), listUnassigned));
                            }
                        ixStep++;
                        // fall through
                        }

                    case 6: // native post-construction validation
                        iResult = postValidate(frameCaller, hStruct);
                        break;

                    case 7:
                        {
                        ObjectHandle hPublic      = hStruct.ensureAccess(Access.PUBLIC);
                        List<Frame>  listFinalize = listFinalizable;
                        if (listFinalize == null)
                            {
                            return frameCaller.assignValue(iReturn, hPublic);
                            }

                        // create a chain (stack) of finalizers
                        int              cFn        = listFinalize.size();
                        FullyBoundHandle hfnFinally = listFinalize.get(cFn - 1).m_hfnFinally;
                        for (int i = cFn - 2; i >= 0; i--)
                            {
                            hfnFinally = listFinalize.get(i).m_hfnFinally.chain(hfnFinally);
                            }

                        return hfnFinally.callChain(frameCaller, hPublic, frame_ ->
                                    frame_.assignValue(iReturn, hPublic));
                        }

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }

        private void prepareFinalizer(Frame frame, MethodStructure ctor, ObjectHandle[] ahVar)
            {
            if (listFinalizable == null)
                {
                listFinalizable = new ArrayList<>();
                }

            FullyBoundHandle hfn = Utils.makeFinalizer(frame, ctor, ahVar);
            if (hfn == null)
                {
                // in case super constructors have their own finalizers, we need a non-null anchor
                // that may be replaced by Frame.chainFinalizers()
                hfn = FullyBoundHandle.NO_OP;
                }

            frame.m_hfnFinally = hfn;
            listFinalizable.add(frame);
            }
        }


    // ----- constants and fields ------------------------------------------------------------------

    public static String[] VOID    = new String[0];
    public static String[] THIS    = new String[] {"this"};
    public static String[] OBJECT  = new String[] {"Object"};
    public static String[] INT     = new String[] {"numbers.Int64"};
    public static String[] STRING  = new String[] {"text.String"};
    public static String[] BOOLEAN = new String[] {"Boolean"};
    public static String[] BYTES   = new String[] {"collections.Array<numbers.UInt8>"};

    /**
     * The container.
     */
    public final Container f_container;

    /**
     * Globally known ClassTemplate name (e.g. Boolean or annotations.LazyVar)
     */
    public final String f_sName;

    /**
     * The underlying ClassStructure.
     */
    protected final ClassStructure f_struct;

    /**
     * The ClassStructure of the super class.
     */
    protected final ClassStructure f_structSuper;

    /**
     * The ClassTemplate of the super class.
     */
    protected ClassTemplate m_templateSuper;

    /**
     * The implicit field names of this template. Generally speaking, this should be an array of
     * Object (NestedIdentity | String), but at the moment all implicit fields are not composites.
     */
    protected final String[] f_asFieldsImplicit;

    /**
     * Cached canonical type composition.
     */
    protected ClassComposition m_clazzCanonical;
    }