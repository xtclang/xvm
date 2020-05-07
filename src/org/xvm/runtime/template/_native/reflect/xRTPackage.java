package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * Native implementation of Package interface.
 */
public class xRTPackage
        extends xConst
    {
    public static xRTPackage INSTANCE;

    public xRTPackage(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("isModuleImport", null, null);
        markNativeMethod("getChildNamesAndClasses", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "isModuleImport":
                return invokeIsModuleImport(frame, (PackageHandle) hTarget, aiReturn);

            case "getChildNamesAndClasses":
                return invokeGetChildNamesAndClasses(frame, (PackageHandle) hTarget, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
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

        public ClassStructure getStructure()
            {
            return (ClassStructure) getId().getComponent();
            }
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Module isModuleImport()}.
     */
    public int invokeIsModuleImport(Frame frame, PackageHandle hTarget, int[] aiReturn)
        {
        ClassStructure pkg = hTarget.getStructure();
        if (pkg instanceof PackageStructure && ((PackageStructure) pkg).isModuleImport())
            {
            ModuleStructure  module        = ((PackageStructure) pkg).getImportedModule();
            ModuleConstant   idModule      = module.getIdentityConstant();
            ConstantPool     pool          = frame.poolContext();
            Constant         constInstance = pool.ensureSingletonConstConstant(idModule);
            ObjectHandle     hInstance     = frame.getConstHandle(constInstance);
            if (Op.isDeferred(hInstance))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hInstance};
                Frame.Continuation stepNext = frameCaller ->
                        frameCaller.assignValues(aiReturn, xBoolean.TRUE, ahValue[0]);
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }
            return frame.assignValues(aiReturn, xBoolean.TRUE, hInstance);
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code (String[], Class[]) getChildNamesAndClasses()}.
     */
    public int invokeGetChildNamesAndClasses(Frame frame, PackageHandle hTarget, int[] aiReturn)
        {
        ClassStructure          pkg         = hTarget.getStructure();
        Map<String, Component>  mapChildren = pkg.getChildByNameMap();
        ArrayList<StringHandle> listNames   = new ArrayList<>(mapChildren.size());
        ArrayList<ObjectHandle> listClasses = new ArrayList<>(mapChildren.size());
        boolean                 fDeferred   = false;
        for (Entry<String, Component> entry : mapChildren.entrySet())
            {
            Component component = entry.getValue();
            if (component instanceof ClassStructure)
                {
                listNames.add(xString.makeHandle(entry.getKey()));
                ObjectHandle hClass = frame.getConstHandle(component.getIdentityConstant());
                fDeferred |= Op.isDeferred(hClass);
                listClasses.add(hClass);
                }
            }

        ObjectHandle[] ahNames   = listNames  .toArray(Utils.OBJECTS_NONE);
        ObjectHandle[] ahClasses = listClasses.toArray(Utils.OBJECTS_NONE);

        ArrayHandle hNames = xString.INSTANCE.ensureArrayTemplate().createArrayHandle(
            xString.INSTANCE.ensureArrayComposition(), ahNames);

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ArrayHandle hClasses = xRTClass.INSTANCE.ensureArrayTemplate().createArrayHandle(
                        xRTClass.INSTANCE.ensureArrayComposition(), ahClasses);
                return frame.assignValues(aiReturn, hNames, hClasses);
                };

            return new Utils.GetArguments(ahClasses, stepNext).doNext(frame);
            }

        ArrayHandle hClasses = xRTClass.INSTANCE.ensureArrayTemplate().createArrayHandle(
                xRTClass.INSTANCE.ensureArrayComposition(), ahClasses);
        return frame.assignValues(aiReturn, hNames, hClasses);
        }
    }