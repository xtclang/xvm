package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.CharStringConstant;
import org.xvm.proto.TypeName.UnionTypeName;

import java.util.HashMap;
import java.util.Map;

/**
 * Heap and constants.
 *
 * @author gg 2017.02.15
 */
public class ObjectHeap
    {
    public final TypeSet f_types;
    public final ConstantPoolAdapter f_constantPool;

    Map<Long, ObjectHandle> m_mapConstants = new HashMap<>();

    public ObjectHeap(ConstantPoolAdapter adapter, TypeSet types)
        {
        f_types = types;
        f_constantPool = adapter;
        }

    // nClassConstId - ClassConstant in the ConstantPool
    public ObjectHandle ensureHandle(int nClassConstId)
        {
        TypeComposition typeComposition = f_types.ensureConstComposition(nClassConstId);

        // TODO: ByComposition may not have a single template
        return typeComposition.f_template.createHandle(typeComposition);
        }

    // nValueConstId -- "literal" (Int/CharString/etc.) Constant known by the ConstantPool
    public ObjectHandle resolveConstHandle(TypeName typeName, int nValueConstId)
        {
        assert(typeName.isResolved());

        if (typeName instanceof UnionTypeName)
            {
            for (TypeName tn : ((UnionTypeName) typeName).m_aTypeName)
                {
                try
                    {
                    String sType = tn.getSimpleName();
                    // TODO: generic names
                    int nClassConstId = f_constantPool.getClassConstId(sType);
                    return resolveConstHandle(nClassConstId, nValueConstId);
                    }
                catch (UnsupportedOperationException e) {}
                }

            throw new UnsupportedOperationException(
                    "Constant " + f_constantPool.getConstantValue(nValueConstId) + " for " + typeName);
            }
        else
            {
            String sType = typeName.getSimpleName();
            // TODO: generic names
            int nClassConstId = f_constantPool.getClassConstId(sType);
            return resolveConstHandle(nClassConstId, nValueConstId);
            }
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
            TypeComposition typeComposition = f_types.ensureConstComposition(nClassConstId);

            // TODO: BiTypeComposition (String?) may not have the template reference

            TypeCompositionTemplate template = typeComposition.f_template;

            Constant constValue = f_constantPool.getConstantValue(nValueConstId); // must exist

            handle = template.createConstHandle(constValue);
            if (handle == null)
                {
                throw new UnsupportedOperationException(
                        "Constant " + constValue + " for " + template);
                }

            registerConstHandle(nClassConstId, nValueConstId, handle);
            }

        return handle;
        }

    public String getPropertyName(int nValueConstId)
        {
        return ((CharStringConstant) f_constantPool.getConstantValue(nValueConstId)).getValue();
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
