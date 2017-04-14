package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.CharStringConstant;
import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.IntConstant;
import org.xvm.asm.ConstantPool.MethodConstant;
import org.xvm.asm.ConstantPool.ModuleConstant;
import org.xvm.proto.template.xClass;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xInt64;
import org.xvm.proto.template.xMethod;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xString;

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

    Map<Integer, ObjectHandle> m_mapConstants = new HashMap<>();

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
    public ObjectHandle resolveConstHandle(int nValueConstId)
        {
        ObjectHandle handle = getConstHandle(nValueConstId);

        if (handle == null)
            {
            Constant constValue = f_constantPool.getConstantValue(nValueConstId); // must exist

            if (constValue instanceof CharStringConstant)
                {
                handle = xString.INSTANCE.createConstHandle(constValue);
                }
            else if (constValue instanceof IntConstant)
                {
                handle = xInt64.INSTANCE.createConstHandle(constValue);
                }
            else if (constValue instanceof MethodConstant)
                {
                handle = xMethod.INSTANCE.createConstHandle(constValue);
                if (handle == null)
                    {
                    // TODO: replace with function when implemented
                    handle = xFunction.INSTANCE.createConstHandle(constValue);
                    }
                }
            else if (constValue instanceof ClassConstant)
                {
                handle = xClass.INSTANCE.createConstHandle(constValue);
                }
            else if (constValue instanceof ModuleConstant)
                {
                handle = xModule.INSTANCE.createConstHandle(constValue);
                }

            if (handle == null)
                {
                throw new UnsupportedOperationException("Unknown constant " + constValue);
                }

            registerConstHandle(nValueConstId, handle);
            }

        return handle;
        }

    public String getPropertyName(int nValueConstId)
        {
        return ((CharStringConstant) f_constantPool.getConstantValue(nValueConstId)).getValue();
        }

    public ObjectHandle getConstHandle(int nValueConstId)
        {
        return m_mapConstants.get(nValueConstId);
        }
    protected void registerConstHandle(int nValueConstId, ObjectHandle handle)
        {
        m_mapConstants.put(nValueConstId, handle);
        }

    }
