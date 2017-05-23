package org.xvm.proto.template;

import org.xvm.asm.Constants;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;

import org.xvm.proto.template.xFunction.FunctionHandle;

import java.util.function.Supplier;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xArray
        extends TypeCompositionTemplate
        implements IndexSupport
    {
    public static xArray INSTANCE;

    public xArray(TypeSet types)
        {
        super(types, "x:collections.Array<ElementType>", "x:Object", Shape.Class);

        addImplement("x:collections.Sequence<ElementType>");

        INSTANCE = this;
        }

    // subclassing
    protected xArray(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    construct Array(Int capacity)
        //    construct Array(Int capacity, function ElementType(Int) supply)
        //
        //    public/private Int capacity = 0;
        //    public/private Int size     = 0;
        //
        //    @op ElementType get(Int index)
        //    @op Void set(Int index, ElementType value)
        //    @op Array.Type<ElementType> slice(Range<Int> range);
        //    @op Array.Type<ElementType> add(Array.Type<ElementType> that);
        //    @op Array.Type<ElementType> replace(Int index, ElementType value);
        //
        //    Ref<ElementType> elementAt(Int index)
        //    Array.Type<ElementType> reify();
        //
        //    static Ordered compare(Array value1, Array value2)
        //
        //    private Element<ElementType>? head;
        //    private class Element<RefType>(ElementType value)
        //
        //    Array.Type<ElementType> ensureMutable()
        //    Array.Type<ElementType> ensureFixedSize();
        //    Array.Type<ElementType> ensurePersistent();
        //    Const+Array.Type<ElementType> ensureConst();

        ConstructTemplate const1 = ensureConstructTemplate(new String[] {"x:collections.Array", "x:Int64"});
        const1.markNative();

        ConstructTemplate const2 = ensureConstructTemplate(new String[] {"x:collections.Array", "x:Int64", "x:Function"});
        const2.markNative();

        PropertyTemplate ptCap = ensurePropertyTemplate("capacity", "x:Int");
        ptCap.setSetAccess(Constants.Access.PRIVATE);

        PropertyTemplate ptLen = ensurePropertyTemplate("size", "x:Int");
        ptLen.setSetAccess(Constants.Access.PRIVATE);

        ensureMethodTemplate("elementAt", INT, new String[]{"x:Ref<ElementType>"}).markNative();
        ensureMethodTemplate("reify", VOID, THIS).markNative();

        ensureMethodTemplate("slice", new String[]{"x:Range<x:Int>"}, THIS);
        ensureMethodTemplate("add", THIS, THIS);
        ensureMethodTemplate("replace", new String[]{"x:Int", "ElementType"}, THIS);

        ensureFunctionTemplate("compare", new String[]{"this.Type", "this.Type"}, new String[]{"x:Ordered"});
        }

    @Override
    public int construct(Frame frame, ConstructTemplate constructor,
                         TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn)
        {
        Type typeEl = clzArray.f_atGenericActual[0];
        String sTemplate = typeEl.f_sName;

        TypeCompositionTemplate templateEl = sTemplate == null ?
                xObject.INSTANCE : f_types.getTemplate(sTemplate);

        // argument [0] is reserved for this:struct
        long cCapacity = ((JavaLong) ahVar[1]).getValue();

        if (templateEl.createArrayStruct(frame, clzArray, cCapacity, Frame.R_FRAME) < 0)
            {
            return Op.R_EXCEPTION;
            }

        ArrayHandle hArray = (ArrayHandle) frame.getFrameLocal();

        if (ahVar.length == 2)
            {
            hArray.m_fFixed = true;
            }
        else
            {
            FunctionHandle hSupplier = (FunctionHandle) ahVar[2];

            xArray array = (xArray) hArray.f_clazz.f_template;

            if (cCapacity > 0)
                {
                int[] ai = new int[]{0};
                ObjectHandle[] ahArg = new ObjectHandle[1];
                ahArg[0] = xInt64.makeHandle(ai[0]);

                // TODO: what if supplier is a "future" result
                hSupplier.call1(frame, ahArg, Frame.R_FRAME);
                Frame frame0 = frame.m_frameNext;

                frame0.m_continuation = new Supplier<Frame>()
                    {
                    public Frame get()
                        {
                        int i = ai[0]++;
                        ExceptionHandle hException =
                                array.assignArrayValue(hArray, i, frame.getFrameLocal());
                        if (hException != null)
                            {
                            frame.m_hException = hException;
                            return null;
                            }

                        if (++i < cCapacity)
                            {
                            ahArg[0] = xInt64.makeHandle(i);
                            // TODO: ditto
                            hSupplier.call1(frame, ahArg, Frame.R_FRAME);
                            Frame frameNext = frame.m_frameNext;
                            frameNext.m_continuation = this;
                            return frameNext;
                            }

                        frame.assignValue(iReturn, hArray);
                        return null;
                        }
                    };

                frame.m_frameNext = frame0;
                return Op.R_CALL;
                }
            }

        return frame.assignValue(iReturn, hArray);
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

        if (lIndex < 0 || lIndex >= cSize)
            {
            if (hArray.m_fFixed || lIndex != cSize)
                {
                return IndexSupport.outOfRange(lIndex, cSize);
                }

            int cCapacity = hArray.m_ahValue.length;
            if (cCapacity <= cSize)
                {
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

        return hArray.f_clazz.f_atGenericActual[0];
        }

    // ----- ObjectHandle helpers -----

    public static GenericArrayHandle makeInstance(long cCapacity)
        {
        return new GenericArrayHandle(INSTANCE.f_clazzCanonical, cCapacity);
        }

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;

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
