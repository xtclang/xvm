package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.Const;
import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.Enum.EnumHandle;
import org.xvm.runtime.template.Function.FullyBoundHandle;
import org.xvm.runtime.template.Ref.RefHandle;
import org.xvm.runtime.template.Service;
import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.collections.xArray;


/**
 * ClassTemplate represents a run-time class.

 */
public abstract class ClassTemplate
    {
    public final TypeSet f_types;
    public final ClassStructure f_struct;

    public final String f_sName; // globally known ClassTemplate name (e.g. Boolean or annotations.LazyVar)

    public final TypeComposition f_clazzCanonical; // public non-parameterized class

    public final ClassTemplate f_templateSuper;
    public final ClassTemplate f_templateCategory; // a native category

    public final boolean f_fService; // is this a service

    // ----- caches ------

    // cache of TypeCompositions
    protected Map<TypeConstant, TypeComposition> m_mapCompositions = new HashMap<>();

    // construct the template
    public ClassTemplate(TypeSet types, ClassStructure structClass)
        {
        f_types = types;
        f_struct = structClass;
        f_sName = structClass.getIdentityConstant().getPathString();

        // calculate the parents (inheritance and "native")
        ClassStructure structSuper = null;
        ClassTemplate templateSuper = null;
        ClassTemplate templateCategory = null;
        boolean fService = false;

        Contribution contribExtend = structClass.findContribution(Composition.Extends);
        if (contribExtend == null)
            {
            if (!f_sName.equals("Object"))
                {
                templateSuper = xObject.INSTANCE;
                }
            }
        else
            {
            IdentityConstant idExtend = (IdentityConstant) contribExtend.
                getTypeConstant().getDefiningConstant();

            structSuper = (ClassStructure) idExtend.getComponent();
            templateSuper = f_types.getTemplate(structSuper.getIdentityConstant());
            fService = templateSuper.isService();
            }

        if (structSuper == null || structClass.getFormat() != structSuper.getFormat())
            {
            switch (structClass.getFormat())
                {
                case SERVICE:
                    templateCategory = Service.INSTANCE;
                    fService = true;
                    break;

                case CONST:
                    templateCategory = Const.INSTANCE;
                    break;

                case ENUM:
                    templateCategory = Enum.INSTANCE;
                    break;
                }
            }

        f_templateSuper = templateSuper;
        f_templateCategory = templateCategory;
        f_clazzCanonical = createCanonicalClass();
        f_fService = fService;
        }

    /**
     * Create the canonical class for this template.
     */
    protected TypeComposition createCanonicalClass()
        {
        return new TypeComposition(this, f_struct.getCanonicalType());
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
     * Produce a TypeComposition for this template using the specified actual type.
     *
     * Note: the passed actual type should be fully resolved (no formal parameters)
     */
    public TypeComposition ensureClass(TypeConstant typeActual)
        {
        assert typeActual.isSingleDefiningConstant() &&
            ((IdentityConstant) typeActual.getDefiningConstant()).getComponent() == f_struct;

        if (!typeActual.isModifyingType())
            {
            return f_clazzCanonical;
            }

        int cParams = typeActual.getParamTypes().size();
        int cFormal = f_struct.isParameterized() ? f_struct.getTypeParams().size() : 0;

        if (cParams != cFormal)
            {
            typeActual = typeActual.normalizeParameters();
            }

        return m_mapCompositions.computeIfAbsent(typeActual,
                (type) -> new TypeComposition(this, type));
        }

    public ClassTemplate getSuper()
        {
        return f_templateSuper;
        }

    public boolean isService()
        {
        return f_fService;
        }

    public boolean isConst()
        {
        switch (f_struct.getFormat())
            {
            case PACKAGE:
            case CONST:
            case ENUM:
            case ENUMVALUE:
            case MODULE:
                return true;

            default:
                return false;
            }
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

    public boolean isSingleton()
        {
        return f_struct.isStatic();
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
        for (TypeComposition clzSuper : f_clazzCanonical.getCallChain())
            {
            propSuper = clzSuper.f_template.getProperty(sPropName);
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
            propSuper.getAccess(), propSuper.getType(), sPropName);
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
        // TODO: remove all this
        while (tTest.isSingleDefiningConstant()
                && tTest.getDefiningConstant().getFormat() == Format.Typedef)
            {
            tTest = ((TypedefStructure)
                    ((IdentityConstant) tTest.getDefiningConstant()).getComponent()).getType();
            }

        // compensate for "function"
        if (tTest.getValueString().contains("Function") &&
            tParam.getValueString().contains("Function"))
            {
            return true;
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
            template = template.f_templateSuper;
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

    // ---- OpCode support: construction and initialization -----

    /**
     * Initialize properties, methods and functions declared at the "top" layer.
     *
     * TODO: remove; should be called from constructors
     */
    public void initDeclared()
        {
        }

    // create an unassigned RefHandle for the specified class
    // sName is an optional ref name
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // assign (Int i = 5;)
    // @return an immutable handle or null if this type doesn't take that constant
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        return null;
        }

    // @return true if a constant always results into the same ObjectHandle
    public boolean isConstantCacheable(Constant constant)
        {
        return true;
        }

    // return a handle with this:struct access
    protected ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert f_struct.getTypeParamsAsList().isEmpty();
        assert f_struct.getFormat() == Component.Format.CLASS ||
               f_struct.getFormat() == Component.Format.CONST;

        return new GenericHandle(clazz, clazz.ensureStructType());
        }

    // invoke the default constructors, then the specified constructor,
    // then finalizers; change this:struct handle to this:public
    // return one of the Op.R_ values
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz); // this:struct

        // assume that we have C1 extending C0 with default constructors (DC),
        // regular constructors (RC), and finalizers (F);
        // the call sequence should be:
        //
        //  ("new" op-code) => DC0 -> DC1 -> RC1 => RC0 -> F0 -> F1 -> "assign" (continuation)
        //
        // -> indicates a call via continuation
        // => indicates a call via Construct op-cod
        //
        // we need to create the call chain in the revers order;
        // the very last frame should also assign the resulting new object

        Frame.Continuation contAssign =
                frameCaller -> frameCaller.assignValue(iReturn,
                    hStruct.f_clazz.ensureAccess(hStruct, Access.PUBLIC));

        Frame frameRC1 = frame.createFrame1(constructor, hStruct, ahVar, Frame.RET_UNUSED);

        Frame frameDC0 = clazz.callDefaultConstructors(frame, hStruct, ahVar,
                frameCaller -> frameCaller.call(frameRC1));

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

        return frame.call(frameDC0 == null ? frameRC1 : frameDC0);
        }

    protected boolean isConstructImmutable()
        {
        return this instanceof Const;
        }

    // ----- OpCode support ------

    // invoke with a zero or one return value
    // return R_CALL or R_BLOCK
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    // invoke with return value of Tuple
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invokeT(chain, 0, hTarget, ahVar, iReturn);
        }

    // invoke with more than one return value
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.invokeN(chain, 0, hTarget, ahVar, aiReturn);
        }

    // invokeNative property "get" operation
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Unknown property getter: (" + f_sName + ")." + property.getName());
        }

    // invokeNative property "set" operation
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Unknown property setter: (" + f_sName + ")." + property.getName());
        }

    // invokeNative with exactly one argument and zero or one return value
    // place the result into the specified frame register
    // return R_NEXT or R_CALL
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more than one arguments and zero or one return values
    // return one of the Op.R_ values
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

    // invokeNative with zero or more arguments and a Tuple return value
    // return one of the Op.R_ values
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

    // invokeNative with zero or more arguments and more than one return values
    // return one of the Op.R_ values
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // Add operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Sub operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Mul operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Div operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Mod operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Neg operation
    // return one of the Op.R_ values
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Next operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Prev operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // ---- OpCode support: register or property operations -----


    // increment the property value and place the result into the specified frame register
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        return new Utils.IncDec(Utils.IncDec.PRE_INC, this,
            hTarget, sPropName, iReturn).doNext(frame);
        }

    // place the property value into the specified frame register and increment it
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        return new Utils.IncDec(Utils.IncDec.POST_INC,
            this, hTarget, sPropName, iReturn).doNext(frame);
        }

    // ----- OpCode support: property operations -----

    // get a property value into the specified register
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        CallChain.PropertyCallChain chain = hTarget.f_clazz.getPropertyGetterChain(sPropName);
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

    public int getFieldValue(Frame frame, ObjectHandle hTarget,
                             PropertyStructure property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        String sName = property.getName();

        if (isGenericType(sName))
            {
            TypeConstant type = hTarget.m_type.getActualParamType(sName);

            return frame.assignValue(iReturn, type.getHandle());
            }

        GenericHandle hThis = (GenericHandle) hTarget;
        ObjectHandle hValue = hThis.m_mapFields.get(sName);

        if (hValue == null)
            {
            String sErr;
            if (isInjectable(property))
                {
                TypeComposition clz = hThis.f_clazz.resolveClass(property.getType());

                hValue = frame.f_context.f_container.getInjectable(sName, clz);
                if (hValue != null)
                    {
                    hThis.m_mapFields.put(sName, hValue);
                    return frame.assignValue(iReturn, hValue);
                    }
                sErr = "Unknown injectable property ";
                }
            else
                {
                sErr = hThis.m_mapFields.containsKey(sName) ?
                        "Un-initialized property \"" : "Invalid property \"";
                }

            return frame.raiseException(xException.makeHandle(sErr + sName + '"'));
            }

        if (isAnnotated(property))
            {
            try
                {
                hValue = ((RefHandle) hValue).get();
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }

        return frame.assignValue(iReturn, hValue);
        }

    // set a property value
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        CallChain.PropertyCallChain chain = hTarget.f_clazz.getPropertySetterChain(sPropName);
        PropertyStructure property = chain.getProperty();

        ExceptionHandle hException = null;
        if (!hTarget.isMutable())
            {
            hException = xException.makeHandle("Immutable object: " + hTarget);
            }
        else if (isCalculated(property))
            {
            hException = xException.makeHandle("Read-only property: " + property.getName());
            }

        if (hException == null)
            {
            if (isNativeSetter(property))
                {
                return invokeNativeSet(frame, hTarget, property, hValue);
                }

            MethodStructure method = hTarget.isStruct() ? null : Adapter.getSetter(property);
            if (method == null)
                {
                hException = setFieldValue(hTarget, property, hValue);
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];
                ahVar[1] = hValue;

                return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
                }
            }

        return hException == null ? Op.R_NEXT : frame.raiseException(hException);
        }

    public ExceptionHandle setFieldValue(ObjectHandle hTarget,
                                         PropertyStructure property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.m_mapFields.containsKey(property.getName());

        if (isAnnotated(property))
            {
            return ((RefHandle) hThis.m_mapFields.get(property.getName())).set(hValue);
            }

        hThis.m_mapFields.put(property.getName(), hValue);
        return null;
        }

    // ----- support for equality and comparison ------

    // compare for equality two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
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

    // compare for order two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
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

    // build the String representation of the target handle
    // returns R_NEXT, R_CALL or R_EXCEPTION
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
        }

    // ----- Op-code support: array operations -----

    // get a handle to an array for the specified class
    // returns R_NEXT or R_EXCEPTION
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

    // ----- to be replaced when the structure support is added

    protected boolean isInjectable(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fInjectable;
        }

    protected boolean isAtomic(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fAtomic;
        }

    protected boolean isCalculated(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fCalculated;
        }

    protected boolean isNativeGetter(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fNativeGetter;
        }

    protected boolean isNativeSetter(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fNativeSetter;
        }

    protected boolean isAnnotated(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_typeAnnotation != null;
        }

    protected TypeComposition getAnnotation(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        TypeConstant type = info == null ? null : info.m_typeAnnotation;
        return type == null ? null : f_types.resolveClass(type);
        }

    protected boolean isGenericType(String sProperty)
        {
        return f_struct.indexOfFormalParameter(sProperty) >= 0;
        }

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        ensureMethodStructure(sName, asParamType, asRetType).setNative(true);
        }

    public MethodStructure ensureMethodStructure(String sName, String[] asParam)
        {
        return ensureMethodStructure(sName, asParam, VOID);
        }

    public MethodStructure ensureMethodStructure(String sName, String[] asParam, String[] asRet)
        {
        MethodStructure method = f_types.f_adapter.getMethod(this, sName, asParam, asRet);
        if (method == null)
            {
            MethodStructure methodSuper = null;
            for (TypeComposition clzSuper : f_clazzCanonical.getCallChain())
                {
                methodSuper = f_types.f_adapter.getMethod(clzSuper.f_template, sName, asParam, asRet);
                if (methodSuper != null)
                    {
                    break;
                    }
                }

            if (methodSuper == null)
                {
                throw new IllegalArgumentException("Method is not defined: " + f_sName + '#' + sName);
                }

            method = f_struct.createMethod(false,
                    methodSuper.getAccess(), null,
                    methodSuper.getReturns().toArray(new Parameter[methodSuper.getReturnCount()]),
                    sName,
                    methodSuper.getParams().toArray(new Parameter[methodSuper.getParamCount()]));
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
            property.setInfo(info = new PropertyInfo(f_types.f_container.f_pool, property));
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
        protected       TypeConstant      m_typeAnnotation;

        public PropertyInfo(ConstantPool pool, PropertyStructure property)
            {
            f_pool = pool;
            f_property = property;
            }

        protected void markAtomic()
            {
            m_fAtomic = true;

            TypeConstant typeProp = f_property.getType();

            TypeConstant typeAnno = typeProp.isEcstasy("Int")
                ? f_pool.ensureEcstasyClassConstant("annotations.AtomicIntNumber").asTypeConstant()
                : f_pool.ensureEcstasyClassConstant("annotations.AtomicVar").asTypeConstant();

            setAnnotation(f_pool.ensureParameterizedTypeConstant(typeAnno, typeProp));
            }

        public void setAnnotation(TypeConstant typeAnno)
            {
            m_typeAnnotation = typeAnno;
            }
        }


    public static String[] VOID   = new String[0];
    public static String[] OBJECT = new String[] {"Object"};
    public static String[] INT    = new String[] {"Int64"};
    public static String[] STRING = new String[] {"String"};
    }
