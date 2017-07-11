package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.*;
import org.xvm.proto.template.xEnum;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type registry.
 *
 * @author gg 2017.02.15
 */
public class TypeSet
    {
    private int m_nMaxTypeId = 0;
    private Map<Integer, Type> m_mapTypes = new ConcurrentHashMap<>();

    public final Container f_container;
    final public Adapter f_adapter;

    // cache - ClassTemplates by name
    final private Map<String, ClassTemplate> m_mapTemplatesByName = new HashMap<>();

    // cache - ClassTemplates by ClassConstant
    final private Map<IdentityConstant, ClassTemplate> m_mapTemplatesByConst = new HashMap<>();

    // cache - TypeCompositions for constants keyed by the ClassConstId from the ConstPool
    private Map<Integer, TypeComposition> m_mapConstCompositions = new TreeMap<>(Integer::compare);

    TypeSet(Container pool)
        {
        f_container = pool;
        f_adapter = pool.f_adapter;
        }

    // ----- templates -----

    public ClassTemplate getTemplate(String sName)
        {
        // for core classes only
        ClassTemplate template = m_mapTemplatesByName.get(sName);
        if (template == null)
            {
            ClassConstant constClass = f_container.f_pool.ensureEcstasyClassConstant(sName);
            template = getTemplate(constClass);
            m_mapTemplatesByName.put(sName, template);
            }
        return template;
        }

    public ClassTemplate getTemplate(IdentityConstant constClass)
        {
        ClassTemplate template = m_mapTemplatesByConst.get(constClass);
        if (template == null)
            {
            ClassStructure structClass = (ClassStructure) constClass.getComponent();
            if (structClass == null)
                {
                throw new RuntimeException("Missing class structure: " + constClass);
                }

            String sName = structClass.getName();
            try
                {
                template = loadCustomTemplate(sName, structClass);
                }
            catch (ClassNotFoundException e)
                {
                System.out.println("Generating template for " + sName);

                switch (structClass.getFormat())
                    {
                    case ENUMVALUE:
                        try
                            {
                            String sEnumName = structClass.getParent().getName();
                            template = loadCustomTemplate(sEnumName, structClass);
                            }
                        catch (ClassNotFoundException e2)
                            {
                            template = new xEnum(this, structClass, false);
                            }
                        break;

                    case CLASS:
                    case INTERFACE:
                        template = new xObject(this, structClass, false);
                        break;

                    case SERVICE:
                        template = new xService(this, structClass, false);
                        break;

                    default:
                        throw new UnsupportedOperationException("Format is not supported: " + structClass);
                    }
                }

            m_mapTemplatesByConst.put(constClass, template);
            template.initDeclared();
            }

        return template;
        }

    private ClassTemplate loadCustomTemplate(String sName, ClassStructure structClass)
            throws ClassNotFoundException
        {
        String sSuffix = sName.substring(sName.lastIndexOf('.') + 1);

        String sClz= "org.xvm.proto.template.x" + sSuffix;
        Class<ClassTemplate> clz = (Class<ClassTemplate>) Class.forName(sClz);

        try
            {
            return clz.getConstructor(TypeSet.class, ClassStructure.class, Boolean.TYPE).
                    newInstance(this, structClass, Boolean.TRUE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("Constructor failed for " + clz.getName(), e);
            }
        }

    // ----- TypeCompositions -----

    // produce a TypeComposition based on the specified ClassTypeConstant
    public TypeComposition resolve(ClassTypeConstant constClassType)
        {
        ClassConstant constClass = constClassType.getClassConstant();
        List<TypeConstant> listParams = constClassType.getTypeConstants();

        int cParams = listParams.size();
        if (cParams == 0)
            {
            return getTemplate(constClass).resolve(Collections.EMPTY_MAP);
            }

        ClassStructure clazz = (ClassStructure) constClass.getComponent();
        List<Map.Entry<CharStringConstant, TypeConstant>> listFormalTypes = clazz.getTypeParamsAsList();

        assert listFormalTypes.size() == listParams.size();

        Map<String, Type> mapParams = new HashMap<>();
        for (int i = 0, c = listParams.size(); i < c; i++)
            {
            Map.Entry<CharStringConstant, TypeConstant> entryFormal = listFormalTypes.get(i);
            String sParamName = entryFormal.getKey().getValue();
            TypeConstant constTypeActual = listParams.get(i);

            if (constTypeActual instanceof ClassTypeConstant)
                {
                ClassTypeConstant constParamClass = (ClassTypeConstant) constTypeActual;
                mapParams.put(sParamName, resolve(constParamClass).ensurePublicType());
                }
            else if (constTypeActual instanceof IntersectionTypeConstant ||
                    constTypeActual instanceof UnionTypeConstant)
                {
                throw new UnsupportedOperationException("TODO");
                }
            else
                {
                throw new IllegalArgumentException("Unresolved type constant: " + constTypeActual);
                }
            }
        return getTemplate(constClass).resolve(mapParams);
        }

    // ensure a TypeComposition for a type referred by a ClassConstant in the ConstantPool
    public TypeComposition ensureComposition(Frame frame, int nClassConstId)
        {
        TypeComposition typeComposition = m_mapConstCompositions.get(nClassConstId);
        if (typeComposition == null)
            {
            // TODO: what if the constant is not a CTC, but TypeParameterTypeConstant,
            // does it need to be resolved by using frame.getThis().f_clazz?
            ClassTypeConstant constTypeClass = (ClassTypeConstant)
                    f_container.f_pool.getConstant(nClassConstId); // must exist

            typeComposition = resolve(constTypeClass);

            m_mapConstCompositions.put(nClassConstId, typeComposition);
            }
        return typeComposition;
        }

    // ----- Types -----

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

    public Type createType(ClassTemplate template, Map<String, Type> mapGenericActual, Constant.Access access)
        {
        Type type = new Type(template.f_sName);
        // TODO create the specified type

        addType(type);
        return type;
        }
    }
