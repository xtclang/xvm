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

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of Module interface.
 */
public class xModule
        extends xPackage
    {
    public static xModule INSTANCE;

    public xModule(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        // while these properties are naturally implementable, they are accessed
        // by the TypeSystem constructor for modules that belong to the constructed
        // TypeSystem, creating "the chicken or the egg" problem
        markNativeProperty("simpleName");
        markNativeProperty("qualifiedName");
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ModuleConstant)
            {
            ModuleConstant   idModule   = (ModuleConstant) constant;
            TypeConstant     typeModule = idModule.getType();
            ClassComposition clazz      = ensureClass(typeModule, typeModule);

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
        VersionConstant ver = null; // TODO CP need version constant on linked ModuleStructure

        return ver == null
                ? frame.assignValue(iReturn, xNullable.NULL)
                : frame.assignDeferredValue(iReturn, frame.getConstHandle(ver));
        }

    /**
     * Implements property: modulesByPath.get()
     */
    public int getPropertyModulesByPath(Frame frame, PackageHandle hTarget, int iReturn)
        {
        // TODO GG: how to cache the result?
        ModuleConstant   idModule = (ModuleConstant) hTarget.getId();
        ModuleStructure  module   = (ModuleStructure) idModule.getComponent();
        ClassComposition clzMap   = ensureListMapComposition();

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();
        int cModules = mapModulePaths.size() - 1;
        if (cModules == 0)
            {
            return Utils.constructListMap(frame, clzMap,
                    xString.ensureEmptyArray(), ensureEmptyArray(), iReturn);
            }

        ObjectHandle[] ahPaths   = new ObjectHandle[cModules];
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

        ArrayHandle hPaths = xString.ensureArrayTemplate().createArrayHandle(
            xString.ensureArrayComposition(), ahPaths);

        ClassComposition clzArray = ensureArrayComposition();
        xArray           template = (xArray) clzArray.getTemplate();

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ArrayHandle hModules = template.createArrayHandle(clzArray, ahModules);
                return Utils.constructListMap(frame, clzMap, hPaths, hModules, iReturn);
                };
            return new Utils.GetArguments(ahModules, stepNext).doNext(frame);
            }

        ArrayHandle hModules = template.createArrayHandle(clzArray, ahModules);
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

        if (oResult instanceof TypeConstant)
            {
            TypeConstant     typeClz = (TypeConstant) oResult;
            IdentityConstant idClz   = typeClz.getConstantPool().ensureClassConstant(typeClz);
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

        if (fClass && sClassOrType.length() == 0)
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
        catch (RuntimeException e) {}

        if (expr != null && errs.getSeriousErrorCount() == 0)
            {
            TypeCompositionStatement stmtModule = new TypeCompositionStatement(module, source, expr);
            if (new StageMgr(expr, Compiler.Stage.Resolved, errs).fastForward(3))
                {
                TypeConstant typeClz = null;
                try
                    {
                    typeClz = expr.ensureTypeConstant();
                    }
                catch (RuntimeException e) {}

                if (typeClz != null && !typeClz.containsUnresolved())
                    {
                    return typeClz;
                    }
                }
            }

        return null;
        }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassComposition for an Array of Module
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZ;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeModuleArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeModule());
            ARRAY_CLZ = clz = INSTANCE.f_templates.resolveClass(typeModuleArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of Module
     */
    public static ArrayHandle ensureEmptyArray()
        {
        if (ARRAY_EMPTY == null)
            {
            ClassComposition clzArray = ensureArrayComposition();
            xArray           template = (xArray) clzArray.getTemplate();
            ARRAY_EMPTY = template.createArrayHandle(clzArray, Utils.OBJECTS_NONE);
            }
        return ARRAY_EMPTY;
        }

    /**
     * @return the ClassComposition for ListMap<String, Module>
     */
    private static ClassComposition ensureListMapComposition()
        {
        ClassComposition clz = LISTMAP_CLZ;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeList = pool.ensureEcstasyTypeConstant("collections.ListMap");
            typeList = pool.ensureParameterizedTypeConstant(typeList, pool.typeString(), pool.typeModule());
            LISTMAP_CLZ = clz = INSTANCE.f_templates.resolveClass(typeList);
            assert clz != null;
            }
        return clz;
        }

    private static ClassComposition LISTMAP_CLZ;


    // ----- data members --------------------------------------------------------------------------

    private static ClassComposition ARRAY_CLZ;
    private static ArrayHandle      ARRAY_EMPTY;
    }
