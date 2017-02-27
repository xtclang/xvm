package org.xvm.proto;

import java.util.Map;

/**
 * TypeComposition (e.g. ArrayList<String>)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    TypeCompositionTemplate m_template;
    Type[] m_atActualGeneric; // corresponding to the m_template's composite name

    Type m_typePublic;
    Type m_typeProtected;
    Type m_typePrivate;

    }
