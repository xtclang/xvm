package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;


/**
 * Native implementation of Package interface.
 */
public class xPackage
        extends ClassTemplate
    {
    public static xPackage INSTANCE;

    public xPackage(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
        markNativeMethod("isModuleImport", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof PackageConstant)
            {
            PackageConstant  idPackage   = (PackageConstant) constant;
            TypeConstant     typePackage = idPackage.getType();
            ClassComposition clazz       = ensureClass(typePackage, typePackage);

            return createPackageHandle(frame, clazz);
            }

        return super.createConstHandle(frame, constant);
        }

    protected int createPackageHandle(Frame frame, ClassComposition clazz)
        {
        MethodStructure methodID = clazz.ensureAutoInitializer();
        if (methodID == null)
            {
            return frame.assignValue(Op.A_STACK, new PackageHandle(clazz));
            }

        PackageHandle hStruct = new PackageHandle(clazz.ensureAccess(Access.STRUCT));
        Frame         frameID = frame.createFrame1(methodID, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);

        frameID.addContinuation(frameCaller ->
            frameCaller.assignValue(Op.A_STACK, hStruct.ensureAccess(Access.PUBLIC)));

        return frame.callInitialized(frameID);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        PackageHandle hPackage = (PackageHandle) hTarget;

        switch (sPropName)
            {
            case "simpleName":
                {
                PackageConstant idPackage = (PackageConstant) hPackage.getId();
                return frame.assignValue(iReturn, xString.makeHandle(idPackage.getName()));
                }

            case "qualifiedName":
                return buildStringValue(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackageHandle hPackage = (PackageHandle) hTarget;

        PackageConstant idPackage = (PackageConstant) hPackage.getId();
        ModuleConstant  idModule  = idPackage.getModuleConstant();
        return frame.assignValue(iReturn,
            xString.makeHandle(idModule.getName() + ':' + idPackage.getPathString()));
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class PackageHandle
            extends GenericHandle
        {
        public PackageHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public IdentityConstant getId()
            {
            return (IdentityConstant) getType().getDefiningConstant();
            }
        }
    }