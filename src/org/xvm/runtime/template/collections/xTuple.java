package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.Mutability;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.numbers.xInt64;

/**
 * Native Tuple implementation.
 */
public class xTuple
        extends ClassTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;
    public static ClassConstant INCEPTION_CLASS;
    public static xTuple.TupleHandle H_VOID;
    public static xEnum MUTABILITY;

    public xTuple(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            H_VOID = makeImmutableHandle(getCanonicalType(), Utils.OBJECTS_NONE);

            // cache Mutability template
            MUTABILITY = (xEnum) templates.getTemplate("collections.VariablyMutable.Mutability");
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("mutability");
        markNativeProperty("size");

        markNativeMethod("add", null, null);
        markNativeMethod("elementAt", INT, null);
        markNativeMethod("ensureFixedSize", BOOLEAN, null);
        markNativeMethod("ensureImmutable", BOOLEAN, null);
        markNativeMethod("ensurePersistent", BOOLEAN, null);
        markNativeMethod("getElement", INT, null);
        markNativeMethod("remove", new String[] {"numbers.Int64"}, null);
        markNativeMethod("remove", new String[] {"Range<numbers.Int64>"}, null);
        markNativeMethod("setElement", null, VOID);
        markNativeMethod("slice", new String[] {"Range<numbers.Int64>"}, null);
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
            frame.pushStack(H_VOID);
            return Op.R_NEXT;
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

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                frameCaller.pushStack(makeImmutableHandle(typeTuple, ahValue));
                return Op.R_NEXT;
                };

            return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

        frame.pushStack(makeImmutableHandle(typeTuple, ahValue));
        return Op.R_NEXT;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hSequence = ahVar[0];
        IndexSupport support = (IndexSupport) hSequence.getOpSupport();

        ObjectHandle[] ahValue;

        try
            {
            ahValue = support.toArray(frame, hSequence);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }

        TupleHandle hTuple = new TupleHandle(clazz, ahValue);

        return frame.assignValue(iReturn, hTuple);
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
            case "add":
                // TODO
                throw new UnsupportedOperationException();

            case "elementAt":
                return makeRef(frame, hTarget, ((ObjectHandle.JavaLong) hArg).getValue(), false, iReturn);

            case "ensureFixedSize": // Tuple ensureFixedSize(Boolean inPlace = false);
                // TODO
                throw new UnsupportedOperationException();

            case "ensureImmutable": // immutable Tuple ensureImmutable(Boolean inPlace = False)
                // TODO
                throw new UnsupportedOperationException();

            case "ensurePersistent": // Tuple ensurePersistent(Boolean inPlace = False)
                // TODO
                throw new UnsupportedOperationException();

            case "getElement":
                return extractArrayValue(frame, hTarget, ((ObjectHandle.JavaLong) hArg).getValue(), iReturn);

            case "remove":
                // TODO - note that there are two remove() methods that each take one parameter
                throw new UnsupportedOperationException();

            case "slice":
                {
                ObjectHandle.GenericHandle hInterval = (ObjectHandle.GenericHandle) hArg;
                long    ixFrom   = ((ObjectHandle.JavaLong) hInterval.getField("lowerBound")).getValue();
                long    ixTo     = ((ObjectHandle.JavaLong) hInterval.getField("upperBound")).getValue();
                boolean fExLower = ((xBoolean.BooleanHandle) hInterval.getField("lowerExclusive")).get();
                boolean fExUpper = ((xBoolean.BooleanHandle) hInterval.getField("upperExclusive")).get();
                boolean fReverse = ((xBoolean.BooleanHandle) hInterval.getField("reversed")).get();
                return slice(frame, (TupleHandle) hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "replace":
                // TODO
                throw new UnsupportedOperationException();

            case "setElement":
                return assignArrayValue(frame, hTarget, ((ObjectHandle.JavaLong) ahArg[0]).getValue(), ahArg[1]);

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
     * slice(Interval<Int>) implementation
     */
    protected int slice(Frame        frame,
                        TupleHandle  hTuple,
                        long         ixLower,
                        boolean      fExLower,
                        long         ixUpper,
                        boolean      fExUpper,
                        boolean      fReverse,
                        int          iReturn)
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
        try
            {
            ObjectHandle[] ahNew;
            if (ixLower >= ixUpper)
                {
                ahNew = new ObjectHandle[0];
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

            TupleHandle hTupleNew = new TupleHandle(/*TODO GG need composition*/ null, ahNew, hTuple.m_mutability);

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

        return hTuple.m_aType[(int) lIndex];
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        return hTuple.m_ahValue.length;
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hTuple.getComposition().toString())
          .append('(');

        ObjectHandle[] ahValue = hTuple.m_ahValue;
        if (ahValue.length > 0)
            {
            Frame.Continuation stepNext = frameCaller ->
                frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));

            return new Utils.TupleToString(sb, hTuple.m_ahValue, null, stepNext).doNext(frame);
            }
        else
            {
            sb.append(')');
            return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }

    // ----- ObjectHandle helpers -----

    /**
     * Make a mutable canonical Tuple handle.
     *
     * @param ahValue  the values
     *
     * @return the handle
     */
    public static TupleHandle makeHandle(ObjectHandle... ahValue)
        {
        return new TupleHandle(INSTANCE.getCanonicalClass(), ahValue);
        }

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
        TupleHandle hTuple = new TupleHandle(INSTANCE.ensureClass(typeTuple), ahValue);
        hTuple.makeImmutable();
        return hTuple;
        }

    /**
     * Make a mutable Tuple handle.
     *
     * @param clazz    the tuple class composition
     * @param ahValue  the values
     *
     * @return the handle
     */
    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle... ahValue)
        {
        return new TupleHandle(clazz, ahValue);
        }

    public static class TupleHandle
            extends ObjectHandle
        {
        public TypeConstant[] m_aType;
        public ObjectHandle[] m_ahValue;
        public Mutability     m_mutability;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue)
            {
            this(clazz, ahValue, null);   // TODO GG mutability?
            }

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue, Mutability mutability)
            {
            super(clazz);

            m_fMutable   = true;
            m_ahValue    = ahValue;
            m_mutability = mutability;
            }

        @Override
        public String toString()
            {
            return "Tuple: " + Arrays.toString(m_ahValue);
            }
        }
    }
