package org.xvm.proto.template.collections;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.asm.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.IndexSupport;
import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.Function.FunctionHandle;
import org.xvm.proto.template.xInt64;
import org.xvm.proto.template.xObject;

import java.util.Collections;

/**
 * TODO:
 *
 * @author gg 2017.02.27
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
        markNativeMethod("reify", VOID, new String[] {"collections.Array<ElementType>"});
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn)
        {
        Type typeEl = clzArray.f_mapGenericActual.get("ElementType");
        TypeComposition clzEl = typeEl.f_clazz;

        ClassTemplate templateEl = clzEl == null ? xObject.INSTANCE : clzEl.f_template;

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
                xArray array = (xArray) hArray.f_clazz.f_template;

                int[] holder = new int[]{0}; // index holder; starts with zero
                ObjectHandle[] ahArg = new ObjectHandle[hSupplier.getVarCount()];
                ahArg[0] = xInt64.makeHandle(0);

                // TODO: what if the supplier produces a "future" result
                hSupplier.call1(frame, null, ahArg, Frame.RET_LOCAL);

                frame.m_frameNext.setContinuation(new Frame.Continuation()
                    {
                    public int proceed(Frame frameCaller)
                        {
                        int i = holder[0]++;
                        ExceptionHandle hException =
                                array.assignArrayValue(hArray, i, frameCaller.getFrameLocal());
                        if (hException != null)
                            {
                            return frameCaller.raiseException(hException);
                            }

                        if (++i < cCapacity)
                            {
                            ahArg[0] = xInt64.makeHandle(i);

                            hSupplier.call1(frameCaller, null, ahArg, Frame.RET_LOCAL);
                            frameCaller.m_frameNext.setContinuation(this);
                            return Op.R_CALL;
                            }

                        return frameCaller.assignValue(iReturn, hArray);
                        }
                    });

                return Op.R_CALL;
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
        Type type1 = getElementType(hArray1, 0);
        Type type2 = getElementType(hArray2, 0);

        TypeComposition clazzEl = type1.f_clazz;
        if (clazzEl == null || clazzEl != type2.f_clazz)
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        // now compare arrays elements one-by-one
        int[] holder = new int[] {0}; // the index holder
        return new Equals(ah1, ah2, clazzEl, cElements, holder, iReturn).doNext(frame);
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
    public Type getElementType(ObjectHandle hTarget, long lIndex)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;

        return hArray.f_clazz.f_mapGenericActual.get("ElementType");
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.m_cSize;
        }

    // ----- helper classes -----

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
            implements Frame.Continuation
        {
        final private ObjectHandle[] ah1;
        final private ObjectHandle[] ah2;
        final private TypeComposition clazzEl;
        final private int cElements;
        final private int[] holder;
        final private int iReturn;

        public Equals(ObjectHandle[] ah1, ObjectHandle[] ah2, TypeComposition clazzEl,
                      int cElements, int[] holder, int iReturn)
            {
            this.ah1 = ah1;
            this.ah2 = ah2;
            this.clazzEl = clazzEl;
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
                switch (clazzEl.callEquals(frameCaller, ah1[iEl], ah2[iEl], Frame.RET_LOCAL))
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

    public static GenericArrayHandle makeHandle(Type typeEl, ObjectHandle[] ahValue)
        {
        return new GenericArrayHandle(INSTANCE.ensureClass(
                Collections.singletonMap("ElementType", typeEl)), ahValue);
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
