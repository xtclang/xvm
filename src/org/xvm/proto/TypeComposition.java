package org.xvm.proto;

/**
 * TypeComposition (e.g. ArrayList<String>)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    TypeCompositionTemplate m_template;

    // at the moment, ignore the case of ArrayList<Runnable | String>
    TypeName[] m_atnGenericActual; // corresponding to the m_template's GenericTypeName

    String m_sSignature; // concatenation of the template name and the generic parameters

    Type m_typePublic;
    Type m_typeProtected;
    Type m_typePrivate;

    TypeComposition(TypeCompositionTemplate template, TypeName[] atnGenericActual)
        {
        m_template = template;
        m_atnGenericActual = atnGenericActual;
        m_sSignature = template.m_sName + Formatting.formatArray(atnGenericActual, "<", ">", ", ");
        }

    Type getPublicType()
        {
        Type type = m_typePublic;
        if (type == null)
            {
            // type = m_typePublic = m_template.createType(m_asGenericActual, TypeCompositionTemplate.Access.Public);
            }
        return type;
        }
    }
