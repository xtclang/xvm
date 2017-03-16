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
            type = f_template.createType(f_atGenericActual, TypeCompositionTemplate.Access.Public);
            }
        return type;
        }

    @Override
    public String toString()
        {
        return f_template.f_sName + Utils.formatArray(f_atGenericActual, "<", ">", ", ");
        }
    }
