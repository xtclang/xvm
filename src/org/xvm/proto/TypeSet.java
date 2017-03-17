package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The type registry.
 *
 * @author gg 2017.02.15
 */
public class TypeSet
    {
    private int m_nMaxTypeId = 0;
    private Map<Integer, Type> m_mapTypes = new TreeMap<>(Integer::compare);
    private Map<String, TypeCompositionTemplate> m_mapTemplates = new TreeMap<>();
    private Map<String, String> m_mapAliases = new TreeMap<>();

    public final ConstantPoolAdapter f_constantPool;

    // cache - TypeCompositions for constants keyed by the ClassConstId from the ConstPool
    private Map<Integer, TypeComposition> m_mapConstCompositions = new TreeMap<>(Integer::compare);

    TypeSet(ConstantPoolAdapter adapter)
        {
        f_constantPool = adapter;
        }

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

    // ----- TypeCompositionTemplates -----

    public TypeCompositionTemplate getTemplate(String sName)
        {
        return m_mapTemplates.get(sName);
        }
    public boolean existsTemplate(String sName)
        {
        return m_mapTemplates.containsKey(sName);
        }
    public void addTemplate(TypeCompositionTemplate template)
        {
        String sName = template.f_sName;
        if (m_mapTemplates.putIfAbsent(sName, template) != null)
            {
            throw new IllegalArgumentException("CompositionTemplateName is already used:" + sName);
            }

        f_constantPool.registerClass(template);

        template.initDeclared();
        }


    public TypeCompositionTemplate ensureTemplate(String sName)
        {
        TypeCompositionTemplate template = getTemplate(sName);
        if (template == null && !m_setMisses.contains(sName))
            {
            System.out.println(sName);

            String sAlias = m_mapAliases.get(sName);
            if (sAlias != null)
                {
                template = getTemplate(sAlias);
                if (template == null)
                    {
                    template = ensureTemplate(sAlias);
                    }
                m_mapTemplates.put(sName, template);
                return template;
                }

            String sSuffix = sName.substring(sName.indexOf(':') + 1);
            sSuffix = sSuffix.substring(sSuffix.lastIndexOf('.') + 1);

            String sClz= "org.xvm.proto.template.x" + sSuffix;
            try
                {
                Class<TypeCompositionTemplate> clz = (Class<TypeCompositionTemplate>) Class.forName(sClz);

                template = clz.getConstructor(TypeSet.class).newInstance(this);

                addTemplate(template);
                }
            catch (ClassNotFoundException e)
                {
                System.out.println("Missing template for " + sName);
                m_setMisses.add(sName);
                // throw new RuntimeException(e);
                }
            catch (Throwable e)
                {
                e.printStackTrace();
                }
            }
        return template;
        }

    // TODO: temporary
    private Set<String> m_setMisses = new HashSet<>();

    public void addAlias(String sAlias, String sRealName)
        {
        m_mapAliases.put(sAlias, sRealName);
        }

    // ----- TypeCompositions -----


    public TypeComposition getConstComposition(int nConstId)
        {
        return m_mapConstCompositions.get(nConstId);
        }

    // ensure a TypeComposition for a type referred by a ClassConstant in the ConstantPool
    public TypeComposition ensureConstComposition(int nClassConstId)
        {
        TypeComposition typeComposition = getConstComposition(nClassConstId);
        if (typeComposition == null)
            {
            ClassConstant classConstant = f_constantPool.getClassConstant(nClassConstId);   // must exist

            String sTemplate = ConstantPoolAdapter.getClassName(classConstant);
            TypeCompositionTemplate template = ensureTemplate(sTemplate);

            typeComposition = template.resolve(classConstant);

            m_mapConstCompositions.put(nClassConstId, typeComposition);
            }
        return typeComposition;
        }


    // ----- debugging -----

    public void dumpTemplates()
        {
        m_mapTemplates.values().forEach(template ->
                {
                System.out.print("\n\n### Composition for ");
                System.out.println(template);
                });
        }

    public String dumpConstCompositions()
        {
        StringBuilder sb = new StringBuilder();

        m_mapConstCompositions.entrySet().forEach(entry ->
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
