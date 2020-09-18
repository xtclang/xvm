package org.xvm.runtime.template.collections;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString.StringHandle;

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

    public xArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
            registerNativeTemplate(new xIntArray    (f_templates, f_struct, true));
            registerNativeTemplate(new xCharArray   (f_templates, f_struct, true));
            registerNativeTemplate(new xBooleanArray(f_templates, f_struct, true));
            registerNativeTemplate(new xBitArray    (f_templates, f_struct, true));
            registerNativeTemplate(new xByteArray   (f_templates, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        // register array specializations
        ConstantPool              pool         = pool();
        Map<TypeConstant, xArray> mapTemplates = new HashMap<>();

        mapTemplates.put(pool.typeInt(),     xIntArray.INSTANCE);
        mapTemplates.put(pool.typeChar(),    xCharArray.INSTANCE);
        mapTemplates.put(pool.typeBoolean(), xBooleanArray.INSTANCE);
        mapTemplates.put(pool.typeBit(),     xBitArray.INSTANCE);
        mapTemplates.put(pool.typeByte(),    xByteArray.INSTANCE);

        ARRAY_TEMPLATES = mapTemplates;

        // cache the constructors
        for (MethodStructure method :
                ((MultiMethodStructure) getStructure().getChild("construct")).methods())
            {
            TypeConstant typeParam0 = method.getParam(0).getType();

            if (method.getParamCount() == 1)
                {
                if (typeParam0.equals(pool.typeInt()))
                    {
                    // 0) construct(Int capacity = 0)
                    CONSTRUCTORS[0] = method.getIdentityConstant();
                    }
                else
                    {
                    // protected construct(ArrayDelegate<Element> delegate)
                    // must not be called
                    }
                }
            else
                {
                // 1) construct(Int size, Element | function Element (Int) supply)
                // 2) construct(Mutability mutability, Element... elements)
                // 3) construct(Array<Element> array, Interval<Int> section)
                if (typeParam0.equals(pool.typeInt()))
                    {
                    CONSTRUCTORS[1] = method.getIdentityConstant();
                    }
                else if (typeParam0.isA(pool.typeArray()))
                    {
                    CONSTRUCTORS[3] = method.getIdentityConstant();
                    }
                else
                    {
                    CONSTRUCTORS[2] = method.getIdentityConstant();
                    }
                }
            }

        // cache "Iterable.toArray()" method
        ITERABLE_TO_ARRAY = f_templates.getClassStructure("Iterable").findMethod("toArray", 1);

        // cache Mutability template
        MUTABILITY = (xEnum) f_templates.getTemplate("collections.Array.Mutability");

        // cache "ConstHelper.createListSet" method
        ClassStructure clzHelper = f_templates.getClassStructure("_native.ConstHelper");
        CREATE_LIST_SET = clzHelper.findMethod("createListSet", 2);

        // mark native properties and methods
        markNativeProperty("capacity");
        markNativeProperty("mutability");
        markNativeProperty("size");

        markNativeMethod("getElement", INT, ELEMENT_TYPE);
        markNativeMethod("setElement", new String[] {"numbers.Int64", "Element"}, VOID);
        markNativeMethod("elementAt", INT, new String[] {"reflect.Var<Element>"});
        markNativeMethod("add", ELEMENT_TYPE, ARRAY);
        markNativeMethod("addAll", new String[] {"Iterable<Element>"}, ARRAY);
        markNativeMethod("insert", new String[] {"numbers.Int64", "Element"}, ARRAY);
        markNativeMethod("insertAll", new String[] {"numbers.Int64", "Iterable<Element>"}, ARRAY);
        markNativeMethod("addAll", new String[] {"Iterable<Element>"}, ARRAY);
        markNativeMethod("delete", new String[] {"numbers.Int64"}, null);
        markNativeMethod("slice", new String[] {"Range<numbers.Int64>"}, ARRAY);
        markNativeMethod("freeze", BOOLEAN, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public ClassComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... atypeParams)
        {
        assert atypeParams.length == 1;

        xArray template = ARRAY_TEMPLATES.get(atypeParams[0]);

        if (template != null)
            {
            return template.getCanonicalClass();
            }

        return super.ensureParameterizedClass(pool, atypeParams);
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        TypeConstant[] atypeParams = type.getParamTypesArray();
        return atypeParams.length > 0
                ? getArrayTemplate(atypeParams[0])
                : this;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constArray = (ArrayConstant) constant;

        boolean fSet;
        switch (constArray.getFormat())
            {
            case Array:
                fSet = false;
                break;

            case Set:
                fSet = true;
                break;

            default:
                throw new IllegalStateException();
            }

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
            typeArray = typeArray.resolveGenerics(frame.poolContext(), frame.getGenericsResolver());
            }

        ClassComposition clzArray = f_templates.resolveClass(typeArray);
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
            if (fDeferred)
                {
                Frame.Continuation stepNext = frameCaller -> frameCaller.pushStack(
                        ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahValue));
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return frame.pushStack(
                    ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahValue));
            }
        }

    private int createListSet(Frame frame, TypeConstant typeEl, ObjectHandle[] ahValue)
        {
        ConstantPool     pool      = frame.poolContext();
        TypeConstant     typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeEl);
        ClassComposition clzArray  = f_templates.resolveClass(typeArray);

        ObjectHandle[] ahVar = new ObjectHandle[CREATE_LIST_SET.getMaxVars()];
        ahVar[0] = typeEl.getTypeHandle();
        ahVar[1] = ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahValue);

        return frame.call1(CREATE_LIST_SET, null, ahVar, Op.A_STACK);
        }

    /**
     * Create a one dimensional immutable array for a specified class and content.
     *
     * @param clzArray  the class of the array
     * @param ahArg     the array elements
     *
     * @return the array handle
     */
    public ArrayHandle createArrayHandle(ClassComposition clzArray, ObjectHandle[] ahArg)
        {
        return new GenericArrayHandle(clzArray, ahArg, Mutability.Constant);
        }

    /**
     * Create a one dimensional array for a specified type and arity.
     *
     * @param clzArray    the class of the array
     * @param cCapacity   the array size
     * @param mutability  the mutability constraint
     *
     * @return the array handle
     */
    public ArrayHandle createArrayHandle(ClassComposition clzArray, int cCapacity, Mutability mutability)
        {
        return new GenericArrayHandle(clzArray, cCapacity, mutability);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clzArray,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        IdentityConstant idConstruct = constructor.getIdentityConstant();
        int              nScenario;
        for (nScenario = 0; nScenario < 4; nScenario++)
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

                xArray      template = (xArray) clzArray.getTemplate();
                ArrayHandle hArray   = template.createArrayHandle(clzArray, (int) cCapacity, Mutability.Mutable);
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

                xArray      template = (xArray) clzArray.getTemplate();
                ArrayHandle hArray   = template.createArrayHandle(clzArray, (int) cCapacity, Mutability.Fixed);

                int cSize = (int) cCapacity;
                if (cSize > 0)
                    {
                    ObjectHandle hSupplier = ahVar[1];
                    // we could get here either naturally (e.g. new Array<String>(7, "");)
                    // or via the ArrayExpression (e.g. new Int[7])
                    TypeConstant typeEl = clzArray.getType().getParamType(0);
                    if (hSupplier == ObjectHandle.DEFAULT)
                        {
                        ObjectHandle hValue = frame.getConstHandle(typeEl.getDefaultValue());
                        if (Op.isDeferred(hValue))
                            {
                            hValue.proceed(frame, frameCaller ->
                                {
                                fill(hArray, cSize, frameCaller.popStack());
                                return frameCaller.assignValue(iReturn, hArray);
                                });
                            }
                        fill(hArray, cSize, hValue);
                        }
                    else if (hSupplier.getType().isA(typeEl))
                        {
                        fill(hArray, cSize, hSupplier);
                        }
                    else
                        {
                        return new Fill(this, hArray, cSize, (FunctionHandle) hSupplier, iReturn).doNext(frame);
                        }
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

            case 3: // construct(Array<Element> array, Interval<Int> section)
                {
                ArrayHandle   hArray    = (ArrayHandle) ahVar[0];
                GenericHandle hInterval = (GenericHandle) ahVar[1];
                JavaLong      hLower    = (JavaLong) hInterval.getField("lowerBound");
                JavaLong      hUpper    = (JavaLong) hInterval.getField("upperBound");
                boolean       fExLower  = ((BooleanHandle) hInterval.getField("lowerExclusive")).get();
                boolean       fExUpper  = ((BooleanHandle) hInterval.getField("upperExclusive")).get();
                boolean       fReverse  = ((BooleanHandle) hInterval.getField("descending")).get();

                long lLower = hLower.getValue();
                long lUpper = hUpper.getValue();

                assert lLower <= lUpper;
                return slice(frame, hArray, lLower, fExLower, lUpper, fExUpper, fReverse, iReturn);
                }

            default:
                throw new IllegalStateException("Unknown constructor: " +
                    idConstruct.getValueString());
            }
        }

    /**
     * Create a copy of the specified array for the specified mutability
     *
     * @param hArray      the array
     * @param mutability  the mutability
     *
     * @return a new array
     */
    protected ArrayHandle createCopy(ArrayHandle hArray, Mutability mutability)
        {
        GenericArrayHandle hSrc = (GenericArrayHandle) hArray;

        return new GenericArrayHandle(hSrc.getComposition(),
            Arrays.copyOfRange(hSrc.m_ahValue, 0, hSrc.m_cSize), mutability);
        }

    /**
     * Fill the array content with the specified value.
     *
     * @param hArray  the array
     * @param cSize   the number of elements to fill
     * @param hValue  the value
     */
    protected void fill(ArrayHandle hArray, int cSize, ObjectHandle hValue)
        {
        GenericArrayHandle ha = (GenericArrayHandle) hArray;

        Arrays.fill(ha.m_ahValue, 0, cSize, hValue);
        ha.m_cSize = cSize;
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // hArg is either Iterable<Element> or Element
        return hArg.getType().isA(hTarget.getType().getParamType(0))
                ? addElement(frame, hTarget, hArg, iReturn)
                : addElements(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName)
            {
            case "capacity":
                return frame.assignValue(iReturn, xInt64.makeHandle(hArray.getCapacity()));

            case "mutability":
                return Utils.assignInitializedEnum(frame,
                    MUTABILITY.getEnumByOrdinal(hArray.m_mutability.ordinal()), iReturn);

            case "size":
                return frame.assignValue(iReturn, xInt64.makeHandle(hArray.m_cSize));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName)
            {
            case "capacity":
                {
                int nCapacityOld = hArray.getCapacity();
                int nCapacityNew = (int) ((JavaLong) hValue).getValue();
                int nSize        = hArray.m_cSize;

                if (nCapacityNew < nSize)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Capacity cannot be less then size "));
                    }

                // for now, no trimming
                if (nCapacityNew > nCapacityOld)
                    {
                    hArray.setCapacity(nCapacityNew);
                    }
                return Op.R_NEXT;
                }

            case "mutability":
                hArray.m_mutability = Mutability.values()[((EnumHandle) hValue).getOrdinal()];
                return Op.R_NEXT;
            }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "add":
                return addElement(frame, hTarget, hArg, iReturn);

            case "addAll":
                return addElements(frame, hTarget, hArg, iReturn);

            case "delete":
                return deleteElement(frame, hTarget, hArg, iReturn);

            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "getElement":
                return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

            case "slice":
                {
                GenericHandle hInterval = (GenericHandle) hArg;
                long    ixFrom   = ((JavaLong) hInterval.getField("lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField("upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hInterval.getField("lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hInterval.getField("upperExclusive")).get();
                boolean fReverse = ((BooleanHandle) hInterval.getField("descending")).get();

                return slice(frame, hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
                }

            case "freeze": // immutable Array freeze(Boolean inPlace = False)
                {
                ArrayHandle hArray  = (ArrayHandle) hTarget;
                boolean    fInPlace = hArg != ObjectHandle.DEFAULT && ((BooleanHandle) hArg).get();

                // TODO GG ensure all elements are immutable, and if not, they must be Freezable

                if (fInPlace)
                    {
                    // TODO call freeze(False) on each element that is not already immutable
                    hArray.makeImmutable();
                    }
                else
                    {
                    // TODO call freeze(False) on each element that is not already immutable as part
                    //      of the creation of the array copy
                    hArray = createCopy(hArray, Mutability.Constant);
                    }
                return frame.assignValue(iReturn, hArray);
                }
// TODO
//            case "ensurePersistent": // Array ensurePersistent(Boolean inPlace = False)
//                {
//                ArrayHandle hArray   = (ArrayHandle) hTarget;
//                boolean     fInPlace = hArg != ObjectHandle.DEFAULT && ((BooleanHandle) hArg).get();
//                if (fInPlace)
//                    {
//                    hArray.m_mutability = Mutability.Persistent;
//                    }
//                else
//                    {
//                    hArray = createCopy(hArray, Mutability.Persistent);
//                    }
//                return frame.assignValue(iReturn, hArray);
//                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "insert":
                return insertElement(frame, hTarget, (JavaLong) ahArg[0], ahArg[1], iReturn);

            case "insertAll":
                return insertElements(frame, hTarget, (JavaLong) ahArg[0], ahArg[1], iReturn);

// TODO
//            case "ensureMutable": // Array ensureMutable()
//                {
//                ArrayHandle hArray = (ArrayHandle) hTarget;
//
//                if (hArray.m_mutability != Mutability.Mutable)
//                    {
//                    hArray = createCopy(hArray, Mutability.Mutable);
//                    }
//                return frame.assignValue(iReturn, hArray);
//                }

            case "setElement":
                return assignArrayValue(frame, hTarget, ((JavaLong) ahArg[0]).getValue(), ahArg[1]);
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        GenericArrayHandle hArray1 = (GenericArrayHandle) hValue1;
        GenericArrayHandle hArray2 = (GenericArrayHandle) hValue2;

        ObjectHandle[] ah1 = hArray1.m_ahValue;
        ObjectHandle[] ah2 = hArray2.m_ahValue;

        // compare the array dimensions
        int cElements = ah1.length;
        if (cElements != ah2.length)
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        // use the compile-time element type
        // and compare arrays elements one-by-one
        TypeConstant typeEl = clazz.getType().getParamType(0);

        int[] holder = new int[] {0}; // the index holder
        return new Equals(ah1, ah2, typeEl, cElements, holder, iReturn).doNext(frame);
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        GenericArrayHandle hArray1 = (GenericArrayHandle) hValue1;
        GenericArrayHandle hArray2 = (GenericArrayHandle) hValue2;

        if (hArray1.isMutable() || hArray2.isMutable() || hArray1.m_cSize != hArray2.m_cSize)
            {
            return false;
            }

        ObjectHandle[] ah1 = hArray1.m_ahValue;
        ObjectHandle[] ah2 = hArray2.m_ahValue;

        if (ah1 == ah2)
            {
            return true;
            }

        for (int i = 0, c = hArray1.m_cSize; i < c; i++)
            {
            ObjectHandle hV1 = ah1[i];
            ObjectHandle hV2 = ah2[i];

            ClassTemplate template = hV1.getTemplate();
            if (template != hV2.getTemplate() || !template.compareIdentity(hV1, hV2))
                {
                return false;
                }
            }
        return true;
        }

    /**
     * add(Element) implementation
     */
    protected int addElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        ArrayHandle hArray     = (ArrayHandle) hTarget;
        Mutability mutability = null;

        switch (hArray.m_mutability)
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hArray.m_mutability;
                hArray     = createCopy(hArray, Mutability.Mutable);
                break;
            }

        try
            {
            insertElement(hArray, hValue, -1);
            }
        catch (ClassCastException e)
            {
            return frame.raiseException(
                xException.illegalCast(frame, hValue.getType().getValueString()));
            }

        if (mutability != null)
            {
            hArray.m_mutability = mutability;
            }

        return frame.assignValue(iReturn, hArray);
        }

    /**
     * addAll(Iterable<Element> values) implementation
     */
    protected int addElements(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        ArrayHandle hArray     = (ArrayHandle) hTarget;
        Mutability  mutability = null;

        switch (hArray.m_mutability)
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hArray.m_mutability;
                hArray     = createCopy(hArray, Mutability.Mutable);
                break;
            }

        try
            {
            addElements(hArray, hValue);
            }
        catch (ClassCastException e)
            {
            return frame.raiseException(
                xException.illegalCast(frame, hValue.getType().getValueString()));
            }

        if (mutability != null)
            {
            hArray.m_mutability = mutability;
            }

        return frame.assignValue(iReturn, hArray);
        }

    /**
     * insert(Int, Element) implementation
     */
    protected int insertElement(Frame frame, ObjectHandle hTarget,
                                JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        ArrayHandle hArray    = (ArrayHandle) hTarget;
        Mutability mutability = null;

        switch (hArray.m_mutability)
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hArray.m_mutability;
                hArray     = createCopy(hArray, Mutability.Mutable);
                break;
            }

        try
            {
            insertElement(hArray, hValue, (int) hIndex.getValue());
            }
        catch (ClassCastException e)
            {
            return frame.raiseException(
                xException.illegalCast(frame, hValue.getType().getValueString()));
            }

        if (mutability != null)
            {
            hArray.m_mutability = mutability;
            }

        return frame.assignValue(iReturn, hArray);
        }

    /**
     * insertAll(Int, Iterable<Element>) implementation
     */
    protected int insertElements(Frame frame, ObjectHandle hTarget,
                                 JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        throw new UnsupportedOperationException("TODO");
        }

    /**
     * Add or insert an element to the array (must be overridden by specialized classes).
     *
     * @param hTarget   the array
     * @param hElement  the element to add
     * @param nIndex    the index (-1 for add)
     */
    protected void insertElement(ArrayHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        GenericArrayHandle hArray  = (GenericArrayHandle) hTarget;
        int                cSize   = hArray.m_cSize;
        ObjectHandle[]     ahValue = hArray.m_ahValue;

        if (cSize == ahValue.length)
            {
            ahValue = hArray.m_ahValue = grow(ahValue, cSize + 1);
            }
        hArray.m_cSize++;

        if (nIndex == -1 || nIndex == cSize)
            {
            // append
            ahValue[cSize] = hElement;
            }
        else
            {
            // insert
            System.arraycopy(ahValue, nIndex, ahValue, nIndex+1, cSize-nIndex);
            ahValue[nIndex] = hElement;
            }
        }

    /**
     * Add an array of elements to the array (must be overridden by specialized classes).
     *
     * @param hTarget    the array
     * @param hElements  the array of values to add
     *
     * @return the resulting array handle
     */
    protected void addElements(ArrayHandle hTarget, ObjectHandle hElements)
        {
        GenericArrayHandle hArray    = (GenericArrayHandle) hTarget;
        GenericArrayHandle hArrayAdd = (GenericArrayHandle) hElements;

        int cThat = hArrayAdd.m_cSize;
        if (cThat > 0)
            {
            ObjectHandle[] ahThis = hArray.m_ahValue;
            int            cThis  = hArray.m_cSize;

            if (cThis + cThat > ahThis.length)
                {
                ahThis = hArray.m_ahValue = grow(ahThis, cThis + cThat);
                }
            hArray.m_cSize += cThat;
            System.arraycopy(hArrayAdd.m_ahValue, 0, ahThis, cThis, cThat);
            }
        }

    /**
     * delete(index) implementation
     */
    protected int deleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;
        long        lIndex = ((JavaLong) hValue).getValue();
        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }

        Mutability mutability = null;
        switch (hArray.m_mutability)
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hArray.m_mutability;
                hArray     = createCopy(hArray, Mutability.Mutable);
                break;
            }

        hArray.deleteElement((int) lIndex);

        if (mutability != null)
            {
            hArray.m_mutability = mutability;
            }

        return frame.assignValue(iReturn, hArray);
        }

    /**
     * slice(Interval<Int>) implementation
     */
    protected int slice(Frame        frame,
                        ObjectHandle hTarget,
                        long         ixLower,
                        boolean      fExLower,
                        long         ixUpper,
                        boolean      fExUpper,
                        boolean      fReverse,
                        int          iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        // calculate inclusive lower
        if (fExLower)
            {
            ++ixLower;
            }

        // calculate exclusive upper
        if (!fExUpper)
            {
            ++ixUpper;
            }

        ObjectHandle[] ahValue = hArray.m_ahValue;
        try
            {
            ObjectHandle[] ahNew;
            if (ixLower >= ixUpper)
                {
                ahNew = Utils.OBJECTS_NONE;
                }
            else if (fReverse)
                {
                int cNew = (int) (ixUpper - ixLower);
                ahNew = new ObjectHandle[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    ahNew[i] = ahValue[(int) ixUpper - i - 1];
                    }
                }
            else
                {
                ahNew = Arrays.copyOfRange(ahValue, (int) ixLower, (int) ixUpper);
                }

            ArrayHandle hArrayNew = new GenericArrayHandle(
                hArray.getComposition(), ahNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = ahValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixLower < 0 || ixLower >= c ? ixLower : ixUpper, c));
            }
        }


    // ----- IndexSupport methods ------------------------------------------------------------------

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }

        return frame.assignValue(iReturn, hArray.m_ahValue[(int) lIndex]);
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;
        int                cSize  = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cSize));
            }

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case Persistent:
                return frame.raiseException(xException.readOnly(frame));
            }

        ObjectHandle[] ahValue = hArray.m_ahValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == ahValue.length)
                {
                if (hArray.m_mutability == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                ahValue = hArray.m_ahValue = grow(ahValue, cSize + 1);
                }
            hArray.m_cSize++;
            }

        ahValue[(int) lIndex] = hValue;
        return Op.R_NEXT;
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

        return hArray.m_cSize;
        }

    // ----- helper methods ------------------------------------------------------------------------

    private xArray getArrayTemplate(TypeConstant typeParam)
        {
        xArray template = ARRAY_TEMPLATES.get(typeParam);
        return template == null ? this : template;
        }

    private ObjectHandle[] grow(ObjectHandle[] ahValue, int cSize)
        {
        int cCapacity = calculateCapacity(ahValue.length, cSize);

        ObjectHandle[] ahNew = new ObjectHandle[cCapacity];
        System.arraycopy(ahValue, 0, ahNew, 0, ahValue.length);
        return ahNew;
        }

    /**
     * Calculate a new capacity based on the current and desired size of an array.
     *
     * @return a new capacity
     */
    protected int calculateCapacity(int cOld, int cDesired)
        {
        assert cDesired > cOld;

        // resize (TODO: we should be much smarter here)
        return cDesired + Math.max(cDesired >> 2, 16);
        }

    // ----- helper classes ------------------------------------------------------------------------

    /**
     * Helper class for array initialization.
     */
    protected static class Fill
            implements Frame.Continuation
        {
        private final xArray template;
        private final ArrayHandle hArray;
        private final long cCapacity;
        private final FunctionHandle hSupplier;
        private final int iReturn;

        private final ObjectHandle[] ahVar;
        private int index = -1;

        public Fill(xArray template, ArrayHandle hArray, long cCapacity,
                    FunctionHandle hSupplier, int iReturn)
            {
            this.template = template;
            this.hArray = hArray;
            this.cCapacity = cCapacity;
            this.hSupplier = hSupplier;
            this.iReturn = iReturn;

            this.ahVar = new ObjectHandle[hSupplier.getVarCount()];
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            return template.assignArrayValue(frameCaller, hArray, index, frameCaller.popStack())
                    == Op.R_EXCEPTION ?
                Op.R_EXCEPTION : doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < cCapacity)
                {
                ahVar[0] = xInt64.makeHandle(index);

                switch (hSupplier.call1(frameCaller, null, ahVar, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, hArray);
            }
        }

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
            implements Frame.Continuation
        {
        final private ObjectHandle[] ah1;
        final private ObjectHandle[] ah2;
        final private TypeConstant typeEl;
        final private int cElements;
        final private int[] holder;
        final private int iReturn;

        public Equals(ObjectHandle[] ah1, ObjectHandle[] ah2, TypeConstant typeEl,
                      int cElements, int[] holder, int iReturn)
            {
            this.ah1 = ah1;
            this.ah2 = ah2;
            this.typeEl = typeEl;
            this.cElements = cElements;
            this.holder = holder;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ObjectHandle hResult = frameCaller.popStack();
            if (hResult == xBoolean.FALSE)
                {
                return frameCaller.assignValue(iReturn, hResult);
                }
            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            int iEl;
            while ((iEl = holder[0]++) < cElements)
                {
                switch (typeEl.callEquals(frameCaller, ah1[iEl], ah2[iEl], Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        ObjectHandle hResult = frameCaller.popStack();
                        if (hResult == xBoolean.FALSE)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, xBoolean.TRUE);
            }
        }


    // ----- ObjectHandle helpers ------------------------------------------------------------------

    /**
     * @return an immutable String array handle
     */
    public static ArrayHandle makeStringArrayHandle(StringHandle[] ahValue)
        {
        if (s_clzStringArray == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeArray = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), pool.typeString());
            s_clzStringArray = INSTANCE.f_templates.resolveClass(typeArray);
            }

        return new GenericArrayHandle(s_clzStringArray, ahValue, Mutability.Constant);
        }

    /**
     * @return an Object array handle with the specified mutability
     */
    public static ArrayHandle makeObjectArrayHandle(ObjectHandle[] ahValue, Mutability mutability)
        {
        if (s_clzObjectArray == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeArray = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), pool.typeObject());
            s_clzObjectArray = INSTANCE.f_templates.resolveClass(typeArray);
            }

        return new GenericArrayHandle(s_clzObjectArray, ahValue, mutability);
        }

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;

        public GenericArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue, Mutability mutability)
            {
            this(clzArray, ahValue, ahValue.length, mutability);
            }

        public GenericArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue,
                                  int cSize, Mutability mutability)
            {
            super(clzArray, mutability);

            m_ahValue = ahValue;
            m_cSize   = cSize;
            }

        public GenericArrayHandle(TypeComposition clzArray, long cCapacity, Mutability mutability)
            {
            super(clzArray, mutability);

            m_ahValue = new ObjectHandle[(int) cCapacity];
            }

        @Override
        public int getCapacity()
            {
            return m_ahValue.length;
            }

        @Override
        public void setCapacity(int nCapacity)
            {
            ObjectHandle[] ahOld = m_ahValue;
            ObjectHandle[] ahNew = new ObjectHandle[nCapacity];
            System.arraycopy(ahOld, 0, ahNew, 0, ahOld.length);
            m_ahValue = ahNew;
            }

        @Override
        public ObjectHandle getElement(int ix)
            {
            return m_ahValue[ix];
            }

        @Override
        public void deleteElement(int ix)
            {
            if (ix < m_cSize - 1)
                {
                System.arraycopy(m_ahValue, ix+1, m_ahValue, ix, m_cSize-ix-1);
                }
            m_ahValue[--m_cSize] = null;
            }

        @Override
        public boolean isNativeEqual()
            {
            return false;
            }
        }

    public enum Mutability {Constant, Persistent, Fixed, Mutable}

    // array of constructors
    private static MethodConstant[] CONSTRUCTORS = new MethodConstant[4];
    private static MethodStructure  ITERABLE_TO_ARRAY;

    protected static final String[] ELEMENT_TYPE = new String[] {"Element"};
    protected static final String[] ARRAY        = new String[] {"collections.Array!<Element>"};

    private static ClassComposition s_clzStringArray;
    private static ClassComposition s_clzObjectArray;
    private static Map<TypeConstant, xArray> ARRAY_TEMPLATES;

    private static MethodStructure CREATE_LIST_SET;
    }
