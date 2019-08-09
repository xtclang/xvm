package org.xvm.runtime;


import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.DeferredPropertyHandle;

import org.xvm.runtime.template.xException;


/**
 * Heap and constants.
 */
public class ObjectHeap
    {
    public final TemplateRegistry f_templates;
    public final ConstantPool f_poolRoot;

    private Map<Constant, ObjectHandle> m_mapConstants = new ConcurrentHashMap<>();

    public ObjectHeap(ConstantPool pool, TemplateRegistry templates)
        {
        f_poolRoot = pool;
        f_templates = templates;
        }

    /**
     * Return a handle for the specified constant (could be DeferredCallHandle).
     *
     * @param constValue "literal" (Int/String/etc.) constant known by the ConstantPool
     *
     * @return R_NEXT or R_CALL
     */
    public ObjectHandle ensureConstHandle(Frame frame, Constant constValue)
        {
        // NOTE: we cannot use computeIfAbsent, since createConstHandle can be recursive,
        // and ConcurrentHashMap is not recursion friendly
        Map<Constant, ObjectHandle> mapConstants = m_mapConstants;
        ObjectHandle hValue = mapConstants.get(constValue);
        if (hValue != null)
            {
            return hValue;
            }

        if (constValue instanceof SingletonConstant)
            {
            hValue = ((SingletonConstant) constValue).getHandle();
            return hValue == null
                ? new DeferredCallHandle(
                    xException.makeHandle("Uninitialized singleton: " + constValue))
                : saveConstHandle(constValue, hValue);
            }

        // support for the "local property" mode
        if (constValue instanceof PropertyConstant)
            {
            PropertyConstant  idProp = (PropertyConstant) constValue;
            PropertyStructure prop   = (PropertyStructure) idProp.getComponent();

            assert !prop.isStatic();

            return saveConstHandle(constValue, new DeferredPropertyHandle(idProp));
            }

        TypeConstant type = getConstType(constValue);

        ClassTemplate template = f_templates.getTemplate(type); // must exist

        switch (template.createConstHandle(frame, constValue))
            {
            case Op.R_NEXT:
                {
                hValue = frame.popStack();
                return constValue.isValueCacheable()
                    ? saveConstHandle(constValue, hValue)
                    : hValue;
                }

            case Op.R_CALL:
                Frame frameNext = frame.m_frameNext;
                if (constValue.isValueCacheable())
                    {
                    frameNext.addContinuation(frameCaller ->
                        {
                        saveConstHandle(constValue, frameCaller.peekStack());
                        return Op.R_NEXT;
                        });
                    }
                return new DeferredCallHandle(frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    private ObjectHandle saveConstHandle(Constant constValue, ObjectHandle hValue)
        {
        ObjectHandle hValue0 = m_mapConstants.putIfAbsent(constValue, hValue);
        return hValue0 == null ? hValue : hValue0;
        }

    /**
     * Obtain an object type for the specified constant.
     */
    public TypeConstant getConstType(Constant constValue)
        {
        switch (constValue.getFormat())
            {
            case Char:
            case String:
            case IntLiteral:
            case Bit:
            case Nibble:
            case Int8:
            case Int16:
            case Int32:
            case Int64:
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
            case Array:
            case UInt8Array:
            case Map:
            case Tuple:
            case Path:
            case FileStore:
            case FSDir:
            case FSFile:
            case Date:
            case Time:
            case DateTime:
            case Duration:
            case Interval:
            case Version:
                return constValue.getType();

            case Set:
            case MapEntry:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Module:
                return f_poolRoot.typeModule();

            case Package:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
                return f_poolRoot.typeClass();

            case Property:
                return f_poolRoot.typeProperty();

            case Method:
                return f_poolRoot.typeFunction();

            case AnnotatedType:
            case ParameterizedType:
            case TerminalType:
                return f_poolRoot.typeType();

            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                return f_poolRoot.typeType();

            case MultiMethod:
            case Signature:
            case Typedef:
            case TypeParameter:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            default:
                throw new IllegalStateException(constValue.toString());
            }
        }
    }
