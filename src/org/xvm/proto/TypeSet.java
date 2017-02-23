package org.xvm.proto;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class TypeSet
    {
    public Type getType(int nTypeId)
        {
        return m_mapTypes.get(nTypeId);
        }

    public int addType(Type type)
        {
        m_mapTypes.put(++m_nMaxId, type);
        return m_nMaxId;
        }

    public Type intern(Type type)
        {
        for (Type t : m_mapTypes.values())
            {
            if (type.extension(t) == Type.Relation.EQUAL)
                {
                return t;
                }
            }
        addType(type);
        return type;
        }

    private int m_nMaxId = 0;
    private Map<Integer, Type> m_mapTypes = new HashMap<>(); // TODO LongArray
    }
