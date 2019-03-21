package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.MutabilityConstraint;
import org.xvm.runtime.ObjectHeap;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString;


/**
 * Native generic Array implementation.
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport
    {
    public static xArray INSTANCE;

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
        registerNative(new xIntArray(f_templates, f_struct, true));
        registerNative(new xCharArray(f_templates, f_struct, true));

        markNativeGetter("size");
        markNativeMethod("construct", INT);
        markNativeMethod("construct", new String[]{"Int64", "Function"});
        markNativeMethod("elementAt", INT, new String[] {"Var<ElementType>"});
        markNativeMethod("addElement", ELEMENT_TYPE, ARRAY);
        markNativeMethod("addElements", ARRAY, ARRAY);
        markNativeMethod("slice", new String[]{"Range<Int64>"}, ARRAY);
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
    public ClassComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... typeParams)
        {
        assert typeParams.length == 1;

        TypeConstant typeEl = typeParams[0];

        // TODO: we should obtain the array template from the element's template
        if (typeEl.equals(pool.typeInt()))
            {
            TypeConstant typeInception = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), typeEl);
            return xIntArray.INSTANCE.ensureClass(typeInception, typeInception);
            }

        return super.ensureParameterizedClass(pool, typeParams);
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        TypeConstant[] atypeParams = type.getParamTypesArray();
        if (atypeParams.length > 0)
            {
            ConstantPool pool      = pool();
            TypeConstant typeParam = atypeParams[0];
            if (typeParam.equals(pool.typeInt()))
                {
                return xIntArray.INSTANCE;
                }
            if (typeParam.equals(pool.typeChar()))
                {
                return xCharArray.INSTANCE;
                }
            }
        return this;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constArray = (ArrayConstant) constant;

        assert constArray.getFormat() == Constant.Format.Array;

        TypeConstant typeArray = constArray.getType();
        TypeConstant typeEl = typeArray.getGenericParamType("ElementType");
        ClassComposition clzArray = ensureParameterizedClass(frame.poolContext(), typeEl);

        Constant[] aconst = constArray.getValue();
        int cSize = aconst.length;

        ObjectHandle[] ahValue = new ObjectHandle[cSize];
        for (int i = 0; i < cSize; i++)
            {
            ObjectHandle hValue = frame.getConstHandle(aconst[i]);

            if (hValue instanceof DeferredCallHandle)
                {
                throw new UnsupportedOperationException("not implemented"); // TODO
                }
            ahValue[i] = hValue;
            }

        xArray template = (xArray) clzArray.getTemplate();
        ArrayHandle hArray = template.createArrayHandle(clzArray, ahValue);

        hArray.makeImmutable();

        frame.pushStack(hArray);
        return Op.R_NEXT;
        }

    /**
     * Create a one dimensional array for a specified class and content.
     *
     * @param clzArray  the class of the array
     * @param ahArg     the array elements
     *
     * @return the array handle
     */
    public ArrayHandle createArrayHandle(ClassComposition clzArray, ObjectHandle[] ahArg)
        {
        return new GenericArrayHandle(clzArray, ahArg);
        }

    /**
     * Create a one dimensional array for a specified type and arity.
     *
     * @param clzArray   the class of the array
     * @param cCapacity  the array size
     *
     * @return the array handle
     */
    public ArrayHandle createArrayHandle(ClassComposition clzArray, long cCapacity)
        {
        return new GenericArrayHandle(clzArray, cCapacity);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clzArray,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        // this is a native constructor
        ObjectHandle hCapacity = ahVar[0];
        long         cCapacity = hCapacity == ObjectHandle.DEFAULT ?
                                    0 : ((JavaLong) hCapacity).getValue();

        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            return frame.raiseException(
                xException.makeHandle("Invalid array size: " + cCapacity));
            }

        xArray      template = (xArray) clzArray.getTemplate();
        ArrayHandle hArray   = template.createArrayHandle(clzArray, cCapacity);

        if (ahVar.length == 1)
            {
            hArray.m_mutability = MutabilityConstraint.Mutable;
            }
        else
            {
            hArray.m_mutability = MutabilityConstraint.FixedSize;

            int cSize = (int) cCapacity;
            if (cSize > 0)
                {
                ObjectHandle hSupplier = ahVar[1];
                if (hSupplier == ObjectHandle.DEFAULT)
                    {
                    TypeConstant typeEl = clzArray.getType().getParamTypesArray()[0];
                    ObjectHandle hValue = frame.getConstHandle(typeEl.getDefaultValue());
                    fill(hArray, cSize, hValue);
                    }
                else
                    {
                    return new Fill(this, hArray, cSize, (FunctionHandle) hSupplier, iReturn).doNext(frame);
                    }
                }
            }

        return frame.assignValue(iReturn, hArray);
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
        if (hArg.getTemplate() instanceof xArray)
            {
            return addElements(frame, hTarget, hArg, iReturn);
            }
        return addElement(frame, hTarget, hArg, iReturn);
        }

    @Override
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName)
            {
            case "size":
                return frame.assignValue(iReturn, xInt64.makeHandle(hArray.m_cSize));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "addElement":
                return addElement(frame, hTarget, hArg, iReturn);

            case "addElements":
                return addElements(frame, hTarget, hArg, iReturn);

            case "elementAt":
                return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

            case "slice":
                {
                GenericHandle hRange = (GenericHandle) hArg;
                long ixFrom = ((JavaLong) hRange.getField("lowerBound")).getValue();
                long ixTo   = ((JavaLong) hRange.getField("upperBound")).getValue();

                return slice(frame, hTarget, ixFrom, ixTo, iReturn);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
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

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;
        int                c      = hArray.m_cSize;

        if (c == 0)
            {
            return frame.assignValue(iReturn, xString.EMPTY_ARRAY);
            }

        StringBuilder sb = new StringBuilder(c*7); // a wild guess
        sb.append('[');

        return new ToString(hArray, new StringBuilder("["), iReturn).doNext(frame);
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
                return frame.raiseException(xException.immutableObject());

            case FixedSize:
                return frame.raiseException(xException.illegalOperation());

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation());
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
        GenericArrayHandle hThis = (GenericArrayHandle) hTarget;

        switch (hThis.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject());

            case FixedSize:
                return frame.raiseException(xException.illegalOperation());

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation());
            }

        GenericArrayHandle hThat = (GenericArrayHandle) hValue;

        int cThat = hThat.m_cSize;
        if (cThat > 0)
            {
            ObjectHandle[] ahThis = hThis.m_ahValue;
            int            cThis  = hThis.m_cSize;

            if (cThis + cThat > ahThis.length)
                {
                ahThis = hThis.m_ahValue = grow(ahThis, cThis + cThat);
                }
            hThis.m_cSize += cThat;
            System.arraycopy(hThat.m_ahValue, 0, ahThis, cThis, cThat);
            }
        return frame.assignValue(iReturn, hThis); // return this
        }

    /**
     * slice(Range<Int>) implementation
     */
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        ObjectHandle[] ahValue = hArray.m_ahValue;
        try
            {
            ObjectHandle[] ahNew     = Arrays.copyOfRange(ahValue, (int) ixFrom, (int) ixTo);
            ArrayHandle    hArrayNew = new GenericArrayHandle(hTarget.getComposition(), ahNew);
            hArrayNew.m_mutability = MutabilityConstraint.Mutable;

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = ahValue.length;
            return frame.raiseException(
                xException.outOfRange(ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
            }
        }


    // ----- IndexSupport methods -----

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfRange(lIndex, hArray.m_cSize));
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
            return frame.raiseException(xException.outOfRange(lIndex, cSize));
            }

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject());

            case Persistent:
                return frame.raiseException(xException.illegalOperation());
            }

        ObjectHandle[] ahValue = hArray.m_ahValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == ahValue.length)
                {
                if (hArray.m_mutability == MutabilityConstraint.FixedSize)
                    {
                    return frame.raiseException(xException.illegalOperation());
                    }

                ahValue = hArray.m_ahValue = grow(ahValue, cSize + 1);
                }
            hArray.m_cSize++;
            }

        ahValue[(int) lIndex] = hValue;
        return Op.R_NEXT;
        }

    @Override
    public TypeConstant getElementType(ObjectHandle hTarget, long lIndex)
        {
        return hTarget.getType().getGenericParamType("ElementType");
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.m_cSize;
        }

    // ----- helper methods -----

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
                        frameCaller.m_frameNext.setContinuation(this);
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
                        frameCaller.m_frameNext.setContinuation(this);
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

    /**
     * Helper class for buildStringValue() implementation.
     */
    protected static class ToString
            implements Frame.Continuation
        {
        final private GenericArrayHandle hArray;
        final private StringBuilder sb;
        final private int iReturn;
        private int index = -1;

        public ToString(GenericArrayHandle hArray, StringBuilder sb, int iReturn)
            {
            this.hArray = hArray;
            this.sb = sb;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            sb.append(((xString.StringHandle) frameCaller.popStack()).getValue())
              .append(", ");
            }

        protected int doNext(Frame frameCaller)
            {
            ObjectHandle[] ahValue = hArray.m_ahValue;
            int            cValues = hArray.m_cSize;
            while (++index < cValues)
                {
                ObjectHandle hValue = ahValue[index];

                if (hValue == null)
                    {
                    // be tolerant here, but this shouldn't happen
                    sb.append("<unassigned>, ");
                    continue;
                    }

                if (sb.length() > 1_000_000)
                    {
                    // cut it off
                    sb.append("...");
                    break;
                    }

                switch (Utils.callToString(frameCaller, hValue))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            if (index > 0)
                {
                sb.setLength(sb.length() - 2); // remove the trailing ", "
                }
            sb.append(']');

            return frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }


    // ----- ObjectHandle helpers -----

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;

        public GenericArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue)
            {
            super(clzArray);

            m_ahValue = ahValue;
            m_cSize = ahValue.length;
            }

        public GenericArrayHandle(TypeComposition clzArray, long cCapacity)
            {
            super(clzArray);

            m_ahValue = new ObjectHandle[(int) cCapacity];
            }

        @Override
        public boolean isNativeEqual()
            {
            return false;
            }
        }

    protected static final String[] ELEMENT_TYPE = new String[] {"ElementType"};
    protected static final String[] ARRAY = new String[]{"collections.Array!<ElementType>"};
    }
