package org.xvm.proto;

import org.xvm.asm.Constant;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TupleConstant;

import org.xvm.proto.template.xClass;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xInt64;
import org.xvm.proto.template.xModule;
import org.xvm.proto.template.xRef.RefHandle;
import org.xvm.proto.template.xString;
import org.xvm.proto.template.xTuple;

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
    public final ConstantPool f_pool;

    Map<Integer, ObjectHandle> m_mapConstants = new HashMap<>();

    public ObjectHeap(ConstantPool pool, TypeSet types)
        {
        f_types = types;
        f_pool = pool;
        }

    // nValueConstId -- "literal" (Int/CharString/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(int nValueConstId)
        {
        ObjectHandle handle = getConstHandle(nValueConstId);

        if (handle == null)
            {
            Constant constValue = f_pool.getConstant(nValueConstId); // must exist

            handle = getConstTemplate(constValue).createConstHandle(constValue, this);

            registerConstHandle(nValueConstId, handle);
            }

        return handle;
        }

    public ClassTemplate getConstTemplate(int nValueConstId)
        {
        Constant constValue = f_pool.getConstant(nValueConstId);
        return getConstTemplate(constValue);
        }

    public ClassTemplate getConstTemplate(Constant constValue)
        {
        if (constValue instanceof CharStringConstant)
            {
            return xString.INSTANCE;
            }

        if (constValue instanceof IntConstant)
            {
            return xInt64.INSTANCE;
            }

        if (constValue instanceof ClassTypeConstant)
            {
            return xClass.INSTANCE;
            }

        if (constValue instanceof ModuleConstant)
            {
            return xModule.INSTANCE;
            }

        if (constValue instanceof TupleConstant)
            {
            return xTuple.INSTANCE;
            }

        if (constValue instanceof MethodConstant)
            {
            return xFunction.INSTANCE;
            }

        throw new UnsupportedOperationException("Unknown constant " + constValue);
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
