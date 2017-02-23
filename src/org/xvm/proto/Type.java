package org.xvm.proto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Type is simply a collection of properties and methods.
 *
 * @author gg 2017.02.16
 */
public class Type
    {
    private int m_nId;

    private Map<String, PropertyTypelet> m_props = new HashMap<>();
    private Map<String, MultiMethodTypelet> m_methods = new HashMap<>();
    private Map<Integer, Relation> m_relations = new HashMap<>(); // cached type relations
    private boolean m_fTrusted;

    public int getId()
        {
        int id = m_nId;
        assert(id > 0);
        return id;
        }

    public void setId(int id)
        {
        assert m_nId == 0;
        m_nId = id;
        }


    /**
     * @return  0 if the specified type is equal to this one; 1 if this type extends it; -1 otherwise
     */
    public Relation extension(Type type)
        {
        int nCompare = 0;
        if (nCompare == 1)
            {
            // cache the results
            m_relations.put(type.getId(), Relation.SUB);
            return Relation.SUB;
            }
        else if (nCompare == -1)
            {
            m_relations.put(type.getId(), Relation.SUPER);
            return Relation.SUPER;
            }
        else
            {
            return Relation.EQUAL;
            }
        }

    public enum Relation {EQUAL, SUPER, SUB, INCOMPATIBLE};

    public static class PropertyTypelet
        {
        Type m_type;
        }

    public static class MultiMethodTypelet
        {
        Set<MethodTypelet> m_setMethods;
        }

    public static class MethodTypelet
        {
        Type[] m_typeArg;
        Type[] m_typeRet;
        }

    }
