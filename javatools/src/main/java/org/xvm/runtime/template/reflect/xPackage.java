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

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of Package interface.
 */
public class xPackage
        extends xConst
    {
    public static xPackage INSTANCE;

    public xPackage(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
            ConstantPool pool = f_container.getConstantPool();
            LIST_MAP_TYPE = pool.ensureParameterizedTypeConstant(
                    pool.ensureEcstasyTypeConstant("collections.ListMap"),
                    pool.typeString(), pool.typeClass());
            LIST_MAP_TEMPLATE = f_container.getTemplate(LIST_MAP_TYPE);
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof PackageConstant idPackage)
            {
            PackageStructure pkg     = (PackageStructure) idPackage.getComponent();
            TypeConstant     typePkg = pkg.isModuleImport()
                    ? pkg.getImportedModule().getIdentityConstant().getType()
                    : idPackage.getType();

            TypeComposition clazz = frame.f_context.f_container.resolveClass(typePkg);
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
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(
            (((PackageHandle) hValue1).getId().equals(((PackageHandle) hValue2).getId()))));
        }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xOrdered.makeHandle(
            (((PackageHandle) hValue1).getId().compareTo(((PackageHandle) hValue2).getId()))));
        }

    @Override
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
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
        ConstantPool   pool = frame.poolContext();
        ClassStructure pkg  = hTarget.getStructure();

        if (!pkg.getIdentityConstant().isShared(pool))
            {
            return frame.raiseException("Foreign package");
            }

        Container       container = frame.f_context.f_container;
        TypeComposition clzMap    = ensureListMapComposition(container);

        Map<String, Component>  mapChildren = pkg.getChildByNameMap();
        ArrayList<StringHandle> listNames   = new ArrayList<>(mapChildren.size());
        ArrayList<ObjectHandle> listClasses = new ArrayList<>(mapChildren.size());
        boolean                 fDeferred   = false;
        for (Map.Entry<String, Component> entry : mapChildren.entrySet())
            {
            Component component = entry.getValue();
            if (component instanceof ClassStructure && !component.isSynthetic())
                {
                IdentityConstant id     = component.getIdentityConstant();
                ObjectHandle     hClass = frame.getConstHandle(pool.ensureClassConstant(id.getType()));

                listNames  .add(xString.makeHandle(entry.getKey()));
                listClasses.add(hClass);
                fDeferred |= Op.isDeferred(hClass);
                }
            }

        StringHandle[] ahNames   = listNames  .toArray(Utils.STRINGS_NONE);
        ObjectHandle[] ahClasses = listClasses.toArray(Utils.OBJECTS_NONE);

        ObjectHandle hNames = xArray.makeStringArrayHandle(ahNames);

        if (fDeferred)
            {
            Frame.Continuation stepNext = frameCaller ->
                {
                ObjectHandle hClasses = xArray.createImmutableArray(
                    xClass.ensureArrayComposition(container), ahClasses);
                return Utils.constructListMap(frame, clzMap, hNames, hClasses, iReturn);
                };

            return new Utils.GetArguments(ahClasses, stepNext).doNext(frame);
            }

        ObjectHandle hClasses = xArray.createImmutableArray(
            xClass.ensureArrayComposition(container), ahClasses);
        return Utils.constructListMap(frame, clzMap, hNames, hClasses, iReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Module isModuleImport()}.
     */
    public int invokeIsModuleImport(Frame frame, PackageHandle hTarget, int[] aiReturn)
        {
        ClassStructure struct = hTarget.getStructure();
        if (struct instanceof ModuleStructure module && !module.isMainModule())
            {
            return frame.assignValues(aiReturn, xBoolean.TRUE, hTarget);
            }

        if (struct instanceof PackageStructure pkg && pkg.isModuleImport())
            {
            ModuleStructure module        = pkg.getImportedModule();
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
     * @return the TypeComposition for {@code ListMap<String, Class>}
     */
    public static TypeComposition ensureListMapComposition(Container container)
        {
        return container.ensureClassComposition(LIST_MAP_TYPE, LIST_MAP_TEMPLATE);
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Create a new PackageHandle for the specified TypeComposition and place it on the stack.
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int createPackageHandle(Frame frame, TypeComposition clazz)
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
            return obj instanceof PackageHandle that && this.getId().equals(that.getId());
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    private static TypeConstant  LIST_MAP_TYPE;
    private static ClassTemplate LIST_MAP_TEMPLATE;
    }