package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.TupleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xTuple
        extends ClassTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;

    public xTuple(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: remove
        f_types.f_adapter.addMethod(f_struct, "construct", new String[]{"collections.Sequence<Object>"}, VOID);

        markNativeMethod("construct", new String[]{"collections.Sequence<Object>"}, VOID);
        }

    @Override
    protected TypeComposition createCanonicalClass()
        {
        // this is Void.class
        return new TypeComposition(this, Collections.EMPTY_MAP);
        }

    @Override
    public TypeComposition resolve(ClassTypeConstant constClassType, Map<String, Type> mapActual)
        {
        List<TypeConstant> listParams = constClassType.getTypeConstants();

        int cParams = listParams.size();
        if (cParams == 0)
            {
            return f_clazzCanonical;
            }

        Map<String, Type> mapParams = new HashMap<>();
        for (int i = 0, c = listParams.size(); i < c; i++)
            {
            mapParams.put("ElementTypes[" + i + ']',
                    resolveParameterType(listParams.get(i), mapActual));
            }
        return ensureClass(mapParams);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        TupleConstant constTuple = (TupleConstant) constant;

        List<Constant> list = constTuple.constants();
        int c = list.size();
        ObjectHandle[] ahValue = new ObjectHandle[c];
        Type[] aType = new Type[c];
        for (int i = 0; i < c; i++)
            {
            Constant constValue = list.get(i);

            ahValue[i] = heap.ensureConstHandle(constValue.getPosition());
            aType[i] = heap.getConstTemplate(constValue).f_clazzCanonical.ensurePublicType();
            }

        TupleHandle hTuple = makeHandle(aType, ahValue);
        hTuple.makeImmutable();
        return hTuple;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hSequence = ahVar[0];
        IndexSupport support = (IndexSupport) hSequence.f_clazz.f_template;

        ObjectHandle[] ahValue;

        try
            {
            ahValue = support.toArray(hSequence);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return Op.R_EXCEPTION;
            }

        TupleHandle hTuple = new TupleHandle(clazz, ahValue);

        return frame.assignValue(iReturn, hTuple);
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            return IndexSupport.outOfRange(lIndex, cElements);
            }

        if (!hTuple.isMutable())
            {
            return xException.makeHandle("Immutable object");
            }

        hTuple.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public Type getElementType(ObjectHandle hTarget, long lIndex)
                throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_aType[(int) lIndex];
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        return hTuple.m_ahValue.length;
        }

    // ----- ObjectHandle helpers -----

    public static TupleHandle makeHandle(Type[] aType, ObjectHandle[] ahValue)
        {
        Map<String, Type> mapParams;
        if (aType.length == 0)
            {
            mapParams = Collections.EMPTY_MAP;
            }
        else
            {
            mapParams = new HashMap<>();
            for (int i = 0, c = aType.length; i < c; i++)
                {
                // TODO: how to name them?
                mapParams.put("ElementTypes[" + i + ']', aType[i]);
                }
            }
        return new TupleHandle(INSTANCE.ensureClass(mapParams), ahValue);
        }

    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle[] ahValue)
        {
        return new TupleHandle(clazz, ahValue);
        }

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
