package org.xvm.proto.template;

import org.xvm.asm.Constants;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xArray
        extends TypeCompositionTemplate
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

        ConstructTemplate const1 = ensureConstructTemplate(INT);
        const1.markNative();

        ConstructTemplate const2 = ensureConstructTemplate(new String[] {"x:Int64", "x:Function"});
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
    public ExceptionHandle construct(Frame frame, ConstructTemplate constructor,
                                     TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        Type typeEl = clazz.f_atGenericActual[0];
        String sTemplate = typeEl.f_sName;

        long cCapacity = ((JavaLong) ahVar[0]).getValue();
        FunctionHandle hSupplier;

        if (ahVar.length == 1)
            {
            hSupplier = null;
            }
        else
            {
            hSupplier = (FunctionHandle) ahVar[1];
            }

        TypeCompositionTemplate templateEl = sTemplate == null ?
                xObject.INSTANCE : f_types.getTemplate(sTemplate);

        if (hSupplier == null)
            {
            return templateEl.createArrayHandle(frame, clazz, cCapacity, false, iReturn);
            }

        ExceptionHandle hException = templateEl.createArrayHandle(
                frame, clazz, cCapacity, true, Frame.R_FRAME);
        if (hException != null)
            {
            return hException;
            }

        ArrayHandle hArray = (ArrayHandle) frame.getFrameLocal();
        TypeCompositionTemplate templateArray = hArray.f_clazz.f_template;

        ObjectHandle[] ahArg = new ObjectHandle[1];
        for (int i = 0; i < cCapacity; i++)
            {
            ahArg[0] = xInt64.makeHandle(i);

            hException = hSupplier.call1(frame, ahArg, Frame.R_FRAME);

            if (hException == null)
                {
                hException = ((xArray) templateArray).
                        setArrayValue(frame, hArray, i, frame.getFrameLocal());
                }

            if (hException != null)
                {
                return hException;
                }
            }
        return frame.assignValue(iReturn, hArray);
        }

    // @op get
    public ExceptionHandle getArrayValue(Frame frame, ArrayHandle hTarget, long lIndex, int iReturn)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;
        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex >= cSize)
            {
            outOfRange(lIndex, cSize);
            }

        return frame.assignValue(iReturn, hArray.m_ahValue[(int) lIndex]);
        }

    // @op set
    public ExceptionHandle setArrayValue(Frame frame, ArrayHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        GenericArrayHandle hArray = (GenericArrayHandle) hTarget;
        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex >= cSize)
            {
            if (hArray.m_fFixed || lIndex != cSize)
                {
                outOfRange(lIndex, cSize);
                }

            // check the capacity
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

    // helpers
    protected static ExceptionHandle outOfRange(long lIndex, long cSize)
        {
        return xException.makeHandle("Array index " + lIndex + " out of range 0.." + cSize);
        }

    public static GenericArrayHandle makeInstance(long cCapacity, boolean fFixed)
        {
        return new GenericArrayHandle(INSTANCE.f_clazzCanonical, cCapacity, fFixed);
        }

    // generic array handle
    public static class GenericArrayHandle
            extends ArrayHandle
        {
        public ObjectHandle[] m_ahValue;
        public int m_cSize;

        protected GenericArrayHandle(TypeComposition clzArray, long cCapacity, boolean fFixed)
            {
            super(clzArray, fFixed);

            m_ahValue = new ObjectHandle[(int) cCapacity];
            m_cSize = 0;
            }
        }
    }
