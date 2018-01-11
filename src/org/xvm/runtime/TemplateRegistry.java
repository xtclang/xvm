package org.xvm.runtime;


import java.io.File;

import java.net.URL;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.Const;
import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.Function;
import org.xvm.runtime.template.Ref;
import org.xvm.runtime.template.Service;
import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.types.xProperty;
import org.xvm.runtime.template.xClass;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xModule;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xType;


/**
 * The type registry.
 */
public class TemplateRegistry
    {
    public final Container f_container;
    public final Adapter f_adapter;

    // native templates
    private final Map<String, Class> f_mapTemplateClasses = new HashMap<>();

    // cache - ClassTemplates by name
    private final Map<String, ClassTemplate> f_mapTemplatesByName = new ConcurrentHashMap<>();

    // cache - ClassTemplates by type
    private final Map<TypeConstant, ClassTemplate> f_mapTemplateByType = new ConcurrentHashMap<>();

    public final static TypeConstant[] VOID = ConstantPool.NO_TYPES;

    TemplateRegistry(Container container)
        {
        f_container = container;
        f_adapter = container.f_adapter;

        loadNativeTemplates();
        }

    private void loadNativeTemplates()
        {
        Class clzObject = xObject.class;
        URL url = clzObject.getProtectionDomain().getCodeSource().getLocation();
        String sRoot = url.getFile();

        File dirNative = new File(sRoot, "org/xvm/runtime/template");
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
                    String sClass = "org.xvm.runtime.template." + sPackage + "x" + sSimpleName;

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

    // see Container.start()
    protected void initNativeInterfaces()
        {
        ConstantPool pool = f_container.f_pool;

        // initialize necessary INSTANCE references
        new Enum(this, (ClassStructure) pool.clzEnum().getComponent(), true).initDeclared();
        new Const(this, (ClassStructure) pool.clzConst().getComponent(), true).initDeclared();
        new Function(this, (ClassStructure) pool.clzFunction().getComponent(), true).initDeclared();
        new Service(this, (ClassStructure) pool.clzService().getComponent(), true).initDeclared();
        new Ref(this, (ClassStructure) pool.clzRef().getComponent(), true).initDeclared();
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
        return template == null ? getTemplate(getClassConstant(sName)) : template;
        }

    public ClassTemplate getTemplate(IdentityConstant constClass)
        {
        // TODO: thread safety
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
            return clz.getConstructor(TemplateRegistry.class, ClassStructure.class, Boolean.TYPE).
                    newInstance(this, structClass, Boolean.TRUE);
            }
        catch (Exception e)
            {
            throw new RuntimeException("Constructor failed for " + clz.getName(), e);
            }
        }

    /**
     * Retrieve a template for the specified constant.
     */
    public ClassTemplate getConstTemplate(Constant constValue)
        {
        switch (constValue.getFormat())
            {
            case Array:
                return xArray.INSTANCE;

            case Int64:
                return xInt64.INSTANCE;

            case IntLiteral:
            case Int8:
            case Int16:
            case Int32:
            case Int128:
            case VarInt:
            case UInt8:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case VarUInt:
            case FPLiteral:
            case Float16:
            case Float32:
            case Float64:
            case Float128:
            case VarFloat:
            case Dec32:
            case Dec64:
            case Dec128:
            case VarDec:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Char:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case String:
                return xString.INSTANCE;

            case Date:
            case Time:
            case DateTime:
            case Duration:
            case TimeInterval:
            case Version:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case SingletonConst:
                {
                SingletonConstant constEnum = (SingletonConstant) constValue;
                ClassTemplate template = getTemplate(constEnum.getValue());
                assert template.isSingleton();
                return template;
                }

            case Tuple:
                return xTuple.INSTANCE;

            case UInt8Array:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Set:
            case MapEntry:
            case Map:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Module:
                return xModule.INSTANCE;

            case Package:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
                return xClass.INSTANCE;

            case Property:
                return xProperty.INSTANCE;

            case Method:
                return Function.INSTANCE;

            case TerminalType:
            case AnnotatedType:
            case ParameterizedType:
                return xClass.INSTANCE;

            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                return xType.INSTANCE;

            case MultiMethod:
            case Register:
            case Signature:
            case Typedef:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            default:
                throw new IllegalStateException(constValue.toString());
            }
        }

    // ----- TypeCompositions -----

    // ensure a TypeComposition for a type referred by a TypeConstant in the ConstantPool
    public TypeComposition resolveClass(int nTypeConstId, GenericTypeResolver resolver)
        {
        TypeConstant type = (TypeConstant)
                f_container.f_pool.getConstant(nTypeConstId); // must exist

        return resolveClass(type.resolveGenerics(resolver));
        }

    // produce a TypeComposition based on the specified TypeConstant
    // using the specified actual type parameters
    public TypeComposition resolveClass(TypeConstant typeActual)
        {
        return getTemplate(typeActual).ensureClass(typeActual);
        }

    // obtain a ClassTemplate for the specified type
    public ClassTemplate getTemplate(TypeConstant typeActual)
        {
        ClassTemplate template = f_mapTemplateByType.get(typeActual);
        if (template == null)
            {
            if (typeActual.isSingleDefiningConstant())
                {
                ClassConstant constClz = (ClassConstant) typeActual.getDefiningConstant();
                template = getTemplate(constClz);
                }
            else
                {
                // TODO we may need to move this logic to the TypeConstant classes
                throw new UnsupportedOperationException();
                }
            f_mapTemplateByType.put(typeActual, template);
            }
        return template;
        }
    }
