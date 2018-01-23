package org.xvm.runtime;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * Heap and constants.
 */
public class ObjectHeap
    {
    public final TemplateRegistry f_templates;
    public final ConstantPool f_pool;

    Map<Integer, ObjectHandle> m_mapConstants = new HashMap<>();

    public ObjectHeap(ConstantPool pool, TemplateRegistry templates)
        {
        f_templates = templates;
        f_pool = pool;
        }

    // nValueConstId -- "literal" (Int/String/etc.) Constant known by the ConstantPool
    public ObjectHandle ensureConstHandle(Frame frame, int nValueConstId)
        {
        return m_mapConstants.computeIfAbsent(nValueConstId, nConstId ->
            {
            Constant constValue = f_pool.getConstant(nConstId);

            TypeConstant type = getConstType(constValue);

            ClassTemplate template = f_templates.getTemplate(type); // must exist

            return template.createConstHandle(frame, constValue);
            });
        }

    /**
     * Obtain a canonical type for the specified constant.
     */
    public TypeConstant getConstType(Constant constValue)
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
                SingletonConstant constEnum = (SingletonConstant) constValue;
                TypeConstant type = constEnum.getValue().asTypeConstant();

                ClassTemplate template = f_templates.getTemplate(constEnum.getValue());

                boolean fStatic = ((ClassStructure) type.getSingleUnderlyingClass().getComponent()).isStatic();
                boolean fSingle = ((ClassStructure) type.getSingleUnderlyingClass().getComponent()).isSingleton();

                assert type.isClassType() &&
                    ((ClassStructure) type.getSingleUnderlyingClass().getComponent()).isStatic();

                return type;
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
