package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ConstantPool.IntConstant;
import org.xvm.asm.ConstantPool.CharStringConstant;

import java.util.HashMap;
import java.util.Map;

/**
 * Heap and constants.
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
    public ObjectHandle resolveConstHandle(ObjectHandle hTarget, TypeName tn, int nValueConstId)
        {
        assert(tn.isResolved());

        String sType = tn.getSimpleName();
        // TODO: generic names
        int nClassConstId = m_constantPool.getClassConstId(sType);

        return resolveConstHandle(nClassConstId, nValueConstId);
        }

    // nValueConstId -- "literal" (Int/CharString/etc.) Constant known by the ConstantPool
    public ObjectHandle resolveConstHandle(int nClassConstId, int nValueConstId)
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

                case Method: // TODO: function is not currently there
                    template.assignConstValue(handle, constValue);
                    break;

                default:
                    throw new UnsupportedOperationException("type " + constValue.getType());
                }
            registerConstHandle(nClassConstId, nValueConstId, handle);
            }

        return handle;
        }

    public String getPropertyName(int nValueConstId)
        {
        return ((CharStringConstant) m_constantPool.getConstantValue(nValueConstId)).getValue();
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
