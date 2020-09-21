package org.xvm.runtime;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Function;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlacePropertyBinary;
import org.xvm.runtime.Utils.InPlacePropertyUnary;
import org.xvm.runtime.Utils.UnaryAction;

import org.xvm.runtime.template.InterfaceProxy;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;
import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Handy;


/**
 * ClassTemplate represents a run-time class.
 */
public abstract class ClassTemplate
        implements OpSupport
    {
    // construct the template
    public ClassTemplate(TemplateRegistry templates, ClassStructure structClass)
        {
        f_templates = templates;
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
        f_templates.registerNativeTemplate(template.getCanonicalType(), template);
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
                if (f_sName.equals("Object"))
                    {
                    return null;
                    }
                templateSuper = m_templateSuper = xObject.INSTANCE;
                }
            else
                {
                templateSuper = m_templateSuper =
                    f_templates.getTemplate(f_structSuper.getIdentityConstant());
                }
            }
        return templateSuper;
        }

    /**
     * Obtain the canonical ClassComposition for this template.
     */
    public ClassComposition getCanonicalClass()
        {
        ClassComposition clz = m_clazzCanonical;
        if (clz == null)
            {
            m_clazzCanonical = clz = ensureCanonicalClass();
            }
        return clz;
        }

    /**
     * Ensure the canonical ClassComposition for this template.
     */
    protected ClassComposition ensureCanonicalClass()
        {
        return ensureClass(getCanonicalType());
        }

    /**
     * Produce a ClassComposition for this template using the actual types for formal parameters.
     *
     * @param pool        the ConstantPool to place a potentially created new type into
     * @param typeParams  the type parameters
     */
    public ClassComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... typeParams)
        {
        TypeConstant typeInception = pool.ensureParameterizedTypeConstant(
            getInceptionClassConstant().getType(), typeParams).normalizeParameters();

        TypeConstant typeMask = getCanonicalType().adoptParameters(pool, typeParams);

        return ensureClass(typeInception, typeMask);
        }

    /**
     * Produce a ClassComposition using the specified actual type.
     *
     * Note: the passed type should be fully resolved and normalized
     *       (all formal parameters resolved)
     */
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        IdentityConstant constInception = getInceptionClassConstant();

        if (typeActual.getDefiningConstant().equals(constInception))
            {
            TypeConstant typeInception = typeActual.isAccessSpecified()
                    ? typeActual.getUnderlyingType()
                    : typeActual;
            return ensureClass(typeInception, typeActual);
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

        return ensureClass(transformer.apply(typeActual), typeActual);
        }

    /**
     * Produce a ClassComposition for this type using the specified actual (inception) type
     * and the revealed (mask) type.
     *
     * Note: the passed inception and mask types should be fully resolved and normalized
     *       (all formal parameters resolved)
     * Note2: the following should always hold true: typeInception.getOpSupport() == this;
     */
    protected ClassComposition ensureClass(TypeConstant typeInception, TypeConstant typeMask)
        {
        ClassComposition clz = typeInception.getConstantPool().
                ensureClassComposition(typeInception, this);

        assert typeMask.normalizeParameters().equals(typeMask);

        return typeMask.equals(typeInception) ? clz : clz.maskAs(typeMask);
        }

    /**
     * Find the specified property in this template or direct inheritance chain.
     *
     * @return the specified property of null
     */
    protected PropertyStructure findProperty(String sPropName)
        {
        // we cannot use the TypeInfo here, since the TypeInfo will be build based on the information
        // provided by this method's caller; however, we can assume a simple class hierarchy
        ClassStructure struct = getStructure();
        do
            {
            PropertyStructure prop = (PropertyStructure) struct.getChild(sPropName);
            if (prop != null)
                {
                return prop;
                }
            struct = struct.getSuper();
            }
        while (struct != null);

        return null;
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
     * Specifies whether or not this template uses a GenericHandle for its objects.
     */
    public boolean isGenericHandle()
        {
        return true;
        }

    /**
     * Specifies whether or not this template represents a non-constant object that is allowed to
     * be passed across service boundaries.
     */
    public boolean isService()
        {
        return false;
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
        return frame.raiseException("Unknown constant:" + constant);
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
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz);

        if (hParent != null)
            {
            // strictly speaking a static child doesn't need to hold the parent's ref,
            // but that decision (not to hold) could be deferred or even statistically implemented,
            // since there could be benefits (e.g. during debugging) for knowing the parent
            ((GenericHandle) hStruct).setField(GenericHandle.OUTER, hParent);
            }

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
        }

    /**
     * Create an ObjectHandle of the "struct" access for the specified natural class.
     *
     * @param frame  the current frame
     * @param clazz  the ClassComposition for the newly created handle
     *
     * @return the newly allocated handle
     */
    public ObjectHandle createStruct(Frame frame, ClassComposition clazz)
        {
        assert clazz.getTemplate() == this;

        return new GenericHandle(clazz.ensureAccess(Access.STRUCT));
        }

    /**
     * Continuation of the {@link #construct} sequence after the struct has been created.
     *
     * @param frame        the current frame
     * @param constructor  (optional) the constructor to call; must be null the struct
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
     * @param frame    the current frame
     * @param hTarget  the object handle
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    protected int makeImmutable(Frame frame, ObjectHandle hTarget)
        {
        if (hTarget.isMutable())
            {
            hTarget.makeImmutable();

            if (hTarget instanceof GenericHandle)
                {
                TypeComposition           clz       = hTarget.getComposition();
                Map<Object, ObjectHandle> mapFields = ((GenericHandle) hTarget).getFields();
                for (Map.Entry<Object, ObjectHandle> entry : mapFields.entrySet())
                    {
                    Object       nid    = entry.getKey();
                    ObjectHandle hValue = entry.getValue();
                    if (hValue != null && hValue.isMutable() && !clz.isLazy(nid))
                        {
                        switch (hValue.getTemplate().makeImmutable(frame, hValue))
                            {
                            case Op.R_NEXT:
                                continue;

                            case Op.R_EXCEPTION:
                                return Op.R_EXCEPTION;

                            default:
                                throw new IllegalStateException();
                            }
                        }
                    }
                }
            }
        return Op.R_NEXT;
        }

    /**
     * Create a proxy handle that could be sent over the service boundaries.
     *
     * @param ctx        the service context that the mutable object "belongs" to
     * @param hTarget    the mutable object handle that needs to be proxied
     * @param typeProxy  the [revealed] type of the proxy handle
     *
     * @return a new ObjectHandle to replace the mutable object with or null
     */
    public ObjectHandle createProxyHandle(ServiceContext ctx, ObjectHandle hTarget,
                                          TypeConstant typeProxy)
        {
        if (typeProxy != null && typeProxy.isInterfaceType())
            {
            assert hTarget.getType().isA(typeProxy);

            TypeInfo info = typeProxy.ensureTypeInfo();

            // ensure the methods only use constants, services or proxy-able interfaces
            for (Map.Entry<MethodConstant, MethodInfo> entry : info.getMethods().entrySet())
                {
                MethodConstant idMethod   = entry.getKey();
                MethodInfo     infoMethod = entry.getValue();
                if (idMethod.getNestedDepth() == 2 && infoMethod.isVirtual())
                    {
                    MethodBody      body   = infoMethod.getHead();
                    MethodStructure method = body.getMethodStructure();
                    for (int i = 0, c = method.getParamCount(); i < c; i++)
                        {
                        TypeConstant typeParam = method.getParam(i).getType();
                        if (!typeParam.isProxyable())
                            {
                            return null;
                            }
                        }
                    }
                }

            for (Map.Entry<PropertyConstant, PropertyInfo> entry : info.getProperties().entrySet())
                {
                PropertyConstant idProp   = entry.getKey();
                PropertyInfo     infoProp = entry.getValue();
                if (idProp.getNestedDepth() == 1 && infoProp.isVirtual())
                    {
                    if (!infoProp.getType().isProxyable())
                        {
                        return null;
                        }
                    }
                }

            ClassComposition clzTarget = (ClassComposition) hTarget.getComposition();
            ProxyComposition clzProxy  = clzTarget.ensureProxyComposition(typeProxy);

            return InterfaceProxy.makeHandle(clzProxy, ctx, hTarget);
            }
        return null;
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
                        switch (makeImmutable(frame, hTarget))
                            {
                            case Op.R_NEXT:
                                return frame.assignValue(iReturn, hTarget);

                            case Op.R_EXCEPTION:
                                return Op.R_EXCEPTION;

                            default:
                                throw new IllegalStateException();
                            }
                    }
                break;

            case 3:
                if (method.getName().equals("equals"))
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
            switch (method.getReturnCount())
                {
                case 0:
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            return frame.assignValue(iReturn, xTuple.H_VOID);

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                case 1:
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return frame.assignTuple(iReturn, frame.popStack());

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
        if (idProp == null)
            {
            throw new IllegalStateException(f_sName);
            }

        TypeComposition clzTarget = hTarget.getComposition();
        CallChain       chain     = clzTarget.getPropertyGetterChain(idProp);

        if (chain == null)
            {
            return frame.raiseException("Unknown property: " + idProp.getValueString());
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

        if (hTarget.isInflated(idProp))
            {
            hTarget = ((GenericHandle) hTarget).getField(idProp);
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
        if (idProp == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis  = (GenericHandle) hTarget;
        ObjectHandle  hValue = hThis.getField(idProp);

        if (hValue == null)
            {
            if (hThis.isInjected(idProp))
                {
                // there is a possibility of multiple service threads getting here at the same time
                // (e.g. getting an injected property on a module).
                synchronized (hThis)
                    {
                    hValue = hThis.getField(idProp);
                    if (hValue == null)
                        {
                        return getInjectedProperty(frame, hThis, idProp, iReturn);
                        }
                    }
                }
            else
                {
                String sErr = hThis.containsField(idProp) ?
                            "Un-initialized property \"" : "Invalid property \"";
                return frame.raiseException(xException.illegalState(frame, sErr + idProp + '"'));
                }
            }

        if (Op.isDeferred(hValue))
            {
            // this can only be a deferred injected property construction call
            // initialized by another service (thread); all we can do is wait...
            // Note, that we assume that the native deferred injection never throws
            return waitForInjectedProperty(frame, hThis, idProp, iReturn);
            }

        if (hTarget.isInflated(idProp))
            {
            RefHandle hRef = (RefHandle) hValue;
            if (!(hRef instanceof FutureHandle))
                {
                return ((xRef) hRef.getTemplate()).getReferent(frame, hRef, iReturn);
                }
            // Frame deals with FutureHandle itself
            }

        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Get the injected property.
     */
    private int getInjectedProperty(Frame frame, GenericHandle hThis, PropertyConstant idProp, int iReturn)
        {
        TypeInfo     info = hThis.getType().ensureTypeInfo();
        PropertyInfo prop = info.findProperty(idProp);

        ObjectHandle hValue = frame.f_context.f_container.getInjectable(
                frame, prop.getInjectedResourceName(), prop.getType());

        if (hValue == null)
            {
            return frame.raiseException(
                xException.illegalState(frame, "Unknown injectable property \"" + idProp + '"'));
            }

        // store off the value (even if deferred), so the concurrent operation wouldn't "double dip"
        hThis.setField(idProp, hValue);

        // native injection can return a deferred handle
        if (Op.isDeferred(hValue))
            {
            return hValue.proceed(frame, frameCaller ->
                {
                ObjectHandle hVal = frameCaller.popStack();
                hThis.setField(idProp, hVal);
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
                    ObjectHandle hValue = hThis.getField(idProp);
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
        if (idProp == null)
            {
            throw new IllegalStateException(f_sName);
            }

        if (hTarget.isStruct())
            {
            return setFieldValue(frame, hTarget, idProp, hValue);
            }

        TypeComposition clzTarget = hTarget.getComposition();
        CallChain       chain     = clzTarget.getPropertySetterChain(idProp);

        if (chain == null)
            {
            return frame.raiseException("Unknown property: " + idProp.getValueString());
            }

        if (chain.isNative())
            {
            return invokeNativeSet(frame, hTarget, idProp.getName(), hValue);
            }

        if (!hTarget.isMutable())
            {
            return frame.raiseException("Attempt to modify property \"" + idProp.getName()
                + "\" on an immutable \"" + hTarget.getType().getValueString() + '"');
            }

        if (chain.isField())
            {
            return clzTarget.setFieldValue(frame, hTarget, idProp, hValue);
            }

        MethodStructure method = chain.getTop();
        ObjectHandle[]  ahVar  = new ObjectHandle[method.getMaxVars()];
        ahVar[0] = hValue;

        if (hTarget.isInflated(idProp))
            {
            hTarget = ((GenericHandle) hTarget).getField(idProp);
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
        if (idProp == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        if (!hThis.containsField(idProp))
            {
            // this should've been caught by the compiler/verifier
            return frame.raiseException("Property is missing: " + idProp.getValueString());
            }

        if (hThis.isInflated(idProp))
            {
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
            ((xRef) hRef.getTemplate()).setReferent(frame, hRef, hValue);
            }
        else
            {
            hThis.setField(idProp, hValue);
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
        if (hTarget.getType().containsGenericParam(sPropName))
            {
            TypeConstant type = hTarget.getType().resolveGenericType(sPropName);

            return frame.assignValue(iReturn, type.getTypeHandle());
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
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
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
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
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
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
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
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
            RefHandle     hRef  = (RefHandle) hThis.getField(idProp);
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
        GenericHandle hThis = (GenericHandle) hTarget;

        if (!hThis.containsField(idProp) &&
                hThis.getComposition().getPropertyGetterChain(idProp) == null)
            {
            return frame.raiseException("Unknown property: \"" + idProp + "\" on " + f_sName);
            }

        if (hTarget.isInflated(idProp))
            {
            RefHandle hRef = (RefHandle) hThis.getField(idProp);
            return frame.assignValue(iReturn, hRef);
            }

        ConstantPool pool         = frame.poolContext();
        TypeConstant typeReferent = idProp.getType().resolveGenerics(pool, hTarget.getType());

        ClassComposition clzRef = fRO
            ? xRef.INSTANCE.ensureParameterizedClass(pool, typeReferent)
            : xVar.INSTANCE.ensureParameterizedClass(pool, typeReferent);

        RefHandle hRef = new RefHandle(clzRef, hThis, idProp);
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
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        // if there is an "equals" function that is not native (on the Object itself),
        // we need to call it
        TypeConstant    type       = clazz.getType();
        MethodStructure functionEq = type.findCallable(frame.poolContext().sigEquals());
        if (functionEq != null && !functionEq.isNative())
            {
            ObjectHandle[] ahVars = new ObjectHandle[functionEq.getMaxVars()];
            ahVars[0] = type.getTypeHandle();
            ahVars[1] = hValue1;
            ahVars[2] = hValue2;
            return frame.call1(functionEq, null, ahVars, iReturn);
            }

        return callEqualsImpl(frame, clazz, hValue1, hValue2, iReturn);
        }

    /**
     * Default implementation for "equals"; overridden only by xConst.
     */
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
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
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
            }

        // if there is an "compare" function, we need to call it
        TypeConstant    type        = clazz.getType();
        MethodStructure functionCmp = type.findCallable(frame.poolContext().sigCompare());
        if (functionCmp != null && !functionCmp.isNative())
            {
            ObjectHandle[] ahVars = new ObjectHandle[functionCmp.getMaxVars()];
            ahVars[0] = type.getTypeHandle();
            ahVars[1] = hValue1;
            ahVars[2] = hValue2;
            return frame.call1(functionCmp, null, ahVars, iReturn);
            }

        return callCompareImpl(frame, clazz, hValue1, hValue2, iReturn);
        }

    /**
     * Default implementation for "compare"; overridden only by xConst.
     */
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.raiseException("No implementation for \"compare()\" function at " + f_sName);
        }

    /**
     * Compare for identity equality two object handles that both associated with this template.
     *
     * More specifically, the ObjectHandles must either be the same runtime object, or the objects
     * that they represent are both immutable and structurally identical (see Ref.equals).
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
        return hValue1 == hValue2 ||
               isGenericHandle() && GenericHandle.compareIdentity(
                        (GenericHandle) hValue1, (GenericHandle) hValue2);
        }


    // ---- OpSupport implementation ---------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return type instanceof AnnotatedTypeConstant
                ? f_templates.getTemplate(((AnnotatedTypeConstant) type).getAnnotationClass())
                : this;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "add", "+", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "sub", "-", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "mul", "*", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "div", "/", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "mod", "%", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftLeft", "<<", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftRight", ">>", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftAllRight", ">>>", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "and", "&", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "or", "|", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "xor", "^", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        return getOpChain(hTarget, "divrem", "/%", hArg).invoke(frame, hTarget, hArg, aiReturn);
        }

    @Override
    public int invokeDotDot(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "to", "..", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDotDotEx(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "toExcluding", "..<", hArg).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "neg", null, null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "not", "~", null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "nextValue", null, null).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "prevValue", null, null).invoke(frame, hTarget, iReturn);
        }

    /**
     * @return a call chain for the specified op; throw if none exists
     */
    protected CallChain getOpChain(ObjectHandle hTarget, String sName, String sOp, ObjectHandle hArg)
        {
        CallChain chain = findOpChain(hTarget, sName, sOp, hArg);
        if (chain == null)
            {
            throw new IllegalStateException("Invalid op for " + this);
            }
        return chain;
        }

    /**
     * @return a call chain for the specified op or null if none exists
     */
    public CallChain findOpChain(ObjectHandle hTarget, String sName, String sOp)
        {
        TypeComposition clz  = hTarget.getComposition();
        TypeInfo        info = clz.getType().ensureTypeInfo();

        Set<MethodConstant> setMethods = info.findOpMethods(sName, sOp, 0);
        switch (setMethods.size())
            {
            case 0:
                return null;

            case 1:
                {
                MethodConstant idMethod = setMethods.iterator().next();
                return clz.getMethodCallChain(idMethod.getSignature());
                }

            default:
                // soft assert
                System.err.println("Ambiguous operation op=" + sOp + ", name=" + sName + " on " +
                        hTarget.getType().getValueString());
                return null;
            }
        }
    /**
     * @return a call chain for the specified op and argument or null if none exists
     */
    public CallChain findOpChain(ObjectHandle hTarget, String sName, String sOp, ObjectHandle hArg)
        {
        TypeComposition clz  = hTarget.getComposition();
        TypeInfo        info = clz.getType().ensureTypeInfo();

        Set<MethodConstant> setMethods = info.findOpMethods(sName, sOp, hArg == null ? 0 : 1);
        switch (setMethods.size())
            {
            case 0:
                return null;

            case 1:
                {
                MethodConstant idMethod = setMethods.iterator().next();
                return clz.getMethodCallChain(idMethod.getSignature());
                }

            default:
                {
                if (hArg != null)
                    {
                    TypeConstant typeArg = hArg.getType();
                    for (MethodConstant idMethod : setMethods)
                        {
                        SignatureConstant sig       = idMethod.getSignature();
                        TypeConstant      typeParam = sig.getRawParams()[0];

                        if (typeArg.isA(typeParam))
                            {
                            return clz.getMethodCallChain(sig);
                            }
                        }
                    }

                // soft assert
                System.err.println("Ambiguous \"" + sOp + "\" operation on " +
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
        TypeComposition clz   = hTarget.getComposition();
        TypeInfo        info  = clz.getType().ensureTypeInfo();
        int             cArgs = ahArg.length;

        Set<MethodConstant> setMethods = info.findOpMethods(sOp, sOp, cArgs);
        switch (setMethods.size())
            {
            case 0:
                return null;

            case 1:
                {
                MethodConstant idMethod = setMethods.iterator().next();
                return clz.getMethodCallChain(idMethod.getSignature());
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
                    return clz.getMethodCallChain(sig);
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
        CallChain chain = hStruct.getComposition().getMethodCallChain(pool().sigValidator());
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
     * Build a String handle for a human readable representation of the target handle.
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
     * @return the constant pool associated with the corresponding structure
     */
    public ConstantPool pool()
        {
        return ConstantPool.getCurrentPool();
        }

    // =========== TEMPORARY ========

    /**
     * Mark the specified method as native.
     */
    protected void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        TypeConstant[] atypeArg = getTypeConstants(this, asParamType);
        TypeConstant[] atypeRet = getTypeConstants(this, asRetType);

        MethodStructure method = getStructure().findMethod(sName, atypeArg, atypeRet);
        if (method == null)
            {
            System.err.println("Missing method " + f_sName + "." + sName +
                    Arrays.toString(asParamType) + "->" + Arrays.toString(asRetType));
            }
        else
            {
            method.markNative();
            }
        }

    /**
     * Get a class type for the specified name in the context of the specified template.
     */
    protected TypeConstant[] getTypeConstants(ClassTemplate template, String[] asType)
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

    protected TypeConstant getClassType(String sName, ClassTemplate template)
        {
        ConstantPool pool = template.pool();

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

            // TODO: auto-narrowing (ThisTypeConstant)
            if (sSimpleName.endsWith("!"))
                {
                sSimpleName = sSimpleName.substring(0, sSimpleName.length() - 1);
                }

            IdentityConstant idClass = f_templates.getIdentityConstant(sSimpleName);
            if (idClass != null)
                {
                String[] asType = Handy.parseDelimitedString(sParam, ',');
                TypeConstant[] acType = getTypeConstants(template, asType);

                constType = pool.ensureClassTypeConstant(idClass, null, acType);
                }
            }
        else
            {
            if (sName.equals("this"))
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

            Component component = f_templates.getComponent(sName);
            if (component != null)
                {
                IdentityConstant constId = component.getIdentityConstant();
                switch (constId.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                        constType = constId.getType();
                        break;

                    case Typedef:
                        constType = ((TypedefStructure) component).getType();
                        break;
                    }
                }
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("ClassTypeConstant is not defined: " + sName);
            }

        return fNullable ? constType.ensureNullable() : constType;
        }

    /**
     * Mark the specified property as native.
     *
     * Note: this also makes the property "calculated" (no storage)
     */
    protected void markNativeProperty(String sPropName)
        {
        PropertyStructure prop = findProperty(sPropName);
        if (prop == null)
            {
            System.err.println("Missing property " + f_sName + "." + sPropName);
            }
        else
            {
            prop.markNative();

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
        private Frame frameTop;
        private int   ixStep;

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

            ixStep = fInitStruct ? 0 : 2;
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
            // the only exception of that flow is an anonymous class wrapper constructor that assigns
            // captured values to the anonymous class properties and needs to be called prior to
            // the class initializer; it also calls the default initializer internally

            while (true)
                {
                int iResult;
                switch (ixStep++)
                    {
                    case 0: // call an anonymous class "wrapper" constructor first
                        if (constructor != null && constructor.isAnonymousClassWrapperConstructor())
                            {
                            // wrapper constructor calls the initializer itself; skip next two steps
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

                    case 2: // call the constructor
                        if (constructor != null)
                            {
                            Frame frameCD = frameCaller.createFrame1(
                                    constructor, hStruct, ahVar, Op.A_IGNORE);

                            FullyBoundHandle hfn = Utils.makeFinalizer(constructor, ahVar);

                            // in case super constructors have their own finalizers we always need
                            // a non-null anchor
                            frameCD.m_hfnFinally = hfn == null ? FullyBoundHandle.NO_OP : hfn;

                            iResult  = frameCaller.callInitialized(frameCD);
                            frameTop = frameCD;
                            break;
                            }
                        ixStep++;
                        // fall through

                    case 3: // validation
                        iResult = callValidator(frameCaller, hStruct);
                        break;

                    case 4: // check unassigned
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

                    case 5: // native post-construction validation
                        iResult = postValidate(frameCaller, hStruct);
                        break;

                    case 6:
                        {
                        ObjectHandle     hPublic    = hStruct.ensureAccess(Access.PUBLIC);
                        FullyBoundHandle hfnFinally = frameTop == null
                                ? FullyBoundHandle.NO_OP
                                : frameTop.m_hfnFinally;

                        return hfnFinally == FullyBoundHandle.NO_OP
                                ? frameCaller.assignValue(iReturn, hPublic)
                                : hfnFinally.callChain(frameCaller, hPublic, frame_ ->
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
        }


    // ----- constants and fields ------------------------------------------------------------------

    public static String[] VOID    = new String[0];
    public static String[] THIS    = new String[] {"this"};
    public static String[] OBJECT  = new String[] {"Object"};
    public static String[] INT     = new String[] {"numbers.Int64"};
    public static String[] STRING  = new String[] {"text.String"};
    public static String[] BOOLEAN = new String[] {"Boolean"};

    /**
     * The TemplateRegistry.
     */
    public final TemplateRegistry f_templates;

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


    // ----- caches ------

    /**
     * Canonical type composition.
     */
    protected ClassComposition m_clazzCanonical;
    }
