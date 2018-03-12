package org.xvm.runtime;


import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

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
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.Utils.BinaryAction;
import org.xvm.runtime.Utils.InPlacePropertyBinary;
import org.xvm.runtime.Utils.InPlacePropertyUnary;
import org.xvm.runtime.Utils.UnaryAction;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction.FullyBoundHandle;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xRef;
import org.xvm.runtime.template.xRef.RefHandle;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xVar;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xArray;


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
     *
     * @return false if the initialization could not be completed due to a non-initialized dependency
     */
    public void initDeclared()
        {
        }

    /**
     * Obtain a canonical type that is represented by this {@link OpSupport} object
     *
     * Note: the following should always hold true: getCanonicalType().getOpSupport() == this;
     */
    public TypeConstant getCanonicalType()
        {
        return f_struct.getCanonicalType();
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
                    f_templates.getTemplate(f_structSuper.getCanonicalType());
                }
            }
        return templateSuper;
        }

    /**
     * @return a category template
     */
    public ClassTemplate getTemplateCategory()
        {
        ClassTemplate templateCategory = f_templateCategory;
        if (templateCategory == null)
            {
            templateCategory = xObject.INSTANCE;

            if (f_structSuper == null || f_struct.getFormat() != f_structSuper.getFormat())
                {
                switch (f_struct.getFormat())
                    {
                    case SERVICE:
                        templateCategory = xService.INSTANCE;
                        break;

                    case CONST:
                        templateCategory = xConst.INSTANCE;
                        break;

                    case ENUM:
                        templateCategory = xEnum.INSTANCE;
                        break;
                    }
                }
            f_templateCategory = templateCategory;
            }
        return templateCategory;
        }

    /**
     * Obtain the canonical class for this template.
     */
    public TypeComposition ensureCanonicalClass()
        {
        TypeComposition clz = m_clazzCanonical;
        if (clz == null)
            {
            clz = ensureClass(getCanonicalType());
            }

        return clz;
        }

    /**
     * Produce a TypeComposition for this template using the actual types for formal parameters.
     *
     * Note: all passed actual types should be fully resolved (no formal parameters)
     */
    public TypeComposition ensureParameterizedClass(TypeConstant... typeParams)
        {
        ClassStructure struct = f_struct;
        TypeConstant type = struct.getConstantPool().ensureParameterizedTypeConstant(
            struct.getIdentityConstant().asTypeConstant(), typeParams);
        return ensureClass(type);
        }

    /**
     * Produce a TypeComposition using the specified actual (inception) type.
     */
    public TypeComposition ensureClass(TypeConstant typeActual)
        {
        return ensureClass(typeActual, typeActual);
        }

    /**
     * Produce a TypeComposition for this type using the specified actual (inception) type
     * and the revealed (mask) type.
     *
     * Note: the passed actual type should be fully resolved (no formal parameters)
     * Note2: the following should always hold true: typeActual.getOpSupport() == this;
     */
    public TypeComposition ensureClass(TypeConstant typeActual, TypeConstant typeMask)
        {
        assert typeActual.getAccess() == Access.PUBLIC;

        int cActual = typeActual.getParamTypes().size();
        int cFormal = f_struct.isParameterized() ? f_struct.getTypeParams().size() : 0;

        TypeConstant typeInception = cActual == cFormal
            ? typeActual
            : typeActual.normalizeParameters();

        assert typeActual.getParamTypes().size() == cFormal || typeActual.isTuple();

        OpSupport support = typeInception.isAnnotated() ?
            typeInception.getOpSupport(f_templates) : this;

        return m_mapCompositions.computeIfAbsent(typeMask, (type) ->
            new TypeComposition(support, typeInception, type));
        }

    // should we generate fields for this class
    public boolean isStateful()
        {
        switch (f_struct.getFormat())
            {
            case CLASS:
            case CONST:
            case MIXIN:
            case SERVICE:
                return true;

            default:
                return false;
            }
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

    public PropertyStructure ensureProperty(String sPropName)
        {
        PropertyStructure property = getProperty(sPropName);
        if (property != null)
            {
            return property;
            }

        PropertyStructure propSuper = null;
        for (TypeComposition clzSuper : ensureCanonicalClass().getCallChain())
            {
            propSuper = clzSuper.getTemplate().getProperty(sPropName);
            if (propSuper != null)
                {
                break;
                }
            }

        if (propSuper == null)
            {
            throw new IllegalArgumentException("Property is not defined " + f_sName + "#" + sPropName);
            }

        return f_struct.createProperty(false,
            propSuper.getAccess(), propSuper.getVarAccess(), propSuper.getType(), sPropName);
        }

    public TypeConstant getTypeConstant()
        {
        return f_struct.getIdentityConstant().asTypeConstant();
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
            // compensate for "function"
            ClassConstant constFunction = tParam.getConstantPool().clzFunction();

            if (tTest.getDefiningConstant().equals(constFunction) &&
                tParam.getDefiningConstant().equals(constFunction))
                {
                return true;
                }
            }

        return tTest.equals(tParam);
        }

    // find one of the "equals" or "compare" functions definition
    // @param atRet is one of xBoolean.TYPES or xOrdered.TYPES
    public MethodStructure findCompareFunction(String sName, TypeConstant[] atRet)
        {
        ClassTemplate template = this;
        TypeConstant typeThis = getTypeConstant();
        TypeConstant[] atArg = new TypeConstant[] {typeThis, typeThis};
        do
            {
            MethodStructure method = getDeclaredMethod(sName, atArg, atRet);
            if  (method != null && method.isStatic())
                {
                return method;
                }
            template = template.m_templateSuper;
            }
        while (template != null);
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
        // type compositions are singletons
        return this == obj;
        }

    @Override
    public String toString()
        {
        return f_struct.toString();
        }

    // ----- constructions  ------------------------------------------------------------------------

    /**
     * Create an object handle for the specified constant.
     *
     * @param frame     the current frame
     * @param constant  the constant
     *
     * @return the corresponding {@link ObjectHandle} or null if the operation failed
     */
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        return null;
        }

    /**
     * Construct an {@link ObjectHandle} of the specified class with the specified constructor.
     *
     * The following steps are to be performed:
     * <ul>
     *   <li>Invoke the default constructor for the "inception" type;
     *   <li>Invoke the specified constructor, potentially calling some super constructors
     *       passing "this:struct" as a target
     *   <li>Invoke all finalizers in the inheritance chain starting at the base passing
     *       "this:private" as a target
     * </ul>
     *
     * @param frame        the current frame
     * @param constructor  the MethodStructure for the constructor
     * @param clazz        the target class
     * @param ahVar        the construction parameters
     * @param iReturn      the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz).ensureAccess(Access.STRUCT);

        // assume that we have C1 with a default constructor (DC), a regular constructor (RC1),
        // and a finalizer (F1) that extends C0 with a constructor (RC0) and a finalizer (F0)
        // the call sequence should be:
        //
        //  ("new" op-code) => DC -> RC1 => RC0 -> F0 -> F1 -> "assign" (continuation)
        //
        // -> indicates a call via continuation
        // => indicates a call via Construct op-code
        //
        // we need to create the call chain in the revers order;
        // the very last frame should also assign the resulting new object

        Frame.Continuation contAssign =
            frameCaller -> frameCaller.assignValue(iReturn, hStruct.ensureAccess(Access.PUBLIC));

        Frame frameRC1 = frame.ensureInitialized(constructor,
            frame.createFrame1(constructor, hStruct, ahVar, Frame.RET_UNUSED));

        // we need a non-null anchor (see Frame#chainFinalizer)
        frameRC1.m_hfnFinally = Utils.makeFinalizer(constructor, hStruct, ahVar); // hF1

        frameRC1.setContinuation(frameCaller ->
            {
            if (isConstructImmutable())
                {
                hStruct.makeImmutable();
                }

            FullyBoundHandle hF = frameRC1.m_hfnFinally;

            // this:struct -> this:public
            return hF.callChain(frameCaller, Access.PUBLIC, contAssign);
            });

        Map<TypeConstant, MethodStructure> mapConstructors = m_mapConstructors;
        if (mapConstructors == null)
            {
            mapConstructors = new ConcurrentHashMap<>();
            }

        MethodStructure methodDC = mapConstructors.computeIfAbsent(
            hStruct.getType(), f_struct::getDefaultConstructor);

        if (methodDC.isAbstract())
            {
            return frame.call(frameRC1);
            }

        Frame frameDC = frame.createFrame1(methodDC, hStruct, ahVar, Frame.RET_UNUSED);

        frameDC.setContinuation(frameCaller -> frameCaller.call(frameRC1));

        return frame.call(frame.ensureInitialized(methodDC, frameDC));
        }

    /**
     * Create an ObjectHandle for the specified clazz.
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
              f_struct.getFormat() == Component.Format.CONST);

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
                        method.getReturn(0).getType() == f_struct.getConstantPool().typeString())
                        {
                        return buildStringValue(frame, hTarget, iReturn);
                        }
                    }
            }

        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
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
                    switch (invokeNative1(frame, method, hTarget, ahArg[0], Frame.RET_UNUSED))
                        {
                        case Op.R_NEXT:
                            return frame.assignValue(iReturn, xTuple.H_VOID);

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                case 1:
                    switch (invokeNative1(frame, method, hTarget, ahArg[0], Frame.RET_LOCAL))
                        {
                        case Op.R_NEXT:
                            return frame.assignTuple(iReturn, new ObjectHandle[]{frame.getFrameLocal()});

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
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Frame.RET_UNUSED))
                        {
                        case Op.R_NEXT:
                            return frame.assignValue(iReturn, xTuple.H_VOID);

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                case 1:
                    switch (invokeNativeN(frame, method, hTarget, ahArg, Frame.RET_LOCAL))
                        {
                        case Op.R_NEXT:
                            return frame.assignTuple(iReturn, new ObjectHandle[]{frame.getFrameLocal()});

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

        CallChain chain = hTarget.getComposition().getPropertyGetterChain(sPropName);
        if (chain.isNative())
            {
            return invokeNativeGet(frame, chain.getProperty(), hTarget, iReturn);
            }

        MethodStructure method = hTarget.isStruct() ? null : chain.getTop();
        if (method == null)
            {
            return getFieldValue(frame, hTarget, chain.getProperty(), iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];

        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    /**
     * Retrieve a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param property   the PropertyStructure representing the property
     * @param iReturn    the register id to place a result of the operation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int getFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        String sName = property.getName();

        if (hTarget.getComposition().isGenericType(sName))
            {
            TypeConstant type = hTarget.getComposition().getActualParamType(sName);

            return frame.assignValue(iReturn, type.getTypeHandle());
            }

        GenericHandle hThis = (GenericHandle) hTarget;
        ObjectHandle hValue = hThis.getField(sName);

        if (hValue == null)
            {
            String sErr;
            if (isInjectable(property))
                {
                hValue = frame.f_context.f_container.getInjectable(sName, property.getType());
                if (hValue != null)
                    {
                    hThis.setField(sName, hValue);
                    return frame.assignValue(iReturn, hValue);
                    }
                sErr = "Unknown injectable property ";
                }
            else
                {
                sErr = hThis.containsField(sName) ?
                        "Un-initialized property \"" : "Invalid property \"";
                }

            return frame.raiseException(xException.makeHandle(sErr + sName + '"'));
            }

        PropertyInfo info = property.getInfo();
        if (info != null && info.isRef())
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

        CallChain chain = hTarget.getComposition().getPropertySetterChain(sPropName);
        PropertyStructure property = chain.getProperty();

        if (!hTarget.isMutable())
            {
            return frame.raiseException(
                xException.makeHandle("Immutable object: " + hTarget));
            }

        if (isCalculated(property))
            {
            return frame.raiseException(
                xException.makeHandle("Read-only property: " + property.getName()));
            }

        if (isNativeSetter(property))
            {
            return invokeNativeSet(frame, hTarget, property, hValue);
            }

        MethodStructure method = hTarget.isStruct() ? null : Adapter.getSetter(property);
        if (method == null)
            {
            return setFieldValue(frame, hTarget, property, hValue);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];
        ahVar[0] = hValue;

        return frame.invoke1(chain, 0, hTarget, ahVar, Frame.RET_UNUSED);
        }

    /**
     * Set a field value.
     *
     * @param frame      the current frame
     * @param hTarget    the target handle
     * @param property   the PropertyStructure representing the property
     * @param hValue     the new value
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int setFieldValue(Frame frame, ObjectHandle hTarget,
                              PropertyStructure property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.containsField(property.getName());

        PropertyInfo info = property.getInfo();
        if (info != null && info.isRef())
            {
            RefHandle hRef = (RefHandle) hThis.getField(property.getName());
            return hRef.getVarSupport().set(frame, hRef, hValue);
            }

        hThis.setField(property.getName(), hValue);
        return Op.R_NEXT;
        }

    /**
     * Invoke a native property "get" operation.
     *
     * @param frame     the current frame
     * @param property  the PropertyStructure representing the property
     * @param hTarget   the target handle
     * @param iReturn   the register id to place the result of invocation into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    protected int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Unknown property: " + property.getName() + " on " + this);
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
        if (info != null && info.isRef())
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
        if (info != null && info.isRef())
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
        if (info != null && info.isRef())
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
        if (info != null && info.isRef())
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
        ClassTemplate.PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
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
        ClassTemplate.PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
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
        ClassTemplate.PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
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
        ClassTemplate.PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
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
        ClassTemplate.PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
            {
            GenericHandle hThis = (GenericHandle) hTarget;
            RefHandle     hRef  = (RefHandle) hThis.getField(sPropName);
            return hRef.getVarSupport().invokeVarMod(frame, hRef, hArg);
            }

        return new InPlacePropertyBinary(
            BinaryAction.MOD, this, hTarget, sPropName, hArg).doNext(frame);
        }


    // ----- Ref operations ------------------------------------------------------------------------

    /**
     * Create a property Ref or Var for the specified target and property.
     *
     * @param hTarget    the target handle
     * @param constProp  the property constant
     * @param fRO        true if the
     *
     * @return the corresponding {@link RefHandle}
     */
    public RefHandle createPropertyRef(ObjectHandle hTarget, PropertyConstant constProp, boolean fRO)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        String sPropName = constProp.getName();
        if (!hThis.containsField(sPropName))
            {
            throw new IllegalStateException("Unknown property: (" + f_sName + ")." + constProp);
            }

        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        if (info != null && info.isRef())
            {
            return ((RefHandle) hThis.getField(sPropName));
            }

        TypeConstant typeReferent = constProp.getRefType().resolveGenerics(hTarget.getType());

        TypeComposition clzRef = fRO
            ? xRef.INSTANCE.ensureParameterizedClass(typeReferent)
            : xVar.INSTANCE.ensureParameterizedClass(typeReferent);

        return new RefHandle(clzRef, hThis, sPropName);
        }


    // ----- array operations ----------------------------------------------------------------------

    /**
     * Create a one dimensional array for a specified type and arity.
     *
     * @param frame      the current frame
     * @param typeEl     the array type
     * @param cCapacity  the array size
     * @param iReturn    the register id to place the array handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int createArrayStruct(Frame frame, TypeConstant typeEl,
                                 long cCapacity, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            return frame.raiseException(
                xException.makeHandle("Invalid array size: " + cCapacity));
            }

        return frame.assignValue(iReturn, xArray.makeHandle(typeEl, cCapacity));
        }


    // ----- support for equality and comparison ---------------------------------------------------

    /**
     * Compare for equality two object handles that both belong to the specified class.
     *
     * @param frame      the current frame
     * @param hValue1    the first value
     * @param hValue2    the second value
     * @param iReturn    the register id to place a Boolean result into
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

        // if there is an "equals" function, we need to call it
        MethodStructure functionEquals = findCompareFunction("equals", xBoolean.PARAMETERS);
        if (functionEquals != null)
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        // only Const classes have an automatic implementation;
        // for everyone else it's either a native method or a ref equality
        return frame.assignValue(iReturn, xBoolean.FALSE);
        }

    /**
     * Compare for order two object handles that both belong to the specified class.
     *
     * @param frame      the current frame
     * @param hValue1    the first value
     * @param hValue2    the second value
     * @param iReturn    the register id to place an Ordered result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        MethodStructure functionCompare = findCompareFunction("compare", xOrdered.TYPES);
        if (functionCompare != null)
            {
            return frame.call1(functionCompare, null,
                new ObjectHandle[]{hValue1, hValue2}, iReturn);
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


    // ---- OpSupport implementation ---------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain("+").invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain("-").invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain("*").invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain("/").invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return getOpChain("&").invoke(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain("neg").invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain("next").invoke(frame, hTarget, iReturn);
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getOpChain("prev").invoke(frame, hTarget, iReturn);
        }

    /**
     * @return a call chain for the specified op or null if non exists
     */
    protected CallChain getOpChain(String sOp)
        {
        TypeInfo info = getCanonicalType().ensureTypeInfo();
        // TODO: use the TypeInfo to get the chain
        CallChain chain = null;

        if (chain == null)
            {
            throw new IllegalStateException("Invalid op for " + this);
            }
        return chain;
        }


    // ----- to<String>() support ------------------------------------------------------------------

    /**
     * Build a String handle for a human readable representation of the target handle.
     *
     * @param frame      the current frame
     * @param hTarget    the target
     * @param iReturn    the register id to place a String result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION},
     *         or {@link Op#R_BLOCK} values
     */
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
        }


    // ----- to be replaced when the structure support is added

    protected boolean isInjectable(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fInjectable;
        }

    protected boolean isAtomicProperty(ObjectHandle hTarget, String sPropName)
        {
        PropertyInfo info = hTarget.getPropertyInfo(sPropName);
        return info != null && info.m_fAtomic;
        }

    protected boolean isCalculated(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fCalculated;
        }

    protected boolean isNativeSetter(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fNativeSetter;
        }

    // is the specified generic property declared at this level
    protected boolean isGenericType(String sProperty)
        {
        return f_struct.indexOfGenericParameter(sProperty) >= 0;
        }

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        getMethodStructure(sName, asParamType, asRetType).setNative(true);
        }

    public MethodStructure getMethodStructure(String sName, String[] asParam)
        {
        return getMethodStructure(sName, asParam, VOID);
        }

    public MethodStructure getMethodStructure(String sName, String[] asParam, String[] asRet)
        {
        MethodStructure method = f_templates.f_adapter.getMethod(this, sName, asParam, asRet);
        if (method == null)
            {
            throw new IllegalArgumentException("Method is not defined: " + f_sName + '#' + sName);
            }
        return method;
        }

    public void markInjectable(String sPropName)
        {
        ensurePropertyInfo(sPropName).m_fInjectable = true;
        }

    public void markCalculated(String sPropName)
        {
        ensurePropertyInfo(sPropName).m_fCalculated = true;
        }

    public void markAtomic(String sPropName)
        {
        ensurePropertyInfo(sPropName).markAtomic();
        }

    // mark the property getter as native
    // Note: this also makes the property "calculated" (no storage)
    public void markNativeGetter(String sPropName)
        {
        MethodStructure methodGet = Adapter.getGetter(ensureProperty(sPropName));
        if (methodGet == null)
            {
            ensurePropertyInfo(sPropName).m_fNativeGetter = true;
            }
        else
            {
            methodGet.setNative(true);
            }
        ensurePropertyInfo(sPropName).m_fCalculated = true;
        }

    // mark the property setter as native
    // Note: this also makes the property "calculated" (no storage)
    public void markNativeSetter(String sPropName)
        {
        MethodStructure methodSet = Adapter.getSetter(ensureProperty(sPropName));
        if (methodSet == null)
            {
            ensurePropertyInfo(sPropName).m_fNativeSetter = true;
            }
        else
            {
            methodSet.setNative(true);
            }
        ensurePropertyInfo(sPropName).m_fCalculated = false;
        }

    public MethodStructure ensureGetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);

        return Adapter.getGetter(prop);
        }

    public MethodStructure ensureSetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);

        return Adapter.getSetter(prop);
        }

    public PropertyInfo ensurePropertyInfo(String sPropName)
        {
        PropertyStructure property = ensureProperty(sPropName);

        PropertyInfo info = property.getInfo();
        if (info == null)
            {
            property.setInfo(info = new PropertyInfo(f_templates.f_container.f_pool, property));
            }
        return info;
        }

    public static class PropertyInfo
        {
        protected final ConstantPool      f_pool;
        protected final PropertyStructure f_property;
        protected       boolean           m_fAtomic;
        protected       boolean           m_fInjectable;
        protected       boolean           m_fCalculated;
        protected       boolean           m_fNativeGetter;
        protected       boolean           m_fNativeSetter;
        protected       TypeConstant      m_typeRef;

        public PropertyInfo(ConstantPool pool, PropertyStructure property)
            {
            f_pool = pool;
            f_property = property;
            }

        protected void markAtomic()
            {
            m_fAtomic = true;

            TypeConstant typeProp = f_property.getType();

            TypeConstant typeRef = typeProp.isEcstasy("Int")
                ? f_pool.ensureEcstasyClassConstant("annotations.AtomicIntNumber").asTypeConstant()
                : f_pool.ensureEcstasyClassConstant("annotations.AtomicVar").asTypeConstant();

            setRefType(f_pool.ensureParameterizedTypeConstant(typeRef, typeProp));
            }

        public boolean isAtomic()
            {
            return m_fAtomic;
            }

        public boolean isRef()
            {
            return m_typeRef != null;
            }

        public TypeConstant getRefType()
            {
            return m_typeRef;
            }

        // specifies that the property value is held by the ref
        protected void setRefType(TypeConstant typeRef)
            {
            m_typeRef = typeRef;
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

    /**
     * The native category (Service, Enum, etc).
     */
    protected ClassTemplate f_templateCategory;

    /**
     * Public non-parameterized TypeComposition.
     */
    protected TypeComposition m_clazzCanonical;

    // ----- caches ------

    /**
     * A cache of TypeCompositions keyed by the "revealed type".
     *
     * We assume that for a given template, there will never be two classes with the same
     * revealed types, but different inception types.
     *
     * If that assumption breaks, we'd need to either change the key, or link-list the classes.
     */
    protected Map<TypeConstant, TypeComposition> m_mapCompositions = new ConcurrentHashMap<>();

    /**
     * A cache of default constructors.
     */
    protected Map<TypeConstant, MethodStructure> m_mapConstructors;
    }
