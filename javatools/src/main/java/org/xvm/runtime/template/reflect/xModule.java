package org.xvm.runtime.template.reflect;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.VersionConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.TypeCompositionStatement;
import org.xvm.compiler.ast.TypeExpression;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of Module interface.
 */
public class xModule
        extends xPackage
    {
    public static xModule INSTANCE;

    public xModule(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        if (this == INSTANCE)
            {
            ConstantPool pool = f_container.getConstantPool();

            MODULE_ARRAY_TYPE  = pool.ensureArrayType(pool.typeModule());
            EMPTY_MODULE_ARRAY = pool.ensureArrayConstant(MODULE_ARRAY_TYPE, Constant.NO_CONSTS);

            VERSION_DEFAULT = new VersionConstant(pool(), new Version("CI"));

            // while these properties are naturally implementable, they are accessed
            // by the TypeSystem constructor for modules that belong to the constructed
            // TypeSystem, creating "the chicken or the egg" problem
            markNativeProperty("simpleName");
            markNativeProperty("qualifiedName");

            invalidateTypeInfo();
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ModuleConstant idModule)
            {
            Container       container  = frame.f_context.f_container;
            TypeConstant    typeModule = idModule.getType();
            TypeComposition clazz      = container.getTemplate(idModule).
                                            ensureClass(container, typeModule, typeModule);
            return createPackageHandle(frame, clazz);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        PackageHandle hModule = (PackageHandle) hTarget;
        switch (sPropName)
            {
            case "qualifiedName":
                return getPropertyQualifiedName(frame, hModule, iReturn);

            case "simpleName":
                return getPropertySimpleName(frame, hModule, iReturn);

            case "version":
                return getPropertyVersion(frame, hModule, iReturn);

            case "modulesByPath":
                return getPropertyModulesByPath(frame, hModule, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        PackageHandle hModule = (PackageHandle) hTarget;
        switch (method.getName())
            {
            case "classForName":
                assert ahArg.length == 1;
                return invokeClassForName(frame, hModule, ahArg[0], aiReturn);

            case "typeForName":
                assert ahArg.length == 1;
                return invokeTypeForName(frame, hModule, ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: simpleName.get()
     */
    public int getPropertySimpleName(Frame frame, PackageHandle hModule, int iReturn)
        {
        String sName = ((ModuleConstant) hModule.getId()).getUnqualifiedName();
        return frame.assignValue(iReturn, xString.makeHandle(sName));
        }

    /**
     * Implements property: qualifiedName.get()
     */
    public int getPropertyQualifiedName(Frame frame, PackageHandle hModule, int iReturn)
        {
        String sName = hModule.getId().getName();
        return frame.assignValue(iReturn, xString.makeHandle(sName));
        }

    /**
     * Implements property: version.get()
     */
    public int getPropertyVersion(Frame frame, PackageHandle hModule, int iReturn)
        {
        ModuleStructure module = (ModuleStructure) hModule.getId().getComponent();
        VersionConstant ver    = module.getVersion();

        return frame.assignDeferredValue(iReturn,
            frame.getConstHandle(ver == null ? VERSION_DEFAULT : ver));
        }

    /**
     * Implements property: modulesByPath.get()
     */
    public int getPropertyModulesByPath(Frame frame, PackageHandle hTarget, int iReturn)
        {
        // TODO GG: how to cache the result?
        Container       container = frame.f_context.f_container;
        ModuleConstant  idModule  = (ModuleConstant) hTarget.getId();
        ModuleStructure module    = (ModuleStructure) idModule.getComponent();
        TypeComposition clzMap    = container.resolveClass(ensureListMapType());

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();
        int cModules = mapModulePaths.size() - 1;
        if (cModules == 0)
            {
            return Utils.constructListMap(frame, clzMap,
                    xString.ensureEmptyArray(), ensureEmptyArray(container), iReturn);
            }

        StringHandle[] ahPaths   = new StringHandle[cModules];
        ObjectHandle[] ahModules = new ObjectHandle[cModules];
        boolean        fDeferred = false;
        int            index     = 0;
        for (Map.Entry<ModuleConstant, String> entry : mapModulePaths.entrySet())
            {
            ModuleConstant idDep = entry.getKey();
            if (idDep != idModule)
                {
                ObjectHandle hM = frame.getConstHandle(idDep);
                ahPaths  [index] = xString.makeHandle(entry.getValue());
                ahModules[index] = hM;
                fDeferred |= Op.isDeferred(hM);
                ++index;
                }
            }

        ObjectHandle    hPaths   = xArray.makeStringArrayHandle(ahPaths);
        TypeComposition clzArray = ensureArrayComposition(container);

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ObjectHandle hModules = xArray.createImmutableArray(clzArray, ahModules);
                return Utils.constructListMap(frame, clzMap, hPaths, hModules, iReturn);
                };
            return new Utils.GetArguments(ahModules, stepNext).doNext(frame);
            }

        ObjectHandle hModules = xArray.createImmutableArray(clzArray, ahModules);
        return Utils.constructListMap(frame, clzMap, hPaths, hModules, iReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Class classForName(String name)}.
     */
    public int invokeClassForName(Frame frame, PackageHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        ModuleStructure module  = (ModuleStructure) hTarget.getStructure();
        String          sClass  = ((StringHandle) hArg).getStringValue();
        Object          oResult = resolveClass(module.getFileStructure(), module, sClass);
        if (oResult == null)
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        if (oResult instanceof TypeConstant typeClz)
            {
            IdentityConstant idClz = typeClz.getConstantPool().ensureClassConstant(typeClz);
            return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(idClz));
            }

        return frame.raiseException((String) oResult);
        }

    /**
     * Implementation for: {@code conditional Type typeForName(String name)}.
     */
    public int invokeTypeForName(Frame frame, PackageHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        ModuleStructure module  = (ModuleStructure) hTarget.getStructure();
        String          sType   = ((StringHandle) hArg).getStringValue();
        Object          oResult = resolveType(module.getFileStructure(), module, sType);
        if (oResult == null)
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        if (oResult instanceof TypeConstant)
            {
            TypeConstant typeClz = ((TypeConstant) oResult).getType();
            return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(typeClz));
            }

        return frame.raiseException((String) oResult);
        }

    /**
     * Resolve a class string into a class type.
     *
     * @param structTS  (optional) the FileStructure representing the TypeSystem
     * @param module    (optional) the module to begin the name resolution from
     * @param sClass    the class string
     *
     * @return either a TypeConstant or null if the class couldn't be resolved for any reason
     */
    public static Object resolveClass(FileStructure structTS, ModuleStructure module, String sClass)
        {
        return resolveClassOrType(structTS, module, sClass, true);
        }

    /**
     * Resolve a type string into a type.
     *
     * @param structTS  (optional) the FileStructure representing the TypeSystem
     * @param module    (optional) the module to begin the name resolution from
     * @param sType     the type string
     *
     * @return either a TypeConstant or null if the type couldn't be resolved for any reason
     */
    public static Object resolveType(FileStructure structTS, ModuleStructure module, String sType)
        {
        return resolveClassOrType(structTS, module, sType, false);
        }

    private static Object resolveClassOrType(FileStructure structTS, ModuleStructure module, String sClassOrType, boolean fClass)
        {
        if (module == null)
            {
            module = structTS == null
                ? INSTANCE.f_struct.getFileStructure().getModule()  // only Ecstasy classes
                : structTS.getModule();
            }

        if (fClass && sClassOrType.isEmpty())
            {
            // module.classForName("") is the module itself
            return module.getIdentityConstant().getType();
            }

        Source         source = new Source(sClassOrType);
        ErrorList      errs   = new ErrorList(10);
        Parser         parser = new Parser(source, errs);
        TypeExpression expr   = null;
        try
            {
            expr = fClass ? parser.parseClassExpression() : parser.parseTypeExpression();
            }
        catch (RuntimeException ignore) {}

        if (expr != null && errs.getSeriousErrorCount() == 0)
            {
            // create a TypeCompositionStatement parent or "expr"
            new TypeCompositionStatement(module, source, expr);

            if (new StageMgr(expr, Compiler.Stage.Resolved, errs).fastForward(3))
                {
                TypeConstant typeClz = null;
                try
                    {
                    typeClz = expr.ensureTypeConstant();
                    }
                catch (RuntimeException ignore) {}

                if (typeClz != null && !typeClz.containsUnresolved())
                    {
                    return typeClz;
                    }
                }
            }

        return null;
        }


    // ----- TypeComposition, and handle caching ---------------------------------------------------

    /**
     * @return the TypeComposition for an Array of Module
     */
    public static TypeComposition ensureArrayComposition(Container container)
        {
        return container.ensureClassComposition(MODULE_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the handle for an empty Array of Module
     */
    public static ArrayHandle ensureEmptyArray(Container container)
        {
        ArrayHandle haEmpty = (ArrayHandle) container.f_heap.getConstHandle(EMPTY_MODULE_ARRAY);
        if (haEmpty == null)
            {
            haEmpty = xArray.createImmutableArray(ensureArrayComposition(container), Utils.OBJECTS_NONE);
            container.f_heap.saveConstHandle(EMPTY_MODULE_ARRAY, haEmpty);
            }
        return haEmpty;
        }

    /**
     * @return the TypeConstant for {@code ListMap<String, Module>}
     */
    private static TypeConstant ensureListMapType()
        {
        TypeConstant type = LISTMAP_TYPE;
        if (type == null)
            {
            ConstantPool pool = INSTANCE.pool();
            LISTMAP_TYPE = type = pool.ensureParameterizedTypeConstant(
                    pool.ensureEcstasyTypeConstant("collections.ListMap"),
                    pool.typeString(), pool.typeModule());
            }
        return type;
        }


    // ----- data members --------------------------------------------------------------------------

    private static TypeConstant    MODULE_ARRAY_TYPE;
    private static ArrayConstant   EMPTY_MODULE_ARRAY;
    private static TypeConstant    LISTMAP_TYPE;
    private static VersionConstant VERSION_DEFAULT;
    }