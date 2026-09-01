package org.xvm.runtime.template._native.reflect;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.VersionTree;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native ModuleTemplate implementation.
 */
public class xRTModuleTemplate
        extends xRTClassTemplate {
    public xRTModuleTemplate(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        ConstantPool pool = f_container.getConstantPool();

        MODULE_TEMPLATE_TYPE = pool.ensureEcstasyTypeConstant("reflect.ModuleTemplate");

        markNativeProperty("qualifiedName");
        markNativeProperty("versionString");
        markNativeProperty("modulesByPath");
        markNativeProperty("fingerprint");
        markNativeProperty("resolved");

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ComponentTemplateHandle hTemplate = componentTemplateHandle(hTarget);
        switch (sPropName) {
        case "qualifiedName": {
            ModuleStructure module = hTemplate.getModuleStructure();
            return frame.assignValue(iReturn,
                xString.makeHandle(frame, module.getIdentityConstant().getName()));
        }

        case "versionString": {
            ModuleStructure       module   = hTemplate.getModuleStructure();
            String                sVersion;
            if (module.isFingerprint()) {
                VersionTree<Boolean> vtree = module.getFingerprintVersions();
                sVersion = vtree.isEmpty()
                        ? null
                        : vtree.findLowestVersion().toString();
            } else {
                sVersion = module.getVersionString();
            }
            return frame.assignValue(iReturn, makeNullableStringHandle(frame, sVersion));
        }

        case "modulesByPath":
            return getPropertyModulesByPath(frame, hTemplate, iReturn);

        case "fingerprint": {
            ModuleStructure module = hTemplate.getModuleStructure();
            return frame.assignValue(iReturn, xBoolean.makeHandle(module.isFingerprint()));
        }

        case "resolved": {
            ModuleStructure module = hTemplate.getModuleStructure();
            return frame.assignValue(iReturn, xBoolean.makeHandle(module.getFileStructure().isLinked()));
        }
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    /**
     * Implements property: modulesByPath.get()
     */
    public int getPropertyModulesByPath(Frame frame, ComponentTemplateHandle hTemplate, int iReturn) {
        // TODO GG: how to cache the result?
        ModuleStructure module    = hTemplate.getModuleStructure();
        Container       container = frame.f_context.f_container;
        TypeComposition clzMap    = container.resolveClass(ensureListMapType(container));

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();
        int                         cModules       = mapModulePaths.size() - 1;

        StringHandle[]            ahPaths    = new StringHandle[cModules];
        ComponentTemplateHandle[] ahTemplate = new ComponentTemplateHandle[cModules];
        int                       index      = 0;
        for (Map.Entry<ModuleConstant, String> entry : mapModulePaths.entrySet()) {
            ModuleConstant idDep = entry.getKey();
            if (!idDep.equals(module.getIdentityConstant())) {
                ModuleStructure moduleDep = module.getFileStructure().getModule(idDep);

                ahPaths[index]    = xString.makeHandle(container, entry.getValue());
                ahTemplate[index] = makeHandle(container, moduleDep);
                ++index;
            }
        }
        ObjectHandle haPaths     = xArray.makeStringArrayHandle(ahPaths);
        ObjectHandle haTemplates = makeTemplateArrayHandle(container, ahTemplate);

        return Utils.constructListMap(frame, clzMap, haPaths, haTemplates, iReturn);
    }

    private static ObjectHandle makeNullableStringHandle(Frame frame, String sValue) {
        return sValue == null ? xNullable.NULL : xString.makeHandle(frame, sValue);
    }

    /**
     * @return the TypeConstant for ListMap<String, ModuleTemplate>
     */
    private static TypeConstant ensureListMapType(Container container) {
        TypeConstant type = LISTMAP_TYPE;
        if (type == null) {
            ConstantPool pool = container.nativeTemplate(xRTModuleTemplate.class).pool();
            LISTMAP_TYPE = type = pool.ensureParameterizedTypeConstant(
                    pool.ensureEcstasyTypeConstant("maps.ListMap"),
                    pool.typeString(), MODULE_TEMPLATE_TYPE);
        }
        return type;
    }

    private static ArrayHandle makeTemplateArrayHandle(Container container, ObjectHandle[] ahTemplate) {
        TypeComposition clzArray = container.ensureClassComposition(
                container.getConstantPool().ensureArrayType(MODULE_TEMPLATE_TYPE),
                        container.nativeTemplate(xArray.class));
        return xArray.makeArrayHandle(clzArray, ahTemplate.length, ahTemplate, Mutability.Constant);
    }

    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link ModuleStructure}.
     *
     * @param module  the {@link ModuleStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(Container container, ModuleStructure module) {
        // note: no need to initialize the struct because there are no natural fields
        xRTModuleTemplate template = container.nativeTemplate(xRTModuleTemplate.class);
        TypeComposition  clz      = template.ensureClass(container, template.getCanonicalType(),
                MODULE_TEMPLATE_TYPE);
        return new ComponentTemplateHandle(clz, module);
    }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeConstant MODULE_TEMPLATE_TYPE;
    private static TypeConstant LISTMAP_TYPE;
}
