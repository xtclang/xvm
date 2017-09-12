package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.template.Const;
import org.xvm.proto.template.Enum;
import org.xvm.proto.template.Function;
import org.xvm.proto.template.Ref;
import org.xvm.proto.template.Service;
import org.xvm.proto.template.xObject;

import java.io.File;

import java.net.URL;

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

    // native templates
    final private Map<String, Class> f_mapTemplateClasses = new HashMap<>();

    // cache - ClassTemplates by name
    final private Map<String, ClassTemplate> f_mapTemplatesByName = new HashMap<>();

    // cache - TypeCompositions for constants keyed by the ClassConstId from the ConstPool
    final private Map<Integer, TypeComposition> f_mapConstCompositions = new TreeMap<>(Integer::compare);

    public final TypeConstant THIS;
    public final TypeConstant[] THIS_1;
    public final TypeConstant[] THIS_2;
    public final static TypeConstant[] VOID = new TypeConstant[0];

    TypeSet(Container container)
        {
        f_container = container;
        f_adapter = container.f_adapter;

        THIS = container.f_pool.ensureThisTypeConstant(Constants.Access.PUBLIC);
        THIS_1 = new TypeConstant[] {THIS};
        THIS_2 = new TypeConstant[] {THIS, THIS};

        loadNativeTemplates();
        }

    private void loadNativeTemplates()
        {
        Class clzObject = xObject.class;
        URL url = clzObject.getProtectionDomain().getCodeSource().getLocation();
        String sRoot = url.getFile();

        File dirNative = new File(sRoot, "org/xvm/proto/template");
        scanNativeDirectory(dirNative, "");
        }

    // sPackage is either empty or ends with a dot
    private void scanNativeDirectory(File dirNative, String sPackage)
        {
        for (String sName : dirNative.list())
            {
            if (sName.endsWith(".class"))
                {
                if (sName.startsWith("x") && !sName.contains("$"))
                    {
                    String sSimpleName = sName.substring(1, sName.length() - 6);
                    String sQualifiedName = sPackage + sSimpleName;
                    String sClass = "org.xvm.proto.template." + sPackage + "x" + sSimpleName;

                    try
                        {
                        f_mapTemplateClasses.put(sQualifiedName, Class.forName(sClass));
                        }
                    catch (ClassNotFoundException e)
                        {
                        throw new IllegalStateException("Cannot load " + sClass, e);
                        }
                    }
                }
            else
                {
                File dir = new File(dirNative, sName);
                if (dir.isDirectory())
                    {
                    scanNativeDirectory(dir, sPackage.isEmpty() ? sName + '.' : sPackage + sName + '.');
                    }
                }
            }
        }

    protected void initNativeInterfaces()
        {
        // initialize necessary INSTANCE references
        new Enum(this, (ClassStructure) getClassConstant("Enum").getComponent(), true).initDeclared();
        new Const(this, (ClassStructure) getClassConstant("Const").getComponent(), true).initDeclared();
        new Function(this, (ClassStructure) getClassConstant("Function").getComponent(), true).initDeclared();
        new Service(this, (ClassStructure) getClassConstant("Service").getComponent(), true).initDeclared();
        new Ref(this, (ClassStructure) getClassConstant("Ref").getComponent(), true).initDeclared();
        }

    // ----- templates -----

    public ClassConstant getClassConstant(String sName)
        {
        // TODO: plug in module repositories
        ModuleStructure module = f_container.f_module;

        Component component = module.getChildByPath(sName);
        if (component instanceof ClassStructure)
            {
            return (ClassConstant) component.getIdentityConstant();
            }

        throw new IllegalArgumentException("Non-existing component: " + sName);
        }

    public ClassTemplate getTemplate(String sName)
        {
        // for core classes only
        ClassTemplate template = f_mapTemplatesByName.get(sName);
        if (template == null)
            {
            template = getTemplate(getClassConstant(sName));
            f_mapTemplatesByName.put(sName, template);
            }
        return template;
        }

    public ClassTemplate getTemplate(IdentityConstant constClass)
        {
        String sName = constClass.getPathString();
        ClassTemplate template = f_mapTemplatesByName.get(sName);
        if (template == null)
            {
            Component struct = constClass.getComponent();
            ClassStructure structClass = (ClassStructure) struct;
            if (structClass == null)
                {
                throw new RuntimeException("Missing class structure: " + constClass);
                }

            template = getNativeTemplate(sName, structClass);
            if (template == null)
                {
                // System.out.println("***** Generating template for " + sName);

                switch (structClass.getFormat())
                    {
                    case ENUMVALUE:
                        String sEnumName = structClass.getParent().getName();
                        template = getNativeTemplate(sEnumName, structClass);
                        if (template == null)
                            {
                            template = new Enum(this, structClass, false);
                            }
                        // no need to call initDeclared() for the values
                        break;

                    case ENUM:
                        template = new Enum(this, structClass, false);
                        template.initDeclared();
                        break;

                    case CLASS:
                    case INTERFACE:
                    case MIXIN:
                        template = new xObject(this, structClass, false);
                        break;

                    case SERVICE:
                        template = new Service(this, structClass, false);
                        break;

                    case CONST:
                        template = new Const(this, structClass, false);
                        break;

                    default:
                        throw new UnsupportedOperationException("Format is not supported: " + structClass);
                    }

                // we don't call initDeclared again
                f_mapTemplatesByName.put(sName, template);
                }
            else
                {
                f_mapTemplatesByName.put(sName, template);
                template.initDeclared();
                }
            }

        return template;
        }

    private ClassTemplate getNativeTemplate(String sName, ClassStructure structClass)
        {
        Class<ClassTemplate> clz = f_mapTemplateClasses.get(sName);
        if (clz == null)
            {
            return null;
            }

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

    // ensure a TypeComposition for a type referred by a ClassConstant in the ConstantPool
    public TypeComposition ensureComposition(int nClassConstId)     // TODO GG add target
        {
        TypeComposition typeComposition = f_mapConstCompositions.get(nClassConstId);
        if (typeComposition == null)
            {
            TypeConstant constType = (TypeConstant)
                    f_container.f_pool.getConstant(nClassConstId); // must exist

            typeComposition = resolveComposition(constType);

            f_mapConstCompositions.put(nClassConstId, typeComposition);
            }
        return typeComposition;
        }

    // produce a TypeComposition based on the specified TypeConstant
    protected TypeComposition resolveComposition(TypeConstant constType)
        {
        Constant constId = constType.getDefiningConstant();
        String   sParam;
        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
                ClassTemplate template = getTemplate((IdentityConstant) constId);
                return template.resolveClass(constType, Collections.EMPTY_MAP); // TODO

            case Register:
                RegisterConstant constReg = (RegisterConstant) constId;
                MethodStructure method = (MethodStructure) constReg.getMethod().getComponent();
                sParam = method.getParam(constReg.getRegister()).getName();
                break;

            case Property:
                sParam  = ((PropertyConstant) constId).getName();
                break;

            default:
                throw new IllegalStateException("unsupported constant: " + constId);
            }

        /* TODO
        Type type = f_mapGenericActual.get(sParam);
        return type == null || type.f_clazz == null
                ? xObject.CLASS
                : type.f_clazz;
        */
        throw new IllegalArgumentException("Unresolved type constant: " + constType);
        }

    // resolve a parameter type given a map of actual parameter types
    public Type resolveParameterType(TypeConstant constType, Map<String, Type> mapActual)
        {
        // TODO merge with above and/or TypeComposition logic
        Constant constId = constType.getDefiningConstant();
        String   sParam;
        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
                ClassTemplate template = getTemplate((IdentityConstant) constId);
                return template.resolveClass(constType, mapActual).ensurePublicType();

            case Register:
                RegisterConstant constReg = (RegisterConstant) constId;
                MethodStructure  method = (MethodStructure) constReg.getMethod().getComponent();
                sParam = method.getParam(constReg.getRegister()).getName();
                break;

            case Property:
                sParam  = ((PropertyConstant) constId).getName();
                break;

            default:
                throw new IllegalStateException("unsupported constant: " + constId);
            }

        Type type = mapActual.get(sParam);
        return type == null ? xObject.TYPE : type;
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

    public Type createType(TypeComposition clazz, Constant.Access access)
        {
        Type type = new Type(clazz);
        // TODO create the specified type

        addType(type);
        return type;
        }
    }
