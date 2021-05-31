package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * The native RTDelegate<Object> implementation.
 */
public class xRTDelegate
        extends ClassTemplate
        implements IndexSupport
    {
    public static xRTDelegate INSTANCE;

    public xRTDelegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
            registerNativeTemplate(new xRTIntDelegate(f_templates, f_struct, true));
            registerNativeTemplate(new xRTCharDelegate(f_templates, f_struct, true));
            registerNativeTemplate(new xRTBooleanDelegate(f_templates, f_struct, true));
            registerNativeTemplate(new xRTBitDelegate(f_templates, f_struct, true));
            registerNativeTemplate(new xRTByteDelegate(f_templates, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        // register native delegations
        ConstantPool                   pool         = pool();
        Map<TypeConstant, xRTDelegate> mapDelegates = new HashMap<>();

        mapDelegates.put(pool.typeInt(),     xRTIntDelegate.INSTANCE);
        mapDelegates.put(pool.typeChar(),    xRTCharDelegate.INSTANCE);
        mapDelegates.put(pool.typeBoolean(), xRTBooleanDelegate.INSTANCE);
        mapDelegates.put(pool.typeBit(),     xRTBitDelegate.INSTANCE);
        mapDelegates.put(pool.typeByte(),    xRTByteDelegate.INSTANCE);

        DELEGATES = mapDelegates;

        // mark native properties and methods
        markNativeProperty("capacity");
        markNativeProperty("mutability");
        markNativeProperty("size");

        markNativeMethod("getElement", INT, ELEMENT_TYPE);
        markNativeMethod("setElement", new String[] {"numbers.Int64", "Element"}, VOID);
        markNativeMethod("elementAt", INT, new String[] {"reflect.Var<Element>"});
        markNativeMethod("insert", null, THIS);
        markNativeMethod("delete", INT, THIS);
        markNativeMethod("reify", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public TypeComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... atypeParams)
        {
        assert atypeParams.length == 1;

        xRTDelegate template = DELEGATES.get(atypeParams[0]);

        return template == null
                ? super.ensureParameterizedClass(pool, atypeParams)
                : template.getCanonicalClass();
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return getArrayTemplate(type.getParamType(0));
        }

    /**
     * Create a delegate for the specified type and content.
     *
     * @param typeElement  the element type
     * @param ahContent    the array elements
     * @param mutability   the desired mutability
     *
     * @return the delegate handle
     */
    public DelegateHandle createDelegate(TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        TypeComposition clzDelegate =
                ensureParameterizedClass(typeElement.getConstantPool(), typeElement);

        int            cSize  = ahContent.length;
        ObjectHandle[] ahValue;
        if (cCapacity > cSize)
            {
            ahValue = new ObjectHandle[cCapacity];
            System.arraycopy(ahContent, 0, ahValue, 0, cSize);
            }
        else
            {
            ahValue = mutability == Mutability.Constant
                ? ahContent
                : ahContent.clone();
            }
        return new GenericArrayDelegate(clzDelegate, ahValue, cSize, mutability);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;

        switch (sPropName)
            {
            case "capacity":
                return getPropertyCapacity(frame, hTarget, iReturn);

            case "mutability":
                return Utils.assignInitializedEnum(frame,
                    xArray.MUTABILITY.getEnumByOrdinal(hDelegate.getMutability().ordinal()), iReturn);

            case "size":
                return getPropertySize(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;

        switch (sPropName)
            {
            case "capacity":
                return setPropertyCapacity(frame, hTarget, ((JavaLong) hValue).getValue());

            case "mutability":
                {
                Mutability mutability = Mutability.values()[((EnumHandle) hValue).getOrdinal()];
                if (mutability.compareTo(hDelegate.getMutability()) > 0)
                    {
                    return frame.raiseException(
                        xException.illegalState(frame, hDelegate.getMutability().toString()));
                    }
                hDelegate.setMutability(mutability);
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
            case "delete":
                return invokeDeleteElement(frame, hTarget, hArg, iReturn);

            case "elementAt": // Var<Element> elementAt(Int index);
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "getElement":
                return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

            case "reify": // Array reify(Mutability? mutability = Null)
                {
                DelegateHandle hDelegate  = (DelegateHandle) hTarget;
                Mutability     mutability = hArg == ObjectHandle.DEFAULT
                        ? hDelegate.getMutability()
                        : Mutability.values()[((EnumHandle) hArg).getOrdinal()];
                return frame.assignValue(iReturn, createCopy(hDelegate, mutability));
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
            case "insert":
                return invokeInsertElement(frame, hTarget, (JavaLong) ahArg[0], ahArg[1], iReturn);

            case "setElement":
                return assignArrayValue(frame, hTarget, ((JavaLong) ahArg[0]).getValue(), ahArg[1]);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        GenericArrayDelegate h1 = (GenericArrayDelegate) hValue1;
        GenericArrayDelegate h2 = (GenericArrayDelegate) hValue2;

        ObjectHandle[] ah1 = h1.m_ahValue;
        ObjectHandle[] ah2 = h2.m_ahValue;

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
        GenericArrayDelegate h1 = (GenericArrayDelegate) hValue1;
        GenericArrayDelegate h2 = (GenericArrayDelegate) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        if (h1.getMutability() != h2.getMutability() || h1.m_cSize != h2.m_cSize)
            {
            return false;
            }

        ObjectHandle[] ah1 = h1.m_ahValue;
        ObjectHandle[] ah2 = h2.m_ahValue;

        if (ah1 == ah2)
            {
            return true;
            }

        for (int i = 0, c = (int) h1.m_cSize; i < c; i++)
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
     * "capacity.get()" implementation.
     */
    protected int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_ahValue.length));
        }

    /**
     * "capacity.set()" implementation.
     */
    protected int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;

        ObjectHandle[] ahOld = hDelegate.m_ahValue;
        int            nSize = (int) hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nCapacityOld = ahOld.length;
        if (nCapacity > nCapacityOld)
            {
            ObjectHandle[] ahNew = new ObjectHandle[(int) nCapacity];
            System.arraycopy(ahOld, 0, ahNew, 0, ahOld.length);
            hDelegate.m_ahValue = ahNew;
            }
        return Op.R_NEXT;
        }

    /**
     * "size.get()" implementation.
     */
    protected int getPropertySize(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_cSize));
        }

    /**
     * "insert(Int, Element)" implementation.
     */
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        DelegateHandle hDelegate  = (DelegateHandle) hTarget;
        Mutability     mutability = null;

        switch (hDelegate.getMutability())
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hDelegate.getMutability();
                hDelegate  = createCopy(hDelegate, Mutability.Mutable);
                break;
            }

        try
            {
            insertElementImpl(hDelegate, hValue, (int) hIndex.getValue());
            }
        catch (ClassCastException e)
            {
            return frame.raiseException(
                xException.illegalCast(frame, hValue.getType().getValueString()));
            }

        if (mutability != null)
            {
            hDelegate.setMutability(mutability);
            }

        return frame.assignValue(iReturn, hDelegate);
        }

    /**
     * "delete(Int index)" implementation.
     */
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;
        long           lIndex    = ((JavaLong) hValue).getValue();

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        Mutability mutability = null;
        switch (hDelegate.getMutability())
            {
            case Fixed:
                return frame.raiseException(xException.readOnly(frame));

            case Constant:
            case Persistent:
                mutability = hDelegate.getMutability();
                hDelegate  = createCopy(hDelegate, Mutability.Mutable);
                break;
            }

        deleteElementImpl(hDelegate, lIndex);

        if (mutability != null)
            {
            hDelegate.setMutability(mutability);
            }

        return frame.assignValue(iReturn, hDelegate);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    /**
     * Slice the content by creating a slicing delegate.
     */
    public DelegateHandle slice(DelegateHandle hTarget, long ofStart, long cSize, boolean fReverse)
        {
        return ofStart == 0 && cSize == hTarget.m_cSize && !fReverse
            ? hTarget
            : xRTSlicingDelegate.INSTANCE.makeHandle(hTarget, ofStart, cSize, fReverse);
        }

    /**
     * Fill the array content with the specified value.
     *
     * @param hTarget  the delegate handle
     * @param cSize    the number of elements to fill
     * @param hValue   the value
     */
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;

        Arrays.fill(hDelegate.m_ahValue, 0, cSize, hValue);
        hDelegate.m_cSize = cSize;
        }

    /**
     * Create a copy of the specified array for the specified mutability
     *
     * @param hTarget     the delegate handle
     * @param mutability  the mutability
     *
     * @return a new array delegate
     */
    protected DelegateHandle createCopy(DelegateHandle hTarget, Mutability mutability)
        {
        return createCopyImpl(hTarget, mutability, 0, hTarget.m_cSize, false);
        }


    // ----- IndexSupport methods ------------------------------------------------------------------

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;

        return lIndex < 0 || lIndex >= hDelegate.m_cSize
                ? frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize))
                : extractArrayValueImpl(frame, hDelegate, lIndex, iReturn);
        }

    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        DelegateHandle hDelegate = (DelegateHandle) hTarget;
        long           cSize     = hDelegate.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            // an array can only grow without any "holes"
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cSize));
            }

        switch (hDelegate.getMutability())
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case Fixed:
                if (lIndex < cSize)
                    {
                    break;
                    }
                // break through
            case Persistent:
                return frame.raiseException(xException.readOnly(frame));
            }

        return assignArrayValueImpl(frame, hDelegate, lIndex, hValue);
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
        {
        return hTarget.getType().getParamType(0);
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        return ((DelegateHandle) hTarget).m_cSize;
        }


    // ----- RTDelegate subclassing support --------------------------------------------------------

    /**
     * Create a copy of the specified array delegate for the specified mutability.
     *
     * @param hTarget     the delegate handle
     * @param mutability  the mutability
     * @param ofStart     the starting index
     * @param cSize       the count
     * @param fReverse    if true, reverseBits the resulting array
     *
     * @return a new array delegate
     */
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;

        if (ofStart == 0 && cSize == hDelegate.m_cSize && cSize == hDelegate.m_ahValue.length
                && mutability == hDelegate.getMutability() && mutability == Mutability.Constant
                && !fReverse)
            {
            // there is absolutely no reason to create a copy
            return hDelegate;
            }

        ObjectHandle[] ahValue = Arrays.copyOfRange(hDelegate.m_ahValue, (int) ofStart, (int) cSize);
        if (fReverse)
            {
            ahValue = reverse(ahValue, (int) cSize);
            }
        return new GenericArrayDelegate(hDelegate.getComposition(), ahValue, mutability);
        }

    /**
     * Storage-specific implementation of {@link #extractArrayValue}.
     */
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        return frame.assignValue(iReturn, ((GenericArrayDelegate) hTarget).m_ahValue[(int) lIndex]);
        }

    /**
     * Storage-specific implementation of {@link #assignArrayValue}.
     */
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  cSize     = (int) hDelegate.m_cSize;

        if (lIndex == cSize)
            {
            if (cSize == ahValue.length)
                {
                ahValue = hDelegate.m_ahValue = grow(ahValue, cSize + 1);
                }
            hDelegate.m_cSize++;
            }

        ahValue[(int) lIndex] = hValue;
        return Op.R_NEXT;
        }

    /**
     * Storage-specific implementation of {@link #invokeInsertElement}.
     */
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;
        int                  cSize     = (int) hDelegate.m_cSize;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  nIndex    = (int) lIndex;

        if (cSize == ahValue.length)
            {
            ahValue = hDelegate.m_ahValue = grow(ahValue, (int) cSize + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex == cSize)
            {
            // append
            ahValue[cSize] = hElement;
            }
        else
            {
            // insert
            System.arraycopy(ahValue, nIndex, ahValue, nIndex +1, cSize - nIndex);
            ahValue[nIndex] = hElement;
            }
        }

    /**
     * Storage-specific implementation of {@link #invokeDeleteElement}.
     */
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        GenericArrayDelegate hDelegate = (GenericArrayDelegate) hTarget;
        int                  cSize     = (int) hDelegate.m_cSize;
        ObjectHandle[]       ahValue   = hDelegate.m_ahValue;
        int                  nIndex    = (int) lIndex;

        if (nIndex < cSize - 1)
            {
            System.arraycopy(ahValue, nIndex + 1, ahValue, nIndex, cSize - nIndex - 1);
            }
        ahValue[(int) --hDelegate.m_cSize] = null;
        }


    // ----- helper methods ------------------------------------------------------------------------

    public static xRTDelegate getArrayTemplate(TypeConstant typeParam)
        {
        xRTDelegate template = DELEGATES.get(typeParam);
        return template == null ? INSTANCE : template;
        }

    private static ObjectHandle[] reverse(ObjectHandle[] ahValue, int cSize)
        {
        ObjectHandle[] ahValueR = new ObjectHandle[cSize];
        for (int i = 0; i < cSize; i++)
            {
            ahValueR[i] = ahValue[cSize - 1 - i];
            }
        return ahValueR;
        }

    private static ObjectHandle[] grow(ObjectHandle[] ahValue, int cSize)
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
    protected static int calculateCapacity(int cOld, int cDesired)
        {
        assert cDesired > cOld;

        // resize (TODO: we should be much smarter here)
        return cDesired + Math.max(cDesired >> 2, 16);
        }

    // ----- helper classes ------------------------------------------------------------------------

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
            implements Frame.Continuation
        {
        final private ObjectHandle[] ah1;
        final private ObjectHandle[] ah2;
        final private TypeConstant   typeEl;
        final private int            cElements;
        final private int[]          holder;
        final private int            iReturn;

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
     * Generic array delegate.
     */
    public static class GenericArrayDelegate
            extends DelegateHandle
        {
        protected ObjectHandle[] m_ahValue;

        /**
         * Construct an array with specified content and mutability.
         */
        protected GenericArrayDelegate(TypeComposition clazz, ObjectHandle[] ahValue,
                                       Mutability mutability)
            {
            this(clazz, ahValue, ahValue.length, mutability);
            }

        /**
         * Construct an array with specified content, capacity and mutability.
         */
        protected GenericArrayDelegate(TypeComposition clzArray, ObjectHandle[] ahValue,
                                    int cSize, Mutability mutability)
            {
            super(clzArray, mutability);

            m_ahValue = ahValue;
            m_cSize   = cSize;
            }

        @Override
        protected boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            // despite the shared array type, the individual elements could be narrower
            // and need to be checked
            if (mapVisited == null)
                {
                mapVisited = new IdentityHashMap<>();
                }

            return mapVisited.put(this, Boolean.TRUE) != null ||
                   areShared(m_ahValue, poolThat, mapVisited);
            }

        @Override
        public boolean isNativeEqual()
            {
            return false;
            }
        }

    /**
     * Abstract array delegate handle.
     */
    public abstract static class DelegateHandle
            extends ObjectHandle
        {
        public  long       m_cSize;
        private Mutability m_mutability;

        protected DelegateHandle(TypeComposition clazz, Mutability mutability)
            {
            super(clazz);

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
            }

        @Override
        public void makeImmutable()
            {
            super.makeImmutable();

            setMutability(Mutability.Constant);
            }

        @Override
        public String toString()
            {
            return getClass().getSimpleName() + " " + m_mutability + ", size=" + m_cSize;
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    protected static final String[] ELEMENT_TYPE = new String[] {"Element"};

    private static Map<TypeConstant, xRTDelegate> DELEGATES;
    }
