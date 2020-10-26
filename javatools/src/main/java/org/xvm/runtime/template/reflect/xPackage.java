package org.xvm.runtime.template.reflect;


import java.util.ArrayList;
import java.util.Map;

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
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTClass;


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
        PackageHandle hModule = (PackageHandle) hTarget;
        switch (sPropName)
            {
            case "classByName":
                return getPropertyClassByName(frame, hModule, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "isModuleImport":
                return invokeIsModuleImport(frame, (PackageHandle) hTarget, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(
            (((PackageHandle) hValue1).getId().equals(((PackageHandle) hValue2).getId()))));
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xOrdered.makeHandle(
            (((PackageHandle) hValue1).getId().compareTo(((PackageHandle) hValue2).getId()))));
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
            xInt64.makeHandle(((PackageHandle) hTarget).getId().hashCode()));
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: classByName.get()
     */
    public int getPropertyClassByName(Frame frame, PackageHandle hTarget, int iReturn)
        {
        // TODO GG: how to cache the result?
        ClassStructure   pkg    = hTarget.getStructure();
        ClassComposition clzMap = ensureListMapComposition();

        Map<String, Component>  mapChildren = pkg.getChildByNameMap();
        ArrayList<StringHandle> listNames   = new ArrayList<>(mapChildren.size());
        ArrayList<ObjectHandle> listClasses = new ArrayList<>(mapChildren.size());
        boolean                 fDeferred   = false;
        for (Map.Entry<String, Component> entry : mapChildren.entrySet())
            {
            Component component = entry.getValue();
            if (component instanceof ClassStructure)
                {
                listNames.add(xString.makeHandle(entry.getKey()));
                IdentityConstant id = component.getIdentityConstant();
                ObjectHandle hClass = frame.getConstHandle(
                        id.getConstantPool().ensureClassConstant(id.getType()));
                fDeferred |= Op.isDeferred(hClass);
                listClasses.add(hClass);
                }
            }

        StringHandle[] ahNames   = listNames  .toArray(new StringHandle[0]);
        ObjectHandle[] ahClasses = listClasses.toArray(Utils.OBJECTS_NONE);

        ArrayHandle hNames = xArray.makeStringArrayHandle(ahNames);

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ArrayHandle hClasses = xArray.INSTANCE.createArrayHandle(
                    xRTClass.ensureArrayComposition(), ahClasses);
                return Utils.constructListMap(frame, clzMap, hNames, hClasses, iReturn);
                };

            return new Utils.GetArguments(ahClasses, stepNext).doNext(frame);
            }

        ArrayHandle hClasses = xArray.INSTANCE.createArrayHandle(
            xRTClass.ensureArrayComposition(), ahClasses);
        return Utils.constructListMap(frame, clzMap, hNames, hClasses, iReturn);
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
            ModuleStructure module        = ((PackageStructure) pkg).getImportedModule();
            ModuleConstant  idModule      = module.getIdentityConstant();
            ConstantPool    pool          = frame.poolContext();
            Constant        constInstance = pool.ensureSingletonConstConstant(idModule);

            return frame.assignConditionalDeferredValue(aiReturn,
                    frame.getConstHandle(constInstance));
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }


    // ----- Helpers -------------------------------------------------------------------------------

    /**
     * @return the ClassComposition for ListMap<String, Class>
     */
    private static ClassComposition ensureListMapComposition()
        {
        ClassComposition clz = LISTMAP_CLZ;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeList = pool.ensureEcstasyTypeConstant("collections.ListMap");
            typeList = pool.ensureParameterizedTypeConstant(typeList, pool.typeString(), pool.typeClass());
            LISTMAP_CLZ = clz = INSTANCE.f_templates.resolveClass(typeList);
            assert clz != null;
            }
        return clz;
        }

    private static ClassComposition LISTMAP_CLZ;


    // ----- ObjectHandle --------------------------------------------------------------------------

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

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return getId().compareTo(((PackageHandle) that).getId());
            }

        @Override
        public int hashCode()
            {
            return getId().hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof PackageHandle &&
                getId().equals(((PackageHandle) obj).getId());
            }
        }
    }