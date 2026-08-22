package org.xvm.runtime.template.reflect;


import java.util.Map;
import java.util.Objects;

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
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Lazy;


/**
 * Native implementation of Module interface.
 */
public class xModule
        extends xPackage {

    public static xModule getInstance(Frame frame) {
        return NativeTemplates.get(frame).module();
    }

    public static xModule getInstance(Container container) {
        return NativeTemplates.get(container).module();
    }

    public xModule(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        if (NativeTemplates.get(this).isModule(this)) {
            // while these properties are naturally implementable, they are accessed
            // by the TypeSystem constructor for modules that belong to the constructed
            // TypeSystem, creating "the chicken or the egg" problem
            markNativeProperty("simpleName");
            markNativeProperty("qualifiedName");

            invalidateTypeInfo();
        }
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof ModuleConstant idModule) {
            return ensureConstHandle(frame, idModule, idModule.getType());
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        PackageHandle hModule = (PackageHandle) hTarget;
        switch (sPropName) {
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
                              ObjectHandle[] ahArg, int[] aiReturn) {
        PackageHandle hModule = (PackageHandle) hTarget;
        switch (method.getName()) {
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
    public int getPropertySimpleName(Frame frame, PackageHandle hModule, int iReturn) {
        String sName = ((ModuleConstant) hModule.getId()).getUnqualifiedName();
        return frame.assignValue(iReturn, xString.makeHandle(frame, sName));
    }

    /**
     * Implements property: qualifiedName.get()
     */
    public int getPropertyQualifiedName(Frame frame, PackageHandle hModule, int iReturn) {
        String sName = hModule.getId().getName();
        return frame.assignValue(iReturn, xString.makeHandle(frame, sName));
    }

    /**
     * Implements property: version.get()
     */
    public int getPropertyVersion(Frame frame, PackageHandle hModule, int iReturn) {
        ModuleStructure module = (ModuleStructure) hModule.getId().getComponent();
        VersionConstant ver    = module.getVersionConstant();

        return frame.assignDeferredValue(iReturn,
            frame.getConstHandle(ver == null ? f_constDefaultVersion.get(this) : ver));
    }

    /**
     * Implements property: modulesByPath.get()
     */
    public int getPropertyModulesByPath(Frame frame, PackageHandle hTarget, int iReturn) {
        // TODO GG: how to cache the result?
        Container       container = frame.f_context.f_container;
        ModuleConstant  idModule  = (ModuleConstant) hTarget.getId();
        ModuleStructure module    = (ModuleStructure) idModule.getComponent();
        TypeComposition clzMap    = container.resolveClass(ensureListMapType(container));

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();
        int cModules = mapModulePaths.size() - 1;
        if (cModules == 0) {
            return Utils.constructListMap(frame, clzMap,
                    xString.ensureEmptyArray(container), ensureEmptyArray(container), iReturn);
        }

        StringHandle[] ahPaths   = new StringHandle[cModules];
        ObjectHandle[] ahModules = new ObjectHandle[cModules];
        boolean        fDeferred = false;
        int            index     = 0;
        for (Map.Entry<ModuleConstant, String> entry : mapModulePaths.entrySet()) {
            ModuleConstant idDep = entry.getKey();
            if (idDep != idModule) {
                ObjectHandle hM = frame.getConstHandle(idDep);
                ahPaths  [index] = xString.makeHandle(container, entry.getValue());
                ahModules[index] = hM;
                fDeferred |= Op.isDeferred(hM);
                ++index;
            }
        }

        ObjectHandle    hPaths   = xArray.makeStringArrayHandle(container, ahPaths);
        TypeComposition clzArray = ensureArrayComposition(container);

        if (fDeferred) {
            Frame.Continuation stepNext = frameCaller -> {
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
    public int invokeClassForName(Frame frame, PackageHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
        ModuleStructure module  = (ModuleStructure) hTarget.getStructure();
        String          sClass  = ((StringHandle) hArg).getStringValue();
        Object          oResult = resolveClass(module.getFileStructure(), module, sClass);
        if (oResult == null) {
            return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
        }

        if (oResult instanceof TypeConstant typeClz) {
            IdentityConstant idClz = typeClz.getConstantPool().ensureClassConstant(typeClz);
            return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(idClz));
        }

        return frame.raiseException((String) oResult);
    }

    /**
     * Implementation for: {@code conditional Type typeForName(String name)}.
     */
    public int invokeTypeForName(Frame frame, PackageHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
        ModuleStructure module  = (ModuleStructure) hTarget.getStructure();
        String          sType   = ((StringHandle) hArg).getStringValue();
        Object          oResult = resolveType(module.getFileStructure(), module, sType);
        if (oResult == null) {
            return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
        }

        if (oResult instanceof TypeConstant) {
            TypeConstant typeClz = ((TypeConstant) oResult).getType();
            return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(typeClz));
        }

        return frame.raiseException((String) oResult);
    }

    /**
     * Resolve a class string into a class type.
     *
     * @param structTS  the FileStructure representing the TypeSystem; required when module is null
     * @param module    the module to begin the name resolution from
     * @param sClass    the class string
     *
     * @return either a TypeConstant or null if the class couldn't be resolved for any reason
     */
    public static Object resolveClass(FileStructure structTS, ModuleStructure module, String sClass) {
        return resolveClassOrType(structTS, module, sClass, true);
    }

    /**
     * Resolve a type string into a type.
     *
     * @param structTS  the FileStructure representing the TypeSystem; required when module is null
     * @param module    the module to begin the name resolution from
     * @param sType     the type string
     *
     * @return either a TypeConstant or null if the type couldn't be resolved for any reason
     */
    public static Object resolveType(FileStructure structTS, ModuleStructure module, String sType) {
        return resolveClassOrType(structTS, module, sType, false);
    }

    private static Object resolveClassOrType(FileStructure structTS, ModuleStructure module, String sClassOrType, boolean fClass) {
        if (module == null) {
            // The old fallback reached through xModule.INSTANCE.f_struct, which made this static
            // helper depend on whichever container last assigned the mutable INSTANCE field.
            module = Objects.requireNonNull(structTS, "structTS").getModule();
        }

        if (fClass && sClassOrType.isEmpty()) {
            // module.classForName("") is the module itself
            return module.getIdentityConstant().getType();
        }

        Source         source = new Source(sClassOrType);
        ErrorList      errs   = new ErrorList(10);
        Parser         parser = new Parser(source, errs);
        TypeExpression expr   = null;
        try {
            expr = fClass ? parser.parseClassExpression() : parser.parseTypeExpression();
        } catch (RuntimeException ignore) {}

        if (expr != null && errs.getSeriousErrorCount() == 0) {
            // create a TypeCompositionStatement parent or "expr"
            TypeCompositionStatement.forModule(module, source, expr);

            if (new StageMgr(expr, Compiler.Stage.Resolved, errs).fastForward(3)) {
                TypeConstant typeClz = null;
                try {
                    typeClz = expr.ensureTypeConstant();
                } catch (RuntimeException ignore) {}

                if (typeClz != null && !typeClz.containsUnresolved()) {
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
    public static TypeComposition ensureArrayComposition(Container container) {
        xModule template = template(container);
        return container.ensureClassComposition(
                template.f_typeModuleArray.get(template), xArray.getInstance(container));
    }

    /**
     * @return the handle for an empty Array of Module
     */
    public static ArrayHandle ensureEmptyArray(Container container) {
        xModule       template   = template(container);
        ArrayConstant constEmpty = template.f_constEmptyModuleArray.get(template);
        // Keep the owner heap local: this is shorter than repeated accessor chains and makes the
        // get/save pair visibly use the same owner-local cache.
        var           heap       = container.getConstHeap();
        ArrayHandle   haEmpty    = heap.getConstHandle(container, constEmpty, ArrayHandle.class);
        if (haEmpty == null) {
            haEmpty = xArray.createImmutableArray(ensureArrayComposition(container), Utils.OBJECTS_NONE);
            heap.saveConstHandle(container, constEmpty, haEmpty);
        }
        return haEmpty;
    }

    /**
     * @return the TypeConstant for {@code ListMap<String, Module>}
     */
    private static TypeConstant ensureListMapType(Container container) {
        // ConstantPool interning keeps this cached in the caller's owner. A static LISTMAP_TYPE
        // would pin the first initialized container and leak it into later containers.
        ConstantPool pool = container.getConstantPool();
        return pool.ensureParameterizedTypeConstant(
                pool.ensureEcstasyTypeConstant("maps.ListMap"),
                pool.typeString(), pool.typeModule());
    }


    // ----- data members --------------------------------------------------------------------------

    // These constants are tied to this template's ConstantPool. Lazy final fields preserve the
    // previous single-computation behavior without sharing one container's constants globally.
    private final Lazy.Owner<xModule, TypeConstant> f_typeModuleArray = Lazy.ofOwner(owner ->
            owner.pool().ensureArrayType(owner.pool().typeModule()));

    private final Lazy.Owner<xModule, ArrayConstant> f_constEmptyModuleArray = Lazy.ofOwner(owner ->
            owner.pool().ensureArrayConstant(owner.f_typeModuleArray.get(owner), Constant.NO_CONSTS));

    private final Lazy.Owner<xModule, VersionConstant> f_constDefaultVersion = Lazy.ofOwner(owner ->
            owner.pool().ensureVersionConstant(new Version("CI")));

    private static xModule template(Container container) {
        return getInstance(Objects.requireNonNull(container, "container"));
    }
}
