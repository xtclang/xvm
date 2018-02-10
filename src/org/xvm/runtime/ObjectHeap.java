package org.xvm.runtime;


import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * Heap and constants.
 */
public class ObjectHeap
    {
    public final TemplateRegistry f_templates;
    public final ConstantPool f_pool;

    private Map<Integer, ObjectHandle> m_mapConstants = new ConcurrentHashMap<>();

    public ObjectHeap(ConstantPool pool, TemplateRegistry templates)
        {
        f_templates = templates;
        f_pool = pool;
        }

    // nValueConstId -- "literal" (Int/String/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(Frame frame, int nValueConstId)
        {
        // we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        Map<Integer, ObjectHandle> mapConstants = m_mapConstants;
        ObjectHandle hValue = mapConstants.get(nValueConstId);
        if (hValue != null)
            {
            return hValue;
            }

        hValue = createConstHandle(frame, nValueConstId);

        ObjectHandle hValue0 = mapConstants.putIfAbsent(nValueConstId, hValue);

        return hValue0 == null ? hValue : hValue0;
        }

    private ObjectHandle createConstHandle(Frame frame, int nValueConstId)
        {
        Constant constValue = f_pool.getConstant(nValueConstId);

        if (constValue instanceof SingletonConstant)
            {
            ObjectHandle hValue = ((SingletonConstant) constValue).getHandle();
            if (hValue != null)
                {
                return hValue;
                }
            System.out.println("unresolved " + constValue);
            }

        TypeConstant type = getConstType(constValue);

        ClassTemplate template = f_templates.getTemplate(type); // must exist

        ObjectHandle hValue = template.createConstHandle(frame, constValue);
        if (hValue == null)
            {
            throw new IllegalStateException("Invalid constant " + constValue);
            }
        return hValue;
        }

    /**
     * Obtain a canonical type for the specified constant.
     */
    protected TypeConstant getConstType(Constant constValue)
        {
        switch (constValue.getFormat())
            {
            case Array:
                return f_pool.typeArray();

            case Int64:
                return f_pool.typeInt();

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
                return f_pool.typeString();

            case Date:
            case Time:
            case DateTime:
            case Duration:
            case TimeInterval:
            case Version:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case SingletonConst:
                {
                IdentityConstant constId = ((SingletonConstant) constValue).getValue();

                assert ((ClassStructure) constId.getComponent()).isSingleton();

                return constId.asTypeConstant();
                }

            case Tuple:
                return f_pool.typeTuple();

            case UInt8Array:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Set:
            case MapEntry:
            case Map:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Module:
                return f_pool.typeModule();

            case Package:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
                return f_pool.typeClass();

            case Property:
                return f_pool.typeProperty();

            case Method:
                return f_pool.typeFunction();

            case AnnotatedType:
            case ParameterizedType:
            case TerminalType:
                return f_pool.typeClass(); // REVIEW type vs. class

            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                return f_pool.typeType();

            case MultiMethod:
            case Register:
            case Signature:
            case Typedef:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            default:
                throw new IllegalStateException(constValue.toString());
            }
        }
    }
