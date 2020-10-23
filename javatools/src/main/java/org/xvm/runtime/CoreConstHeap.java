package org.xvm.runtime;


import org.xvm.asm.Constant;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * The Constant heap for the core Ecstasy container.
 */
public class CoreConstHeap
        extends ConstHeap
    {
    /**
     * Construct the core Constant heap for shared Ecstasy module.
     *
     * @param templates  the template registry
     */
    public CoreConstHeap(TemplateRegistry templates)
        {
        f_templates = templates;
        }

    @Override
    protected ClassTemplate getTemplate(Constant constValue)
        {
        return f_templates.getTemplate(getConstType(constValue)); // must exist
        }

    /**
     * Obtain an object type for the specified constant.
     */
    private TypeConstant getConstType(Constant constValue)
        {
        String sComponent;

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
            case IntN:
            case UInt8:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case UIntN:
            case FPLiteral:
            case BFloat16:
            case Float16:
            case Float32:
            case Float64:
            case Float128:
            case FloatN:
            case Dec32:
            case Dec64:
            case Dec128:
            case DecN:
            case Array:
            case UInt8Array:
            case Tuple:
            case Path:
            case Date:
            case Time:
            case DateTime:
            case Duration:
            case Range:
            case Version:
            case Module:
            case Package:
                return constValue.getType();

            case FileStore:
                sComponent = "_native.fs.CPFileStore";
                break;

            case FSDir:
                sComponent = "_native.fs.CPDirectory";
                break;

            case FSFile:
                sComponent = "_native.fs.CPFile";
                break;

            case Map:
                sComponent = "collections.ListMap";
                break;

            case Set:
                // see xArray.createConstHandle()
                sComponent = "collections.Array";
                break;

            case MapEntry:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
            case DecoratedClass:
                sComponent = "_native.reflect.RTClass";
                break;

            case PropertyClassType:
                sComponent = "_native.reflect.RTProperty";
                break;

            case Method:
                sComponent = ((MethodConstant) constValue).isFunction()
                        ? "_native.reflect.RTFunction" : "_native.reflect.RTMethod";
                break;

            case AnnotatedType:
            case ParameterizedType:
            case TerminalType:
            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                sComponent = "_native.reflect.RTType";
                break;

            case MultiMethod:   // REVIEW does the compiler ever generate this?
            case Typedef:       // REVIEW does the compiler ever generate this?
            case TypeParameter: // REVIEW does the compiler ever generate this?
            case Signature:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            default:
                throw new IllegalStateException(constValue.toString());
            }

        return f_templates.getComponent(sComponent).getIdentityConstant().getType();
        }

    /**
     * The registry.
     */
    private final TemplateRegistry f_templates;
    }
