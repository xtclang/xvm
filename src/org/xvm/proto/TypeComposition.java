package org.xvm.proto;

/**
 * TypeComposition (e.g. ArrayList<String>)
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

    @Override
    public String toString()
        {
        return f_template.f_sName + Utils.formatArray(f_atGenericActual, "<", ">", ", ");
        }
    }
