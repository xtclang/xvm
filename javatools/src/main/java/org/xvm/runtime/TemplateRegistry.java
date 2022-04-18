package org.xvm.runtime;


import java.io.File;
import java.io.IOException;

import java.net.URL;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.jar.JarFile;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.Child;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTType;

import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;

import org.xvm.util.Handy;


/**
 * The template registry.
 */
public class TemplateRegistry
    {
    public TemplateRegistry(ModuleRepository repository)
        {
        f_repository = repository;
        }

    public void loadNativeTemplates()
        {
        ModuleStructure moduleRoot   = f_repository.loadModule(Constants.ECSTASY_MODULE);
        ModuleStructure moduleNative = f_repository.loadModule(NATIVE_MODULE);

        // "root" is a merge of "native" module into the "system"
        FileStructure containerRoot = new FileStructure(moduleRoot);
        containerRoot.merge(moduleNative, true);

        // obtain the cloned modules that belong to the merged container
        m_moduleSystem = (ModuleStructure) containerRoot.getChild(Constants.ECSTASY_MODULE);
        m_moduleNative = (ModuleStructure) containerRoot.getChild(NATIVE_MODULE);

        ConstantPool pool = containerRoot.getConstantPool();
        ConstantPool.setCurrentPool(pool);

        if (pool.getNakedRefType() == null)
            {
            ClassStructure clzNakedRef = (ClassStructure) m_moduleNative.getChild("NakedRef");
            pool.setNakedRefType(clzNakedRef.getFormalType());
            }

        Class clzObject = xObject.class;
        URL    url      = clzObject.getProtectionDomain().getCodeSource().getLocation();
        String sRoot    = url.getFile();
        Map<String, Class> mapTemplateClasses = new HashMap<>();
        if (sRoot.endsWith(".jar"))
            {
            scanNativeJarDirectory(sRoot, "org/xvm/runtime/template", mapTemplateClasses);
            }
        else
            {
            File dirTemplates = new File(sRoot, "org/xvm/runtime/template");
            scanNativeDirectory(dirTemplates, "", mapTemplateClasses);
            }

        // we need a number of INSTANCE static variables to be set up right away
        // (they are used by the ClassTemplate constructor)
        storeNativeTemplate(new xObject (this, getClassStructure("Object"),  true));
        storeNativeTemplate(new xEnum   (this, getClassStructure("Enum"),    true));
        storeNativeTemplate(new xConst  (this, getClassStructure("Const"),   true));
        storeNativeTemplate(new xService(this, getClassStructure("Service"), true));

        for (Map.Entry<String, Class> entry : mapTemplateClasses.entrySet())
            {
            ClassStructure structClass = getClassStructure(entry.getKey());
            if (structClass == null)
                {
                // this is a native class for a composite type;
                // it will be declared by the corresponding "primitive"
                // (see xArray.initNative() for an example)
                continue;
                }

            if (f_mapTemplatesByType.containsKey(
                    structClass.getIdentityConstant().getType()))
                {
                // already loaded - one of the "base" classes
                continue;
                }

            Class<ClassTemplate> clz = entry.getValue();

            try
                {
                storeNativeTemplate(clz.getConstructor(
                    TemplateRegistry.class, ClassStructure.class, Boolean.TYPE).
                    newInstance(this, structClass, Boolean.TRUE));
                }
            catch (Exception e)
                {
                throw new RuntimeException("Constructor failed for " + clz.getName(), e);
                }
            }

        // add run-time templates
        f_mapTemplatesByType.put(pool.typeFunction(), xRTFunction.INSTANCE);
        f_mapTemplatesByType.put(pool.typeType()    , xRTType    .INSTANCE);

        // clone the map since the loop below can add to it
        Set<ClassTemplate> setTemplates = new HashSet<>(f_mapTemplatesByType.values());

        for (ClassTemplate template : setTemplates)
            {
            template.registerNativeTemplates();
            }

        Utils.initNative(this);

        for (ClassTemplate template : f_mapTemplatesByType.values())
            {
            template.initNative();
            }
        ConstantPool.setCurrentPool(null);
        }

    private void scanNativeJarDirectory(String sJarFile, String sPackage, Map<String, Class> mapTemplateClasses)
        {
        JarFile jf;
        try
            {
            jf = new JarFile(sJarFile);
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }

        jf.stream().filter(e -> isNativeClass(sPackage, e.getName()))
                   .forEach(e -> mapTemplateClasses.put(componentName(e.getName()), classForName(e.getName())));
        }

    private static boolean isNativeClass(String sPackage, String sFile)
        {
        return sFile.startsWith(sPackage)
            && sFile.endsWith(".class")
            && sFile.indexOf('$') < 0
            && sFile.charAt(sFile.lastIndexOf('/') + 1) == 'x';
        }

    private static String componentName(String sFile)
        {
        // input : org/xvm/runtime/template/numbers/xFloat64.class
        // output: numbers.Float64
        String[]      parts = Handy.parseDelimitedString(sFile, '/');
        StringBuilder sb    = new StringBuilder();
        for (int i = 4, c = parts.length - 1; i < c; ++i)
            {
            sb.append(parts[i])
              .append('.');
            }
        String sClass = parts[parts.length-1];
        assert sClass.charAt(0) == 'x';
        assert sClass.endsWith(".class");
        sb.append(sClass, 1, sClass.indexOf('.'));
        return sb.toString();
        }

    private static Class classForName(String sFile)
        {
        assert sFile.endsWith(".class");
        String sClz = sFile.substring(0, sFile.length() - ".class".length()).replace('/', '.');
        try
            {
            return Class.forName(sClz);
            }
        catch (ClassNotFoundException e)
            {
            throw new RuntimeException(e);
            }
        }

    // sPackage is either empty or ends with a dot
    private void scanNativeDirectory(File dirNative, String sPackage, Map<String, Class> mapTemplateClasses)
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
                        mapTemplateClasses.put(sQualifiedName, Class.forName(sClass));
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
                    scanNativeDirectory(dir, sPackage.isEmpty() ? sName + '.' : sPackage + sName + '.',
                        mapTemplateClasses);
                    }
                }
            }
        }

    protected void storeNativeTemplate(ClassTemplate template)
        {
        // register just a naked underlying type
        TypeConstant typeBase = template.getClassConstant().getType();

        registerNativeTemplate(typeBase, template);
        }

    public void registerNativeTemplate(TypeConstant type, ClassTemplate template)
        {
        f_mapTemplatesByType.putIfAbsent(type, template);
        }


    // ----- templates and structures --------------------------------------------------------------

    /**
     * Create a new FileStructure for the specified module built on top of the system modules.
     *
     * @param moduleApp  the module to build a FileStructure for
     *
     * @return a new FileStructure
     */
    public FileStructure createFileStructure(ModuleStructure moduleApp)
        {
        FileStructure structApp = new FileStructure(m_moduleSystem);
        structApp.merge(m_moduleNative, false);
        structApp.merge(moduleApp, true);

        assert structApp.validateConstants();

        return structApp;
        }

    // used only by the native templates
    public ClassStructure getClassStructure(String sName)
        {
        // this call (class by name) can only come from the root module
        Component comp = getComponent(sName);
        while (comp instanceof TypedefStructure)
            {
            comp = ((TypedefStructure) comp).getType().getSingleUnderlyingClass(true).getComponent();
            }

        return (ClassStructure) comp;
        }

    public Component getComponent(String sName)
        {
        return sName.startsWith(PREF_NATIVE)
                ? m_moduleNative.getChildByPath(sName.substring(PREF_LENGTH))
                : m_moduleSystem.getChildByPath(sName);
        }

    // this call (id by name) can only come from the root module
    public IdentityConstant getIdentityConstant(String sName)
        {
        try
            {
            return f_mapIdByName.computeIfAbsent(sName, s ->
                getClassStructure(s).getIdentityConstant());
            }
        catch (NullPointerException e)
            {
            throw new IllegalArgumentException("Missing constant: " + sName);
            }
        }

    /**
     * @return a ClassTemplate for a type associated with the specified constant
     */
    public ClassTemplate getTemplate(Constant constValue)
        {
        return getTemplate(getConstType(constValue)); // must exist
        }

    /**
     * Ensure a ClassTemplate for the specified type.
     */
    public ClassTemplate getTemplate(TypeConstant typeActual)
        {
        ClassTemplate template = f_mapTemplatesByType.get(typeActual);
        if (template == null)
            {
            if (typeActual.isSingleDefiningConstant())
                {
                IdentityConstant idClass = typeActual.getSingleUnderlyingClass(true);
                template = getTemplate(idClass).getTemplate(typeActual);
                f_mapTemplatesByType.put(typeActual, template);
                }
            else
                {
                throw new UnsupportedOperationException();
                }
            }
        return template;
        }

    /**
     * Ensure a ClassTemplate for the specified name.
     *
     * Note: this call can only come from the root module.
     */
    public ClassTemplate getTemplate(String sName)
        {
        return getTemplate(getIdentityConstant(sName));
        }

    /**
     * Ensure a ClassTemplate for the specified class identity.
     */
    public ClassTemplate getTemplate(IdentityConstant constClass)
        {
        return f_mapTemplatesByType.computeIfAbsent(constClass.getType(), type ->
            {
            Component struct = constClass.getComponent();
            ClassStructure structClass = (ClassStructure) struct;
            if (structClass == null)
                {
                throw new RuntimeException("Missing class structure: " + constClass);
                }

            ClassTemplate template;
            switch (structClass.getFormat())
                {
                case ENUMVALUE:
                case ENUM:
                    template = new xEnum(this, structClass, false);
                    template.initNative();
                    break;

                case MIXIN:
                case CLASS:
                case INTERFACE:
                    template = structClass.isVirtualChild()
                        ? new Child(this, structClass, false)
                        : new xObject(this, structClass, false);
                    break;

                case SERVICE:
                    template = new xService(this, structClass, false);
                    break;

                case CONST:
                    template = structClass.isException()
                            ? new xException(this, structClass, false)
                            : new xConst(this, structClass, false);
                    break;

                case MODULE:
                    template = new xModule(this, structClass, false);
                    break;

                case PACKAGE:
                    template = new xPackage(this, structClass, false);
                    break;

                default:
                    throw new UnsupportedOperationException("Format is not supported: " + structClass);
                }
            return template;
            });
        }

    /**
     * Produce a TypeComposition based on the specified TypeConstant.
     */
    public TypeComposition resolveClass(TypeConstant typeActual)
        {
        if (typeActual instanceof PropertyClassTypeConstant typeProp)
            {
            ClassComposition clz = (ClassComposition) resolveClass(
                                        typeProp.getParentType().removeAccess());
            return clz.ensurePropertyComposition(typeProp.getPropertyInfo());
            }
        return getTemplate(typeActual).ensureClass(typeActual.normalizeParameters());
        }

    /**
     * Obtain an object type for the specified constant.
     */
    private TypeConstant getConstType(Constant constValue)
        {
        String sComponent;

        switch (constValue.getFormat())
            {
            case Char:
            case String:
            case IntLiteral:
            case Bit:
            case Nibble:
            case CInt8:
            case Int8:
            case CInt16:
            case Int16:
            case CInt32:
            case Int32:
            case CInt64:
            case Int64:
            case CInt128:
            case Int128:
            case CIntN:
            case IntN:
            case CUInt8:
            case UInt8:
            case CUInt16:
            case UInt16:
            case CUInt32:
            case UInt32:
            case CUInt64:
            case UInt64:
            case CUInt128:
            case UInt128:
            case CUIntN:
            case UIntN:
            case FPLiteral:
            case BFloat16:
            case Float16:
            case Float32:
            case Float64:
            case Float128:
            case FloatN:
            case Dec32:
            case Dec64:
            case Dec128:
            case DecN:
            case Array:
            case UInt8Array:
            case Tuple:
            case Path:
            case Date:
            case Time:
            case DateTime:
            case Duration:
            case Range:
            case Version:
            case Module:
            case Package:
            case RegEx:
                return constValue.getType();

            case FileStore:
                sComponent = "_native.fs.CPFileStore";
                break;

            case FSDir:
                sComponent = "_native.fs.CPDirectory";
                break;

            case FSFile:
                sComponent = "_native.fs.CPFile";
                break;

            case Map:
                sComponent = "collections.ListMap";
                break;

            case Set:
                // see xArray.createConstHandle()
                sComponent = "collections.Array";
                break;

            case MapEntry:
                throw new UnsupportedOperationException("TODO: " + constValue);

            case Class:
            case DecoratedClass:
            case NativeClass:
                sComponent = "reflect.Class";
                break;

            case PropertyClassType:
                sComponent = "_native.reflect.RTProperty";
                break;

            case Method:
                sComponent = ((MethodConstant) constValue).isFunction()
                        ? "_native.reflect.RTFunction" : "_native.reflect.RTMethod";
                break;

            case AnnotatedType:
            case ParameterizedType:
            case TerminalType:
            case ImmutableType:
            case AccessType:
            case UnionType:
            case IntersectionType:
            case DifferenceType:
                sComponent = "_native.reflect.RTType";
                break;

            case MultiMethod:   // REVIEW does the compiler ever generate this?
            case Typedef:       // REVIEW does the compiler ever generate this?
            case TypeParameter: // REVIEW does the compiler ever generate this?
            case Signature:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            default:
                throw new IllegalStateException(constValue.toString());
            }

        return getComponent(sComponent).getIdentityConstant().getType();
        }

    public static final String NATIVE_MODULE = Constants.PROTOTYPE_MODULE;
    public static final String PREF_NATIVE   = "_native.";
    public static final int    PREF_LENGTH   = PREF_NATIVE.length();

    public final ModuleRepository f_repository;
    private      ModuleStructure  m_moduleSystem;
    private      ModuleStructure  m_moduleNative;

    // cache - IdentityConstant by name (only for core classes)
    private final Map<String, IdentityConstant> f_mapIdByName = new ConcurrentHashMap<>();

    // cache - ClassTemplates by type
    private final Map<TypeConstant, ClassTemplate> f_mapTemplatesByType = new ConcurrentHashMap<>();
    }