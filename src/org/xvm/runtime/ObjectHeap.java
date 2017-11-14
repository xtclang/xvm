package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.runtime.template.xClass;
import org.xvm.runtime.template.Function;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xModule;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xType;

import org.xvm.runtime.template.types.xProperty;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple;


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

    // nValueConstId -- "literal" (Int/String/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(Frame frame, int nValueConstId)
        {
        ObjectHandle handle = m_mapConstants.get(nValueConstId);
        if (handle == null)
            {
            Constant constValue = f_pool.getConstant(nValueConstId);
            ClassTemplate template = getConstTemplate(constValue); // must exist

            handle = template.createConstHandle(frame, constValue);

            if (template.isConstantCacheable(constValue))
                {
                m_mapConstants.put(nValueConstId, handle);
                }
            }

        return handle;
        }

    public ClassTemplate getConstTemplate(int nValueConstId)
        {
        Constant constValue = f_pool.getConstant(nValueConstId); // must exist
        return getConstTemplate(constValue);
        }

    public ClassTemplate getConstTemplate(Constant constValue)
        {
        switch (constValue.getFormat())
            {
            case Array:
                return xArray.INSTANCE;

            case Int64:
                return xInt64.INSTANCE;

            case IntLiteral:
            case Int8:
            case Int16:
            case Int32:
            case Int128:
            case VarInt:
            case UInt8:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case VarUInt:
            case FPLiteral:
            case Float16:
            case Float32:
            case Float64:
            case Float128:
            case VarFloat:
            case Dec32:
            case Dec64:
            case Dec128:
            case VarDec:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Char:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case String:
                return xString.INSTANCE;

            case Date:
            case Time:
            case DateTime:
            case Duration:
            case TimeInterval:
            case Version:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case SingletonConst:
                {
                SingletonConstant constEnum = (SingletonConstant) constValue;
                ClassTemplate template = f_types.getTemplate(constEnum.getValue());
                assert (template.isSingleton());
                return template;
                }

            case Tuple:
                return xTuple.INSTANCE;

            case UInt8Array:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Set:
            case MapEntry:
            case Map:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Module:
                return xModule.INSTANCE;

            case Package:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Property:
                return xProperty.INSTANCE;

            case Method:
                return Function.INSTANCE;

            case TerminalType:
            case AnnotatedType:
            case ParameterizedType:
                return xClass.INSTANCE;

            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                return xType.INSTANCE;

            case ConditionNot:
            case ConditionAll:
            case ConditionAny:
            case ConditionNamed:
            case ConditionPresent:
            case ConditionVersionMatches:
            case ConditionVersioned:
            case MultiMethod:
            case Register:
            case Signature:
            case Typedef:
            case Class:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case UnresolvedName:
            default:
                throw new IllegalStateException(constValue.toString());
            }
        }
    }
