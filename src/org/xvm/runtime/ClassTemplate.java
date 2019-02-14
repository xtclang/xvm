package org.xvm.runtime;


import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlacePropertyBinary;
import org.xvm.runtime.Utils.InPlacePropertyUnary;
import org.xvm.runtime.Utils.UnaryAction;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction.FullyBoundHandle;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xRef;
import org.xvm.runtime.template.xRef.RefHandle;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xVar;

import org.xvm.runtime.template.collections.xTuple;


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
        f_struct = structClass;
        f_sName = structClass.getIdentityConstant().getPathString();

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
     * Initialize properties, methods and functions declared at the "top" layer.
     */
    public void initDeclared()
        {
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
    public ClassConstant getClassConstant()
        {
        return (ClassConstant) f_struct.getIdentityConstant();
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
    protected ClassConstant getInceptionClassConstant()
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
     * Obtain the canonical TypeComposition for this template.
     */
    public TypeComposition getCanonicalClass()
        {
        TypeComposition clz = m_clazzCanonical;
        if (clz == null)
            {
            m_clazzCanonical = clz = ensureCanonicalClass();
            }
        return clz;
        }

    /**
     * Ensure the canonical TypeComposition for this template.
     */
    protected TypeComposition ensureCanonicalClass()
        {
        return ensureClass(getCanonicalType());
        }

    /**
     * Produce a TypeComposition for this template using the actual types for formal parameters.
     *
     * @param pool        the ConstantPool to place a potentially created new type into
     * @param typeParams  the type parameters
     */
    public TypeComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... typeParams)
        {
        TypeConstant typeInception = pool.ensureParameterizedTypeConstant(
            getInceptionClassConstant().getType(), typeParams).normalizeParameters(pool);

        TypeConstant typeMask = getCanonicalType().adoptParameters(pool, typeParams);

        return ensureClass(typeInception, typeMask);
        }

    /**
     * Produce a TypeComposition using the specified actual type.
     *
     * Note: the passed type should be fully resolved and normalized
     *       (all formal parameters resolved)
     */
    public TypeComposition ensureClass(TypeConstant typeActual)
        {
        ClassConstant constInception = getInceptionClassConstant();

        if (typeActual.getDefiningConstant().equals(constInception))
            {
            return ensureClass(typeActual, typeActual);
            }

        // replace the TerminalType of the typeActual with the inception type
        Function<TypeConstant, TypeConstant> transformer =
                new Function<TypeConstant, TypeConstant>()
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
     * Produce a TypeComposition for this type using the specified actual (inception) type
     * and the revealed (mask) type.
     *
     * Note: the passed inception and mask types should be fully resolved and normalized
     *       (all formal parameters resolved)
     * Note2: the following should always hold true: typeInception.getOpSupport() == this;
     */
    protected TypeComposition ensureClass(TypeConstant typeInception, TypeConstant typeMask)
        {
        ConstantPool pool = typeInception.getConstantPool();

        assert !typeInception.isAccessSpecified();
        assert !typeMask.isAccessSpecified();
        assert typeInception.normalizeParameters(pool).equals(typeInception);
        assert typeMask.normalizeParameters(pool).equals(typeMask);

        return m_mapCompositions.computeIfAbsent(typeInception, (typeI) ->
            {
            OpSupport support = typeI.isAnnotated() ? typeI.getOpSupport(f_templates) : this;

            return new TypeComposition(support, typeI, typeMask);
            });
        }

    protected boolean isConstructImmutable()
        {
        return this instanceof xConst;
        }

    protected ClassTemplate getChildTemplate(String sName)
        {
        return f_templates.getTemplate(f_sName + '.' + sName);
        }

    public PropertyStructure getProperty(String sPropName)
        {
        return (PropertyStructure) f_struct.getChild(sPropName);
        }

    // get a method declared at this template level
    public MethodStructure getDeclaredMethod(String sName, TypeConstant[] atParam, TypeConstant[] atReturn)
        {
        MultiMethodStructure mms = (MultiMethodStructure) f_struct.getChild(sName);
        if (mms != null)
            {
            nextMethod:
            for (MethodStructure method : mms.methods())
                {
                MethodConstant constMethod = method.getIdentityConstant();

                TypeConstant[] atParamTest = constMethod.getRawParams();
                TypeConstant[] atReturnTest = constMethod.getRawReturns();

                if (atParam != null) // temporary work around; TODO: remove
                    {
                    int cParams = atParamTest.length;
                    if (cParams != atParam.length)
                        {
                        continue;
                        }

                    for (int i = 0; i < cParams; i++)
                        {
                        if (!compareTypes(atParamTest[i], atParam[i]))
                            {
                            continue nextMethod;
                            }
                        }
                    }

                if (atReturn != null) // temporary work around; TODO: remove
                    {
                    int cReturns = atReturnTest.length;
                    if (cReturns != atReturn.length)
                        {
                        continue;
                        }

                    for (int i = 0; i < cReturns; i++)
                        {
                        if (!compareTypes(atReturnTest[i], atReturn[i]))
                            {
                            continue nextMethod;
                            }
                        }
                    }
                return method;
                }
            }

        return null;
        }

    // compare the specified types
    private static boolean compareTypes(TypeConstant tTest, TypeConstant tParam)
        {
        if (tTest.isSingleDefiningConstant() && tParam.isSingleDefiningConstant())
            {
            // compensate for "function"; TODO: how to do it cleanly?
            ClassConstant constFunction = tParam.getConstantPool().clzFunction();

            if (tTest.getDefiningConstant().equals(constFunction) &&
                tParam.getDefiningConstant().equals(constFunction))
                {
                return true;
                }
            }

        return tTest.equals(tParam);
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
     * Create an object handle for the specified constant and push it on the frame's local stack.
     *
     * @param frame     the current frame
     * @param constant  the constant
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int createConstHandle(Frame frame, Constant constant)
        {
        frame.raiseException(xException.makeHandle("Unknown constant:" + constant));
        return Op.R_EXCEPTION;
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
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
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
            ((GenericHandle) hStruct).setField(GenericHandle.OUTER, hParent);
            }

        // assume that we have class D with an auto-generated initializer (ID), a constructor (CD),
        // and a finalizer (FD) that extends B with a constructor (CB) and a finalizer (FB)
        // the call sequence should be:
        //
        //  ("new" op-code) => ID -> CD => CB -> FB -> FD -> "assign" (continuation)
        //
        // -> indicates a call via continuation
        // => indicates a call via Construct op-code
        //
        // we need to create the call chain in the revers order;
        // the very last frame should also assign the resulting new object

        Frame.Continuation contAssign = frameCaller ->
            frameCaller.assignValue(iReturn, hStruct.ensureAccess(Access.PUBLIC));

        Frame frameCD = frame.ensureInitialized(constructor,
            frame.createFrame1(constructor, hStruct, ahVar, Op.A_IGNORE));

        // we need a non-null anchor (see Frame#chainFinalizer)
        frameCD.m_hfnFinally = Utils.makeFinalizer(constructor, hStruct, ahVar); // hF1

        frameCD.setContinuation(frameCaller ->
            {
            if (!hStruct.validateFields())
                {
                return frame.raiseException(xException.unassignedFields());
                }

            if (isConstructImmutable())
                {
                hStruct.makeImmutable();
                }

            FullyBoundHandle hFD = frameCD.m_hfnFinally;

            // this:struct -> this:public
            return hFD.callChain(frameCaller, Access.PUBLIC, contAssign);
            });

        Map<TypeConstant, MethodStructure> mapInitializers = m_mapInitializers;
        if (mapInitializers == null)
            {
            mapInitializers = m_mapInitializers = new ConcurrentHashMap<>();
            }

        MethodStructure methodID = mapInitializers.computeIfAbsent(
            hStruct.getType(), f_struct::createInitializer);

        if (methodID.isAbstract())
            {
            return frame.call(frameCD);
            }

        Frame frameID = frame.createFrame1(methodID, hStruct, ahVar, Op.A_IGNORE);

        frameID.setContinuation(frameCaller -> frameCaller.call(frameCD));

        return frame.call(frame.ensureInitialized(methodID, frameID));
        }

    /**
     * Create an ObjectHandle of the "struct" access for the specified natural class.
     *
     * @param frame  the current frame
     * @param clazz  the TypeComposition for the newly created handle
     *
     * @return the newly allocated handle
     */
    protected ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert clazz.getTemplate() == this &&
             (f_struct.getFormat() == Component.Format.CLASS ||
              f_struct.getFormat() == Component.Format.CONST ||
              f_struct.getFormat() == Component.Format.ENUMVALUE);

        clazz = clazz.ensureAccess(Access.STRUCT);

        return new GenericHandle(clazz);
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("to"))
                    {
                    if (method.getReturnCount() == 1 &&
                        method.getReturn(0).getType().equals(pool().typeString()))
                        {
                        return buildStringValue(frame, hTarget, iReturn);
                        }
                    }
            }

        throw new IllegalStateException("Compilation failed for method: " + f_sName + "#"
                + method.getIdentityConstant().getSignature().getValueString());
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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
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
                            return frame.assignTuple(iReturn, new ObjectHandle[]{frame.popStack()});

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
                            return frame.assignTuple(iReturn, new ObjectHandle[]{frame.popStack()});

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
     * @return one of the {@link Op#R_CALL}, {@link Op#R_EXCEPTION}, or {@link Op#R_BLOCK} values
     */
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: " + method + " on " + this);
        }


    // ----- property operations -------------------------------------------------------------------

    /**
     * Retrieve a property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        if (hTarget.isStruct())
            {
            return getFieldValue(frame, hTarget, sPropName, iReturn);
            }

        CallChain chain = hTarget.getComposition().getPropertyGetterChain(sPropName);
        if (chain.isNative())
            {
            return invokeNativeGet(frame, sPropName, hTarget, iReturn);
            }

        if (chain.isField())
            {
            return getFieldValue(frame, hTarget, sPropName, iReturn);
            }

        MethodStructure method = chain.getTop();
        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];

        ObjectHandle hProp = ((GenericHandle) hTarget).getField(sPropName);

        return frame.invoke1(chain, 0, hProp, ahVar, iReturn);
        }

    /**
     * Retrieve a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int getFieldValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;
        ObjectHandle hValue = hThis.getField(sPropName);

        PropertyInfo info = hTarget.getPropertyInfo(sPropName);

        if (hValue == null)
            {
            String sErr;
            if (info.isInjected())
                {
                hValue = frame.f_context.f_container.getInjectable(sPropName, info.getType());
                if (hValue != null)
                    {
                    hThis.setField(sPropName, hValue);
                    return frame.assignValue(iReturn, hValue);
                    }
                sErr = "Unknown injectable property ";
                }
            else
                {
                sErr = hThis.containsField(sPropName) ?
                        "Un-initialized property \"" : "Invalid property \"";
                }

            return frame.raiseException(xException.makeHandle(sErr + sPropName + '"'));
            }

        if (info.isRefAnnotated())
            {
            RefHandle hRef = (RefHandle) hValue;
            return hRef.getVarSupport().get(frame, hRef, iReturn);
            }
         return frame.assignValue(iReturn, hValue);
        }

    /**
     * Set a property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hValue     the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int setPropertyValue(Frame frame, ObjectHandle hTarget,
                                 String sPropName, ObjectHandle hValue)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        if (hTarget.isStruct())
            {
            return setFieldValue(frame, hTarget, sPropName, hValue);
            }

        if (!hTarget.isMutable())
            {
            return frame.raiseException(xException.immutableObject());
            }

        CallChain chain = hTarget.getComposition().getPropertySetterChain(sPropName);

//        if (chain.getDepth() == 0)
//            {
//            return frame.raiseException(
//                xException.makeHandle("Read-only property: " + property.getName()));
//            }

        if (chain.isNative())
            {
            return invokeNativeSet(frame, hTarget, chain.getProperty(), hValue);
            }

        if (chain.isField())
            {
            return setFieldValue(frame, hTarget, sPropName, hValue);
            }

        MethodStructure method = chain.getTop();
        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];
        ahVar[0] = hValue;

        ObjectHandle hProp = ((GenericHandle) hTarget).getField(sPropName);

        return frame.invoke1(chain, 0, hProp, ahVar, Op.A_IGNORE);
        }

    /**
     * Set a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hValue     the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int setFieldValue(Frame frame, ObjectHandle hTarget,
                              String sPropName, ObjectHandle hValue)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.containsField(sPropName);

        PropertyInfo info = hThis.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            RefHandle hRef = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().set(frame, hRef, hValue);
            }

        hThis.setField(sPropName, hValue);
        return Op.R_NEXT;
        }

    /**
     * Invoke a native property "get" operation.
     *
     * @param frame     the current frame
     * @param sPropName the PropertyStructure representing the property
     * @param hTarget   the target handle
     * @param iReturn   the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget.getComposition().containsGenericParam(sPropName))
            {
            TypeConstant type = hTarget.getComposition().getActualParamType(sPropName);

            return frame.assignValue(iReturn, type.getTypeHandle());
            }

        throw new IllegalStateException("Unknown property: " + sPropName + " on " + this);
        }

    /**
     * Invoke a native property "set" operation.
     *
     * @param frame     the current frame
     * @param property  the PropertyStructure representing the property
     * @param hTarget   the target handle
     * @param hValue    the new property value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    protected int invokeNativeSet(Frame frame, ObjectHandle hTarget, PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Unknown property: " + property.getName() + " on " + this);
        }

    /**
     * Increment the property value and retrieve the new value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle hRef = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarPreInc(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
            UnaryAction.INC, this, hTarget, sPropName, false, iReturn).doNext(frame);
        }

    /**
     * Retrieve the property value and then increment it.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle hRef = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarPostInc(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
            UnaryAction.INC, this, hTarget, sPropName, true, iReturn).doNext(frame);
        }

    /**
     * Decrement the property value and retrieve the new value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePreDec(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle hRef = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarPreDec(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
            UnaryAction.DEC, this, hTarget, sPropName, false, iReturn).doNext(frame);
        }

    /**
     * Retrieve the property value and then decrement it.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePostDec(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle hRef = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarPostDec(frame, hRef, iReturn);
            }
        return new InPlacePropertyUnary(
            UnaryAction.DEC, this, hTarget, sPropName, true, iReturn).doNext(frame);
        }

    /**
     * Add the specified argument to the property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyAdd(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarAdd(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.ADD, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Subtract the specified argument from the property value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertySub(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarSub(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.SUB, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Multiply the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyMul(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarMul(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.MUL, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Divide the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyDiv(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarDiv(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.DIV, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Mod the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyMod(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarMod(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.MOD, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Shift-left the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyShl(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarShl(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.SHL, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Shift-right the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyShr(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarShr(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.SHR, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Unsigned shift-right the property value by the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyShrAll(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarShrAll(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.USHR, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * "And" the property value with the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyAnd(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarAnd(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.AND, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * "Or" the property value with the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyOr(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarOr(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.OR, this, hTarget, sPropName, hArg).doNext(frame);
        }

    /**
     * Exclusive-or the property value with the specified argument.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param sPropName  the property name
     * @param hArg       the argument handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int invokePropertyXor(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hArg)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarXor(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.XOR, this, hTarget, sPropName, hArg).doNext(frame);
        }


    // ----- Ref operations ------------------------------------------------------------------------

    /**
     * Create a property Ref or Var for the specified target and property.
     *
     * @param pool       the ConstantPool to place a potentially created new type into
     * @param hTarget    the target handle
     * @param constProp  the property constant
     * @param fRO        true if the
     *
     * @return the corresponding {@link RefHandle}
     */
    public RefHandle createPropertyRef(ConstantPool pool, ObjectHandle hTarget,
                                       PropertyConstant constProp, boolean fRO)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        String sPropName = constProp.getName();
        if (!hThis.containsField(sPropName))
            {
            throw new IllegalStateException("Unknown property: (" + f_sName + ")." + constProp);
            }

        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info.isRefAnnotated())
            {
            return ((RefHandle) hThis.getField(sPropName));
            }

        TypeConstant typeReferent = constProp.getType().resolveGenerics(pool, hTarget.getType());

        TypeComposition clzRef = fRO
            ? xRef.INSTANCE.ensureParameterizedClass(pool, typeReferent)
            : xVar.INSTANCE.ensureParameterizedClass(pool, typeReferent);

        return new RefHandle(clzRef, hThis, sPropName);
        }

    // ----- support for equality and comparison ---------------------------------------------------

    /**
     * Compare for equality two object handles that both belong to the specified class.
     *
     * @param frame    the current frame
     * @param hValue1  the first value
     * @param hValue2  the second value
     * @param iReturn  the register id to place a Boolean result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
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
        TypeConstant    type           = clazz.getType();
        MethodStructure functionEquals = type.ensureTypeInfo().findEqualsFunction();
        if (functionEquals != null && !functionEquals.isNative())
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{type.getTypeHandle(), hValue1, hValue2}, iReturn);
            }

        // only Const classes have an automatic implementation;
        // for everyone else it's either a natural method or a ref equality
        return frame.assignValue(iReturn, xBoolean.FALSE);
        }

    /**
     * Compare for order two object handles that both belong to the specified class.
     *
     * @param frame    the current frame
     * @param hValue1  the first value
     * @param hValue2  the second value
     * @param iReturn  the register id to place an Ordered result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        TypeConstant    type            = clazz.getType();
        MethodStructure functionCompare = type.ensureTypeInfo().findCompareFunction();
        if (functionCompare != null && !functionCompare.isNative())
            {
            return frame.call1(functionCompare, null,
                new ObjectHandle[]{type.getTypeHandle(), hValue1, hValue2}, iReturn);
            }

        // only Const and Enum classes have automatic implementations
        switch (f_struct.getFormat())
            {
            case ENUMVALUE:
                EnumHandle hV1 = (EnumHandle) hValue1;
                EnumHandle hV2 = (EnumHandle) hValue2;

                return frame.assignValue(iReturn,
                        xOrdered.makeHandle(hV1.getValue() - hV2.getValue()));

            default:
                throw new IllegalStateException(
                        "No implementation for \"compare()\" function at " + f_sName);
            }
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
        return this;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "add", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "sub", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "mul", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "div", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "mod", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftLeft", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftRight", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "shiftAllRight", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "and", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "or", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "xor", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDivMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        return getOpChain(hTarget, "divmod", 1).invoke(frame, hTarget, hArg, aiReturn);
        }

    @Override
    public int invokeDotDot(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain(hTarget, "through", 1).invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "neg", 0).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "not", 0).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "next", 0).invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain(hTarget, "prev", 0).invoke(frame, hTarget, iReturn);
        }

    /**
     * @return a call chain for the specified op; throw if non exists
     */
    protected CallChain getOpChain(ObjectHandle hTarget, String sOp, int cArgs)
        {
        CallChain chain = findOpChain(hTarget, sOp, cArgs);
        if (chain == null)
            {
            throw new IllegalStateException("Invalid op for " + this);
            }
        return chain;
        }

    /**
     * @return a call chain for the specified op or null if non exists
     */
    protected CallChain findOpChain(ObjectHandle hTarget, String sOp, int cArgs)
        {
        TypeComposition clz = hTarget.getComposition();
        TypeInfo info = clz.getType().ensureTypeInfo();

        // TODO: what if there is more than one valid method?

        for (MethodConstant constMethod: info.findOpMethods(sOp, sOp, cArgs))
            {
            CallChain chain = clz.getMethodCallChain(constMethod.getSignature());
            if (chain.getDepth() > 0)
                {
                return chain;
                }
            }
        return null;
        }


    // ----- to<String>() support ------------------------------------------------------------------

    /**
     * Build a String handle for a human readable representation of the target handle.
     *
     * @param frame    the current frame
     * @param hTarget  the target
     * @param iReturn  the register id to place a String result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
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
        return f_struct.getConstantPool();
        }

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        MethodStructure method = getMethodStructure(sName, asParamType, asRetType);
        if (method != null)
            {
            method.setNative(true);
            }
        }

    public MethodStructure getMethodStructure(String sName, String[] asParam, String[] asRet)
        {
        return f_templates.f_adapter.getMethod(this, sName, asParam, asRet);
        }

    // mark the property getter as native
    // Note: this also makes the property "calculated" (no storage)
    public void markNativeGetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);
        if (prop != null)
            {
            prop.markNativeGetter();

            MethodStructure methGetter = prop.getGetter();
            if (methGetter != null)
                {
                methGetter.setNative(true);
                }
            }
        }


    // ----- constants and fields ------------------------------------------------------------------

    public static String[] VOID   = new String[0];
    public static String[] THIS   = new String[] {"this"};
    public static String[] OBJECT = new String[] {"Object"};
    public static String[] INT    = new String[] {"Int64"};
    public static String[] STRING = new String[] {"String"};

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
    public final ClassStructure f_struct;

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
    protected TypeComposition m_clazzCanonical;

    /**
     * A cache of "instantiate-able" TypeCompositions keyed by the "inception type". Most of the
     * time the revealed type is identical to the inception type and is defined by a
     * {@link ClassConstant} referring to a concrete natural class (not an interface).
     *
     * The only exceptions are the native types (e.g. Ref, Service), for which the inception type is
     * defined by a {@link org.xvm.asm.constants.NativeRebaseConstant} class constant and the
     * revealed type refers to the corresponding natural interface.
     *
     * We assume that for a given template, there will never be two instantiate-able classes with
     * the same inception type, but different revealed type. OTOH, the TypeComposition may
     * hide (or mask) its original identity via the {@link TypeComposition#maskAs(TypeConstant)}
     * operation and later reveal it back. All those transformations are handled by the
     * TypeComposition itself and are not known or controllable by the ClassTemplate.
     */
    private Map<TypeConstant, TypeComposition> m_mapCompositions = new ConcurrentHashMap<>();

    /**
     * A cache of default constructors.
     */
    private Map<TypeConstant, MethodStructure> m_mapInitializers;
    }
