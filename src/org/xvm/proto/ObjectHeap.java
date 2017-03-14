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

    public ObjectHeap(ConstantPoolAdapter adapter, TypeSet types)
        {
        m_types = types;
        m_constantPool = adapter;
        }

    // nClassConstId - ClassConstant in the ConstantPool
    public ObjectHandle ensureHandle(int nClassConstId)
        {
        TypeComposition typeComposition = m_types.ensureConstComposition(nClassConstId);

        return typeComposition.f_template.createHandle(typeComposition);
        }

    // nValueConstId -- "literal" (Int/CharString/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(int nClassConstId, int nValueConstId)
        {
        ObjectHandle handle = null;
        if (nValueConstId > 0)
            {
            handle = getConstHandle(nClassConstId, nValueConstId);
            }

        if (handle == null)
            {
            TypeComposition typeComposition = m_types.ensureConstComposition(nClassConstId);
            TypeCompositionTemplate template = typeComposition.f_template;

            Constant constValue = m_constantPool.getConstantValue(nValueConstId); // must exist
            handle = template.createHandle(typeComposition);
            switch (constValue.getType())
                {
                case Int:
                    template.assignConstValue(handle, ((IntConstant) constValue).getValue());
                    break;

                case CharString:
                    template.assignConstValue(handle, ((CharStringConstant) constValue).getValue());
                    break;
                }
            registerConstHandle(nClassConstId, nValueConstId, handle);
            }

        return handle;
        }

    public ObjectHandle getConstHandle(int nClassConstId, int nValueConstId)
        {
        return m_mapConstants.get(((long) nClassConstId << 32) | ((long) nValueConstId));
        }
    protected void registerConstHandle(int nClassConstId, int nValueConstId, ObjectHandle handle)
        {
        m_mapConstants.put(((long) nClassConstId << 32) | ((long) nValueConstId), handle);
        }

    }
