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
import org.xvm.runtime.NativeTemplates;
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

import org.xvm.util.Lazy;


/**
 * Native ModuleTemplate implementation.
 */
public class xRTModuleTemplate
        extends xRTClassTemplate {
    public xRTModuleTemplate(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
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
            return frame.assignValue(iReturn, sVersion == null
                ? xNullable.makeHandle(frame)
                : xString.makeHandle(frame, sVersion));
        }

        case "modulesByPath":
            return getPropertyModulesByPath(frame, hTemplate, iReturn);

        case "fingerprint": {
            ModuleStructure module = hTemplate.getModuleStructure();
            return frame.assignValue(iReturn, xBoolean.makeHandle(module.isFingerprint()));
        }

        case "resolved": {
            ModuleStructure module = (ModuleStructure) hTemplate.getComponent();
            return frame.assignValue(iReturn, xBoolean.makeHandle(frame, module.getFileStructure().isLinked()));
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
        ObjectHandle haPaths     = xArray.makeStringArrayHandle(container, ahPaths);
        ObjectHandle haTemplates = makeTemplateArrayHandle(container, ahTemplate);

        return Utils.constructListMap(frame, clzMap, haPaths, haTemplates, iReturn);
    }

    private static ObjectHandle makeNullableStringHandle(String sValue) {
        return sValue == null ? xNullable.NULL : xString.makeHandle(sValue);
    }

    /**
     * @return the TypeConstant for ListMap<String, ModuleTemplate>
     */
    private static TypeConstant ensureListMapType(Container container) {
        // ConstantPool interning keeps this cached in the caller's owner. A static LISTMAP_TYPE
        // would pin the first initialized container and leak it into later containers.
        ConstantPool pool = container.getConstantPool();
        xRTModuleTemplate template = template(container);
        return pool.ensureParameterizedTypeConstant(
                pool.ensureEcstasyTypeConstant("maps.ListMap"),
                pool.typeString(),
                template.typeModuleTemplate(container));
    }

    private static ArrayHandle makeTemplateArrayHandle(Container container, ObjectHandle[] ahTemplate) {
        xRTModuleTemplate template = template(container);
        TypeComposition clzArray = container.ensureClassComposition(
                container.getConstantPool().ensureArrayType(template.typeModuleTemplate(container)),
                xArray.getInstance(container));
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
        xRTModuleTemplate template = template(container);
        TypeComposition   clz      = template.ensureClass(container,
                template.getCanonicalType(), template.typeModuleTemplate(container));
        return new ComponentTemplateHandle(clz, module);
    }


    // ----- data members --------------------------------------------------------------------------

    // This type is owned by a ConstantPool. Store it on the owner template and register it into the
    // caller's pool when constructing arrays/maps, instead of publishing one container's value in a
    // mutable static field.
    private final Lazy<TypeConstant> f_typeModuleTemplate = Lazy.of(() ->
            pool().ensureEcstasyTypeConstant("reflect.ModuleTemplate"));

    private static xRTModuleTemplate template(Container container) {
        return NativeTemplates.get(container).moduleTemplate();
    }

    private TypeConstant typeModuleTemplate(Container container) {
        return container.getConstantPool().register(f_typeModuleTemplate.get());
    }
}
