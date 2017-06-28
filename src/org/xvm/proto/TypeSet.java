package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.TypeConstant;

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

    // cache - ClassTemplates by name
    final private Map<String, ClassTemplate> m_mapTemplatesByName = new HashMap<>();

    // cache - ClassTemplates by ClassConstant
    final private Map<ClassConstant, ClassTemplate> m_mapTemplatesByConst = new HashMap<>();

    // cache - TypeCompositions for constants keyed by the ClassConstId from the ConstPool
    private Map<Integer, TypeComposition> m_mapConstCompositions = new TreeMap<>(Integer::compare);

    TypeSet(Container pool)
        {
        f_container = pool;
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

    public ClassTemplate getTemplate(ClassConstant constClass)
        {
        ClassTemplate template = m_mapTemplatesByConst.get(constClass);
        if (template == null)
            {
            ClassStructure structClass = (ClassStructure) constClass.getComponent();
            String sName = structClass.getName();

            String sSuffix = sName.substring(sName.indexOf(':') + 1);
            sSuffix = sSuffix.substring(sSuffix.lastIndexOf('.') + 1);

            String sClz= "org.xvm.proto.template.x" + sSuffix;
            try
                {
                Class<ClassTemplate> clz = (Class<ClassTemplate>) Class.forName(sClz);

                template = clz.getConstructor(TypeSet.class).newInstance(this);
                }
            catch (ClassNotFoundException e)
                {
                System.out.println("Missing template for " + sName);
                // throw new RuntimeException(e);
                }
            catch (Throwable e)
                {
                e.printStackTrace();
                }
            m_mapTemplatesByConst.put(constClass, template);
            }
        return template;
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
            return resolve(constClass, Utils.TYPE_NONE);
            }

        TypeComposition[] aClz = new TypeComposition[cParams];
        int iParam = 0;
        for (TypeConstant constParamType : listParams)
            {
            if (constParamType instanceof ClassTypeConstant)
                {
                ClassTypeConstant constParamClass = (ClassTypeConstant) constParamType;
                aClz[iParam++] = resolve(constParamClass);
                }
            else
                {
                throw new IllegalArgumentException("Invalid param type constant: " + constParamType);
                }
            }
        return resolve(constClass, aClz);
        }

    // produce a TypeComposition for the ClassConstant by resolving the generic types
    public TypeComposition resolve(ClassConstant constClass, TypeComposition[] aclzGenericActual)
        {
        int    c = aclzGenericActual.length;
        Type[] aType = new Type[c];
        for (int i = 0; i < c; i++)
            {
            aType[i] = aclzGenericActual[i].ensurePublicType();
            }
        return resolve(constClass, aType);
        }

    // produce a TypeComposition for the ClassConstant by resolving the generic types
    public TypeComposition resolve(ClassConstant constClass, Type[] atGenericActual)
        {
        return getTemplate(constClass).resolve(atGenericActual);
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

    public Type createType(ClassTemplate template, Type[] atGenericActual, Constant.Access access)
        {
        Type type = new Type(template.f_sName);
        // TODO create the specified type

        addType(type);
        return type;
        }
    }
