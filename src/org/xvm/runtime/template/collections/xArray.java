package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHeap;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.Function.FunctionHandle;
import org.xvm.runtime.template.xInt64;


/**
 * TODO:
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport
    {
    public static xArray INSTANCE;

    public xArray(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;

            new xIntArray(f_types, f_struct, true); // TODO: how to do it right?
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("construct", INT);
        markNativeMethod("construct", new String[]{"Int64", "Function"});
        markNativeMethod("elementAt", INT, new String[] {"Ref<ElementType>"});
        markNativeMethod("reify", VOID, new String[]{"collections.Array<ElementType>"});
        }

    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constArray = (ArrayConstant) constant;

        assert constArray.getFormat() == Constant.Format.Array;

        TypeConstant typeArray = constArray.getType();
        TypeComposition clzArray = f_types.resolveClass(typeArray);

        TypeConstant typeEl = clzArray.getActualParamType("ElementType");
        TypeComposition clzEl = f_types.resolveClass(typeEl);
        ClassTemplate templateEl = clzEl.f_template;

        Constant[] aconst = constArray.getValue();
        int cSize = aconst.length;

        int nR = templateEl.createArrayStruct(frame, clzArray, cSize, Frame.RET_LOCAL);
        if (nR == Op.R_EXCEPTION)
            {
            throw new IllegalStateException("Failed to create an array " + clzArray);
            }

        ArrayHandle hArray = (ArrayHandle) frame.getFrameLocal();
        IndexSupport templateArray = (IndexSupport) hArray.f_clazz.f_template;

        ObjectHeap heap = f_types.f_container.f_heapGlobal;

        for (int i = 0; i < cSize; i++)
            {
            Constant constValue = aconst[i];

            ObjectHandle hValue = heap.ensureConstHandle(frame, constValue.getPosition());

            ExceptionHandle hException =
                    templateArray.assignArrayValue(hArray, i, hValue);
            if (hException != null)
                {
                throw new IllegalStateException("Failed to initialize an array " + hException);
                }
            }

        hArray.makeImmutable();
        return hArray;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn)
        {
        // this is a native constructor
        TypeConstant typeEl = clzArray.getActualParamType("ElementType");
        TypeComposition clzEl = f_types.resolveClass(typeEl);
        ClassTemplate templateEl = clzEl.f_template;

        long cCapacity = ((JavaLong) ahVar[0]).getValue();

        int nR = templateEl.createArrayStruct(frame, clzArray, cCapacity, Frame.RET_LOCAL);
        if (nR == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        ArrayHandle hArray = (ArrayHandle) frame.getFrameLocal();

        if (cCapacity > 0)
            {
            hArray.m_fFixed = true;

            if (ahVar.length == 2)
                {
                FunctionHandle hSupplier = (FunctionHandle) ahVar[1];

                return new Fill(this, hArray, cCapacity, hSupplier, iReturn).doNext(frame);
                }
            }

        return frame.assignValue(iReturn, hArray);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
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

        // then compare the element types
        TypeConstant type1 = getElementType(hArray1, 0);
        TypeConstant type2 = getElementType(hArray2, 0);

        if (type1.equals(type2))
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        // now compare arrays elements one-by-one
        int[] holder = new int[] {0}; // the index holder
        return new Equals(ah1, ah2, type1, cElements, holder, iReturn).doNext(frame);
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            throw IndexSupport.outOfRange(lIndex, hArray.m_cSize).getException();
            }

        return hArray.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return IndexSupport.outOfRange(lIndex, cSize);
            }

        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            int cCapacity = hArray.m_ahValue.length;
            if (cSize == cCapacity)
                {
                if (hArray.m_fFixed)
                    {
                    return IndexSupport.outOfRange(lIndex, cSize);
                    }

                // resize (TODO: we should be much smarter here)
                cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

                ObjectHandle[] ahNew = new ObjectHandle[cCapacity];
                System.arraycopy(hArray.m_ahValue, 0, ahNew, 0, cSize);
                hArray.m_ahValue = ahNew;
                }

            hArray.m_cSize++;
            }

        hArray.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public TypeConstant getElementType(ObjectHandle hTarget, long lIndex)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        return hArray.f_clazz.getActualParamType("ElementType");
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.m_cSize;
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
            ExceptionHandle hException =
                    template.assignArrayValue(hArray, index, frameCaller.getFrameLocal());
            if (hException != null)
                {
                return frameCaller.raiseException(hException);
                }
            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < cCapacity)
                {
                ahVar[0] = xInt64.makeHandle(index);

                switch (hSupplier.call1(frameCaller, null, ahVar, Frame.RET_LOCAL))
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
            ObjectHandle hResult = frameCaller.getFrameLocal();
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
                switch (typeEl.callEquals(frameCaller, ah1[iEl], ah2[iEl], Frame.RET_LOCAL))
                    {
                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_NEXT:
                        ObjectHandle hResult = frameCaller.getFrameLocal();
                        if (hResult == xBoolean.FALSE)
                            {
                            return frameCaller.assignValue(iReturn, hResult);
                            }
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, xBoolean.TRUE);
            }
        }

    // ----- ObjectHandle helpers -----

    public static GenericArrayHandle makeHandle(long cCapacity)
        {
        return new GenericArrayHandle(INSTANCE.f_clazzCanonical, cCapacity);
        }

    public static GenericArrayHandle makeHandle(TypeConstant typeEl, ObjectHandle[] ahValue)
        {
        return new GenericArrayHandle(INSTANCE.ensureParameterizedClass(typeEl), ahValue); // ElementType
        }

    public static GenericArrayHandle makeHandle(TypeComposition clzArray, long cCapacity)
        {
        return new GenericArrayHandle(clzArray, cCapacity);
        }

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;

        protected GenericArrayHandle(TypeComposition clzArray, ObjectHandle[] ahValue)
            {
            super(clzArray);

            m_ahValue = ahValue;
            m_cSize = ahValue.length;
            }

        protected GenericArrayHandle(TypeComposition clzArray, long cCapacity)
            {
            super(clzArray);

            m_ahValue = new ObjectHandle[(int) cCapacity];
            }

        @Override
        public String toString()
            {
            return super.toString() + (m_fFixed ? "fixed" : "capacity=" + m_ahValue.length)
                    + ", size=" + m_cSize;
            }
        }
    }
