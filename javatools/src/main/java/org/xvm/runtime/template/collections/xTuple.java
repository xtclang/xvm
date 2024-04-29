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
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
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

    public xTuple(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

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
        H_VOID = makeImmutableHandle(getCanonicalClass(), Utils.OBJECTS_NONE);

        // Note: all interface methods and properties are implicitly native due to "NativeRebase"
        }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual)
        {
        return typeActual.getParamsCount() == 0
            ? getCanonicalClass()
            : getCanonicalClass(container).ensureCanonicalizedComposition(typeActual);
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

        TypeConstant typeTuple = constTuple.getType();

        typeTuple = typeTuple.resolveGenerics(frame.poolContext(),
                        frame.getGenericsResolver(typeTuple.containsDynamicType()));

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

        TypeComposition clzTuple = ensureClass(frame.f_context.f_container, typeTuple);
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

            return frame.assignValue(iReturn, makeHandle(clazz, ahValue));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public int createProxyHandle(Frame frame, ServiceContext ctxTarget,
                                 ObjectHandle hTarget, TypeConstant typeProxy)
        {
        // this method is called from ServiceContext.validatePassThrough(); see if we can freeze all
        // the tuple values (implementing AutoFreezable(False) contract)
        TupleHandle    hTuple  = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue.clone();

        switch (frame.f_context.validatePassThrough(frame, ctxTarget, null, ahValue))
            {
            case Op.R_NEXT:
                return frame.assignValue(Op.A_STACK, makeHandle(hTuple.getComposition(), ahValue));

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValue(Op.A_STACK, makeHandle(hTuple.getComposition(), ahValue)));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        switch (sPropName)
            {
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

                return invokeAddAll(frame, hTuple, hThat, iReturn);
                }

            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "freeze": // immutable Tuple freeze(Boolean inPlace = False)
                {
                TupleHandle hTuple   = (TupleHandle) hTarget;
                boolean     fInPlace = hArg != ObjectHandle.DEFAULT && ((BooleanHandle) hArg).get();
                return invokeFreeze(frame, hTuple, fInPlace, iReturn);
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

                long    ixFrom   = ((JavaLong) hInterval.getField(frame, "lowerBound")).getValue();
                long    ixTo     = ((JavaLong) hInterval.getField(frame, "upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hInterval.getField(frame, "lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hInterval.getField(frame, "upperExclusive")).get();
                boolean fReverse = ((BooleanHandle) hInterval.getField(frame, "descending")).get();
                return invokeSlice(frame, (TupleHandle) hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
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
            case "add":
                {
                TupleHandle  hTuple = (TupleHandle) hTarget;
                TypeHandle   hType  = (TypeHandle) ahArg[0];
                ObjectHandle hValue = ahArg[1];

                return invokeAdd(frame, hTuple, hType, hValue, iReturn);
                }

            case "replace":
                {
                TupleHandle  hTuple = (TupleHandle) hTarget;
                long         lIndex = ((JavaLong) ahArg[0]).getValue();
                ObjectHandle hValue = ahArg[1];

                return invokeReplace(frame, hTuple, lIndex, hValue, iReturn);
                }

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
     * Native "Element Tuple!<> add(Element value)" implementation.
     */
    protected int invokeAdd(Frame frame, TupleHandle hThis, TypeHandle hType, ObjectHandle hValue, int iReturn)
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

        TypeConstant    typeTupleNew = frame.poolContext().ensureTupleType(atypeNew);
        TypeComposition clzTupleNew  = ensureClass(frame.f_context.f_container, typeTupleNew);
        TupleHandle     hTupleNew    = makeHandle(clzTupleNew, ahNew);

        return frame.assignValue(iReturn, hTupleNew);
        }

    /**
     * Native "Tuple!<> addAll(Tuple! that)" implementation.
     */
    protected int invokeAddAll(Frame frame, TupleHandle hThis, TupleHandle hThat, int iReturn)
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

        TypeConstant    typeTupleNew = frame.poolContext().ensureTupleType(atypeNew);
        TypeComposition clzTupleNew  = ensureClass(frame.f_context.f_container, typeTupleNew);
        TupleHandle     hTupleNew    = makeHandle(clzTupleNew, ahNew);

        return frame.assignValue(iReturn, hTupleNew);
        }

    /**
     * Native "Tuple replace(Int index, Object value)" implementation.
     */
    protected int invokeReplace(Frame frame, TupleHandle hThis, long lIndex, ObjectHandle hValue, int iReturn)
        {
        ObjectHandle[] ahValue = hThis.m_ahValue;
        int            cValues = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cValues)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cValues));
            }

        TypeComposition clzThis = hThis.getComposition();
        if (!hValue.getType().isA(clzThis.getType().getParamType((int) lIndex)))
            {
            return frame.raiseException(
                    xException.typeMismatch(frame, hValue.getType().getValueString()));
            }

        ahValue = ahValue.clone();
        ahValue[(int) lIndex] = hValue;

        return frame.assignValue(iReturn, makeHandle(clzThis, ahValue));
        }

    /**
     * Native "immutable Tuple freeze(Boolean inPlace = False)" implementation.
     */
    protected int invokeFreeze(Frame frame, TupleHandle hTuple, boolean fInPlace, int iReturn)
        {
        if (hTuple.isMutable())
            {
            ObjectHandle[]     ahValue;
            Frame.Continuation stepNext;

            if (fInPlace)
                {
                ahValue  = hTuple.m_ahValue;
                stepNext = frameCaller ->
                    {
                    hTuple.makeImmutable();
                    return frameCaller.assignValue(iReturn, hTuple);
                    };
                }
            else
                {
                ahValue  = hTuple.m_ahValue.clone();
                stepNext = frameCaller -> frameCaller.assignValue(iReturn,
                                makeHandle(hTuple.getComposition(), ahValue));
                }

            switch (freezeValues(frame, ahValue, fInPlace, 0))
                {
                case Op.R_NEXT:
                    return stepNext.proceed(frame);

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(stepNext);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return Op.R_NEXT;
        }

    private int freezeValues(Frame frame, ObjectHandle[] ahValue, boolean fInPlace, int index)
        {
        for (int i = index, c = ahValue.length; i < c; i++)
            {
            ObjectHandle hValue = ahValue[i];
            if (!hValue.isPassThrough(null))
                {
                if (hValue.getType().isA(frame.poolContext().typeFreezable()))
                    {
                    int ix = i;
                    return Utils.callFreeze(frame, hValue, fInPlace, frameCaller ->
                        {
                        ahValue[ix] = frameCaller.popStack();
                        return freezeValues(frameCaller, ahValue, fInPlace, ix+1);
                        });
                    }
                return frame.raiseException("Tuple element [" + i + "] of type \"" +
                    hValue.getType().removeAccess().getValueString() + "\" is not freezable");
                }
            }
        return Op.R_NEXT;
        }

    /**
     * Native "Tuple!<> slice(Interval<Int>)" implementation.
     */
    protected int invokeSlice(Frame   frame,   TupleHandle hTuple,
                              long    ixLower, boolean     fExLower,
                              long    ixUpper, boolean     fExUpper,
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

            TypeConstant    typeTupleNew = frame.poolContext().ensureTupleType(atypeNew);
            TypeComposition clzTupleNew  = ensureClass(frame.f_context.f_container, typeTupleNew);
            TupleHandle     hTupleNew    = makeHandle(clzTupleNew, ahNew);

            return frame.assignValue(iReturn, hTupleNew);
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
        return frame.raiseException(xException.unsupported(frame));
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
        private final TupleHandle    hTuple1;
        private final TupleHandle    hTuple2;
        private final int            cElements;
        private final TypeConstant[] atype;
        private final int            iReturn;

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
     * @param clazz     the tuple class composition
     * @param ahValue   the values
     *
     * @return the handle
     */
    public static TupleHandle makeImmutableHandle(TypeComposition clazz, ObjectHandle... ahValue)
        {
        return new TupleHandle(clazz, ahValue, false);
        }

    /**
     * Make a Tuple handle depending on the specified type composition. If the type is mutable,
     * the resulting Tuple is immutable.
     *
     * @param clazz    the tuple class composition
     * @param ahValue  the values
     *
     * @return the handle
     */
    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle... ahValue)
        {
        return new TupleHandle(clazz, ahValue, !clazz.getType().isImmutable());
        }

    public static class TupleHandle
            extends ObjectHandle
        {
        public ObjectHandle[] m_ahValue;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue, boolean fMutable)
            {
            super(clazz);

            m_ahValue  = ahValue;
            m_fMutable = fMutable;
            }

        @Override
        public boolean makeImmutable()
            {
            if (m_fMutable)
                {
                ObjectHandle[] ahValue = m_ahValue;
                for (int i = 0, c = ahValue.length; i < c; i++)
                    {
                    ObjectHandle hValue = ahValue[i];
                    if (hValue == null)
                        {
                        // this can only be a scenario of a conditional Tuple
                        assert i > 0 && ahValue[0] == xBoolean.FALSE;
                        }
                    else if (!hValue.isService() && !hValue.makeImmutable())
                        {
                        return false;
                        }
                    }
                return super.makeImmutable();
                }
            return true;
            }

        @Override
        protected TypeConstant augmentType(TypeConstant type)
            {
            ObjectHandle[] ahValue     = m_ahValue;
            TypeConstant[] atypeOrig   = type.getParamTypesArray();
            TypeConstant[] atypeActual = null;

            for (int i = 0, c = Math.min(ahValue.length, atypeOrig.length); i < c; i++)
                {
                ObjectHandle hValue = ahValue[i];
                if (hValue == null)
                    {
                    // this can only be a scenario of a conditional Tuple
                    assert i > 0 && ahValue[0] == xBoolean.FALSE;
                    return type;
                    }
                TypeConstant typeVal = hValue.getType();
                if (!typeVal.equals(atypeOrig[i]))
                    {
                    if (atypeActual == null)
                        {
                        atypeActual = atypeOrig.clone();
                        }
                    atypeActual[i] = typeVal;
                    }
                }
            return super.augmentType(atypeActual == null
                    ? type
                    : type.getConstantPool().ensureTupleType(atypeActual));
            }

        @Override
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
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
    }