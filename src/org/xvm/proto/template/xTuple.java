package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.TupleConstant;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

import java.util.Arrays;
import java.util.List;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xTuple
        extends TypeCompositionTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;

    public xTuple(TypeSet types)
        {
        // deferring the variable length generics <ElementType...>
        super(types, "x:Tuple", "x:Object", Shape.Interface);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
// TODO deferred for now
//        addImplement("FixedSizeAble");
//        addImplement("PersistentAble");
//        addImplement("ConstAble");

        //    @ro Int size;
        //    @op ElementTypes[index] get(Int index);
        //    @op Void set(Int index, ElementTypes[index] newValue);
        //    @op Ref<ElementTypes[index]> elementAt(Int index);
        //    @op Tuple add(Tuple that);
        //    Tuple<ElementTypes> replace(Int index, ElementTypes[index] value);
        //    @op Tuple slice(Range<Int> range);
        //    Tuple remove(Int index);
        //    Tuple remove(Range<Int> range);
        // TODO deferred
        //    Tuple<ElementTypes> ensureFixedSize();
        //    Tuple<ElementTypes> ensurePersistent();
        //    Tuple<ElementTypes> ensureConst();

        ensurePropertyTemplate("size", "x:Int").makeReadOnly();
        ensureMethodTemplate("get", INT, new String[]{"x:Type"}); // not quite right
        ensureMethodTemplate("set", new String[]{"x:Int", "x:Type"}, VOID); // not quite right
        ensureMethodTemplate("elementAt", INT, new String[]{"x:Ref<x:Type>"}); // not quite right
        ensureMethodTemplate("add", new String[]{"x:Tuple"}, new String[]{"x:Tuple"}); // non "virtual"
        ensureMethodTemplate("replace", new String[]{"x:Int", "x:Type"}, THIS); // not quite right
        ensureMethodTemplate("slice", new String[]{"x:Range<x:Int>"}, new String[]{"x:Tuple"}); // non "virtual"
        ensureMethodTemplate("remove", INT, new String[]{"x:Tuple"}); // non "virtual"
        ensureMethodTemplate("remove", new String[]{"x:Range<x:Int>"}, new String[]{"x:Tuple"}); // non "virtual"
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new TupleHandle(clazz, null);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        TupleConstant constTuple = (TupleConstant) constant;

        List<Constant> list = constTuple.constants();
        int c = list.size();
        ObjectHandle[] ahValue = new ObjectHandle[c];
        for (int i = 0; i < c; i++)
            {
            ahValue[i] = heap.ensureConstHandle(list.get(i));
            }

        TupleHandle hTuple = new TupleHandle(INSTANCE.f_clazzCanonical, ahValue);
        hTuple.makeImmutable();
        return hTuple;
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        if (lIndex < 0 || lIndex >= hTuple.m_ahValue.length)
            {
            throw IndexSupport.outOfRange(lIndex, hTuple.m_ahValue.length).getException();
            }

        return hTuple.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        if (lIndex < 0 || lIndex >= hTuple.m_ahValue.length)
            {
            return IndexSupport.outOfRange(lIndex, hTuple.m_ahValue.length);
            }

        if (!hTuple.isMutable())
            {

            }

        hTuple.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public Type getElementType(ObjectHandle hTarget, long lIndex)
                throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        if (lIndex < 0 || lIndex >= hTuple.m_aType.length)
            {
            throw IndexSupport.outOfRange(lIndex, hTuple.m_aType.length).getException();
            }
        return hTuple.m_aType[(int) lIndex];
        }

    // ----- ObjectHandle helpers -----

    public static class TupleHandle
            extends ObjectHandle
        {
        public Type[] m_aType;
        public ObjectHandle[] m_ahValue;
        public boolean m_fFixedSize;
        public boolean m_fPersistent;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue)
            {
            super(clazz);

            m_fMutable = true;
            m_ahValue = ahValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + Arrays.toString(m_ahValue);
            }
        }
    }
