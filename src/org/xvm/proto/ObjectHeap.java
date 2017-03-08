package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.IntConstant;
import org.xvm.asm.ConstantPool.CharStringConstant;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class ObjectHeap
    {
    private TypeSet m_types;
    private ConstantPoolAdapter m_constantPool;

    Map<Long, ObjectHandle> m_mapConstants = new HashMap<>();

    ObjectHeap(ConstantPoolAdapter adapter, TypeSet types)
        {
        m_types = types;
        m_constantPool = adapter;
        }

    // *Ids  -- as known by the ConstantPool
    ObjectHandle ensureConstHandle(int nConstTypeID, int nConstValueId)
        {
        ObjectHandle handle = null;
        if (nConstValueId > 0)
            {
            handle = getConstHandle(nConstTypeID, nConstValueId);
            }

        if (handle == null)
            {
            TypeComposition typeComposition = m_types.ensureConstComposition(nConstTypeID);
            TypeCompositionTemplate template = typeComposition.m_template;

            if (nConstValueId > 0)
                {
                Constant constValue = m_constantPool.getConstantValue(nConstValueId); // must exist
                switch (constValue.getType())
                    {
                    case Int:
                        handle = template.createInitializedHandle(((IntConstant) constValue).getValue());
                        break;

                    case CharString:
                        handle = template.createInitializedHandle(((CharStringConstant) constValue).getValue());
                        break;
                    }
                registerConstHandle(nConstTypeID, nConstValueId, handle);
                }
            else
                {

                }
            }

        return handle;
        }

    ObjectHandle getConstHandle(int nConstType, int nConstValue)
        {
        return m_mapConstants.get(((long) nConstType << 32) | ((long) nConstValue));
        }
    void registerConstHandle(int nConstType, int nConstValue, ObjectHandle handle)
        {
        m_mapConstants.put(((long) nConstType << 32) | ((long) nConstValue), handle);
        }

    }
