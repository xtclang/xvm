package org.xvm.runtime.template.collections;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template._native.collections.arrays.xRTBooleanDelegate;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTBitDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;
import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBit;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native generic Array implementation.
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport
    {
    public static xArray INSTANCE;
    public static xEnum  MUTABILITY;

    public xArray(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        if (this == INSTANCE)
            {
            registerNativeTemplate(new xBitArray   (f_container, f_struct, true));
            registerNativeTemplate(new xByteArray  (f_container, f_struct, true));
            registerNativeTemplate(new xNibbleArray(f_container, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        // register array specializations
        ConstantPool              pool         = f_container.getConstantPool();
        Map<TypeConstant, xArray> mapTemplates = new HashMap<>();

        mapTemplates.put(pool.typeBit(),  xBitArray.INSTANCE);
        mapTemplates.put(pool.typeByte(), xByteArray.INSTANCE);

        ARRAY_TEMPLATES = mapTemplates;

        // cache the constructors
        for (MethodStructure method :
                ((MultiMethodStructure) getStructure().getChild("construct")).methods())
            {
            TypeConstant typeParam0 = method.getParam(0).getType();

            if (method.getParamCount() == 1)
                {
                // 0) construct(Int capacity = 0)
                CONSTRUCTORS[0] = method.getIdentityConstant();
                }
            else if (method.getAccess() == Constants.Access.PUBLIC)
                {
                // private construct(ArrayDelegate<Element> delegate, Mutability mutability)
                // must not be called
                // 1) construct(Int size, Element | function Element (Int) supply)
                // 2) construct(Mutability mutability, Element... elements)
                if (typeParam0.equals(pool.typeInt()))
                    {
                    CONSTRUCTORS[1] = method.getIdentityConstant();
                    }
                else
                    {
                    CONSTRUCTORS[2] = method.getIdentityConstant();
                    }
                }
            else
                {
                // private construct(ArrayDelegate<Element> delegate, Mutability mutability)
                CONSTRUCTORS[3] = method.getIdentityConstant();
                }
            }

        // cache "Iterable.toArray()" method
        ITERABLE_TO_ARRAY = f_container.getClassStructure("Iterable").findMethod("toArray", 1);

        // cache Mutability template
        MUTABILITY = (xEnum) f_container.getTemplate("collections.Array.Mutability");

        // cache "ConstHelper.createListSet" method
        ClassStructure clzHelper = f_container.getClassStructure("_native.ConstHelper");
        CREATE_LIST_SET = clzHelper.findMethod("createListSet", 2);

        OBJECT_ARRAY_CLZ  = f_container.resolveClass(pool.ensureArrayType(pool.typeObject()));
        STRING_ARRAY_CLZ  = f_container.resolveClass(pool.ensureArrayType(pool.typeString()));
        BOOLEAN_ARRAY_CLZ = f_container.resolveClass(pool.ensureArrayType(pool.typeBoolean()));
        CHAR_ARRAY_CLZ    = f_container.resolveClass(pool.ensureArrayType(pool.typeChar()));
        BIT_ARRAY_CLZ     = f_container.resolveClass(pool.typeBitArray());
        BYTE_ARRAY_CLZ    = f_container.resolveClass(pool.typeByteArray());

        // mark native properties and methods
        markNativeProperty("delegate");
        markNativeProperty("mutability");

        markNativeMethod("clear", VOID, THIS);
        markNativeMethod("getElement", INT, ELEMENT_TYPE);
        markNativeMethod("setElement", null, VOID);
        markNativeMethod("elementAt", INT, null);
        markNativeMethod("slice", null, THIS);
        markNativeMethod("deleteAll", null, THIS);

        ClassTemplate mixinNumber = f_container.getTemplate("collections.arrays.NumberArray");
        mixinNumber.markNativeMethod("asBitArray" , VOID, null);

        invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public TypeComposition ensureParameterizedClass(Container container, TypeConstant... atypeParams)
        {
        assert atypeParams.length == 1;

        xArray template = ARRAY_TEMPLATES.get(atypeParams[0]);

        return template == null
                ? super.ensureParameterizedClass(container, atypeParams)
                : template.getCanonicalClass();
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        xArray template = ARRAY_TEMPLATES.get(type.getParamType(0));

        return template == null ? this : template;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constArray = (ArrayConstant) constant;

        boolean fSet = switch (constArray.getFormat())
            {
            case Array -> false;
            case Set   -> true;
            default    -> throw new IllegalStateException();
            };

        TypeConstant typeArray = constArray.getType();
        Constant[]   aconst    = constArray.getValue();
        int          cSize     = aconst.length;

        ObjectHandle[] ahValue   = new ObjectHandle[cSize];
        boolean        fDeferred = false;
        for (int i = 0; i < cSize; i++)
            {
            ObjectHandle hValue = frame.getConstHandle(aconst[i]);

            if (Op.isDeferred(hValue))
                {
                fDeferred = true;
                }
            ahValue[i] = hValue;
            }

        if (typeArray.containsFormalType(true))
            {
            typeArray = typeArray.resolveGenerics(frame.poolContext(),
                            frame.getGenericsResolver(typeArray.containsDynamicType(null)));
            }

        if (fSet)
            {
            TypeConstant typeEl = typeArray.getParamType(0);

            if (fDeferred)
                {
                Frame.Continuation stepNext = frameCaller ->
                    createListSet(frameCaller, typeEl, ahValue);
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return createListSet(frame, typeEl, ahValue);
            }
        else
            {
            TypeComposition clzArray = frame.f_context.f_container.resolveClass(typeArray);
            if (fDeferred)
                {
                Frame.Continuation stepNext = frameCaller ->
                        frameCaller.pushStack(createImmutableArray(clzArray, ahValue));
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return frame.pushStack(createImmutableArray(clzArray, ahValue));
            }
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clzArray,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        IdentityConstant idConstruct = constructor.getIdentityConstant();
        int              nScenario;
        for (nScenario = 0; nScenario < 3; nScenario++)
            {
            if (CONSTRUCTORS[nScenario].equals(idConstruct))
                {
                break;
                }
            }

        switch (nScenario)
            {
            case 0: // construct(Int capacity = 0)
                {
                ObjectHandle hCapacity = ahVar[0];
                long         cCapacity = hCapacity == ObjectHandle.DEFAULT ?
                                            0 : ((JavaLong) hCapacity).getValue();

                if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + cCapacity));
                    }

                ObjectHandle hArray = createEmptyArray(clzArray, (int) cCapacity, Mutability.Mutable);
                return frame.assignValue(iReturn, hArray);
                }

            case 1: // construct(Int size, Element | function Element (Int) supply)
                {
                JavaLong hCapacity = (JavaLong) ahVar[0];
                long     cCapacity = hCapacity.getValue();

                if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + cCapacity));
                    }

                ArrayHandle hArray = createEmptyArray(clzArray, (int) cCapacity, Mutability.Fixed);
                int cSize = (int) cCapacity;
                if (cSize > 0)
                    {
                    hArray.m_hDelegate.m_cSize = cSize;

                    ObjectHandle hValue = ahVar[1];
                    // we could get here either naturally (e.g. new Array<String>(7, "");)
                    // or via the ArrayExpression (e.g. new Int[7])
                    TypeConstant typeEl = clzArray.getType().getParamType(0);
                    if (hValue == ObjectHandle.DEFAULT)
                        {
                        hValue = frame.getConstHandle(typeEl.getDefaultValue());
                        if (Op.isDeferred(hValue))
                            {
                            return hValue.proceed(frame, frameCaller ->
                                fill(frameCaller, hArray, cSize, frameCaller.popStack(), iReturn));
                            }
                        }
                    else
                        {
                        ConstantPool pool      = frame.poolContext();
                        TypeConstant typeValue = hValue.getType();

                        IsFunction:
                        if (typeValue.isA(pool.typeFunction()))
                            {
                            TypeConstant[] atypeParam = pool.extractFunctionParams(typeValue);
                            if (atypeParam.length != 1 || !atypeParam[0].equals(pool.typeInt()))
                                {
                                break IsFunction;
                                }
                            TypeConstant[] atypeRet = pool.extractFunctionReturns(typeValue);
                            if (atypeRet.length != 1 || !atypeRet[0].isA(typeEl))
                                {
                                break IsFunction;
                                }

                            FunctionHandle      hfnSupplier = (FunctionHandle) hValue;
                            int                 cArgs       = hfnSupplier.getVarCount();
                            ObjectHandle[]      ahArg       = new ObjectHandle[cArgs];
                            Utils.ValueSupplier supplier    = (frameCaller, index) ->
                                {
                                ahArg[0] = xInt.makeHandle(index);
                                return hfnSupplier.call1(frameCaller, null, ahArg, Op.A_STACK);
                                };
                            return new Utils.FillArray(hArray, cSize, supplier, iReturn).doNext(frame);
                            }
                        }
                    return fill(frame, hArray, cSize, hValue, iReturn);
                    }
                return frame.assignValue(iReturn, hArray);
                }

            case 2: // construct(Mutability mutability, Element... elements)
                {
                // call Iterable.to<Element> naturally
                ObjectHandle   hMutability = ahVar[0];
                ObjectHandle   hSequence   = ahVar[1];
                ObjectHandle[] ahVars      = new ObjectHandle[ITERABLE_TO_ARRAY.getMaxVars()];
                ahVars[0] = hMutability;

                return frame.call1(ITERABLE_TO_ARRAY, hSequence, ahVars, iReturn);
                }

            case 3: // private construct(ArrayDelegate<Element> delegate, Mutability mutability)
                {
                ObjectHandle hTarget = ahVar[0];
                if (!(hTarget instanceof DelegateHandle hDelegate))
                    {
                    return frame.raiseException(xException.unsupportedOperation(frame));
                    }

                ObjectHandle hMutability = ahVar[1];
                ArrayHandle  hArray      = new ArrayHandle(
                    clzArray, hDelegate, Mutability.values()[((EnumHandle) hMutability).getOrdinal()]);

                if (hArray.m_mutability == Mutability.Constant)
                    {
                    hArray.makeImmutable();
                    }
                return frame.assignValue(iReturn, hArray);
                }

            default:
                return frame.raiseException("Unknown constructor: " + idConstruct.getValueString());
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName)
            {
            case "delegate":
                return frame.assignValue(iReturn, hArray.m_hDelegate);

            case "mutability":
                return Utils.assignInitializedEnum(frame,
                    MUTABILITY.getEnumByOrdinal(hArray.m_mutability.ordinal()), iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName)
            {
            case "mutability":
                {
                Mutability mutability = Mutability.values()[((EnumHandle) hValue).getOrdinal()];
                if (mutability.compareTo(hArray.m_mutability) > 0)
                    {
                    return frame.raiseException(
                        xException.illegalState(frame, hArray.m_mutability.toString()));
                    }
                hArray.setMutability(mutability);
                return Op.R_NEXT;
                }
            }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "getElement":
                return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

            case "slice":
                {
                GenericHandle hInterval = (GenericHandle) hArg;

                long    ixFrom   = ((JavaLong) hInterval.getField(frame, "lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField(frame, "upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hInterval.getField(frame, "lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hInterval.getField(frame, "upperExclusive")).get();
                boolean fReverse = ((BooleanHandle) hInterval.getField(frame, "descending")).get();

                return invokeSlice(frame, hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
                }

            case "deleteAll":
                {
                GenericHandle hInterval = (GenericHandle) hArg;

                long    ixFrom   = ((JavaLong) hInterval.getField(frame, "lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField(frame, "upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hInterval.getField(frame, "lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hInterval.getField(frame, "upperExclusive")).get();

                return invokeDeleteAll(frame, hTarget, ixFrom, fExLower, ixTo, fExUpper, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "asBitArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;

                // a view cannot naturally grow or shrink
                Mutability mutability = hArray.m_mutability == Mutability.Constant ||
                                        hArray.m_mutability == Mutability.Persistent
                        ? Mutability.Constant
                        : Mutability.Fixed;

                DelegateHandle hView  =
                        xRTViewToBit.INSTANCE.createBitViewDelegate(hArray.m_hDelegate, mutability);

                return frame.assignValue(iReturn,
                        new ArrayHandle(xBitArray.INSTANCE.getCanonicalClass(), hView, mutability));
                }

            case "clear":
                {
                ArrayHandle hArray     = (ArrayHandle) hTarget;
                Mutability  mutability = hArray.m_mutability;

                if (hArray.m_hDelegate.m_cSize > 0)
                    {
                    switch (mutability)
                        {
                        case Mutable:
                            hArray.m_hDelegate = makeDelegate(hArray.getComposition(), 0,
                                Utils.OBJECTS_NONE, mutability);
                            break;

                        case Fixed:
                            return frame.raiseException(xException.readOnly(frame, mutability));

                        case Constant:
                        case Persistent:
                            hArray = createEmptyArray(hArray.getComposition(), 0, mutability);
                            break;
                        }
                    }
                return frame.assignValue(iReturn, hArray);
                }

            case "setElement":
                return assignArrayValue(frame, hTarget, ((JavaLong) ahArg[0]).getValue(), ahArg[1]);
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int createPropertyRef(Frame frame, ObjectHandle hTarget,
                                 PropertyConstant idProp, boolean fRO, int iReturn)
        {
        if (idProp.getName().equals("delegate"))
            {
            ArrayHandle    hArray    = (ArrayHandle) hTarget;
            DelegateHandle hDelegate = hArray.m_hDelegate;

            ConstantPool   pool      = frame.poolContext();
            TypeConstant   typeRef   = pool.ensureParameterizedTypeConstant(
                                           pool.typeVar(), hDelegate.getType());

            ClassComposition clzRef  = frame.f_context.f_container.ensureClassComposition(typeRef, xRef.INSTANCE);
            RefHandle        hRef    = new RefHandle(clzRef, "delegate", hDelegate);

            return frame.assignValue(iReturn, hRef);
            }

        return super.createPropertyRef(frame, hTarget, idProp, fRO, iReturn);
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        if (super.compareIdentity(hValue1, hValue2))
            {
            return true;
            }

        ArrayHandle hArray1 = (ArrayHandle) hValue1;
        ArrayHandle hArray2 = (ArrayHandle) hValue2;

        return !hArray1.isMutable() && !hArray2.isMutable() &&
            hArray1.m_hDelegate.getTemplate().
                compareIdentity(hArray1.m_hDelegate, hArray2.m_hDelegate);
        }


    // ----- IndexSupport methods ------------------------------------------------------------------

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                extractArrayValue(frame, hDelegate, lIndex, iReturn);
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                assignArrayValue(frame, hDelegate, lIndex, hValue);
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
        {
        return hTarget.getType().resolveGenericType("Element");
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.m_hDelegate.m_cSize;
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePreInc(frame, hDelegate, lIndex, iReturn);
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePostInc(frame, hDelegate, lIndex, iReturn);
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePreDec(frame, hDelegate, lIndex, iReturn);
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePostDec(frame, hDelegate, lIndex, iReturn);
        }

    @Override
    public ObjectHandle[] toArray(Frame frame, ObjectHandle hTarget)
            throws ExceptionHandle.WrapperException
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;

        return ((xRTDelegate) hDelegate.getTemplate()).toArray(frame, hDelegate);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Construct an immutable ListSet handle based on the specified array of handles and put on the
     * frame's stack.
     *
     * @param frame    the current frame
     * @param typeEl   the array element type
     * @param ahValue  the array handles
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    private int createListSet(Frame frame, TypeConstant typeEl, ObjectHandle[] ahValue)
        {
        TypeConstant    typeArray = frame.poolContext().ensureArrayType(typeEl);
        TypeComposition clzArray  = frame.f_context.f_container.resolveClass(typeArray);

        return createListSet(frame, createImmutableArray(clzArray, ahValue), Op.A_STACK);
        }

    /**
     * Construct an immutable ListSet handle based on the specified array.
     *
     * @param frame    the current frame
     * @param hArray   the array handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public static int createListSet(Frame frame, ArrayHandle hArray, int iResult)
        {
        ObjectHandle[] ahVar = new ObjectHandle[CREATE_LIST_SET.getMaxVars()];
        ahVar[0] = hArray.getType().getParamType(0).ensureTypeHandle(frame.f_context.f_container);
        ahVar[1] = hArray;

        return frame.call1(CREATE_LIST_SET, null, ahVar, iResult);
        }

    /**
     * slice(Interval<Int>) implementation.
     */
    protected int invokeSlice(Frame frame, ObjectHandle hTarget, long ixLower, boolean fExLower,
                              long ixUpper, boolean fExUpper, boolean fReverse, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        if (fExLower)
            {
            // exclusive lower
            ++ixLower;
            }

        if (ixLower < 0)
            {
            return frame.raiseException(xException.outOfBounds(frame, ixLower, 0));
            }

        if (fExUpper)
            {
            // exclusive upper
            --ixUpper;
            }

        int cSize = (int) hDelegate.m_cSize;
        if (ixUpper < ixLower - 1 || ixUpper >= cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, ixUpper, cSize));
            }

        DelegateHandle hSlice = template.slice(hDelegate, ixLower, ixUpper - ixLower + 1, fReverse);
        if (hSlice != hDelegate)
            {
            Mutability mutability = hArray.m_mutability;
            if (mutability == Mutability.Mutable)
                {
                mutability = Mutability.Fixed;
                }
            hArray = new ArrayHandle(hArray.getComposition(), hSlice, mutability);
            }
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * deleteAll(Interval<Int>) implementation.
     */
    protected int invokeDeleteAll(Frame frame, ObjectHandle hTarget, long ixLower, boolean fExLower,
                                 long ixUpper, boolean fExUpper, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        if (fExLower)
            {
            // exclusive lower
            ++ixLower;
            }

        if (ixLower < 0)
            {
            return frame.raiseException(xException.outOfBounds(frame, ixLower, 0));
            }

        if (fExUpper)
            {
            // exclusive upper
            --ixUpper;
            }

        int cSize = (int) hDelegate.m_cSize;
        if (ixUpper < 0 || ixUpper >= cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, ixUpper, cSize));
            }

        Mutability mutability = hArray.m_mutability;
        if (mutability == Mutability.Fixed)
            {
            return frame.raiseException(xException.sizeLimited(frame, "Fixed size array"));
            }

        DelegateHandle hDelegateNew = template.deleteRange(hDelegate, ixLower, ixUpper - ixLower + 1);
        if (hDelegateNew != hDelegate)
            {
            if (hDelegateNew == null)
                {
                return frame.raiseException(xException.readOnly(frame, mutability));
                }
            hArray = new ArrayHandle(hArray.getComposition(), hDelegateNew, mutability);
            }
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Fill the array content with the specified value.
     *
     * @param hTarget  the array
     * @param cSize    the number of elements to fill
     * @param hValue   the value
     */
    protected int fill(Frame frame, ObjectHandle hTarget, int cSize, ObjectHandle hValue, int iReturn)
        {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.m_hDelegate;
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        DelegateHandle hDelegateNew = template.fill(hDelegate, cSize, hValue);
        if (hDelegateNew != hDelegate)
            {
            if (hDelegateNew == null)
                {
                return frame.raiseException(xException.readOnly(frame, hArray.m_mutability));
                }
            hArray = new ArrayHandle(hArray.getComposition(), hDelegateNew, hArray.m_mutability);
            }
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Create an immutable one dimensional array for a specified type and size filled based the
     * specified supplier.
     */
    public static int createAndFill(Frame frame, TypeComposition clzArray, int cSize,
                                    Utils.ValueSupplier supplier, int iReturn)
        {
        // make it "Fixed" first; freeze after filling up
        ArrayHandle hArray = createEmptyArray(clzArray, cSize, Mutability.Fixed);

        switch (new Utils.FillArray(hArray, cSize, supplier, iReturn).doNext(frame))
            {
            case Op.R_NEXT:
                hArray.setMutability(Mutability.Constant);
                return Op.R_NEXT;

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    hArray.setMutability(Mutability.Constant);
                    return Op.R_NEXT;
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }


    // ----- TypeComposition helpers ---------------------------------------------------------------

    /**
     * @return the TypeComposition for Array<Boolean>.
     */
    public static TypeComposition getBooleanArrayComposition()
        {
        return BOOLEAN_ARRAY_CLZ;
        }


    // ----- ObjectHandle helpers ------------------------------------------------------------------

    /**
     * Create a one dimensional immutable array for a specified class and content.
     *
     * @param clzArray  the class of the array
     * @param ahArg     the array elements
     *
     * @return the array handle
     */
    public static ArrayHandle createImmutableArray(TypeComposition clzArray, ObjectHandle[] ahArg)
        {
        return makeArrayHandle(clzArray, ahArg.length, ahArg, Mutability.Constant);
        }

    /**
     * Create an empty one dimensional array for a specified type and arity.
     *
     * @param clzArray    the class of the array
     * @param cCapacity   the array capacity
     * @param mutability  the mutability constraint
     *
     * @return the array handle
     */
    public static ArrayHandle createEmptyArray(TypeComposition clzArray, int cCapacity,
                                               Mutability mutability)
        {
        return makeArrayHandle(clzArray, cCapacity, Utils.OBJECTS_NONE, mutability);
        }

    /**
     * @return an immutable String array handle
     */
    public static ArrayHandle makeStringArrayHandle(StringHandle[] ahValue)
        {
        return makeArrayHandle(STRING_ARRAY_CLZ, ahValue.length, ahValue, Mutability.Constant);
        }

    /**
     * @return a Bit array handle
     */
    public static ArrayHandle makeBitArrayHandle(byte[] abValue, int cBits, Mutability mutability)
        {
        DelegateHandle hDelegate = xRTBitDelegate.INSTANCE.makeHandle(abValue, cBits, mutability);
        return new ArrayHandle(BIT_ARRAY_CLZ, hDelegate, mutability);
        }

    /**
     * @return a Boolean array handle
     */
    public static ArrayHandle makeBooleanArrayHandle(byte[] abValue, int cBits, Mutability mutability)
        {
        DelegateHandle hDelegate = xRTBooleanDelegate.INSTANCE.makeHandle(abValue, cBits, mutability);
        return new ArrayHandle(BOOLEAN_ARRAY_CLZ, hDelegate, mutability);
        }

    /**
     * @return a Byte array handle
     */
    public static ArrayHandle makeByteArrayHandle(byte[] abValue, Mutability mutability)
        {
        DelegateHandle hDelegate = xRTUInt8Delegate.INSTANCE.makeHandle(abValue, abValue.length, mutability);
        return new ArrayHandle(BYTE_ARRAY_CLZ, hDelegate, mutability);
        }

    /**
     * @return a Char array handle
     */
    public static ArrayHandle makeCharArrayHandle(char[] achValue, Mutability mutability)
        {
        DelegateHandle hDelegate = xRTCharDelegate.INSTANCE.makeHandle(achValue, mutability);
        return new ArrayHandle(CHAR_ARRAY_CLZ, hDelegate, mutability);
        }

    /**
     * @return an Object array handle with the specified mutability
     */
    public static ArrayHandle makeObjectArrayHandle(ObjectHandle[] ahValue, Mutability mutability)
        {
        return makeArrayHandle(OBJECT_ARRAY_CLZ, ahValue.length, ahValue, mutability);
        }

    /**
     * Create an ArrayHandle for the specified TypeComposition and fill it with objects from the
     * specified array.
     */
    public static ArrayHandle makeArrayHandle(TypeComposition clzArray, int cCapacity,
                                               ObjectHandle[] ahValue, Mutability mutability)
        {
        DelegateHandle hDelegate = makeDelegate(clzArray, cCapacity, ahValue, mutability);
        return new ArrayHandle(clzArray, hDelegate, mutability);
        }

    /**
     * Create a DelegateHandle for the specified TypeComposition and fill it with objects from the
     * specified array.
     */
    protected static DelegateHandle makeDelegate(TypeComposition clzArray, int cCapacity,
                                                 ObjectHandle[] ahValue, Mutability mutability)
        {
        TypeConstant typeElement      = clzArray.getType().getParamType(0);
        xRTDelegate  templateDelegate = xRTDelegate.getArrayTemplate(typeElement);

        if (mutability != Mutability.Fixed)
            {
            cCapacity = ahValue.length;
            }
        return templateDelegate.createDelegate(
                clzArray.getContainer(), typeElement, cCapacity, ahValue, mutability);
        }

    public static class ArrayHandle
            extends ObjectHandle
        {
        protected Mutability     m_mutability;
        public    DelegateHandle m_hDelegate;

        protected ArrayHandle(TypeComposition clzArray, DelegateHandle hDelegate,
                              Mutability mutability)
            {
            super(clzArray);

            m_hDelegate  = hDelegate;
            m_fMutable   = mutability != Mutability.Constant;
            m_mutability = mutability;
            }

        public Mutability getMutability()
            {
            return m_mutability;
            }

        public void setMutability(Mutability mutability)
            {
            assert mutability.compareTo(m_mutability) <= 0;
            m_mutability = mutability;
            m_hDelegate.setMutability(mutability);
            }

        @Override
        public xArray getTemplate()
            {
            return (xArray) super.getTemplate();
            }

        @Override
        public boolean makeImmutable()
            {
            setMutability(Mutability.Constant);
            super.makeImmutable();

            return m_hDelegate.makeImmutable();
            }

        @Override
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            return m_hDelegate.isShared(poolThat, mapVisited);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_mutability + " " + m_hDelegate.toString();
            }
        }

    public enum Mutability {Constant, Persistent, Fixed, Mutable}

    // array of constructors
    private static final MethodConstant[] CONSTRUCTORS = new MethodConstant[4];
    private static MethodStructure  ITERABLE_TO_ARRAY;

    protected static final String[] ELEMENT_TYPE = new String[] {"Element"};

    private static TypeComposition OBJECT_ARRAY_CLZ;
    private static TypeComposition STRING_ARRAY_CLZ;
    private static TypeComposition BIT_ARRAY_CLZ;
    private static TypeComposition BOOLEAN_ARRAY_CLZ;
    private static TypeComposition BYTE_ARRAY_CLZ;
    private static TypeComposition CHAR_ARRAY_CLZ;
    private static Map<TypeConstant, xArray> ARRAY_TEMPLATES;

    private static MethodStructure CREATE_LIST_SET;
    }