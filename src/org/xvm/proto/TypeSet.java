package org.xvm.proto;

import java.util.*;
import java.util.function.Consumer;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class TypeSet
    {
    private int m_nMaxTypeId = 0;
    private Map<Integer, Type> m_mapTypes = new TreeMap<>(Integer::compare);
    private Map<String, TypeCompositionTemplate> m_mapTemplates = new TreeMap<>();
    private Map<String, TypeComposition> m_mapCompositions = new TreeMap<>();

    public Type getType(int nTypeId)
        {
        return m_mapTypes.get(nTypeId);
        }

    public int addType(Type type)
        {
        int nTypeId = type.getId();
        if (nTypeId == 0)
            {
            type.setId(nTypeId = ++m_nMaxTypeId);
            }
        else
            {
            m_nMaxTypeId = Math.max(m_nMaxTypeId, nTypeId);
            }

        if (m_mapTypes.putIfAbsent(nTypeId, type) != null)
            {
            throw new IllegalArgumentException("TypeId is already used:" + type);
            }

        return nTypeId;
        }

    public Type intern(Type type)
        {
        for (Type t : m_mapTypes.values())
            {
            if (type.calculateRelation(t) == Type.Relation.EQUAL)
                {
                return t;
                }
            }
        addType(type);
        return type;
        }

    public TypeCompositionTemplate getCompositionTemplate(String sName)
        {
        return m_mapTemplates.get(sName);
        }
    public boolean existsCompositionTemplate(String sName)
        {
        return m_mapTemplates.containsKey(sName);
        }
    public void addCompositionTemplate(TypeCompositionTemplate template)
        {
        String sName = template.m_sName;
        if (m_mapTemplates.putIfAbsent(sName, template) != null)
            {
            throw new IllegalArgumentException("CompositionTemplateName is already used:" + sName);
            }
        template.initDeclared();
        }
    public void forEachComposition(Consumer<TypeCompositionTemplate> consumer)
        {
        m_mapTemplates.values().forEach(consumer::accept);
        }

    public TypeCompositionTemplate ensureTemplate(String sName)
        {
        TypeCompositionTemplate template = getCompositionTemplate(sName);
        if (template == null && !m_setMisses.contains(sName))
            {
            System.out.println(sName);

            String sSuffix = sName.substring(sName.indexOf(':') + 1).substring(sName.lastIndexOf('.') + 1);
            String sClz= "org.xvm.proto.template.x" + sSuffix;
            try
                {
                Class<TypeCompositionTemplate> clz = (Class<TypeCompositionTemplate>) Class.forName(sClz);

                template = clz.getConstructor(TypeSet.class).newInstance(this);

                addCompositionTemplate(template);
                }
            catch (Throwable e)
                {
                System.out.println("Missing template for " + sName);
                m_setMisses.add(sName);
                // throw new RuntimeException(e);
                }
            }
        return template;
        }

    private Set<String> m_setMisses = new HashSet<>();

    public TypeComposition getComposition(String sName)
        {
        return m_mapCompositions.get(sName);
        }
    public void addComposition(TypeComposition composition)
        {
        String sName = composition.m_sName;
        if (m_mapCompositions.putIfAbsent(sName, composition) != null)
            {
            throw new IllegalArgumentException("CompositionName is already used:" + composition);
            }
        }

    public void dumpTemplates()
        {
        m_mapTemplates.values().forEach(template ->
        {
        System.out.print("\n\n### Composition for ");
        System.out.println(template);
        });
        }

    public String dumpCompositions()
        {
        StringBuilder sb = new StringBuilder();

        m_mapCompositions.entrySet().forEach(entry ->
                sb.append('\n').append(entry.getKey()).append(':').append(entry.getValue()));

        return sb.toString();
        }

    public String dumpTypes()
        {
        StringBuilder sb = new StringBuilder();

        m_mapTypes.entrySet().forEach(entry ->
                sb.append('\n').append(entry.getKey()).append(':').append(entry.getValue()));

        return sb.toString();
        }

    }
