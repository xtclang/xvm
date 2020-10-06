package org.xvm.runtime.template._native.reflect;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native ModuleTemplate implementation.
 */
public class xRTModuleTemplate
        extends xRTClassTemplate
    {
    public static xRTModuleTemplate INSTANCE;

    public xRTModuleTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        if (this == INSTANCE)
            {
            MODULE_TEMPLATE_COMPOSITION = ensureClass(getCanonicalType(),
                pool().ensureEcstasyTypeConstant("reflect.ModuleTemplate"));

            markNativeProperty("qualifiedName");
            markNativeProperty("moduleNamesByPath");
            }
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "qualifiedName":
                {
                ModuleStructure module = (ModuleStructure) hTemplate.getComponent();
                return frame.assignValue(iReturn,
                    xString.makeHandle(module.getIdentityConstant().getName()));
                }

            case "moduleNamesByPath":
                return getPropertyModuleNamesByPath(frame, hTemplate, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    /**
     * Implements property: moduleNamesByPath.get()
     */
    public int getPropertyModuleNamesByPath(Frame frame, ComponentTemplateHandle hTemplate, int iReturn)
        {
        // TODO GG: how to cache the result?
        ModuleStructure  module = (ModuleStructure) hTemplate.getComponent();
        ClassComposition clzMap = ensureListMapComposition();

        // starting with this module, find all module dependencies, and the shortest path to each
        Map<ModuleConstant, String> mapModulePaths = module.collectDependencies();
        int cModules = mapModulePaths.size() - 1;
        if (cModules == 0)
            {
            return Utils.constructListMap(frame, clzMap,
                    xString.ensureEmptyArray(), xString.ensureEmptyArray(), iReturn);
            }

        StringHandle[] ahPaths = new StringHandle[cModules];
        StringHandle[] ahNames = new StringHandle[cModules];
        int            index       = 0;
        for (Map.Entry<ModuleConstant, String> entry : mapModulePaths.entrySet())
            {
            ModuleConstant idDep = entry.getKey();
            if (!idDep.equals(module.getIdentityConstant()))
                {
                ahPaths[index] = xString.makeHandle(entry.getValue());
                ahNames[index] = xString.makeHandle(idDep.getName());
                ++index;
                }
            }

        ArrayHandle haPaths = xArray.makeStringArrayHandle(ahPaths);
        ArrayHandle haNames = xArray.makeStringArrayHandle(ahNames);

        return Utils.constructListMap(frame, clzMap, haPaths, haNames, iReturn);
        }

    /**
     * @return the ClassComposition for ListMap<String, String>
     */
    private static ClassComposition ensureListMapComposition()
        {
        ClassComposition clz = LISTMAP_CLZ;
        if (clz == null)
            {
            ConstantPool pool     = INSTANCE.pool();
            TypeConstant typeList = pool.ensureEcstasyTypeConstant("collections.ListMap");
            typeList = pool.ensureParameterizedTypeConstant(typeList, pool.typeString(), pool.typeString());
            LISTMAP_CLZ = clz = INSTANCE.f_templates.resolveClass(typeList);
            assert clz != null;
            }
        return clz;
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link ModuleStructure}.
     *
     * @param module  the {@link ModuleStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(ModuleStructure module)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(MODULE_TEMPLATE_COMPOSITION, module);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition MODULE_TEMPLATE_COMPOSITION;
    private static ClassComposition LISTMAP_CLZ;
    }
