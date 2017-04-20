package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.Access;
import org.xvm.proto.TypeCompositionTemplate.Shape;

import java.util.Arrays;

/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String>)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    public final TypeCompositionTemplate f_template;

    // at the moment, ignore the case of ArrayList<Runnable | String>
    public final Type[] f_atGenericActual; // corresponding to the m_template's GenericTypeName

    private Type m_typePublic;
    private Type m_typeProtected;
    private Type m_typePrivate;
    private Type m_typeStruct;

    public TypeComposition(TypeCompositionTemplate template, Type[] atnGenericActual)
        {
        // assert(atnGenericActual.length == template.f_asFormalType.length);

        f_template = template;
        f_atGenericActual = atnGenericActual;
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.f_clazz == this;

        Type typeCurrent = handle.m_type;
        Type typeTarget;

        switch (access)
            {
            case Public:
                typeTarget = ensurePublicType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case Protected:
                typeTarget = ensureProtectedType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case Private:
                typeTarget = ensurePrivateType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case Struct:
                typeTarget = ensureStructType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        handle = handle.cloneHandle();
        handle.m_type = typeTarget;
        return handle;
        }

    public Type ensurePublicType()
        {
        Type type = m_typePublic;
        if (type == null)
            {
            m_typePublic = type = f_template.createType(f_atGenericActual, TypeCompositionTemplate.Access.Public);
            }
        return type;
        }
    public Type ensureProtectedType()
        {
        Type type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_template.createType(f_atGenericActual, TypeCompositionTemplate.Access.Protected);
            }
        return type;
        }

    public Type ensurePrivateType()
        {
        Type type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_template.createType(f_atGenericActual, TypeCompositionTemplate.Access.Private);
            }
        return type;
        }

    public Type ensureStructType()
        {
        Type type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_template.createType(f_atGenericActual, TypeCompositionTemplate.Access.Struct);
            }
        return type;
        }

    public boolean isStruct(Type type)
        {
        return type == m_typeStruct;
        }

    // does this class extend that?
    public boolean extends_(TypeComposition that)
        {
        assert that.f_template.f_shape != Shape.Interface;

        if (this.f_template.extends_(that.f_template))
            {
            // TODO: check the generic type relationship
            return true;
            }

        return false;
        }

    public Type resolveFormalType(String sFormalName)
        {
        TypeCompositionTemplate template = f_template;
        String[] asFormalName = template.f_asFormalType;

        for (int i = 0, c = asFormalName.length; i < c; i++)
            {
            if (asFormalName[i].equals(sFormalName))
                {
                return f_atGenericActual[i];
                }
            }
        throw new IllegalArgumentException("Invalid formal name: " + sFormalName);
        }

    // create an un-initialized handle for this class
    public ObjectHandle createHandle()
        {
        // TODO: ByComposition may not have a single template
        return f_template.createHandle(this);
        }

    @Override
    public String toString()
        {
        return f_template.f_sName + Utils.formatArray(f_atGenericActual, "<", ">", ", ");
        }
    }
