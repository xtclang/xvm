package org.xvm.proto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Type is simply a collection of properties and methods.
 *
 * @author gg 2017.02.16
 */
public class Type
    {
    final private String f_sName; // optional
    private int m_nId;

    private Map<String, PropertyTypelet> m_props = new HashMap<>();
    private Map<String, MultiMethodTypelet> m_methods = new HashMap<>();
    private boolean m_fConstant;

    private Map<Integer, Relation> m_relations = new HashMap<>(); // cached type relations

    public Type(String sName)
        {
        f_sName = sName;
        }

    public int getId()
        {
        return m_nId;
        }

    public void setId(int id)
        {
        assert m_nId == 0;
        m_nId = id;
        }

    public void addProperty(String sName, Type type)
        {

        }

    public void resolve(TypeSet typeset)
        {

        }

    /**
     * @return  Relation between this and that type
     */
    public Relation calculateRelation(Type that)
        {
        Relation relation = Relation.INCOMPATIBLE;

        // TODO: compare

        if (relation != Relation.EQUAL)
            {
            // cache the results
            m_relations.put(that.getId(), Relation.SUB);
            }

        return relation;
        }

    // SUPER == assignable from; SUB == assignable to
    public enum Relation {EQUAL, SUPER, SUB, INCOMPATIBLE};

    @Override
    public int hashCode()
        {
        assert m_nId != 0;

        return m_nId;
        }

    @Override
    public boolean equals(Object obj)
        {
        assert m_nId != 0;

        Type that = (Type) obj;
        return that.m_nId == this.m_nId;
        }

    @Override
    public String toString()
        {
        return f_sName;
        }

    // ----- debugging support ------

    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("Id=").append(m_nId);
        if (f_sName != null)
            {
            sb.append(" Name=").append(f_sName);
            }
        if (m_fConstant)
            {
            sb.append(" constant");
            }
        if (!m_props.isEmpty())
            {
            sb.append("\nProperties:");
            for (PropertyTypelet t : m_props.values())
                {
                sb.append(t);
                }

            sb.append("\nMethods:");
            for (MultiMethodTypelet t : m_methods.values())
                {
                sb.append(t);
                }
            }
        return sb.toString();
        }

    public static class PropertyTypelet
        {
        String m_sName;
        Type m_type;

        PropertyTypelet(String sName, Type type)
            {
            m_sName = sName;
            m_type = type;
            }

        @Override
        public String toString()
            {
            // e.g. "#17 name"
            return "\n  #" + m_type + " " + m_sName;
            }
        }

    public static class MultiMethodTypelet
        {
        String m_sName;
        Set<MethodTypelet> m_setMethods;

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            for (MethodTypelet mt : m_setMethods)
                {
                sb.append("\n  ").append(mt);
                }
            return sb.toString();
            }

        protected class MethodTypelet
            {
            Type[] m_typeArg;
            Type[] m_typeRet;

            MethodTypelet(Type[] typeArg, Type[] typeRet)
                {
                m_typeArg = typeArg;
                m_typeRet = typeRet;
                }

            @Override
            public String toString()
                {
                // e.g. "(#17) foo(#11, #34)"
                StringBuilder sb = new StringBuilder();
                sb.append('(');
                for (int i = 0, c = m_typeRet.length; i < c; i++)
                    {
                    sb.append('#').append(m_typeRet[i].m_nId);
                    if (i < c)
                        {
                        sb.append(", ");
                        }
                    }
                sb.append(") ").append(MultiMethodTypelet.this.m_sName);

                sb.append('(');
                for (int i = 0, c = m_typeArg.length; i < c; i++)
                    {
                    sb.append('#').append(m_typeArg[i].m_nId);
                    if (i < c)
                        {
                        sb.append(", ");
                        }
                    }
                sb.append(')');
                return sb.toString();
                }
            }
        }
    }
