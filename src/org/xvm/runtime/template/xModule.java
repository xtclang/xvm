package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;

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
    public void initDeclared()
        {
        markNativeProperty("simpleName");
        markNativeProperty("qualifiedName");
        markNativeProperty("dependsOn");

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
            case "simpleName":
                {
                ModuleConstant idModule = (ModuleConstant) hModule.getId();
                return frame.assignValue(iReturn,
                        xString.makeHandle(idModule.getUnqualifiedName()));
                }

            case "qualifiedName":
                return buildStringValue(frame, hTarget, iReturn);

            case "dependsOn":
                {
                ModuleConstant     idModule  = (ModuleConstant) hModule.getId();
                ModuleStructure    module    = (ModuleStructure) idModule.getComponent();
                List<ObjectHandle> listDeps  = new ArrayList<>();
                boolean            fDeferred = false;

                for (Component child : module.children())
                    {
                    if (child instanceof PackageStructure)
                        {
                        PackageStructure pkg = (PackageStructure) child;
                        if (pkg.isModuleImport())
                            {
                            ModuleConstant idImport = pkg.getImportedModule().getIdentityConstant();
                            ObjectHandle   hImport  = frame.getConstHandle(idImport);

                            fDeferred |= Op.isDeferred(hImport);
                            listDeps.add(hImport);
                            }
                        }
                    }
                ConstantPool   pool        = pool();
                TypeConstant   typeArray   = pool.ensureParameterizedTypeConstant(
                                                pool.typeArray(), pool.typeModule());
                ClassComposition clzArray  = f_templates.resolveClass(typeArray);
                ObjectHandle[]   ahModules = listDeps.toArray(Utils.OBJECTS_NONE);
                if (fDeferred)
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        {
                        frameCaller.pushStack(
                            ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahModules));
                        return Op.R_NEXT;
                        };

                    return new Utils.GetArguments(ahModules, stepNext).doNext(frame);
                    }

                frame.pushStack(((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahModules));
                return Op.R_NEXT;
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackageHandle  hModule  = (PackageHandle) hTarget;
        ModuleConstant idModule = (ModuleConstant) hModule.getId();
        return frame.assignValue(iReturn,
                xString.makeHandle(idModule.getName()));
        }
    }
