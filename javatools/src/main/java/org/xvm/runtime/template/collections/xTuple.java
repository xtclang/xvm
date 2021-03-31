package org.xvm.runtime.template.collections;


import java.util.Arrays;

import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

/**
 * Native Tuple implementation.
 */
public class xTuple
        extends ClassTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;
    public static ClassConstant INCEPTION_CLASS;
    public static TupleHandle H_VOID;
    public static xEnum MUTABILITY;

    public xTuple(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void initNative()
        {
        H_VOID = new TupleHandle(getCanonicalClass(), Utils.OBJECTS_NONE, Mutability.Constant);

        // cache Mutability template
        MUTABILITY = (xEnum) f_templates.getTemplate("collections.Tuple.Mutability");

        // Note: all interface properties are implicitly native due to "NativeRebase"

        markNativeMethod("add", null, null);
        markNativeMethod("addAll", null, null);
        markNativeMethod("elementAt", INT, null);
        markNativeMethod("ensureMutability", null, null);
        markNativeMethod("freeze", BOOLEAN, null);
        markNativeMethod("getElement", INT, null);
        markNativeMethod("remove", new String[] {"numbers.Int64"}, null);
        markNativeMethod("removeAll", new String[] {"Range<numbers.Int64>"}, null);
        markNativeMethod("setElement", null, VOID);
        markNativeMethod("slice", new String[] {"Range<numbers.Int64>"}, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeComposition ensureClass(TypeConstant typeActual)
        {
        return typeActual.getParamsCount() == 0
            ? super.ensureClass(typeActual)
            : getCanonicalClass().ensureCanonicalizedComposition(typeActual);
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return INCEPTION_CLASS;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constTuple = (ArrayConstant) constant;

        assert constTuple.getFormat() == Constant.Format.Tuple;

        Constant[] aconst = constTuple.getValue();
        int c = aconst.length;

        if (c == 0)
            {
            return frame.pushStack(H_VOID);
            }

        TypeConstant typeTuple = constTuple.getType().resolveGenerics(
            frame.poolContext(), frame.getGenericsResolver());

        ObjectHandle[] ahValue   = new ObjectHandle[c];
        boolean        fDeferred = false;
        for (int i = 0; i < c; i++)
            {
            ObjectHandle hValue = frame.getConstHandle(aconst[i]);

            if (Op.isDeferred(hValue))
                {
                fDeferred = true;
                }
            ahValue[i] = hValue;
            }

        TypeComposition clzTuple = ensureClass(typeTuple);
        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                    frameCaller.pushStack(makeImmutableHandle(clzTuple, ahValue));
            return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

        return frame.pushStack(makeImmutableHandle(clzTuple, ahValue));
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hSequence = ahVar[0];
        IndexSupport support   = (IndexSupport) hSequence.getOpSupport();

        try
            {
            ObjectHandle[] ahValue = support.toArray(frame, hSequence);

            return frame.assignValue(iReturn,
                    new TupleHandle(clazz, ahValue, Mutability.Fixed));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        switch (sPropName)
            {
            case "mutability":
                return Utils.assignInitializedEnum(frame,
                    MUTABILITY.getEnumByOrdinal(hTuple.m_mutability.ordinal()), iReturn);

            case "size":
                return frame.assignValue(iReturn, xInt64.makeHandle(hTuple.m_ahValue.length));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "addAll": // Tuple!<> addAll(Tuple!<> that);
                {
                TupleHandle hTuple = (TupleHandle) hTarget;
                TupleHandle hThat  = (TupleHandle) hArg;

                return addAll(frame, hTuple, hThat, iReturn);
                }

            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "freeze": // immutable Tuple freeze(Boolean inPlace = False)
                {
                TupleHandle hTuple   = (TupleHandle) hTarget;
                boolean     fInPlace = hArg != ObjectHandle.DEFAULT && ((BooleanHandle) hArg).get();
                return freeze(frame, hTuple, fInPlace, iReturn);
                }

            case "getElement":
                return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

            case "remove":
            case "removeAll":
                // TODO
                throw new UnsupportedOperationException();

            case "slice":
                {
                GenericHandle hInterval = (GenericHandle) hArg;

                long    ixFrom   = ((JavaLong) hInterval.getField("lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField("upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hInterval.getField("lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hInterval.getField("upperExclusive")).get();
                boolean fReverse = ((BooleanHandle) hInterval.getField("descending")).get();
                return slice(frame, (TupleHandle) hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "add": // <Element> Tuple!<> add(Element value);
                {
                TupleHandle  hTuple = (TupleHandle) hTarget;
                TypeHandle   hType  = (TypeHandle) ahArg[0];
                ObjectHandle hValue = ahArg[1];

                return add(frame, hTuple, hType, hValue, iReturn);
                }

            case "ensureMutability": // Tuple ensureMutability(Tuple.Mutability mutability, Boolean inPlace = false);
                {
                TupleHandle  hTuple       = (TupleHandle) hTarget;
                EnumHandle   hMutability  = (EnumHandle) ahArg[0];
                ObjectHandle hInPlace     = ahArg[1];
                boolean      fInPlace     = hInPlace != ObjectHandle.DEFAULT &&
                        ((BooleanHandle) hInPlace).get();

                switch (Mutability.values()[hMutability.getOrdinal()])
                    {
                    case Constant:
                        return freeze(frame, hTuple, fInPlace, iReturn);

                    case Persistent:
                        return ensurePersistent(frame, hTuple, fInPlace, iReturn);

                    case Fixed:
                        return ensureFixedSize(frame, hTuple, fInPlace, iReturn);

                    default:
                        throw new IllegalStateException();
                    }
                }

            case "replace":
                // TODO
                throw new UnsupportedOperationException();

            case "setElement":
                return assignArrayValue(frame, hTarget, ((JavaLong) ahArg[0]).getValue(), ahArg[1]);

            default:
                return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
            }
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        TupleHandle hTuple1 = (TupleHandle) hValue1;
        TupleHandle hTuple2 = (TupleHandle) hValue2;

        if (hTuple1.isMutable() || hTuple2.isMutable())
            {
            return false;
            }

        ObjectHandle[] ah1 = hTuple1.m_ahValue;
        ObjectHandle[] ah2 = hTuple2.m_ahValue;

        if (ah1 == ah2)
            {
            return true;
            }

        if (ah1.length != ah2.length)
            {
            return false;
            }

        for (int i = 0, c = ah1.length; i < c; i++)
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
     * addAll(Tuple! that) implementation
     */
    protected int add(Frame frame, TupleHandle hThis, TypeHandle hType, ObjectHandle hValue, int iReturn)
        {
        ObjectHandle[] ahValue = hThis.m_ahValue;
        int            cValues = ahValue.length;

        TypeConstant[] atype     = hThis.getType().getParamTypesArray();
        int            cTypes    = atype.length;
        int            cNew      = cValues + 1;

        ObjectHandle[] ahNew = new ObjectHandle[cNew];
        System.arraycopy(ahValue, 0, ahNew, 0, cValues);
        ahNew[cValues] = hValue;

        TypeConstant[] atypeNew;
        if (cTypes != cValues)
            {
            // it shouldn't happen, but not a place to report an error;
            // simply ignore the types
            atypeNew = TypeConstant.NO_TYPES;
            }
        else
            {
            atypeNew = new TypeConstant[cNew];
            System.arraycopy(atype, 0, atypeNew, 0, cTypes);
            atypeNew[cTypes] = hType.getDataType();
            }

        TypeConstant    typeTupleNew = pool().ensureTupleType(atypeNew);
        TypeComposition clzTupleNew  = ensureClass(typeTupleNew);
        TupleHandle     hTupleNew    = new TupleHandle(clzTupleNew, ahNew, hThis.m_mutability);

        return frame.assignValue(iReturn, hTupleNew);
        }

    /**
     * addAll(Tuple! that) implementation
     */
    protected int addAll(Frame frame, TupleHandle hThis, TupleHandle hThat, int iReturn)
        {
        ObjectHandle[] ahValue = hThis.m_ahValue;
        int            cValues = ahValue.length;

        if (cValues == 0)
            {
            return frame.assignValue(iReturn, hThat);
            }

        ObjectHandle[] ahValueAdd = hThat.m_ahValue;
        int            cValuesAdd = ahValueAdd.length;
        if (cValuesAdd == 0)
            {
            return frame.assignValue(iReturn, hThis);
            }

        TypeConstant[] atype     = hThis.getType().getParamTypesArray();
        int            cTypes    = atype.length;
        TypeConstant[] atypeAdd  = hThat.getType().getParamTypesArray();
        int            cTypesAdd = atypeAdd.length;
        int            cNew      = cValues + cValuesAdd;

        ObjectHandle[] ahNew = new ObjectHandle[cNew];
        System.arraycopy(ahValue, 0, ahNew, 0, cValues);
        System.arraycopy(ahValueAdd, 0, ahNew, cValues, cValuesAdd);

        TypeConstant[] atypeNew;
        if (cTypes != cValues || cTypesAdd != cValuesAdd)
            {
            // it shouldn't happen, but not a place to report an error;
            // simply ignore the types
            atypeNew = TypeConstant.NO_TYPES;
            }
        else
            {
            atypeNew = new TypeConstant[cNew];
            System.arraycopy(atype, 0, atypeNew, 0, cTypes);
            System.arraycopy(atypeAdd, 0, atypeNew, cTypes, cTypesAdd);
            }

        TypeConstant    typeTupleNew = pool().ensureTupleType(atypeNew);
        TypeComposition clzTupleNew  = ensureClass(typeTupleNew);
        TupleHandle     hTupleNew    = new TupleHandle(clzTupleNew, ahNew, hThis.m_mutability);

        return frame.assignValue(iReturn, hTupleNew);
        }

    /**
     * immutable Tuple freeze(Boolean inPlace = False) implementation
     */
    protected int freeze(Frame frame, TupleHandle hTuple, boolean fInPlace, int iReturn)
        {
        switch (hTuple.m_mutability)
            {
            case Constant:
                return frame.assignValue(iReturn, hTuple);

            case Fixed:
                // TODO: ensure all elements are immutable or Freezable
                if (fInPlace)
                    {
                    hTuple.makeImmutable();
                    return frame.assignValue(iReturn, hTuple);
                    }
                return frame.assignValue(iReturn,
                    new TupleHandle(hTuple.getComposition(),
                        hTuple.m_ahValue.clone(), Mutability.Constant));

            case Persistent:
                if (fInPlace)
                    {
                    hTuple.m_mutability = Mutability.Fixed;
                    return frame.assignValue(iReturn, hTuple);
                    }
                return frame.assignValue(iReturn,
                    new TupleHandle(hTuple.getComposition(),
                        hTuple.m_ahValue.clone(), Mutability.Fixed));

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * ensureFixedSize(Boolean inPlace = false) implementation
     */
    protected int ensureFixedSize(Frame frame, TupleHandle hTuple, boolean fInPlace, int iReturn)
        {
        switch (hTuple.m_mutability)
            {
            case Constant:
                return fInPlace
                    ? frame.raiseException(xException.immutableObject(frame))
                    : frame.assignValue(iReturn,
                        new TupleHandle(hTuple.getComposition(),
                            hTuple.m_ahValue.clone(), Mutability.Fixed));

            case Fixed:
                return frame.assignValue(iReturn, hTuple);

            case Persistent:
                if (fInPlace)
                    {
                    hTuple.m_mutability = Mutability.Fixed;
                    return frame.assignValue(iReturn, hTuple);
                    }
                return frame.assignValue(iReturn,
                        new TupleHandle(hTuple.getComposition(),
                            hTuple.m_ahValue.clone(), Mutability.Fixed));

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Tuple ensurePersistent(Boolean inPlace = False) implementation
     */
    protected int ensurePersistent(Frame frame, TupleHandle hTuple, boolean fInPlace, int iReturn)
        {
        switch (hTuple.m_mutability)
            {
            case Constant:
                return fInPlace
                    ? frame.raiseException(xException.immutableObject(frame))
                    : frame.assignValue(iReturn,
                        new TupleHandle(hTuple.getComposition(),
                            hTuple.m_ahValue.clone(), Mutability.Persistent));

            case Fixed:
                if (fInPlace)
                    {
                    hTuple.m_mutability = Mutability.Fixed;
                    return frame.assignValue(iReturn, hTuple);
                    }
                return frame.assignValue(iReturn,
                        new TupleHandle(hTuple.getComposition(),
                            hTuple.m_ahValue.clone(), Mutability.Persistent));

            case Persistent:
                return frame.assignValue(iReturn, hTuple);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * slice(Interval<Int>) implementation
     */
    protected int slice(Frame   frame,    TupleHandle hTuple,
                        long    ixLower,  boolean     fExLower,
                        long    ixUpper,  boolean     fExUpper,
                        boolean fReverse,
                        int     iReturn)
        {
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

        ObjectHandle[] ahValue = hTuple.m_ahValue;
        TypeConstant[] atype   = hTuple.getType().getParamTypesArray();
        int            cTypes  = atype.length;

        if (cTypes > 0 && cTypes < ahValue.length)
            {
            // it shouldn't happen, but not a place to report an error;
            // simply ignore the types
            atype  = TypeConstant.NO_TYPES;
            }

        try
            {
            ObjectHandle[] ahNew;
            TypeConstant[] atypeNew;
            if (ixLower >= ixUpper)
                {
                ahNew    = Utils.OBJECTS_NONE;
                atypeNew = TypeConstant.NO_TYPES;
                }
            else if (fReverse)
                {
                int cNew = (int) (ixUpper - ixLower);
                ahNew    = new ObjectHandle[cNew];
                atypeNew = new TypeConstant[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    int iOrig = (int) ixUpper - i - 1;

                    ahNew[i]    = ahValue[iOrig];
                    atypeNew[i] = atype  [iOrig];
                    }
                }
            else
                {
                ahNew    = Arrays.copyOfRange(ahValue, (int) ixLower, (int) ixUpper);
                atypeNew = Arrays.copyOfRange(atype,   (int) ixLower, (int) ixUpper);
                }

            TypeConstant    typeTupleNew = pool().ensureTupleType(atypeNew);
            TypeComposition clzTupleNew  = ensureClass(typeTupleNew);
            TupleHandle     hTupleNew    = new TupleHandle(clzTupleNew, ahNew, hTuple.m_mutability);

            return frame.assignValue(iReturn, hTupleNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = ahValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixLower < 0 || ixLower >= c ? ixLower : ixUpper, c));
            }
        }


    // ----- IndexSupport methods -----

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cElements));
            }

        return frame.assignValue(iReturn, hTuple.m_ahValue[(int) lIndex]);
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cElements));
            }

        if (!hTuple.isMutable())
            {
            return frame.raiseException(xException.immutableObject(frame));
            }

        hTuple.m_ahValue[(int) lIndex] = hValue;
        return Op.R_NEXT;
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
                throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw xException.outOfBounds(frame, lIndex, cElements).getException();
            }

        return hTuple.getType().getParamType((int) lIndex);
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        return hTuple.m_ahValue.length;
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        TupleHandle hTuple1 = (TupleHandle) hValue1;
        TupleHandle hTuple2 = (TupleHandle) hValue2;

        ObjectHandle[] ah1 = hTuple1.m_ahValue;
        ObjectHandle[] ah2 = hTuple2.m_ahValue;

        // compare the tuple sizes first
        int cElements = ah1.length;
        if (cElements != ah2.length)
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        TypeConstant[] atypeCommon = clazz.getType().getParamTypesArray();
        int            cCommon     = atypeCommon.length;

        if (cCommon < cElements)
            {
            TypeConstant[] atype1 = hTuple1.getType().getParamTypesArray();
            TypeConstant[] atype2 = hTuple2.getType().getParamTypesArray();

            if (cCommon == 0)
                {
                atypeCommon = atype1;
                }
            else
                {
                TypeConstant[] atypeC = atype1.clone();
                System.arraycopy(atypeCommon, 0, atypeC, 0, cCommon);
                atypeCommon = atypeC;
                }

            // for the types that were not explicitly specified do a strict check
            for (int i = cCommon; i < cElements; i++)
                {
                if (!atype1[i].equals(atype2[i]))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }
                }
            }

        return new Equals(hTuple1, hTuple2, cElements, atypeCommon, iReturn).doNext(frame);
        }

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
            implements Frame.Continuation
        {
        final private TupleHandle    hTuple1;
        final private TupleHandle    hTuple2;
        final private int            cElements;
        final private TypeConstant[] atype;
        final private int            iReturn;

        private int index = -1;

        public Equals(TupleHandle h1, TupleHandle h2, int cElements, TypeConstant[] aType,
                      int iReturn)
            {
            this.hTuple1   = h1;
            this.hTuple2   = h2;
            this.cElements = cElements;
            this.atype     = aType;
            this.iReturn   = iReturn;
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
            int cTypes = atype.length;
            while (++index < cElements)
                {
                ObjectHandle h1 = hTuple1.m_ahValue[index];
                ObjectHandle h2 = hTuple2.m_ahValue[index];

                int iResult = index < cTypes
                    ? atype[index].callEquals(frameCaller, h1, h2, Op.A_STACK)
                    : xObject.INSTANCE.callEquals(frameCaller, xObject.CLASS, h1, h2, Op.A_STACK);
                switch (iResult)
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
     * Make an immutable Tuple handle.
     *
     * @param typeTuple  the tuple type
     * @param ahValue    the values
     *
     * @return the handle
     */
    public static TupleHandle makeImmutableHandle(TypeConstant typeTuple, ObjectHandle... ahValue)
        {
        return makeImmutableHandle(INSTANCE.ensureClass(typeTuple), ahValue);
        }

    /**
     * Make an immutable Tuple handle.
     *
     * @param clazz     the tuple class composition
     * @param ahValue   the values
     *
     * @return the handle
     */
    public static TupleHandle makeImmutableHandle(TypeComposition clazz, ObjectHandle... ahValue)
        {
        return new TupleHandle(clazz, ahValue, Mutability.Constant);
        }

    /**
     * Make a mutable (fixed size) Tuple handle.
     *
     * @param clazz    the tuple class composition
     * @param ahValue  the values
     *
     * @return the handle
     */
    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle... ahValue)
        {
        return new TupleHandle(clazz, ahValue, Mutability.Fixed);
        }

    public static class TupleHandle
            extends ObjectHandle
        {
        public ObjectHandle[] m_ahValue;
        public Mutability     m_mutability;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue, Mutability mutability)
            {
            super(clazz);

            m_fMutable   = mutability != Mutability.Constant;
            m_ahValue    = ahValue;
            m_mutability = mutability;
            }

        @Override
        public void makeImmutable()
            {
            super.makeImmutable();

            m_mutability = Mutability.Constant;
            }

        @Override
        protected boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            if (mapVisited == null)
                {
                mapVisited = new IdentityHashMap<>();
                }

            return mapVisited.put(this, Boolean.TRUE) != null ||
                   areShared(m_ahValue, poolThat, mapVisited);
            }

        @Override
        public String toString()
            {
            return "Tuple: " + Arrays.toString(m_ahValue);
            }
        }

    public enum Mutability {Constant, Persistent, Fixed}
    }
