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
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
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
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native generic Array implementation.
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport
    {
    public static xArray   INSTANCE;
    public static xEnum    MUTABILITY;
    public enum Mutability {Mutable, FixedSize, Persistent, Constant}

    public xArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // register array specializations
        ConstantPool              pool         = pool();
        Map<TypeConstant, xArray> mapTemplates = new HashMap<>();

        registerNative(new xIntArray(f_templates, f_struct, true));
        registerNative(new xCharArray(f_templates, f_struct, true));
        registerNative(new xBooleanArray(f_templates, f_struct, true));
        registerNative(new xBitArray(f_templates, f_struct, true));
        registerNative(new xByteArray(f_templates, f_struct, true));

        mapTemplates.put(pool.typeInt(), xIntArray.INSTANCE);
        mapTemplates.put(pool.typeByte(), xByteArray.INSTANCE);
        mapTemplates.put(pool.typeChar(), xCharArray.INSTANCE);
        mapTemplates.put(pool.typeBoolean(), xBooleanArray.INSTANCE);
        mapTemplates.put(pool.ensureEcstasyTypeConstant("Bit"), xBitArray.INSTANCE);

        ARRAY_TEMPLATES = mapTemplates;

        // cache the constructors
        for (MethodStructure method :
                ((MultiMethodStructure) f_struct.getChild("construct")).methods())
            {
            TypeConstant typeParam0 = method.getParam(0).getType();

            if (method.getParamCount() == 1)
                {
                if (typeParam0.equals(pool.typeInt()))
                    {
                    // 0) construct(Int capacity = 0)
                    CONSTRUCTORS[0] = method;
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
                    CONSTRUCTORS[1] = method;
                    }
                else if (typeParam0.isA(pool.typeArray()))
                    {
                    CONSTRUCTORS[3] = method;
                    }
                else
                    {
                    CONSTRUCTORS[2] = method;
                    }
                }
            }

        // cache "Iterable.toArray()" method
        ITERABLE_TO_ARRAY = f_templates.getClassStructure("Iterable").findMethod("toArray", 1);

        // cache Mutability template
        MUTABILITY = (xEnum) f_templates.getTemplate("collections.VariablyMutable.Mutability");

        // mark native properties and methods
        markNativeProperty("capacity");
        markNativeProperty("mutability");
        markNativeProperty("size");

        markNativeMethod("getElement", INT, ELEMENT_TYPE);
        markNativeMethod("setElement", new String[] {"Int64", "Element"}, VOID);
        markNativeMethod("elementAt", INT, new String[] {"Var<Element>"});
        markNativeMethod("add", ELEMENT_TYPE, ARRAY);
        markNativeMethod("addAll", new String[] {"Iterable<Element>"}, ARRAY);
        markNativeMethod("slice", new String[] {"Interval<Int64>"}, ARRAY);
        markNativeMethod("ensureImmutable", BOOLEAN, null);
        markNativeMethod("ensurePersistent", BOOLEAN, null);

        getCanonicalType().invalidateTypeInfo();
        }

    private void registerNative(xArray template)
        {
        template.initDeclared();
        f_templates.registerNativeTemplate(template.getCanonicalType(), template);
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

        assert constArray.getFormat() == Constant.Format.Array;

        TypeConstant typeArray = constArray.getType();
        Constant[]   aconst    = constArray.getValue();
        int cSize = aconst.length;

        ObjectHandle[] ahValue   = new ObjectHandle[cSize];
        boolean        fDeferred = false;
        for (int i = 0; i < cSize; i++)
            {
            ObjectHandle hValue = frame.getConstHandle(aconst[i]);

            if (hValue instanceof DeferredCallHandle)
                {
                fDeferred = true;
                }
            ahValue[i] = hValue;
            }

        ClassComposition clzArray = f_templates.resolveClass(typeArray);
        ObjectHandle     hResult  = fDeferred
            ? new DeferredArrayHandle(clzArray, ahValue)
            : ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahValue);

        frame.pushStack(hResult);
        return Op.R_NEXT;
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
        int nScenario;
        for (nScenario = 0; nScenario < 4; nScenario++)
            {
            if (CONSTRUCTORS[nScenario] == constructor)
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
                ArrayHandle hArray   = template.createArrayHandle(clzArray, (int) cCapacity, Mutability.FixedSize);

                int cSize = (int) cCapacity;
                if (cSize > 0)
                    {
                    ObjectHandle hSupplier = ahVar[1];
                    // we could get here either naturally (e.g. new Array<String>(7, "");)
                    // or via the ArrayExpression (e.g. new Int[7])
                    if (hSupplier == ObjectHandle.DEFAULT)
                        {
                        TypeConstant typeEl = clzArray.getType().getParamTypesArray()[0];
                        ObjectHandle hValue = frame.getConstHandle(typeEl.getDefaultValue());
                        fill(hArray, cSize, hValue);
                        }
                    else if (hSupplier.getType().isA(clzArray.getType().getParamTypesArray()[0]))
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
                ObjectHandle   hSequence  = ahVar[1];
                ObjectHandle[] ahVars     = new ObjectHandle[ITERABLE_TO_ARRAY.getMaxVars()];
                ahVars[0] = hMutability;

                return frame.call1(ITERABLE_TO_ARRAY, hSequence, ahVars, iReturn);
                }

            case 3: // construct(Array<Element> array, Interval<Int> section)
                {
                ArrayHandle   hArray    = (ArrayHandle) ahVar[0];
                GenericHandle hInterval = (GenericHandle) ahVar[1];
                JavaLong      hLower    = (JavaLong) hInterval.getField("lowerBound");
                JavaLong      hUpper    = (JavaLong) hInterval.getField("upperBound");
                boolean       fReverse  = ((BooleanHandle) hInterval.getField("reversed")).get();

                long lLower = hLower.getValue();
                long lUpper = hUpper.getValue();

                return lLower <= lUpper
                    ? slice(frame, hArray, lLower, lUpper, fReverse, iReturn)
                    : slice(frame, hArray, lUpper, lLower, fReverse, iReturn);
                }

            default:
                throw new IllegalStateException();
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
        return hArg.getType().isA(hTarget.getType().getParamTypesArray()[0])
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
                {
                EnumHandle hEnum = MUTABILITY.getEnumByOrdinal(hArray.m_mutability.ordinal());
                return frame.assignValue(iReturn, Utils.ensureInitializedEnum(frame, hEnum));
                }
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
            case "mutability":
                hArray.m_mutability = Mutability.values()[((EnumHandle) hValue).getValue()];
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

            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "getElement":
                return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

            case "slice":
                {
                GenericHandle hInterval = (GenericHandle) hArg;
                long    ixFrom   = ((JavaLong) hInterval.getField("lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField("upperBound")).getValue();
                boolean fReverse = ((BooleanHandle) hInterval.getField("reversed")).get();

                return slice(frame, hTarget, ixFrom, ixTo, fReverse, iReturn);
                }

            case "ensureImmutable": // immutable Array ensureImmutable(Boolean inPlace = False)
                {
                ArrayHandle   hArray   = (ArrayHandle) hTarget;
                BooleanHandle hInPlace = hArg == ObjectHandle.DEFAULT
                        ? xBoolean.FALSE
                        : (BooleanHandle) hArg;
                if (hInPlace.get())
                    {
                    hArray.makeImmutable();
                    }
                else
                    {
                    hArray = createCopy(hArray, Mutability.Constant);
                    }
                return frame.assignValue(iReturn, hArray);
                }

            case "ensurePersistent": // Array ensurePersistent(Boolean inPlace = False)
                {
                ArrayHandle   hArray   = (ArrayHandle) hTarget;
                BooleanHandle hInPlace = hArg == ObjectHandle.DEFAULT
                        ? xBoolean.FALSE
                        : (BooleanHandle) hArg;
                if (hInPlace.get())
                    {
                    hArray.m_mutability = Mutability.Persistent;
                    }
                else
                    {
                    hArray = createCopy(hArray, Mutability.Persistent);
                    }
                return frame.assignValue(iReturn, hArray);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
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
        TypeConstant typeEl = clazz.getType().getParamTypesArray()[0];

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
     * addElement(TypeElement) implementation
     */
    protected int addElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;
        int                ixNext = hArray.m_cSize;

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case FixedSize:
                return frame.raiseException(xException.readOnly(frame));

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        ObjectHandle[] ahValue = hArray.m_ahValue;
        if (ixNext == ahValue.length)
            {
            ahValue = hArray.m_ahValue = grow(ahValue, ixNext + 1);
            }
        hArray.m_cSize++;

        ahValue[ixNext] = hValue;
        return frame.assignValue(iReturn, hArray); // return this
        }

    /**
     * addElements(Element[]) implementation
     */
    protected int addElements(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case FixedSize:
                return frame.raiseException(xException.readOnly(frame));

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        GenericArrayHandle hArrayAdd = (GenericArrayHandle) hValue;

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
        return frame.assignValue(iReturn, hArray); // return this
        }

    /**
     * slice(Interval<Int>) implementation
     */
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, boolean fReverse, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        ObjectHandle[] ahValue = hArray.m_ahValue;
        try
            {
            ObjectHandle[] ahNew;
            if (fReverse)
                {
                int cNew = (int) (ixTo - ixFrom + 1);

                ahNew = new ObjectHandle[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    ahNew[i] = ahValue[(int) ixTo - i];
                    }
                }
            else
                {
                ahNew = Arrays.copyOfRange(ahValue, (int) ixFrom, (int) ixTo + 1);
                }

            ArrayHandle hArrayNew = new GenericArrayHandle(
                hArray.getComposition(), ahNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = ahValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
            }
        }


    // ----- IndexSupport methods -----

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
                if (hArray.m_mutability == Mutability.FixedSize)
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

    // ----- helper methods -----

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

    // ----- helper classes -----

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


    // ----- ObjectHandle helpers -----

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
        public boolean isNativeEqual()
            {
            return false;
            }
        }

    // array of constructors
    private static MethodStructure[] CONSTRUCTORS = new MethodStructure[4];
    private static MethodStructure ITERABLE_TO_ARRAY;

    protected static final String[] ELEMENT_TYPE = new String[] {"Element"};
    protected static final String[] ARRAY        = new String[] {"collections.Array!<Element>"};

    private static ClassComposition s_clzStringArray;
    private static ClassComposition s_clzObjectArray;
    private static Map<TypeConstant, xArray> ARRAY_TEMPLATES;
    }
