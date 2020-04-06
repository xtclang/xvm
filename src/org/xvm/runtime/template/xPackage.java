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
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native implementation of Package interface.
 */
public class xPackage
        extends xConst
    {
    public static xPackage INSTANCE;

    public xPackage(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        PackageHandle hPackage = (PackageHandle) hTarget;

        switch (sPropName)
            {
            case "simpleName":
                {
                return frame.assignValue(iReturn, xString.makeHandle(getSimpleName(hPackage)));
                }

            case "qualifiedName":
                return frame.assignValue(iReturn, xString.makeHandle(getQualifiedName(hPackage)));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
            xString.makeHandle(getQualifiedName((PackageHandle) hTarget)));
        }

    @Override
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
            xInt64.makeHandle(getQualifiedName((PackageHandle) hTarget).length()));
        }

    @Override
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn)
        {
        PackageHandle hPackage = (PackageHandle) hTarget;

        return xString.callAppendTo(frame,
            xString.makeHandle(getQualifiedName(hPackage)), hAppender, iReturn);
        }

    /**
     * @return a simple name for a package or module
     */
    protected String getSimpleName(PackageHandle hPackage)
        {
        return hPackage.getId().getName();
        }

    /**
     * @return a qualified name for a package or module
     */
    protected String getQualifiedName(PackageHandle hPackage)
        {
        PackageConstant idPackage = (PackageConstant) hPackage.getId();
        ModuleConstant  idModule  = idPackage.getModuleConstant();
        return idModule.getName() + ':' + idPackage.getPathString();
        }

    /**
     * Create a new PackageHandle for the specified ClassComposition and place it on the stack.
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int createPackageHandle(Frame frame, ClassComposition clazz)
        {
        PackageHandle   hStruct     = new PackageHandle(clazz.ensureAccess(Access.STRUCT));
        MethodStructure constructor = clazz.getTemplate().getStructure().findMethod("construct", 0);

        return proceedConstruction(frame, constructor, true, hStruct, Utils.OBJECTS_NONE, Op.A_STACK);
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