package org.xvm.runtime.template._native.reflect;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;

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
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.util.Severity;


/**
 * Native implementation of Module interface.
 */
public class xRTModule
        extends xRTPackage
    {
    public static xRTModule INSTANCE;

    public xRTModule(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("qualifiedName");
        markNativeProperty("simpleName");
        markNativeProperty("version");

        markNativeMethod("classForName", null, null);
        markNativeMethod("getModuleDependencies", null, null);

        getCanonicalType().invalidateTypeInfo();
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

            case "getModuleDependencies":
                return invokeGetModuleDependencies(frame, hModule, aiReturn);
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
     * Implementation for: {@code (String[], Module[]) getModuleDependencies()}.
     */
    public int invokeGetModuleDependencies(Frame frame, PackageHandle hTarget, int[] aiReturn)
        {
        ModuleConstant  idModule = (ModuleConstant) hTarget.getId();
        ModuleStructure module   = (ModuleStructure) idModule.getComponent();

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = collectDependencies(module);
        int cModules = mapModulePaths.size() - 1;
        if (cModules == 0)
            {
            return frame.assignValues(aiReturn, xString.ensureEmptyArray(), ensureEmptyArray());
            }

        ObjectHandle[] ahPaths   = new ObjectHandle[cModules];
        ObjectHandle[] ahModules = new ObjectHandle[cModules];
        boolean        fDeferred = false;
        int            index     = 0;
        for (Entry<ModuleConstant, String> entry : mapModulePaths.entrySet())
            {
            ModuleConstant idDep = entry.getKey();
            if (idDep != idModule)
                {
                ObjectHandle hModule = frame.getConstHandle(idDep);
                ahPaths  [index] = xString.makeHandle(entry.getValue());
                ahModules[index] = hModule;
                fDeferred |= Op.isDeferred(hModule);
                ++index;
                }
            }

        ArrayHandle hPaths = xString.ensureArrayTemplate().createArrayHandle(
            xString.ensureArrayComposition(), ahPaths);

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ArrayHandle hModules = ensureArrayTemplate().createArrayHandle(
                        ensureArrayComposition(), ahModules);
                return frame.assignValues(aiReturn, hPaths, hModules);
                };

            return new Utils.GetArguments(ahModules, stepNext).doNext(frame);
            }

        ArrayHandle hModules = ensureArrayTemplate().createArrayHandle(
                ensureArrayComposition(), ahModules);
        return frame.assignValues(aiReturn, hPaths, hModules);
        }

    /**
     * Given a module, build a list of all of the module dependencies, and the shortest path to
     * each.
     *
     * @param module   pass the primary module
     *
     * @return  a map containing all of the module dependencies, and the shortest path to each
     */
    public static Map<ModuleConstant, String> collectDependencies(ModuleStructure module)
        {
        Map<ModuleConstant, String> mapModulePaths = new HashMap<>();
        mapModulePaths.put(module.getIdentityConstant(), "");
        xRTModule.collectDependencies("", module, mapModulePaths);
        return mapModulePaths;
        }

    /**
     * Given a module, build a list of all of the module dependencies, and the shortest path to
     * each.
     *
     * @param sModulePath     pass "" for the primary module
     * @param moduleOrPkg     pass the primary module
     * @param mapModulePaths  pass a map containing all previously encountered modules (including
     *                        the current one)
     */
    private static void collectDependencies(String sModulePath, ClassStructure moduleOrPkg,
                                            Map<ModuleConstant, String> mapModulePaths)
        {
        for (Component child : moduleOrPkg.children())
            {
            if (child instanceof PackageStructure)
                {
                PackageStructure pkg = (PackageStructure) child;
                if (pkg.isModuleImport())
                    {
                    ModuleStructure moduleDep  = pkg.getImportedModule();
                    ModuleConstant  idDep      = moduleDep.getIdentityConstant();
                    String          sOldPath   = mapModulePaths.get(idDep);
                    String          sLocalPath = pkg.getIdentityConstant().getPathString();
                    String          sNewPath   = sModulePath.length() == 0
                                               ? sLocalPath
                                               : sModulePath + '.' + sLocalPath;
                    if (sOldPath == null)
                        {
                        mapModulePaths.put(idDep, sNewPath);
                        collectDependencies(sNewPath, moduleDep, mapModulePaths);
                        }
                    else if (sNewPath.length() < sOldPath.length())
                        {
                        mapModulePaths.put(idDep, sNewPath);

                        // replace everything else using the new path that was already registered
                        // as being reached via the old path
                        mapModulePaths.entrySet().stream()
                                .filter(e -> e.getValue().startsWith(sOldPath + '.'))
                                .forEach(e -> e.setValue(sNewPath + e.getValue().substring(sOldPath.length())));
                        }
                    }
                else
                    {
                    collectDependencies(sModulePath, pkg, mapModulePaths);
                    }
                }
            }
        }

    /**
     * Resolve a class string into a class type.
     *
     * @param typeSystem  (optional) the FileStructure representing the TypeSystem
     * @param module      (optional) the module to begin the name resolution from
     * @param sClass      the class string
     *
     * @return either a TypeConstant or a String error
     */
    public static Object resolveClass(FileStructure typeSystem, ModuleStructure module, String sClass)
        {
        if (module == null)
            {
            module = typeSystem == null
                    ? INSTANCE.f_struct.getFileStructure().getModule()  // only Ecstasy classes
                    : typeSystem.getModule();
            }

        if (sClass.length() == 0)
            {
            // module.classForName("") is the module itself
            return module.getIdentityConstant().getType();
            }

        Source         source = new Source(sClass);
        ErrorList      errs   = new ErrorList(10);
        TypeExpression expr   = new Parser(source, errs).parseClassExpression();
        if (errs.getSeriousErrorCount() == 0)
            {
            TypeCompositionStatement stmtModule = new TypeCompositionStatement(module, source, expr);
            if (new StageMgr(expr, Compiler.Stage.Resolved, errs).fastForward(3))
                {
                TypeConstant typeClz;
                try
                    {
                    typeClz = expr.ensureTypeConstant();
                    }
                catch (RuntimeException e)
                    {
                    return "Exception occurred while resolving \"" + sClass + "\": " + e;
                    }

                return typeClz == null || typeClz.containsUnresolved()
                        ? null
                        : typeClz;
                }
            }

        return errs.getErrors().stream()
                .filter(i -> (i.getSeverity().compareTo(Severity.ERROR) > 0))
                .findFirst().get().getMessage();
        }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Module
     */
    public static xArray ensureArrayTemplate()
        {
        xArray template = ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeModuleArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeModule());
            ARRAY_TEMPLATE = template = ((xArray) INSTANCE.f_templates.getTemplate(typeModuleArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Module
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeModuleArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeModule());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeModuleArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of Module
     */
    public static ObjectHandle.ArrayHandle ensureEmptyArray()
        {
        if (ARRAY_EMPTY == null)
            {
            ARRAY_EMPTY = ensureArrayTemplate().createArrayHandle(
                ensureArrayComposition(), new ObjectHandle[0]);
            }
        return ARRAY_EMPTY;
        }


    // ----- data members --------------------------------------------------------------------------

    private static xArray           ARRAY_TEMPLATE;
    private static ClassComposition ARRAY_CLZCOMP;
    private static ArrayHandle      ARRAY_EMPTY;
    }
