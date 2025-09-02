package org.xvm.runtime.template._native.reflect;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xPackage.PackageHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;

import org.xvm.runtime.template._native.mgmt.xCoreRepository;


/**
 * Native FileTemplate implementation.
 */
public class xRTFileTemplate
        extends xRTComponentTemplate {
    public static xRTFileTemplate INSTANCE;

    public xRTFileTemplate(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        FILE_TEMPLATE_TYPE = pool().ensureEcstasyTypeConstant("reflect.FileTemplate");
        LINK_MODULES_METHOD = f_struct.findMethod("linkModules", 1);

        markNativeProperty("mainModule");
        markNativeProperty("resolved");
        markNativeProperty("contents");
        markNativeProperty("createdMillis");

        markNativeMethod("extractVersionImpl", STRING, null);
        markNativeMethod("resolve", null, null);
        markNativeMethod("replace", null, null);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ComponentTemplateHandle hFile      = (ComponentTemplateHandle) hTarget;
        FileStructure           fileStruct = (FileStructure) hFile.getComponent();
        switch (sPropName) {
        case "mainModule":
            return frame.assignValue(iReturn,
                xRTModuleTemplate.makeHandle(frame.f_context.f_container, fileStruct.getModule()));

        case "resolved":
            return frame.assignValue(iReturn, xBoolean.makeHandle(fileStruct.isLinked()));

        case "contents":
            return getPropertyContents(frame, fileStruct, iReturn);

        case "createdMillis": {
            File fileOS = fileStruct.getOSFile();
            if (fileOS != null) {
                try {
                    BasicFileAttributes attr =
                            Files.readAttributes(fileOS.toPath(), BasicFileAttributes.class);
                    return frame.assignValue(iReturn,
                            xInt64.makeHandle(attr.lastModifiedTime().toMillis()));
                } catch (IOException ignore) {}
            }
            return frame.assignValue(iReturn, xInt64.makeHandle(0L));
        }
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        ComponentTemplateHandle hFile = (ComponentTemplateHandle) hTarget;
        FileStructure           file  = (FileStructure) hFile.getComponent();
        switch (method.getName()) {
        case "resolve":
            return invokeResolve(frame, file, hArg,
                    Utils.OBJECTS_NONE, Utils.OBJECTS_NONE, iReturn);

        case "replace":
            return invokeReplace(frame, file, (ArrayHandle) hArg);
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        ComponentTemplateHandle hFile = (ComponentTemplateHandle) hTarget;
        FileStructure           file  = (FileStructure) hFile.getComponent();
        switch (method.getName()) {
        case "extractVersionImpl": { // conditional ModuleTemplate extractVersionImpl(String version)
            String sVersion = ((StringHandle) ahArg[0]).getStringValue();

            ModuleStructure module = file.getModule();
            if (!sVersion.isEmpty()) {
                Version version = new Version(sVersion);
                if (module.containsVersion(version)) {
                    module = module.extractVersion(version);
                } else {
                    module = null;
                }
            }
            return module == null
                ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                : frame.assignValues(aiReturn,
                        xBoolean.TRUE,
                        xRTModuleTemplate.makeHandle(frame.f_context.f_container, module));
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Implementation of "Byte[] contents.get()".
     */
    private int getPropertyContents(Frame frame, FileStructure fileStruct, int iReturn) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            fileStruct.writeTo(stream);
        } catch (IOException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }

        return frame.assignValue(iReturn,
            xArray.makeByteArrayHandle(stream.toByteArray(), xArray.Mutability.Constant));
    }

    /**
     * Native implementation used by "void resolve(ModuleRepository repository)" method and
     * "Container.Linker.resolveAndLink()".
     */
    public int invokeResolve(Frame frame, FileStructure file, ObjectHandle hRepo,
                             ObjectHandle[] ahSharedModules, ObjectHandle[] ahAddModules, int iReturn) {
        Container container   = frame.f_context.f_container;
        int       cShared     = ahSharedModules.length;
        int       cAdditional = ahAddModules.length;
        if (file.isLinked()) {
            if (cShared + cAdditional == 0) {
                return frame.assignValue(iReturn, makeHandle(container, file));
            }
        } else {
            file = container.createFileStructure(file.getModule());
        }

        // TODO GG: for now, we treat shared and additional modules in the same way, just adding
        //          them to the new type system; we will need to add some facility that would
        //          allow the runtime to distinguish between the two and prohibit sharing of
        //          types that belong to the "additional" modules and allow sharing the state by
        //          "shared" modules
        for (ObjectHandle hSharedModule : ahSharedModules) {
            PackageHandle hModule = (PackageHandle) hSharedModule;
            file.merge((ModuleStructure) hModule.getStructure(), true, false);
            assert file.validateConstants();
        }

        for (ObjectHandle hAddModule : ahAddModules) {
            ComponentTemplateHandle hModule = (ComponentTemplateHandle) hAddModule;
            file.merge((ModuleStructure) hModule.getComponent(), true, false);
            assert file.validateConstants();
        }

        if (hRepo.getTemplate() instanceof xCoreRepository) {
            ModuleConstant idMissing = file.linkModules(f_container.getModuleRepository(), true);
            return idMissing == null
                    ? frame.assignValue(iReturn, makeHandle(container, file))
                    : frame.raiseException("Missing dependent module: " + idMissing.getName());
        }

        ObjectHandle[] ahArg = new ObjectHandle[LINK_MODULES_METHOD.getMaxVars()];
        ahArg[0] = hRepo;

        FileStructure fileUnlinked = file;
        switch (frame.call1(LINK_MODULES_METHOD, makeHandle(container, fileUnlinked), ahArg, iReturn)) {
        case Op.R_NEXT:
            fileUnlinked.markLinked();
            return frame.assignValue(iReturn, makeHandle(container, fileUnlinked));

        case Op.R_CALL:
            frame.m_frameNext.addContinuation(frameCaller -> {
                fileUnlinked.markLinked();
                return frameCaller.assignValue(iReturn, makeHandle(container, fileUnlinked));
            });
            return Op.R_CALL;

        case Op.R_EXCEPTION:
            return Op.R_EXCEPTION;

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Native implementation of "void replace(ModuleTemplate[] unresolved)" method.
     */
    private int invokeReplace(Frame frame, FileStructure file, ArrayHandle hArray) {
        List<ModuleStructure> listUnlinked = new ArrayList<>();

        GenericArrayDelegate haGeneric = (GenericArrayDelegate) hArray.m_hDelegate;
        for (long i = 0, c = haGeneric.m_cSize; i < c; i++) {
            ComponentTemplateHandle hModule        = (ComponentTemplateHandle) haGeneric.get(i);
            ModuleStructure         moduleUnlinked = (ModuleStructure) hModule.getComponent();
            ModuleStructure         moduleReplace  = file.getModule(moduleUnlinked.getIdentityConstant());

            if (moduleReplace == null) {
                return frame.raiseException("Missing module \"" + moduleUnlinked.getName() +
                                            "\" at " + file.getModuleId());
            }

            if (moduleReplace.isFingerprint()) {
                listUnlinked.add(moduleUnlinked);
            }
        }
        file.replace(listUnlinked);
        return Op.R_NEXT;
    }

    @Override
    protected int invokeChildren(Frame frame, ComponentTemplateHandle hComponent, int iReturn) {
        // calling the super() would pick up all modules, including the native, so we limit
        // the modules to the dependents of the main module

        FileStructure   file   = (FileStructure) hComponent.getComponent();
        ModuleStructure module = file.getModule();

        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();

        int            cModules  = mapModulePaths.size() - 1;
        ObjectHandle[] ahModule  = new ObjectHandle[cModules];
        Container      container = frame.f_context.f_container;
        int            index     = 0;

        for (ModuleConstant idDep : mapModulePaths.keySet()) {
            if (!idDep.equals(module.getIdentityConstant())) {
                ahModule[index++] = makeComponentHandle(container, file.getModule(idDep));
            }
        }
        assert index == cModules;

        TypeComposition clzArray = container.resolveClass(ensureComponentArrayType());
        return frame.assignValue(iReturn,
                xArray.createImmutableArray(clzArray, ahModule));

    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        FileStructure module = (FileStructure) ((ComponentTemplateHandle) hTarget).getComponent();
        return frame.assignValue(iReturn, xString.makeHandle(module.getModuleId().getName()));
    }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified file {@link FileStructure}.
     *
     * @param container   the container the handle should belong to
     * @param fileStruct  the {@link FileStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(Container container, FileStructure fileStruct) {
        // note: no need to initialize the struct because there are no natural fields
        TypeComposition clzFile = INSTANCE.ensureClass(container,
                INSTANCE.getCanonicalType(), FILE_TEMPLATE_TYPE);
        return new ComponentTemplateHandle(clzFile, fileStruct);
    }


    // ----- constants -----------------------------------------------------------------------------

    public static TypeConstant     FILE_TEMPLATE_TYPE;
    private static MethodStructure LINK_MODULES_METHOD;
}